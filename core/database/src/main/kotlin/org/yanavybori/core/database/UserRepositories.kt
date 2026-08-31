package org.yanavybori.core.database

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yanavybori.core.common.Clock
import org.yanavybori.core.common.ComplaintRepository
import org.yanavybori.core.common.ComplaintStatusPolicy
import org.yanavybori.core.common.CounterRepository
import org.yanavybori.core.common.IdGenerator
import org.yanavybori.core.common.JournalEventFactory
import org.yanavybori.core.common.JournalRepository
import org.yanavybori.core.common.ObservationRepository
import org.yanavybori.core.common.ProtocolRepository
import org.yanavybori.core.common.ReconciliationRepository
import org.yanavybori.core.common.SESSION_DELETION_PASSWORD_MIN_LENGTH
import org.yanavybori.core.common.SystemClock
import org.yanavybori.core.common.UuidGenerator
import org.yanavybori.core.model.ChecklistItemState
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.CounterMark
import org.yanavybori.core.model.CounterSession
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalCategory
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.MediaAsset
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.PrivacyFinding
import org.yanavybori.core.model.PrivacyReport
import org.yanavybori.core.model.PrivacyStatus
import org.yanavybori.core.model.ProtocolSnapshot
import org.yanavybori.core.model.ReconciliationResult
import org.yanavybori.core.model.ReconciliationSession

private val userJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val Context.activeSessionDataStore by preferencesDataStore(name = "active_observation")

class ActiveSessionStore(private val context: Context) {
    private val activeIdKey = stringPreferencesKey("active_session_id")
    val activeId: Flow<String?> = context.activeSessionDataStore.data
        .map { it[activeIdKey] }
        .distinctUntilChanged()

    suspend fun set(id: String) {
        context.activeSessionDataStore.edit { it[activeIdKey] = id }
    }

    suspend fun clear() {
        context.activeSessionDataStore.edit { it.remove(activeIdKey) }
    }
}

