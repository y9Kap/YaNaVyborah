package org.yanavybori.web

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yanavybori.core.common.ChecklistStateUpdate
import org.yanavybori.core.common.ComplaintRepository
import org.yanavybori.core.common.ComplaintStatusPolicy
import org.yanavybori.core.common.CounterPolicy
import org.yanavybori.core.common.CounterRepository
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.common.JournalEventFactory
import org.yanavybori.core.common.JournalRepository
import org.yanavybori.core.common.KnowledgeRepository
import org.yanavybori.core.common.MediaImportRequest
import org.yanavybori.core.common.MediaRepository
import org.yanavybori.core.common.ObservationRepository
import org.yanavybori.core.common.ProtocolRepository
import org.yanavybori.core.common.ReconciliationRepository
import org.yanavybori.core.common.SESSION_DELETION_PASSWORD_MIN_LENGTH
import org.yanavybori.core.common.SystemClock
import org.yanavybori.core.common.UuidGenerator
import org.yanavybori.core.common.UserDataRepository
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ChecklistItemState
import org.yanavybori.core.model.ChecklistSectionState
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
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.PrivacyReport
import org.yanavybori.core.model.PrivacyStatus
import org.yanavybori.core.model.ProtocolSnapshot
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationSession
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.SearchResult
import org.yanavybori.core.model.SearchResultType
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.SituationAudience
import org.yanavybori.core.model.VotingDayDefinition
import org.yanavybori.core.model.UserDataSnapshot

@Serializable
internal data class BrowserUserData(
    val sessions: List<ObservationSession> = emptyList(),
    val activeSessionId: String? = null,
    val passwordDigests: Map<String, String> = emptyMap(),
    val checklistStates: List<ChecklistItemState> = emptyList(),
    val checklistSections: List<ChecklistSectionState> = emptyList(),
    val journalEvents: List<JournalEvent> = emptyList(),
    val complaints: List<Complaint> = emptyList(),
    val counters: List<CounterSession> = emptyList(),
    val counterMarks: List<CounterMark> = emptyList(),
    val reconciliations: List<ReconciliationSession> = emptyList(),
    val protocols: List<ProtocolSnapshot> = emptyList(),
)

@Serializable
internal data class PickedBrowserFile(
    val name: String,
    val mimeType: String,
    val base64: String,
)

internal object BrowserFileRegistry {
    private val files = mutableMapOf<String, PickedBrowserFile>()
    fun put(file: PickedBrowserFile): String = UuidGenerator.newId().also { files[it] = file }
    fun take(id: String): PickedBrowserFile? = files.remove(id)
}

