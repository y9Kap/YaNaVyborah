package org.yanavybori.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.yanavybori.core.common.ReconciliationEngine
import org.yanavybori.core.content.AssetElectionPackSource
import org.yanavybori.core.content.ElectionPackImporter
import org.yanavybori.core.crypto.AndroidKeystoreCryptoManager
import org.yanavybori.core.database.ActiveSessionStore
import org.yanavybori.core.database.ContentDatabase
import org.yanavybori.core.database.RoomComplaintRepository
import org.yanavybori.core.database.RoomCounterRepository
import org.yanavybori.core.database.RoomElectionPackRepository
import org.yanavybori.core.database.RoomJournalRepository
import org.yanavybori.core.database.RoomKnowledgeRepository
import org.yanavybori.core.database.RoomObservationRepository
import org.yanavybori.core.database.RoomProtocolRepository
import org.yanavybori.core.database.RoomReconciliationRepository
import org.yanavybori.core.database.UserDatabase
import org.yanavybori.core.files.LocalPrivacyScanner
import org.yanavybori.core.files.PrivateMediaRepository
import org.yanavybori.feature.observer.ObserverDependencies

sealed interface BootstrapState {
    data object Loading : BootstrapState
    data object Ready : BootstrapState
    data class Failed(val message: String) : BootstrapState
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val contentDatabase = ContentDatabase.create(appContext)
    private val userDatabase = UserDatabase.create(appContext)

    val electionPackRepository = RoomElectionPackRepository(contentDatabase.contentDao())
    private val knowledgeRepository = RoomKnowledgeRepository(contentDatabase.contentDao())
    private val observationRepository = RoomObservationRepository(
        userDatabase.observationDao(),
        userDatabase.checklistStateDao(),
        userDatabase.journalDao(),
        ActiveSessionStore(appContext),
    )
    private val journalRepository = RoomJournalRepository(userDatabase.journalDao())
    private val complaintRepository = RoomComplaintRepository(userDatabase.complaintDao())
    private val counterRepository = RoomCounterRepository(userDatabase.counterDao())
    private val reconciliationRepository = RoomReconciliationRepository(userDatabase.reconciliationDao())
    private val protocolRepository = RoomProtocolRepository(userDatabase.protocolDao())
    private val mediaRepository = PrivateMediaRepository(
        appContext,
        userDatabase.mediaDao(),
        AndroidKeystoreCryptoManager(),
        LocalPrivacyScanner(),
    )
    private val importer = ElectionPackImporter(electionPackRepository)

    val observerDependencies = ObserverDependencies(
        electionPackRepository = electionPackRepository,
        knowledgeRepository = knowledgeRepository,
        observationRepository = observationRepository,
        journalRepository = journalRepository,
        complaintRepository = complaintRepository,
        counterRepository = counterRepository,
        reconciliationRepository = reconciliationRepository,
        protocolRepository = protocolRepository,
        mediaRepository = mediaRepository,
        reconciliationEngine = ReconciliationEngine(),
    )

    private val mutableBootstrapState = MutableStateFlow<BootstrapState>(BootstrapState.Loading)
    val bootstrapState: StateFlow<BootstrapState> = mutableBootstrapState.asStateFlow()

    suspend fun bootstrapElectionPack() {
        mutableBootstrapState.value = BootstrapState.Loading
        runCatching {
            importer.import(AssetElectionPackSource(appContext, "demo-election-pack"))
        }.onSuccess {
            mutableBootstrapState.value = BootstrapState.Ready
        }.onFailure {
            mutableBootstrapState.value = BootstrapState.Failed(it.message ?: "Не удалось загрузить Election Pack")
        }
    }

    fun close() {
        contentDatabase.close()
        userDatabase.close()
    }
}
