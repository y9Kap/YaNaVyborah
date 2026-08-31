package org.yanavybori.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ElectionPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val locale: String,
    val jurisdiction: String,
    val electionType: String,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val contentVersion: Int,
    val schemaVersion: Int,
    val publisher: String,
    val isDemo: Boolean = false,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val files: List<ElectionPackFile> = emptyList(),
    val hashes: Map<String, String> = emptyMap(),
    val optionalSignature: String? = null,
)

@Serializable
data class EmergencyContact(
    val id: String,
    val title: String,
    val phone: String,
    val description: String = "",
    val region: String? = null,
    val type: EmergencyContactType = EmergencyContactType.OTHER,
)

@Serializable
enum class EmergencyContactType {
    EMERGENCY_SERVICE,
    HEADQUARTERS,
    LAWYER,
    COORDINATOR,
    OTHER,
}

@Serializable
data class ElectionPackFile(
    val path: String,
    val sha256: String,
    val mediaType: String = "application/json",
)

@Serializable
data class VotingDayDefinition(
    val id: String,
    val packId: String,
    val order: Int,
    val title: String,
    val shortTitle: String = title,
    val description: String = "",
    val stages: List<VotingStageDefinition> = emptyList(),
)

@Serializable
data class VotingStageDefinition(
    val id: String,
    val title: String,
    val order: Int,
)

@Serializable
data class ChecklistDefinition(
    val id: String,
    val packId: String,
    val title: String,
    val votingDayIds: List<String>,
    val stageIds: List<String> = emptyList(),
    val itemIds: List<String>,
)

@Serializable
data class ChecklistItem(
    val id: String,
    val definitionId: String,
    val order: Int,
    val title: String,
    val shortExplanation: String,
    val whenToCheck: String,
    val whatToCheck: List<String>,
    val lawReferenceIds: List<String> = emptyList(),
    val possibleProblems: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val legalBasis: String = "",
    val liability: String = "",
    val sourceDocumentId: String? = null,
    val sourcePage: Int? = null,
)

@Serializable
data class Situation(
    val id: String,
    val packId: String,
    val audience: SituationAudience,
    val category: SituationCategory,
    val parentId: String? = null,
    val answerLabel: String? = null,
    val title: String,
    val summary: String,
    val factsToCheck: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList(),
    val dataToRecord: List<String> = emptyList(),
    val lawReferenceIds: List<String> = emptyList(),
    val complaintTemplateId: String? = null,
    val referenceDocumentIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
enum class SituationAudience { OBSERVER, VOTER, WORK_PRESSURE }

@Serializable
enum class SituationCategory {
    GENERAL,
    POLICE_INTERACTION,
    PRESSURE_ON_OBSERVER,
    VOTER_RIGHTS,
    WORK_PRESSURE,
}

@Serializable
data class LawReference(
    val id: String,
    val packId: String,
    val title: String,
    val citation: String,
    val summary: String,
    val text: String,
    val source: String,
    val sourceVersion: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class ComplaintTemplate(
    val id: String,
    val packId: String,
    val title: String,
    val defaultRecipient: String,
    val body: String,
    val requiredFacts: List<String> = emptyList(),
    val lawReferenceIds: List<String> = emptyList(),
)

@Serializable
data class ReconciliationDefinition(
    val id: String,
    val packId: String,
    val title: String,
    val description: String,
    val votingDayIds: List<String> = emptyList(),
    val fields: List<ReconciliationFieldDefinition>,
    val rules: List<ReconciliationRule>,
)

@Serializable
data class ReconciliationFieldDefinition(
    val id: String,
    val label: String,
    val order: Int,
    val inputType: ReconciliationInputType = ReconciliationInputType.NON_NEGATIVE_INTEGER,
    val hint: String = "",
)

@Serializable
enum class ReconciliationInputType { NON_NEGATIVE_INTEGER, INTEGER, TEXT }

@Serializable
data class ReconciliationRule(
    val id: String,
    val type: ReconciliationRuleType,
    val inputIds: List<String>,
    val targetInputId: String? = null,
    val previousInputId: String? = null,
    val comparisonInputIds: List<String> = emptyList(),
    val minimum: Long? = null,
    val maximum: Long? = null,
    val message: String,
)

@Serializable
enum class ReconciliationRuleType {
    EQUAL,
    SUM_EQUALS,
    SUM_LESS_OR_EQUAL,
    SUM_EQUALS_SUM,
    RANGE,
    MATCH_PREVIOUS,
    UNIQUE,
    SEQUENTIAL,
    CUSTOM_WARNING,
}

@Serializable
data class ReconciliationResult(
    val ruleId: String,
    val status: ReconciliationStatus,
    val message: String,
    val explanation: String,
    val sourceValues: Map<String, String>,
)

@Serializable
enum class ReconciliationStatus { OK, WARNING, ERROR, NOT_CHECKED }

@Serializable
data class ReferenceDocument(
    val id: String,
    val packId: String,
    val title: String,
    val description: String,
    val contentPath: String,
    val mimeType: String,
    val content: String = "",
    val hotspots: List<ReferenceHotspot> = emptyList(),
    val previewLines: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class ReferenceHotspot(
    val number: Int,
    val label: String,
    val explanation: String,
    val xFraction: Float,
    val yFraction: Float,
)

@Serializable
data class ElectionPackContent(
    val manifest: ElectionPackManifest,
    val votingDays: List<VotingDayDefinition>,
    val checklistDefinitions: List<ChecklistDefinition>,
    val checklistItems: List<ChecklistItem>,
    val situations: List<Situation>,
    val lawReferences: List<LawReference>,
    val complaintTemplates: List<ComplaintTemplate>,
    val reconciliationDefinitions: List<ReconciliationDefinition>,
    val referenceDocuments: List<ReferenceDocument>,
)

@Serializable
data class SearchResult(
    val id: String,
    val type: SearchResultType,
    val title: String,
    val summary: String,
    val matchedTags: List<String> = emptyList(),
)

@Serializable
enum class SearchResultType { SITUATION, LAW, COMPLAINT_TEMPLATE, INSTRUCTION }
