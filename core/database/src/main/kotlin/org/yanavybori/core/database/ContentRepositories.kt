package org.yanavybori.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.common.KnowledgeRepository
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.SearchResult
import org.yanavybori.core.model.SearchResultType
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.SituationAudience
import org.yanavybori.core.model.VotingDayDefinition

private val contentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class RoomElectionPackRepository(
    private val dao: ContentDao,
) : ElectionPackRepository {
    override fun observeActiveManifest(): Flow<ElectionPackManifest?> =
        dao.observeActivePack().map { it?.let { entity -> contentJson.decodeFromString(entity.manifestJson) } }

    override suspend fun activeManifest(): ElectionPackManifest? =
        dao.activePack()?.let { contentJson.decodeFromString(it.manifestJson) }

    override suspend fun replaceAtomically(content: ElectionPackContent) {
        val packId = content.manifest.id
        dao.replaceAtomically(
            ContentEntityBundle(
                pack = ElectionPackEntity(
                    id = packId,
                    contentVersion = content.manifest.contentVersion,
                    schemaVersion = content.manifest.schemaVersion,
                    active = true,
                    manifestJson = contentJson.encodeToString(content.manifest),
                ),
                votingDays = content.votingDays.map {
                    VotingDayEntity(it.id, packId, it.order, contentJson.encodeToString(it))
                },
                checklistDefinitions = content.checklistDefinitions.map {
                    ChecklistDefinitionEntity(it.id, packId, contentJson.encodeToString(it))
                },
                checklistItems = content.checklistItems.map {
                    ChecklistItemEntity(it.id, packId, it.definitionId, it.order, contentJson.encodeToString(it))
                },
                situations = content.situations.map {
                    SituationEntity(
                        it.id,
                        packId,
                        it.audience.name,
                        listOf(it.title, it.summary, it.tags.joinToString()).joinToString(" ").lowercase(),
                        contentJson.encodeToString(it),
                    )
                },
                laws = content.lawReferences.map {
                    LawReferenceEntity(
                        it.id,
                        packId,
                        listOf(it.title, it.citation, it.summary, it.tags.joinToString()).joinToString(" ").lowercase(),
                        contentJson.encodeToString(it),
                    )
                },
                complaintTemplates = content.complaintTemplates.map {
                    ComplaintTemplateEntity(
                        it.id,
                        packId,
                        listOf(it.title, it.defaultRecipient, it.body).joinToString(" ").lowercase(),
                        contentJson.encodeToString(it),
                    )
                },
                reconciliationDefinitions = content.reconciliationDefinitions.map {
                    ReconciliationDefinitionEntity(it.id, packId, contentJson.encodeToString(it))
                },
                referenceDocuments = content.referenceDocuments.map {
                    ReferenceDocumentEntity(
                        it.id,
                        packId,
                        listOf(it.title, it.description, it.content, it.tags.joinToString()).joinToString(" ").lowercase(),
                        contentJson.encodeToString(it),
                    )
                },
            ),
        )
    }
}

class RoomKnowledgeRepository(
    private val dao: ContentDao,
) : KnowledgeRepository {
    override fun observeVotingDays(packId: String): Flow<List<VotingDayDefinition>> =
        dao.observeVotingDays(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeChecklistDefinitions(packId: String): Flow<List<ChecklistDefinition>> =
        dao.observeChecklistDefinitions(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeChecklistItems(packId: String): Flow<List<ChecklistItem>> =
        dao.observeChecklistItems(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeSituations(packId: String, audience: SituationAudience): Flow<List<Situation>> =
        dao.observeSituations(packId, audience.name).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeLawReferences(packId: String): Flow<List<LawReference>> =
        dao.observeLawReferences(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeComplaintTemplates(packId: String): Flow<List<ComplaintTemplate>> =
        dao.observeComplaintTemplates(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeReconciliationDefinitions(packId: String): Flow<List<ReconciliationDefinition>> =
        dao.observeReconciliationDefinitions(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override fun observeReferenceDocuments(packId: String): Flow<List<ReferenceDocument>> =
        dao.observeReferenceDocuments(packId).map { rows -> rows.map { contentJson.decodeFromString(it.payloadJson) } }

    override suspend fun lawReference(id: String): LawReference? =
        dao.lawReference(id)?.let { contentJson.decodeFromString(it.payloadJson) }

    override suspend fun search(packId: String, query: String): List<SearchResult> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val situations = dao.searchSituations(packId, normalized).map {
            val item = contentJson.decodeFromString<Situation>(it.payloadJson)
            SearchResult(item.id, SearchResultType.SITUATION, item.title, item.summary,
                item.tags.filter { tag -> normalized in tag.lowercase() })
        }
        val laws = dao.searchLaws(packId, normalized).map {
            val item = contentJson.decodeFromString<LawReference>(it.payloadJson)
            SearchResult(item.id, SearchResultType.LAW, item.title, item.summary,
                item.tags.filter { tag -> normalized in tag.lowercase() })
        }
        val complaints = dao.searchComplaintTemplates(packId, normalized).map {
            val item = contentJson.decodeFromString<ComplaintTemplate>(it.payloadJson)
            SearchResult(item.id, SearchResultType.COMPLAINT_TEMPLATE, item.title, item.defaultRecipient)
        }
        return (situations + laws + complaints).distinctBy { it.type to it.id }
    }
}
