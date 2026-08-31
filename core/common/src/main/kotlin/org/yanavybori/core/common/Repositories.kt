package org.yanavybori.core.common

import kotlinx.coroutines.flow.Flow
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ChecklistItemState
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.CounterMark
import org.yanavybori.core.model.CounterSession
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.MediaAsset
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.PrivacyReport
import org.yanavybori.core.model.ProtocolSnapshot
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationSession
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.SearchResult
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.SituationAudience
import org.yanavybori.core.model.VotingDayDefinition

const val SESSION_DELETION_PASSWORD_MIN_LENGTH = 4

interface ElectionPackRepository {
    fun observeActiveManifest(): Flow<ElectionPackManifest?>
    suspend fun activeManifest(): ElectionPackManifest?
    suspend fun replaceAtomically(content: ElectionPackContent)
}

interface KnowledgeRepository {
    fun observeVotingDays(packId: String): Flow<List<VotingDayDefinition>>
    fun observeChecklistDefinitions(packId: String): Flow<List<ChecklistDefinition>>
    fun observeChecklistItems(packId: String): Flow<List<ChecklistItem>>
    fun observeSituations(packId: String, audience: SituationAudience): Flow<List<Situation>>
    fun observeLawReferences(packId: String): Flow<List<LawReference>>
    fun observeComplaintTemplates(packId: String): Flow<List<ComplaintTemplate>>
    fun observeReconciliationDefinitions(packId: String): Flow<List<ReconciliationDefinition>>
    fun observeReferenceDocuments(packId: String): Flow<List<ReferenceDocument>>
    suspend fun lawReference(id: String): LawReference?
    suspend fun search(packId: String, query: String): List<SearchResult>
}

interface ObservationRepository {
    fun observeActiveSession(): Flow<ObservationSession?>
    fun observeSessions(): Flow<List<ObservationSession>>
    suspend fun createSession(
        electionPackId: String,
        observerFullName: String,
        region: String,
        precinctNumber: String,
        precinctName: String?,
        commissionMemberNames: List<String>,
        deletionPassword: String,
        votingDayId: String,
        stageId: String,
    ): ObservationSession
    suspend fun selectSession(sessionId: String)
    suspend fun updateDayAndStage(sessionId: String, votingDayId: String, stageId: String)
    suspend fun finishSession(sessionId: String)
    suspend fun setDeletionPassword(sessionId: String, password: String)
    suspend fun deleteSession(sessionId: String, password: String)
    fun observeChecklistStates(sessionId: String, votingDayId: String): Flow<List<ChecklistItemState>>
    suspend fun setChecklistState(
        sessionId: String,
        votingDayId: String,
        checklistItemId: String,
        status: ChecklistStatus,
    ): ChecklistItemState
}

interface JournalRepository {
    fun observeEvents(sessionId: String): Flow<List<JournalEvent>>
    suspend fun getEvent(eventId: String): JournalEvent?
    suspend fun create(event: JournalEvent): JournalEvent
    suspend fun update(event: JournalEvent)
}

interface ComplaintRepository {
    fun observeComplaints(sessionId: String): Flow<List<Complaint>>
    suspend fun getComplaint(id: String): Complaint?
    suspend fun create(complaint: Complaint): Complaint
    suspend fun update(complaint: Complaint)
    suspend fun updateStatus(id: String, status: ComplaintStatus, submittedAt: Long? = null)
}

interface CounterRepository {
    fun observeCounters(sessionId: String, votingDayId: String): Flow<List<CounterSession>>
    fun observeMarks(counterSessionId: String): Flow<List<CounterMark>>
    fun observeLastMarksForDay(sessionId: String, votingDayId: String): Flow<List<CounterMark>>
    suspend fun createCounter(sessionId: String, votingDayId: String, label: String): CounterSession
    suspend fun increment(counterSessionId: String): CounterMark
    suspend fun decrement(counterSessionId: String): CounterMark
    suspend fun undoLast(counterSessionId: String): Boolean
    suspend fun stop(counterSessionId: String)
}

interface ReconciliationRepository {
    fun observeSessions(observationSessionId: String): Flow<List<ReconciliationSession>>
    suspend fun save(session: ReconciliationSession)
}

interface ProtocolRepository {
    fun observeSnapshots(observationSessionId: String): Flow<List<ProtocolSnapshot>>
    suspend fun save(snapshot: ProtocolSnapshot)
}

data class MediaImportRequest(
    val contentUri: String,
    val source: MediaSource,
    val originalNameHint: String? = null,
)

interface MediaRepository {
    fun observeMedia(): Flow<List<MediaAsset>>
    suspend fun import(request: MediaImportRequest): MediaAsset
    suspend fun get(id: String): MediaAsset?
    suspend fun privacyReport(mediaAssetId: String): PrivacyReport?
    suspend fun delete(id: String)
}

interface PrivacyScanner {
    suspend fun scan(localPlainFilePath: String, mediaAssetId: String, mimeType: String): PrivacyReport
}

interface Transport {
    val id: String
    suspend fun isAvailable(): Boolean
    suspend fun send(exportBundlePath: String): TransportResult
}

sealed interface TransportResult {
    data class Sent(val receipt: String? = null) : TransportResult
    data class Failed(val reason: String) : TransportResult
}
