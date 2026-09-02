package org.yanavybori.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.yanavybori.core.common.CounterPolicy

@Entity(tableName = "observation_sessions")
data class ObservationSessionEntity(
    @PrimaryKey val id: String,
    val electionPackId: String,
    val precinctNumber: String,
    val precinctName: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val currentVotingDay: String,
    val currentStage: String,
    @ColumnInfo(defaultValue = "''") val observerFullName: String = "",
    @ColumnInfo(defaultValue = "''") val region: String = "",
    @ColumnInfo(defaultValue = "'[]'") val commissionMemberNamesJson: String = "[]",
    val deletionPasswordHash: String? = null,
    val deletionPasswordSalt: String? = null,
)

@Entity(tableName = "checklist_states")
data class ChecklistStateEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val votingDayId: String,
    val checklistItemId: String,
    val status: String,
    val updatedAt: Long,
)

@Entity(tableName = "journal_events")
data class JournalEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val votingDayId: String,
    val timestamp: Long,
    val category: String,
    val severity: String,
    val title: String,
    val description: String,
    val relatedSituationId: String?,
    val relatedChecklistItemId: String?,
    val participantNotes: String,
    val mediaIdsJson: String,
    val relatedComplaintIdsJson: String,
    val tagsJson: String,
)

@Entity(tableName = "complaints")
data class ComplaintEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val relatedEventIdsJson: String,
    val templateId: String?,
    val recipient: String,
    val createdAt: Long,
    val submittedAt: Long?,
    val status: String,
    val text: String,
    val registrationNumber: String?,
    val acceptedCopyMediaId: String?,
    val notes: String,
)

@Entity(tableName = "counter_sessions")
data class CounterSessionEntity(
    @PrimaryKey val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val label: String,
    val startedAt: Long,
    val stoppedAt: Long?,
    val currentValue: Long,
)

@Entity(tableName = "counter_marks")
data class CounterMarkEntity(
    @PrimaryKey val id: String,
    val counterSessionId: String,
    val timestamp: Long,
    val delta: Int,
)

@Entity(tableName = "reconciliation_sessions")
data class ReconciliationSessionEntity(
    @PrimaryKey val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val definitionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val valuesJson: String,
    val resultsJson: String,
    val photoMediaId: String?,
)

@Entity(tableName = "protocol_snapshots")
data class ProtocolSnapshotEntity(
    @PrimaryKey val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val protocolFormId: String,
    val capturedAt: Long,
    val valuesJson: String,
    val photoMediaId: String?,
    val reconciliationSessionIdsJson: String,
    val comments: String,
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val importedAt: Long,
    val mimeType: String,
    val originalName: String,
    val size: Long,
    val sha256: String,
    val encryptedStoragePath: String,
    val source: String,
    val privacyStatus: String,
)

@Entity(tableName = "privacy_reports")
data class PrivacyReportEntity(
    @PrimaryKey val id: String,
    val mediaAssetId: String,
    val scannedAt: Long,
    val findingsJson: String,
    val scannerVersion: String,
)

@Dao
abstract class ObservationDao {
    @Query("SELECT * FROM observation_sessions ORDER BY startedAt DESC")
    abstract fun observeAll(): Flow<List<ObservationSessionEntity>>
    @Query("SELECT * FROM observation_sessions WHERE id = :id")
    abstract fun observeById(id: String): Flow<ObservationSessionEntity?>
    @Query("SELECT * FROM observation_sessions WHERE id = :id")
    abstract suspend fun get(id: String): ObservationSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ObservationSessionEntity)

    @Query("DELETE FROM counter_marks WHERE counterSessionId IN (SELECT id FROM counter_sessions WHERE observationSessionId = :sessionId)")
    protected abstract suspend fun deleteCounterMarks(sessionId: String)
    @Query("DELETE FROM counter_sessions WHERE observationSessionId = :sessionId")
    protected abstract suspend fun deleteCounters(sessionId: String)
    @Query("DELETE FROM checklist_states WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteChecklistStates(sessionId: String)
    @Query("DELETE FROM journal_events WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteJournalEvents(sessionId: String)
    @Query("DELETE FROM complaints WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteComplaints(sessionId: String)
    @Query("DELETE FROM reconciliation_sessions WHERE observationSessionId = :sessionId")
    protected abstract suspend fun deleteReconciliations(sessionId: String)
    @Query("DELETE FROM protocol_snapshots WHERE observationSessionId = :sessionId")
    protected abstract suspend fun deleteProtocols(sessionId: String)
    @Query("DELETE FROM observation_sessions WHERE id = :sessionId")
    protected abstract suspend fun deleteObservation(sessionId: String)

    @Transaction
    open suspend fun deleteSession(sessionId: String) {
        deleteCounterMarks(sessionId)
        deleteCounters(sessionId)
        deleteChecklistStates(sessionId)
        deleteJournalEvents(sessionId)
        deleteComplaints(sessionId)
        deleteReconciliations(sessionId)
        deleteProtocols(sessionId)
        deleteObservation(sessionId)
    }
}

@Dao
interface ChecklistStateDao {
    @Query("SELECT * FROM checklist_states WHERE sessionId = :sessionId AND votingDayId = :votingDayId")
    fun observe(sessionId: String, votingDayId: String): Flow<List<ChecklistStateEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChecklistStateEntity)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun observe(sessionId: String): Flow<List<JournalEventEntity>>
    @Query("SELECT * FROM journal_events WHERE id = :id")
    suspend fun get(id: String): JournalEventEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JournalEventEntity)
}

