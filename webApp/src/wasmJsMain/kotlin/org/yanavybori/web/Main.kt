package org.yanavybori.web

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.yanavybori.core.content.ElectionPackSource
import org.yanavybori.core.ui.LocalPlatformUi
import org.yanavybori.feature.observer.ObserverDependencies
import org.yanavybori.shared.SharedAppContainer
import org.yanavybori.shared.YaNaVyborahRoot

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val store = BrowserStore()
    val platformUi = BrowserPlatformUi()
    val source = object : ElectionPackSource {
        override suspend fun read(path: String): ByteArray = platformUi.readBundledFile("demo-election-pack/$path")
    }
    val dependencies = ObserverDependencies(
        electionPackRepository = store,
        knowledgeRepository = store,
        observationRepository = store,
        journalRepository = store,
        complaintRepository = store,
        counterRepository = store,
        reconciliationRepository = store,
        protocolRepository = store,
        mediaRepository = store,
        readPackFile = { path -> source.read(path) },
    )
    val container = SharedAppContainer(dependencies, source, store, "0.6.0")

    ComposeViewport(document.body!!) {
        CompositionLocalProvider(LocalPlatformUi provides platformUi) {
            YaNaVyborahRoot(container)
        }
    }
    document.getElementById("startup")?.remove()
    CoroutineScope(Dispatchers.Default).launch { container.bootstrapElectionPack() }
}
