package org.yanavybori.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "election_packs")
data class ElectionPackEntity(
    @androidx.room.PrimaryKey val id: String,
    val contentVersion: Int,
    val schemaVersion: Int,
    val active: Boolean,
    val manifestJson: String,
)

@Entity(tableName = "voting_days")
data class VotingDayEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val sortOrder: Int,
    val payloadJson: String,
)

@Entity(tableName = "checklist_definitions")
data class ChecklistDefinitionEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val payloadJson: String,
)

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val definitionId: String,
    val sortOrder: Int,
    val payloadJson: String,
)

@Entity(tableName = "situations")
data class SituationEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val audience: String,
    val searchableText: String,
    val payloadJson: String,
)

@Entity(tableName = "law_references")
data class LawReferenceEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val searchableText: String,
    val payloadJson: String,
)

@Entity(tableName = "complaint_templates")
data class ComplaintTemplateEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val searchableText: String,
    val payloadJson: String,
)

@Entity(tableName = "reconciliation_definitions")
data class ReconciliationDefinitionEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val payloadJson: String,
)

@Entity(tableName = "reference_documents")
data class ReferenceDocumentEntity(
    @androidx.room.PrimaryKey val id: String,
    val packId: String,
    val searchableText: String,
    val payloadJson: String,
)

@Dao
abstract class ContentDao {
    @Query("SELECT * FROM election_packs WHERE active = 1 LIMIT 1")
    abstract fun observeActivePack(): Flow<ElectionPackEntity?>

    @Query("SELECT * FROM election_packs WHERE active = 1 LIMIT 1")
    abstract suspend fun activePack(): ElectionPackEntity?

    @Query("SELECT * FROM voting_days WHERE packId = :packId ORDER BY sortOrder")
    abstract fun observeVotingDays(packId: String): Flow<List<VotingDayEntity>>

    @Query("SELECT * FROM checklist_definitions WHERE packId = :packId")
    abstract fun observeChecklistDefinitions(packId: String): Flow<List<ChecklistDefinitionEntity>>

    @Query("SELECT * FROM checklist_items WHERE packId = :packId ORDER BY sortOrder")
    abstract fun observeChecklistItems(packId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM situations WHERE packId = :packId AND audience = :audience")
    abstract fun observeSituations(packId: String, audience: String): Flow<List<SituationEntity>>

    @Query("SELECT * FROM law_references WHERE packId = :packId")
    abstract fun observeLawReferences(packId: String): Flow<List<LawReferenceEntity>>

    @Query("SELECT * FROM complaint_templates WHERE packId = :packId")
    abstract fun observeComplaintTemplates(packId: String): Flow<List<ComplaintTemplateEntity>>

    @Query("SELECT * FROM reconciliation_definitions WHERE packId = :packId")
    abstract fun observeReconciliationDefinitions(packId: String): Flow<List<ReconciliationDefinitionEntity>>

    @Query("SELECT * FROM reference_documents WHERE packId = :packId")
    abstract fun observeReferenceDocuments(packId: String): Flow<List<ReferenceDocumentEntity>>

    @Query("SELECT * FROM law_references WHERE id = :id")
    abstract suspend fun lawReference(id: String): LawReferenceEntity?

    @Query("SELECT * FROM situations WHERE packId = :packId AND searchableText LIKE '%' || :query || '%'")
    abstract suspend fun searchSituations(packId: String, query: String): List<SituationEntity>

    @Query("SELECT * FROM law_references WHERE packId = :packId AND searchableText LIKE '%' || :query || '%'")
    abstract suspend fun searchLaws(packId: String, query: String): List<LawReferenceEntity>

    @Query("SELECT * FROM complaint_templates WHERE packId = :packId AND searchableText LIKE '%' || :query || '%'")
    abstract suspend fun searchComplaintTemplates(packId: String, query: String): List<ComplaintTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPack(entity: ElectionPackEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertVotingDays(entities: List<VotingDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChecklistDefinitions(entities: List<ChecklistDefinitionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertChecklistItems(entities: List<ChecklistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSituations(entities: List<SituationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLaws(entities: List<LawReferenceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertComplaintTemplates(entities: List<ComplaintTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertReconciliationDefinitions(entities: List<ReconciliationDefinitionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertReferenceDocuments(entities: List<ReferenceDocumentEntity>)

    @Query("DELETE FROM election_packs") protected abstract suspend fun clearPacks()
    @Query("DELETE FROM voting_days") protected abstract suspend fun clearVotingDays()
    @Query("DELETE FROM checklist_definitions") protected abstract suspend fun clearChecklistDefinitions()
    @Query("DELETE FROM checklist_items") protected abstract suspend fun clearChecklistItems()
    @Query("DELETE FROM situations") protected abstract suspend fun clearSituations()
    @Query("DELETE FROM law_references") protected abstract suspend fun clearLaws()
    @Query("DELETE FROM complaint_templates") protected abstract suspend fun clearComplaintTemplates()
    @Query("DELETE FROM reconciliation_definitions") protected abstract suspend fun clearReconciliationDefinitions()
    @Query("DELETE FROM reference_documents") protected abstract suspend fun clearReferenceDocuments()

    @Transaction
    open suspend fun replaceAtomically(bundle: ContentEntityBundle) {
        clearVotingDays()
        clearChecklistDefinitions()
        clearChecklistItems()
        clearSituations()
        clearLaws()
        clearComplaintTemplates()
        clearReconciliationDefinitions()
        clearReferenceDocuments()
        clearPacks()
        insertPack(bundle.pack)
        insertVotingDays(bundle.votingDays)
        insertChecklistDefinitions(bundle.checklistDefinitions)
        insertChecklistItems(bundle.checklistItems)
        insertSituations(bundle.situations)
        insertLaws(bundle.laws)
        insertComplaintTemplates(bundle.complaintTemplates)
        insertReconciliationDefinitions(bundle.reconciliationDefinitions)
        insertReferenceDocuments(bundle.referenceDocuments)
    }
}

data class ContentEntityBundle(
    val pack: ElectionPackEntity,
    val votingDays: List<VotingDayEntity>,
    val checklistDefinitions: List<ChecklistDefinitionEntity>,
    val checklistItems: List<ChecklistItemEntity>,
    val situations: List<SituationEntity>,
    val laws: List<LawReferenceEntity>,
    val complaintTemplates: List<ComplaintTemplateEntity>,
    val reconciliationDefinitions: List<ReconciliationDefinitionEntity>,
    val referenceDocuments: List<ReferenceDocumentEntity>,
)

@Database(
    entities = [
        ElectionPackEntity::class,
        VotingDayEntity::class,
        ChecklistDefinitionEntity::class,
        ChecklistItemEntity::class,
        SituationEntity::class,
        LawReferenceEntity::class,
        ComplaintTemplateEntity::class,
        ReconciliationDefinitionEntity::class,
        ReferenceDocumentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        const val NAME = "content.db"
        fun create(context: Context): ContentDatabase = Room.databaseBuilder(
            context.applicationContext,
            ContentDatabase::class.java,
            NAME,
        ).build()
    }
}
