package org.yanavybori.app

import android.content.Context
import org.yanavybori.core.common.ReconciliationEngine
import org.yanavybori.core.content.AssetElectionPackSource
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
import org.yanavybori.core.database.RoomUserDataRepository
import org.yanavybori.core.database.UserDatabase
import org.yanavybori.core.files.LocalPrivacyScanner
import org.yanavybori.core.files.PrivateMediaRepository
import org.yanavybori.feature.observer.ObserverDependencies

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val contentDatabase = ContentDatabase.create(appContext)
    private val userDatabase = UserDatabase.create(appContext)
    private val activeSessionStore = ActiveSessionStore(appContext)

    val electionPackRepository = RoomElectionPackRepository(contentDatabase.contentDao())
    private val knowledgeRepository = RoomKnowledgeRepository(contentDatabase.contentDao())
    private val observationRepository = RoomObservationRepository(
        userDatabase.observationDao(),
        userDatabase.checklistStateDao(),
        userDatabase.journalDao(),
        activeSessionStore,
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
    private val userDataRepository = RoomUserDataRepository(
        userDatabase.observationDao(),
        userDatabase.checklistStateDao(),
        userDatabase.journalDao(),
        userDatabase.complaintDao(),
        userDatabase.counterDao(),
        userDatabase.reconciliationDao(),
        userDatabase.protocolDao(),
        userDatabase.mediaDao(),
        activeSessionStore,
    )

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
        readPackFile = { path -> AssetElectionPackSource(appContext, "demo-election-pack").read(path) },
        reconciliationEngine = ReconciliationEngine(),
    )

    val shared = org.yanavybori.shared.SharedAppContainer(
        observerDependencies,
        AssetElectionPackSource(appContext, "demo-election-pack"),
        userDataRepository,
        BuildConfig.VERSION_NAME,
    )

    suspend fun bootstrapElectionPack() = shared.bootstrapElectionPack()

    fun close() {
        contentDatabase.close()
        userDatabase.close()
    }
}