internal class BrowserStore :
    ElectionPackRepository,
    KnowledgeRepository,
    ObservationRepository,
    JournalRepository,
    ComplaintRepository,
    CounterRepository,
    ReconciliationRepository,
    ProtocolRepository,
    MediaRepository,
    UserDataRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val content = MutableStateFlow<ElectionPackContent?>(null)
    private val user = MutableStateFlow(loadUserData())
    private val media = MutableStateFlow<List<MediaAsset>>(emptyList())
    private val mediaBytes = mutableMapOf<String, ByteArray>()
    private val journalFactory = JournalEventFactory()

    override fun observeActiveManifest(): Flow<ElectionPackManifest?> = content.map { it?.manifest }
    override suspend fun activeManifest(): ElectionPackManifest? = content.value?.manifest
    override suspend fun replaceAtomically(content: ElectionPackContent) { this.content.value = content }

    override fun observeVotingDays(packId: String): Flow<List<VotingDayDefinition>> =
        content.map { value -> value?.votingDays.orEmpty().filter { it.packId == packId }.sortedBy { it.order } }
    override fun observeChecklistDefinitions(packId: String): Flow<List<ChecklistDefinition>> =
        content.map { value -> value?.checklistDefinitions.orEmpty().filter { it.packId == packId } }
    override fun observeChecklistItems(packId: String): Flow<List<ChecklistItem>> =
        content.map { value -> value?.checklistItems.orEmpty().filter { item ->
            value?.checklistDefinitions.orEmpty().any { it.packId == packId && it.id == item.definitionId }
        }.sortedBy { it.order } }
    override fun observeSituations(packId: String, audience: SituationAudience): Flow<List<Situation>> =
        content.map { value -> value?.situations.orEmpty().filter { it.packId == packId && it.audience == audience } }
    override fun observeLawReferences(packId: String): Flow<List<LawReference>> =
        content.map { value -> value?.lawReferences.orEmpty().filter { it.packId == packId } }
    override fun observeComplaintTemplates(packId: String): Flow<List<ComplaintTemplate>> =
        content.map { value -> value?.complaintTemplates.orEmpty().filter { it.packId == packId } }
    override fun observeReconciliationDefinitions(packId: String): Flow<List<ReconciliationDefinition>> =
        content.map { value -> value?.reconciliationDefinitions.orEmpty().filter { it.packId == packId } }
    override fun observeReferenceDocuments(packId: String): Flow<List<ReferenceDocument>> =
        content.map { value -> value?.referenceDocuments.orEmpty().filter { it.packId == packId } }
    override suspend fun lawReference(id: String): LawReference? = content.value?.lawReferences?.firstOrNull { it.id == id }

    override suspend fun search(packId: String, query: String): List<SearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val pack = content.value ?: return emptyList()
        val situations = pack.situations.filter { it.packId == packId && it.searchableText().contains(needle) }
            .map { SearchResult(it.id, SearchResultType.SITUATION, it.title, it.summary, it.tags.filter { tag -> needle in tag.lowercase() }) }
        val laws = pack.lawReferences.filter { it.packId == packId && it.searchableText().contains(needle) }
            .map { SearchResult(it.id, SearchResultType.LAW, it.title, it.summary, it.tags.filter { tag -> needle in tag.lowercase() }) }
        val complaints = pack.complaintTemplates.filter { it.packId == packId && it.searchableText().contains(needle) }
            .map { SearchResult(it.id, SearchResultType.COMPLAINT_TEMPLATE, it.title, it.defaultRecipient) }
        val documents = pack.referenceDocuments.filter { it.packId == packId && it.searchableText().contains(needle) }
            .map { SearchResult(it.id, SearchResultType.INSTRUCTION, it.title, it.description, it.tags.filter { tag -> needle in tag.lowercase() }) }
        return (situations + laws + complaints + documents).distinctBy { it.type to it.id }
    }

    override fun observeActiveSession(): Flow<ObservationSession?> = user.map { state ->
        state.sessions.firstOrNull { it.id == state.activeSessionId }
    }
    override fun observeSessions(): Flow<List<ObservationSession>> = user.map { it.sessions.sortedByDescending { session -> session.startedAt } }

    override suspend fun createSession(
        electionPackId: String,
        observerFullName: String,
        region: String,
        precinctNumber: String,
        precinctName: String?,
        commissionMemberNames: List<String>,
        deletionPassword: String,
        votingDayId: String,
        stageId: String,
    ): ObservationSession {
        require(observerFullName.isNotBlank()) { "Укажите ФИО наблюдателя" }
        require(region.isNotBlank()) { "Укажите регион" }
        require(precinctNumber.isNotBlank()) { "Укажите номер участка" }
        validatePassword(deletionPassword)
        val id = UuidGenerator.newId()
        val session = ObservationSession(
            id = id,
            electionPackId = electionPackId,
            precinctNumber = precinctNumber.trim(),
            precinctName = precinctName?.trim()?.takeIf(String::isNotBlank),
            startedAt = SystemClock.now(),
            currentVotingDay = votingDayId,
            currentStage = stageId,
            observerFullName = observerFullName.trim(),
            region = region.trim(),
            commissionMemberNames = commissionMemberNames.map(String::trim).filter(String::isNotBlank).distinct(),
            hasDeletionPassword = true,
        )
        updateUser { it.copy(
            sessions = it.sessions + session,
            activeSessionId = id,
            passwordDigests = it.passwordDigests + (id to passwordDigest(id, deletionPassword)),
        ) }
        return session
    }

    override suspend fun selectSession(sessionId: String) {
        require(user.value.sessions.any { it.id == sessionId }) { "Сессия наблюдения не найдена" }
        updateUser { it.copy(activeSessionId = sessionId) }
    }

    override suspend fun updateDayAndStage(sessionId: String, votingDayId: String, stageId: String) =
        updateSession(sessionId) { it.copy(currentVotingDay = votingDayId, currentStage = stageId) }

    override suspend fun finishSession(sessionId: String) =
        updateSession(sessionId) { it.copy(finishedAt = SystemClock.now()) }

    override suspend fun setDeletionPassword(sessionId: String, password: String) {
        validatePassword(password)
        val session = requireSession(sessionId)
        check(!session.hasDeletionPassword) { "Пароль для удаления уже установлен" }
        updateUser { state -> state.copy(
            sessions = state.sessions.map { if (it.id == sessionId) it.copy(hasDeletionPassword = true) else it },
            passwordDigests = state.passwordDigests + (sessionId to passwordDigest(sessionId, password)),
        ) }
    }

    override suspend fun deleteSession(sessionId: String, password: String) {
        requireSession(sessionId)
        require(user.value.passwordDigests[sessionId] == passwordDigest(sessionId, password)) { "Неверный пароль" }
        updateUser { state -> state.copy(
            sessions = state.sessions.filterNot { it.id == sessionId },
            activeSessionId = state.activeSessionId.takeUnless { it == sessionId },
            passwordDigests = state.passwordDigests - sessionId,
            checklistStates = state.checklistStates.filterNot { it.sessionId == sessionId },
            checklistSections = state.checklistSections.filterNot { it.sessionId == sessionId },
            journalEvents = state.journalEvents.filterNot { it.sessionId == sessionId },
            complaints = state.complaints.filterNot { it.sessionId == sessionId },
            counters = state.counters.filterNot { it.observationSessionId == sessionId },
            counterMarks = state.counterMarks.filter { mark -> state.counters.none { it.observationSessionId == sessionId && it.id == mark.counterSessionId } },
            reconciliations = state.reconciliations.filterNot { it.observationSessionId == sessionId },
            protocols = state.protocols.filterNot { it.observationSessionId == sessionId },
        ) }
    }

    override fun observeChecklistStates(sessionId: String, votingDayId: String): Flow<List<ChecklistItemState>> =
        user.map { state -> state.checklistStates.filter { it.sessionId == sessionId && it.votingDayId == votingDayId } }
    override fun observeChecklistSections(sessionId: String, votingDayId: String): Flow<List<ChecklistSectionState>> =
        user.map { state -> state.checklistSections.filter { it.sessionId == sessionId && it.votingDayId == votingDayId } }
    override suspend fun setChecklistSections(sections: List<ChecklistSectionState>) = updateUser { state ->
        val keys = sections.map { Triple(it.sessionId, it.votingDayId, it.definitionId) }.toSet()
        state.copy(checklistSections = state.checklistSections.filterNot {
            Triple(it.sessionId, it.votingDayId, it.definitionId) in keys
        } + sections)
    }

    override suspend fun setChecklistState(
        sessionId: String,
        votingDayId: String,
        checklistItemId: String,
        status: ChecklistStatus,
    ): ChecklistStateUpdate {
        requireSession(sessionId)
        val itemState = ChecklistItemState(
            "$sessionId:$votingDayId:$checklistItemId", sessionId, votingDayId, checklistItemId, status, SystemClock.now(),
        )
        val event = status.takeUnless { it == ChecklistStatus.NOT_CHECKED }?.let {
            journalFactory.fromChecklist(sessionId, votingDayId, checklistItemId, "Чек-лист обновлён", status)
        }
        updateUser { state -> state.copy(
            checklistStates = state.checklistStates.filterNot { it.id == itemState.id } + itemState,
            journalEvents = if (event == null) state.journalEvents else state.journalEvents + event,
        ) }
        return ChecklistStateUpdate(itemState, event)
    }

    override fun observeEvents(sessionId: String): Flow<List<JournalEvent>> =
        user.map { state -> state.journalEvents.filter { it.sessionId == sessionId }.sortedByDescending { it.timestamp } }
    override suspend fun getEvent(eventId: String): JournalEvent? = user.value.journalEvents.firstOrNull { it.id == eventId }
    override suspend fun create(event: JournalEvent): JournalEvent = event.also {
        updateUser { state -> state.copy(journalEvents = state.journalEvents.filterNot { it.id == event.id } + event) }
    }
    override suspend fun update(event: JournalEvent) = updateUser { state ->
        state.copy(journalEvents = state.journalEvents.filterNot { it.id == event.id } + event)
    }

    override fun observeComplaints(sessionId: String): Flow<List<Complaint>> =
        user.map { state -> state.complaints.filter { it.sessionId == sessionId }.sortedByDescending { it.createdAt } }
    override suspend fun getComplaint(id: String): Complaint? = user.value.complaints.firstOrNull { it.id == id }
    override suspend fun create(complaint: Complaint): Complaint = complaint.also {
        updateUser { state -> state.copy(complaints = state.complaints.filterNot { it.id == complaint.id } + complaint) }
    }
    override suspend fun update(complaint: Complaint) = updateUser { state ->
        state.copy(complaints = state.complaints.filterNot { it.id == complaint.id } + complaint)
    }
    override suspend fun updateStatus(id: String, status: ComplaintStatus, submittedAt: Long?) {
        val complaint = requireNotNull(getComplaint(id))
        update(ComplaintStatusPolicy.transition(complaint, status, submittedAt ?: SystemClock.now()))
    }

    override fun observeCounters(sessionId: String, votingDayId: String): Flow<List<CounterSession>> =
        user.map { state -> state.counters.filter { it.observationSessionId == sessionId && it.votingDayId == votingDayId } }
    override fun observeMarks(counterSessionId: String): Flow<List<CounterMark>> =
        user.map { state -> state.counterMarks.filter { it.counterSessionId == counterSessionId }.sortedByDescending { it.timestamp } }
    override fun observeLastMarksForDay(sessionId: String, votingDayId: String): Flow<List<CounterMark>> = user.map { state ->
        val ids = state.counters.filter { it.observationSessionId == sessionId && it.votingDayId == votingDayId }.map { it.id }.toSet()
        state.counterMarks.filter { it.counterSessionId in ids }.groupBy { it.counterSessionId }
            .mapNotNull { (_, marks) -> marks.lastOrNull() }
    }
    override suspend fun createCounter(sessionId: String, votingDayId: String, label: String): CounterSession {
        require(label.isNotBlank()) { "Укажите название счётчика" }
        val counter = CounterSession(UuidGenerator.newId(), sessionId, votingDayId, label.trim(), SystemClock.now(), null, 0)
        updateUser { it.copy(counters = it.counters + counter) }
        return counter
    }
    override suspend fun increment(counterSessionId: String): CounterMark = changeCounter(counterSessionId, 1)
    override suspend fun decrement(counterSessionId: String): CounterMark = changeCounter(counterSessionId, -1)
    override suspend fun undoLast(counterSessionId: String): Boolean {
        val last = user.value.counterMarks.lastOrNull { it.counterSessionId == counterSessionId } ?: return false
        updateUser { state -> state.copy(
            counters = state.counters.map { counter ->
                if (counter.id != counterSessionId) counter else counter.copy(currentValue =
                    if (last.delta > 0) CounterPolicy.decrement(counter.currentValue, last.delta)
                    else CounterPolicy.increment(counter.currentValue, -last.delta))
            },
            counterMarks = state.counterMarks.filterNot { it.id == last.id },
        ) }
        return true
    }
    override suspend fun stop(counterSessionId: String) = updateCounter(counterSessionId) { it.copy(stoppedAt = SystemClock.now()) }

    override fun observeSessions(observationSessionId: String): Flow<List<ReconciliationSession>> =
        user.map { state -> state.reconciliations.filter { it.observationSessionId == observationSessionId }.sortedByDescending { it.updatedAt } }
    override suspend fun save(session: ReconciliationSession) = updateUser { state ->
        state.copy(reconciliations = state.reconciliations.filterNot { it.id == session.id } + session)
    }

    override fun observeSnapshots(observationSessionId: String): Flow<List<ProtocolSnapshot>> =
        user.map { state -> state.protocols.filter { it.observationSessionId == observationSessionId }.sortedByDescending { it.capturedAt } }
    override suspend fun save(snapshot: ProtocolSnapshot) = updateUser { state ->
        state.copy(protocols = state.protocols.filterNot { it.id == snapshot.id } + snapshot)
    }

    override fun observeMedia(): Flow<List<MediaAsset>> = media
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun import(request: MediaImportRequest): MediaAsset {
        val file = requireNotNull(BrowserFileRegistry.take(request.contentUri)) { "Выбранный файл больше недоступен" }
        val bytes = Base64.decode(file.base64)
        val now = SystemClock.now()
        val asset = MediaAsset(
            id = UuidGenerator.newId(),
            createdAt = now,
            importedAt = now,
            mimeType = file.mimeType.ifBlank { "application/octet-stream" },
            originalName = request.originalNameHint ?: file.name,
            size = bytes.size.toLong(),
            sha256 = Sha256.digest(bytes),
            encryptedStoragePath = "browser-memory",
            source = request.source,
            privacyStatus = PrivacyStatus.NOT_SCANNED,
        )
        mediaBytes[asset.id] = bytes
        media.value = media.value + asset
        return asset
    }
    override suspend fun get(id: String): MediaAsset? = media.value.firstOrNull { it.id == id }
    override suspend fun loadImagePreview(id: String, maxDimension: Int): ByteArray? = mediaBytes[id]
    override suspend fun loadOriginal(id: String): ByteArray? = mediaBytes[id]
    override suspend fun privacyReport(mediaAssetId: String): PrivacyReport? = null
    override suspend fun delete(id: String) {
        mediaBytes.remove(id)
        media.value = media.value.filterNot { it.id == id }
    }

    override suspend fun snapshot() = UserDataSnapshot(
        activeSessionId = user.value.activeSessionId,
        observationSessions = user.value.sessions,
        checklistStates = user.value.checklistStates,
        checklistSections = user.value.checklistSections,
        journalEvents = user.value.journalEvents,
        complaints = user.value.complaints,
        counterSessions = user.value.counters,
        counterMarks = user.value.counterMarks,
        reconciliationSessions = user.value.reconciliations,
        protocolSnapshots = user.value.protocols,
        mediaAssets = media.value.map { it.copy(encryptedStoragePath = "") },
    )

    private suspend fun changeCounter(counterSessionId: String, delta: Int): CounterMark {
        val counter = requireNotNull(user.value.counters.firstOrNull { it.id == counterSessionId })
        check(counter.stoppedAt == null) { "Счётчик уже остановлен" }
        val next = if (delta > 0) CounterPolicy.increment(counter.currentValue, delta)
        else CounterPolicy.decrement(counter.currentValue, -delta)
        val mark = CounterMark(UuidGenerator.newId(), counterSessionId, SystemClock.now(), delta)
        updateUser { state -> state.copy(
            counters = state.counters.map { if (it.id == counterSessionId) it.copy(currentValue = next) else it },
            counterMarks = state.counterMarks + mark,
        ) }
        return mark
    }

    private suspend fun updateCounter(id: String, transform: (CounterSession) -> CounterSession) = updateUser { state ->
        require(state.counters.any { it.id == id }) { "Счётчик не найден" }
        state.copy(counters = state.counters.map { if (it.id == id) transform(it) else it })
    }

    private suspend fun updateSession(id: String, transform: (ObservationSession) -> ObservationSession) = updateUser { state ->
        require(state.sessions.any { it.id == id }) { "Сессия наблюдения не найдена" }
        state.copy(sessions = state.sessions.map { if (it.id == id) transform(it) else it })
    }

    private fun requireSession(id: String): ObservationSession =
        requireNotNull(user.value.sessions.firstOrNull { it.id == id }) { "Сессия наблюдения не найдена" }

    private fun updateUser(transform: (BrowserUserData) -> BrowserUserData) {
        user.value = transform(user.value)
        runCatching { localStorage.setItem(STORAGE_KEY, json.encodeToString(user.value)) }
    }

    private fun loadUserData(): BrowserUserData = runCatching {
        localStorage.getItem(STORAGE_KEY)?.let(json::decodeFromString) ?: BrowserUserData()
    }.getOrDefault(BrowserUserData())

    private fun validatePassword(password: String) {
        require(password.isNotBlank()) { "Пароль не может состоять только из пробелов" }
        require(password.length >= SESSION_DELETION_PASSWORD_MIN_LENGTH) {
            "Пароль должен содержать не менее $SESSION_DELETION_PASSWORD_MIN_LENGTH символов"
        }
    }

    private fun passwordDigest(sessionId: String, password: String): String =
        Sha256.digest("$sessionId:$password".encodeToByteArray())

    private fun Situation.searchableText() = listOf(title, summary, tags.joinToString()).joinToString(" ").lowercase()
    private fun LawReference.searchableText() = listOf(title, citation, summary, text, tags.joinToString()).joinToString(" ").lowercase()
    private fun ComplaintTemplate.searchableText() = listOf(title, defaultRecipient, body).joinToString(" ").lowercase()
    private fun ReferenceDocument.searchableText() = listOf(title, description, content, tags.joinToString()).joinToString(" ").lowercase()

    private companion object { const val STORAGE_KEY = "yanavyborah.user-data.v1" }
}
