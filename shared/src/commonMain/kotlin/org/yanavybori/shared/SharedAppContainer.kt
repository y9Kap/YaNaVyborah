package org.yanavybori.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yanavybori.core.content.ElectionPackImporter
import org.yanavybori.core.content.ElectionPackSource
import org.yanavybori.feature.observer.ObserverDependencies
import org.yanavybori.core.common.UserDataRepository

sealed interface BootstrapState {
    data object Loading : BootstrapState
    data object Ready : BootstrapState
    data class Failed(val message: String) : BootstrapState
}

/** Shared application lifecycle; the host owns and closes its persistence adapters. */
class SharedAppContainer(
    val observerDependencies: ObserverDependencies,
    private val electionPackSource: ElectionPackSource,
    val userDataRepository: UserDataRepository,
    val applicationVersion: String,
) {
    val electionPackRepository = observerDependencies.electionPackRepository
    private val importer = ElectionPackImporter(electionPackRepository)
    private val bootstrapMutex = Mutex()
    private val mutableBootstrapState = MutableStateFlow<BootstrapState>(BootstrapState.Loading)
    val bootstrapState: StateFlow<BootstrapState> = mutableBootstrapState.asStateFlow()

    suspend fun bootstrapElectionPack() = bootstrapMutex.withLock {
        mutableBootstrapState.value = BootstrapState.Loading
        try {
            importer.import(electionPackSource)
            mutableBootstrapState.value = BootstrapState.Ready
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableBootstrapState.value = BootstrapState.Failed(error.message ?: "Не удалось загрузить Election Pack")
        }
    }
}