@Dao
interface ComplaintDao {
    @Query("SELECT * FROM complaints WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observe(sessionId: String): Flow<List<ComplaintEntity>>
    @Query("SELECT * FROM complaints WHERE id = :id")
    suspend fun get(id: String): ComplaintEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ComplaintEntity)
}

@Dao
abstract class CounterDao {
    @Query("SELECT * FROM counter_sessions WHERE observationSessionId = :sessionId AND votingDayId = :votingDayId ORDER BY startedAt")
    abstract fun observeCounters(sessionId: String, votingDayId: String): Flow<List<CounterSessionEntity>>
    @Query("SELECT * FROM counter_marks WHERE counterSessionId = :counterId ORDER BY timestamp, rowid")
    abstract fun observeMarks(counterId: String): Flow<List<CounterMarkEntity>>
    @Query(
        """
        SELECT marks.* FROM counter_marks AS marks
        INNER JOIN counter_sessions AS counters ON counters.id = marks.counterSessionId
        WHERE counters.observationSessionId = :sessionId
            AND counters.votingDayId = :votingDayId
            AND marks.rowid = (
                SELECT candidate.rowid FROM counter_marks AS candidate
                WHERE candidate.counterSessionId = marks.counterSessionId
                ORDER BY candidate.timestamp DESC, candidate.rowid DESC
                LIMIT 1
            )
        ORDER BY marks.timestamp, marks.rowid
        """,
    )
    abstract fun observeLastMarksForDay(sessionId: String, votingDayId: String): Flow<List<CounterMarkEntity>>
    @Query("SELECT * FROM counter_sessions WHERE id = :id")
    protected abstract suspend fun getCounter(id: String): CounterSessionEntity?
    @Query("SELECT * FROM counter_marks WHERE counterSessionId = :counterId ORDER BY timestamp DESC, rowid DESC LIMIT 1")
    protected abstract suspend fun getLastMark(counterId: String): CounterMarkEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCounter(entity: CounterSessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMark(entity: CounterMarkEntity)
    @Query("DELETE FROM counter_marks WHERE id = :id")
    protected abstract suspend fun deleteMark(id: String)

    @Transaction
    open suspend fun increment(counterId: String, mark: CounterMarkEntity): CounterSessionEntity {
        val counter = requireNotNull(getCounter(counterId)) { "Счётчик не найден" }
        check(counter.stoppedAt == null) { "Счётчик остановлен" }
        val updatedValue = when {
            mark.delta > 0 -> CounterPolicy.increment(counter.currentValue, mark.delta)
            mark.delta < 0 -> CounterPolicy.decrement(counter.currentValue, -mark.delta)
            else -> error("Отметка счётчика не может быть нулевой")
        }
        insertMark(mark)
        return counter.copy(currentValue = updatedValue)
            .also { upsertCounter(it) }
    }

    @Transaction
    open suspend fun undoLast(counterId: String): Boolean {
        val counter = getCounter(counterId) ?: return false
        val mark = getLastMark(counterId) ?: return false
        deleteMark(mark.id)
        upsertCounter(counter.copy(currentValue = CounterPolicy.undo(counter.currentValue, mark.delta)))
        return true
    }

    @Transaction
    open suspend fun stop(counterId: String, at: Long) {
        val counter = getCounter(counterId) ?: return
        upsertCounter(counter.copy(stoppedAt = at))
    }
}

@Dao
interface ReconciliationDao {
    @Query("SELECT * FROM reconciliation_sessions WHERE observationSessionId = :sessionId ORDER BY updatedAt DESC")
    fun observe(sessionId: String): Flow<List<ReconciliationSessionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReconciliationSessionEntity)
}

@Dao
interface ProtocolDao {
    @Query("SELECT * FROM protocol_snapshots WHERE observationSessionId = :sessionId ORDER BY capturedAt DESC")
    fun observe(sessionId: String): Flow<List<ProtocolSnapshotEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProtocolSnapshotEntity)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_assets ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<MediaAssetEntity>>
    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun get(id: String): MediaAssetEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaAssetEntity)
    @Query("SELECT * FROM privacy_reports WHERE mediaAssetId = :mediaAssetId LIMIT 1")
    suspend fun privacyReport(mediaAssetId: String): PrivacyReportEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacyReport(entity: PrivacyReportEntity)
    @Query("DELETE FROM privacy_reports WHERE mediaAssetId = :mediaAssetId")
    suspend fun deletePrivacyReports(mediaAssetId: String)
    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        ObservationSessionEntity::class,
        ChecklistStateEntity::class,
        JournalEventEntity::class,
        ComplaintEntity::class,
        CounterSessionEntity::class,
        CounterMarkEntity::class,
        ReconciliationSessionEntity::class,
        ProtocolSnapshotEntity::class,
        MediaAssetEntity::class,
        PrivacyReportEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
    abstract fun checklistStateDao(): ChecklistStateDao
    abstract fun journalDao(): JournalDao
    abstract fun complaintDao(): ComplaintDao
    abstract fun counterDao(): CounterDao
    abstract fun reconciliationDao(): ReconciliationDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "user.db"
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE observation_sessions " +
                        "ADD COLUMN observerFullName TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE observation_sessions " +
                        "ADD COLUMN region TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE observation_sessions " +
                        "ADD COLUMN commissionMemberNamesJson TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "ALTER TABLE observation_sessions ADD COLUMN deletionPasswordHash TEXT",
                )
                db.execSQL(
                    "ALTER TABLE observation_sessions ADD COLUMN deletionPasswordSalt TEXT",
                )
            }
        }
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reconciliation_sessions ADD COLUMN photoMediaId TEXT",
                )
            }
        }

        fun create(context: Context): UserDatabase = Room.databaseBuilder(
            context.applicationContext,
            UserDatabase::class.java,
            NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