class SessionPasswordHasher(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val iterations: Int = DEFAULT_ITERATIONS,
) {
    data class Digest(val encodedHash: String, val salt: String)

    init {
        require(iterations > 0)
    }

    fun create(password: String): Digest {
        validate(password)
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val hash = derive(password, salt, iterations)
        return Digest(
            encodedHash = "$SCHEME\$$iterations\$${hash.toHex()}",
            salt = salt.toHex(),
        )
    }

    fun matches(password: String, salt: String, encodedHash: String): Boolean {
        if (password.length < SESSION_DELETION_PASSWORD_MIN_LENGTH || password.isBlank()) return false
        val parts = encodedHash.split('$')
        if (parts.size != 3 || parts[0] != SCHEME) return false
        val storedIterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val expected = parts[2].hexToBytesOrNull() ?: return false
        val saltBytes = salt.hexToBytesOrNull() ?: return false
        val actual = derive(password, saltBytes, storedIterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun validate(password: String) {
        require(password.isNotBlank()) { "Пароль не может состоять только из пробелов" }
        require(password.length >= SESSION_DELETION_PASSWORD_MIN_LENGTH) {
            "Пароль должен содержать не менее $SESSION_DELETION_PASSWORD_MIN_LENGTH символов"
        }
    }

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val keyBytes = password.toByteArray(Charsets.UTF_8)
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
            var block = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
            val result = block.copyOf()
            repeat(rounds - 1) {
                block = mac.doFinal(block)
                result.indices.forEach { index ->
                    result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
                }
            }
            result
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { index ->
                substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private companion object {
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_SIZE = 16
        const val SCHEME = "pbkdf2-sha256"
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomObservationRepository(
    private val observationDao: ObservationDao,
    private val checklistStateDao: ChecklistStateDao,
    private val journalDao: JournalDao,
    private val activeSessionStore: ActiveSessionStore,
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
    private val passwordHasher: SessionPasswordHasher = SessionPasswordHasher(),
) : ObservationRepository {
    private val journalFactory = JournalEventFactory(clock, ids)

    override fun observeActiveSession(): Flow<ObservationSession?> =
        activeSessionStore.activeId.flatMapLatest { id ->
            if (id == null) flowOf(null) else observationDao.observeById(id).map { it?.toModel() }
        }

    override fun observeSessions(): Flow<List<ObservationSession>> =
        observationDao.observeAll().map { rows -> rows.map { it.toModel() } }

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
        val passwordDigest = withContext(Dispatchers.Default) {
            passwordHasher.create(deletionPassword)
        }
        val session = ObservationSession(
            id = ids.newId(),
            electionPackId = electionPackId,
            precinctNumber = precinctNumber.trim(),
            precinctName = precinctName?.trim()?.takeIf { it.isNotBlank() },
            startedAt = clock.now(),
            currentVotingDay = votingDayId,
            currentStage = stageId,
            observerFullName = observerFullName.trim(),
            region = region.trim(),
            commissionMemberNames = commissionMemberNames
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct(),
            hasDeletionPassword = true,
        )
        observationDao.upsert(
            session.toEntity(
                deletionPasswordHash = passwordDigest.encodedHash,
                deletionPasswordSalt = passwordDigest.salt,
            ),
        )
        activeSessionStore.set(session.id)
        return session
    }

    override suspend fun selectSession(sessionId: String) {
        requireNotNull(observationDao.get(sessionId)) { "Сессия наблюдения не найдена" }
        activeSessionStore.set(sessionId)
    }

    override suspend fun updateDayAndStage(sessionId: String, votingDayId: String, stageId: String) {
        val session = requireNotNull(observationDao.get(sessionId))
        observationDao.upsert(session.copy(currentVotingDay = votingDayId, currentStage = stageId))
    }

    override suspend fun finishSession(sessionId: String) {
        val session = requireNotNull(observationDao.get(sessionId))
        observationDao.upsert(session.copy(finishedAt = clock.now()))
    }

    override suspend fun setDeletionPassword(sessionId: String, password: String) {
        val session = requireNotNull(observationDao.get(sessionId)) { "Сессия наблюдения не найдена" }
        check(session.deletionPasswordHash == null && session.deletionPasswordSalt == null) {
            "Пароль для удаления уже установлен"
        }
        val digest = withContext(Dispatchers.Default) { passwordHasher.create(password) }
        observationDao.upsert(
            session.copy(
                deletionPasswordHash = digest.encodedHash,
                deletionPasswordSalt = digest.salt,
            ),
        )
    }

    override suspend fun deleteSession(sessionId: String, password: String) {
        val session = requireNotNull(observationDao.get(sessionId)) { "Сессия наблюдения не найдена" }
        val hash = requireNotNull(session.deletionPasswordHash) {
            "Сначала установите пароль для удаления этой сессии"
        }
        val salt = requireNotNull(session.deletionPasswordSalt) {
            "Сначала установите пароль для удаления этой сессии"
        }
        val passwordMatches = withContext(Dispatchers.Default) {
            passwordHasher.matches(password, salt, hash)
        }
        require(passwordMatches) { "Неверный пароль" }
        observationDao.deleteSession(sessionId)
        if (activeSessionStore.activeId.first() == sessionId) activeSessionStore.clear()
    }

    override fun observeChecklistStates(sessionId: String, votingDayId: String): Flow<List<ChecklistItemState>> =
        checklistStateDao.observe(sessionId, votingDayId).map { rows -> rows.map { it.toModel() } }

    override suspend fun setChecklistState(
        sessionId: String,
        votingDayId: String,
        checklistItemId: String,
        status: ChecklistStatus,
    ): ChecklistItemState {
        val state = ChecklistItemState(
            id = "$sessionId:$votingDayId:$checklistItemId",
            sessionId = sessionId,
            votingDayId = votingDayId,
            checklistItemId = checklistItemId,
            status = status,
            updatedAt = clock.now(),
        )
        checklistStateDao.upsert(state.toEntity())
        if (status != ChecklistStatus.NOT_CHECKED) {
            journalDao.upsert(
                journalFactory.fromChecklist(
                    sessionId,
                    votingDayId,
                    checklistItemId,
                    "Чек-лист обновлён",
                    status,
                ).toEntity(),
            )
        }
        return state
    }
}

class RoomJournalRepository(private val dao: JournalDao) : JournalRepository {
    override fun observeEvents(sessionId: String): Flow<List<JournalEvent>> =
        dao.observe(sessionId).map { rows -> rows.map { it.toModel() } }
    override suspend fun getEvent(eventId: String): JournalEvent? = dao.get(eventId)?.toModel()
    override suspend fun create(event: JournalEvent): JournalEvent = event.also { dao.upsert(it.toEntity()) }
    override suspend fun update(event: JournalEvent) = dao.upsert(event.toEntity())
}

class RoomComplaintRepository(
    private val dao: ComplaintDao,
    private val clock: Clock = SystemClock,
) : ComplaintRepository {
    override fun observeComplaints(sessionId: String): Flow<List<Complaint>> =
        dao.observe(sessionId).map { rows -> rows.map { it.toModel() } }
    override suspend fun getComplaint(id: String): Complaint? = dao.get(id)?.toModel()
    override suspend fun create(complaint: Complaint): Complaint = complaint.also { dao.upsert(it.toEntity()) }
    override suspend fun update(complaint: Complaint) = dao.upsert(complaint.toEntity())
    override suspend fun updateStatus(id: String, status: ComplaintStatus, submittedAt: Long?) {
        val complaint = requireNotNull(dao.get(id)?.toModel())
        val updated = ComplaintStatusPolicy.transition(complaint, status, submittedAt ?: clock.now())
        dao.upsert(updated.toEntity())
    }
}

class RoomCounterRepository(
    private val dao: CounterDao,
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
) : CounterRepository {
    override fun observeCounters(sessionId: String, votingDayId: String): Flow<List<CounterSession>> =
        dao.observeCounters(sessionId, votingDayId).map { rows -> rows.map { it.toModel() } }
    override fun observeMarks(counterSessionId: String): Flow<List<CounterMark>> =
        dao.observeMarks(counterSessionId).map { rows -> rows.map { it.toModel() } }
    override fun observeLastMarksForDay(sessionId: String, votingDayId: String): Flow<List<CounterMark>> =
        dao.observeLastMarksForDay(sessionId, votingDayId).map { rows -> rows.map { it.toModel() } }
    override suspend fun createCounter(sessionId: String, votingDayId: String, label: String): CounterSession {
        require(label.isNotBlank()) { "Укажите название счётчика" }
        val counter = CounterSession(ids.newId(), sessionId, votingDayId, label.trim(), clock.now(), null, 0)
        dao.upsertCounter(counter.toEntity())
        return counter
    }
    override suspend fun increment(counterSessionId: String): CounterMark {
        val mark = CounterMark(ids.newId(), counterSessionId, clock.now())
        dao.increment(counterSessionId, mark.toEntity())
        return mark
    }
    override suspend fun decrement(counterSessionId: String): CounterMark {
        val mark = CounterMark(ids.newId(), counterSessionId, clock.now(), delta = -1)
        dao.increment(counterSessionId, mark.toEntity())
        return mark
    }
    override suspend fun undoLast(counterSessionId: String): Boolean = dao.undoLast(counterSessionId)
    override suspend fun stop(counterSessionId: String) = dao.stop(counterSessionId, clock.now())
}

class RoomReconciliationRepository(private val dao: ReconciliationDao) : ReconciliationRepository {
    override fun observeSessions(observationSessionId: String): Flow<List<ReconciliationSession>> =
        dao.observe(observationSessionId).map { rows -> rows.map { it.toModel() } }
    override suspend fun save(session: ReconciliationSession) = dao.upsert(session.toEntity())
}

class RoomProtocolRepository(private val dao: ProtocolDao) : ProtocolRepository {
    override fun observeSnapshots(observationSessionId: String): Flow<List<ProtocolSnapshot>> =
        dao.observe(observationSessionId).map { rows -> rows.map { it.toModel() } }
    override suspend fun save(snapshot: ProtocolSnapshot) = dao.upsert(snapshot.toEntity())
}

private fun ObservationSessionEntity.toModel() = ObservationSession(
    id = id,
    electionPackId = electionPackId,
    precinctNumber = precinctNumber,
    precinctName = precinctName,
    startedAt = startedAt,
    finishedAt = finishedAt,
    currentVotingDay = currentVotingDay,
    currentStage = currentStage,
    observerFullName = observerFullName,
    region = region,
    commissionMemberNames = userJson.decodeFromString(commissionMemberNamesJson),
    hasDeletionPassword = deletionPasswordHash != null && deletionPasswordSalt != null,
)
private fun ObservationSession.toEntity(
    deletionPasswordHash: String?,
    deletionPasswordSalt: String?,
) = ObservationSessionEntity(
    id = id,
    electionPackId = electionPackId,
    precinctNumber = precinctNumber,
    precinctName = precinctName,
    startedAt = startedAt,
    finishedAt = finishedAt,
    currentVotingDay = currentVotingDay,
    currentStage = currentStage,
    observerFullName = observerFullName,
    region = region,
    commissionMemberNamesJson = userJson.encodeToString(commissionMemberNames),
    deletionPasswordHash = deletionPasswordHash,
    deletionPasswordSalt = deletionPasswordSalt,
)
private fun ChecklistStateEntity.toModel() = ChecklistItemState(
    id, sessionId, votingDayId, checklistItemId, ChecklistStatus.valueOf(status), updatedAt,
)
private fun ChecklistItemState.toEntity() = ChecklistStateEntity(
    id, sessionId, votingDayId, checklistItemId, status.name, updatedAt,
)
private fun JournalEventEntity.toModel() = JournalEvent(
    id, sessionId, votingDayId, timestamp, JournalCategory.valueOf(category), EventSeverity.valueOf(severity),
    title, description, relatedSituationId, relatedChecklistItemId, participantNotes,
    userJson.decodeFromString(mediaIdsJson), userJson.decodeFromString(relatedComplaintIdsJson),
    userJson.decodeFromString(tagsJson),
)
private fun JournalEvent.toEntity() = JournalEventEntity(
    id, sessionId, votingDayId, timestamp, category.name, severity.name, title, description,
    relatedSituationId, relatedChecklistItemId, participantNotes, userJson.encodeToString(mediaIds),
    userJson.encodeToString(relatedComplaintIds), userJson.encodeToString(tags),
)
private fun ComplaintEntity.toModel() = Complaint(
    id, sessionId, userJson.decodeFromString(relatedEventIdsJson), templateId, recipient, createdAt,
    submittedAt, ComplaintStatus.valueOf(status), text, registrationNumber, acceptedCopyMediaId, notes,
)
private fun Complaint.toEntity() = ComplaintEntity(
    id, sessionId, userJson.encodeToString(relatedEventIds), templateId, recipient, createdAt, submittedAt,
    status.name, text, registrationNumber, acceptedCopyMediaId, notes,
)
private fun CounterSessionEntity.toModel() = CounterSession(
    id, observationSessionId, votingDayId, label, startedAt, stoppedAt, currentValue,
)
private fun CounterSession.toEntity() = CounterSessionEntity(
    id, observationSessionId, votingDayId, label, startedAt, stoppedAt, currentValue,
)
private fun CounterMarkEntity.toModel() = CounterMark(id, counterSessionId, timestamp, delta)
private fun CounterMark.toEntity() = CounterMarkEntity(id, counterSessionId, timestamp, delta)
private fun ReconciliationSessionEntity.toModel() = ReconciliationSession(
    id, observationSessionId, votingDayId, definitionId, createdAt, updatedAt,
    userJson.decodeFromString(valuesJson), userJson.decodeFromString(resultsJson),
)
private fun ReconciliationSession.toEntity() = ReconciliationSessionEntity(
    id, observationSessionId, votingDayId, definitionId, createdAt, updatedAt,
    userJson.encodeToString(values), userJson.encodeToString(results),
)
private fun ProtocolSnapshotEntity.toModel() = ProtocolSnapshot(
    id, observationSessionId, votingDayId, protocolFormId, capturedAt,
    userJson.decodeFromString(valuesJson), photoMediaId,
    userJson.decodeFromString(reconciliationSessionIdsJson), comments,
)
private fun ProtocolSnapshot.toEntity() = ProtocolSnapshotEntity(
    id, observationSessionId, votingDayId, protocolFormId, capturedAt, userJson.encodeToString(values),
    photoMediaId, userJson.encodeToString(reconciliationSessionIds), comments,
)

fun MediaAssetEntity.toModel() = MediaAsset(
    id, createdAt, importedAt, mimeType, originalName, size, sha256, encryptedStoragePath,
    MediaSource.valueOf(source), PrivacyStatus.valueOf(privacyStatus),
)
fun MediaAsset.toEntity() = MediaAssetEntity(
    id, createdAt, importedAt, mimeType, originalName, size, sha256, encryptedStoragePath,
    source.name, privacyStatus.name,
)
fun PrivacyReportEntity.toModel() = PrivacyReport(
    id, mediaAssetId, scannedAt, userJson.decodeFromString(findingsJson), scannerVersion,
)
fun PrivacyReport.toEntity() = PrivacyReportEntity(
    id, mediaAssetId, scannedAt, userJson.encodeToString<List<PrivacyFinding>>(findings), scannerVersion,
)
