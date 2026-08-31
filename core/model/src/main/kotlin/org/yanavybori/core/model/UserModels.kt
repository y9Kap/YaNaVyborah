package org.yanavybori.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ObservationSession(
    val id: String,
    val electionPackId: String,
    val precinctNumber: String,
    val precinctName: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val currentVotingDay: String,
    val currentStage: String,
    val observerFullName: String = "",
    val region: String = "",
    val commissionMemberNames: List<String> = emptyList(),
    val hasDeletionPassword: Boolean = false,
)

@Serializable
data class ChecklistItemState(
    val id: String,
    val sessionId: String,
    val votingDayId: String,
    val checklistItemId: String,
    val status: ChecklistStatus,
    val updatedAt: Long,
)

@Serializable
enum class ChecklistStatus { NOT_CHECKED, OK, PROBLEM, NOT_APPLICABLE }

@Serializable
data class JournalEvent(
    val id: String,
    val sessionId: String,
    val votingDayId: String,
    val timestamp: Long,
    val category: JournalCategory,
    val severity: EventSeverity,
    val title: String,
    val description: String = "",
    val relatedSituationId: String? = null,
    val relatedChecklistItemId: String? = null,
    val participantNotes: String = "",
    val mediaIds: List<String> = emptyList(),
    val relatedComplaintIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
enum class JournalCategory {
    NORMAL,
    CONTROL,
    PROBLEM,
    SUSPECTED_VIOLATION,
    DOCUMENT,
    COMPLAINT,
    POLICE,
    COUNTING,
    PROTOCOL,
    CUSTOM,
}

@Serializable
enum class EventSeverity { INFO, ATTENTION, IMPORTANT, URGENT }

@Serializable
data class Complaint(
    val id: String,
    val sessionId: String,
    val relatedEventIds: List<String> = emptyList(),
    val templateId: String? = null,
    val recipient: String,
    val createdAt: Long,
    val submittedAt: Long? = null,
    val status: ComplaintStatus,
    val text: String,
    val registrationNumber: String? = null,
    val acceptedCopyMediaId: String? = null,
    val notes: String = "",
)

@Serializable
enum class ComplaintStatus { DRAFT, READY, SUBMITTED, ACCEPTED, REJECTED, UNKNOWN }

@Serializable
data class CounterSession(
    val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val label: String,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val currentValue: Long,
)

@Serializable
data class CounterMark(
    val id: String,
    val counterSessionId: String,
    val timestamp: Long,
    val delta: Int = 1,
)

@Serializable
data class ReconciliationSession(
    val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val definitionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val values: Map<String, String>,
    val results: List<ReconciliationResult>,
)

@Serializable
data class ProtocolSnapshot(
    val id: String,
    val observationSessionId: String,
    val votingDayId: String,
    val protocolFormId: String,
    val capturedAt: Long,
    val values: Map<String, String>,
    val photoMediaId: String? = null,
    val reconciliationSessionIds: List<String> = emptyList(),
    val comments: String = "",
)

@Serializable
data class MediaAsset(
    val id: String,
    val createdAt: Long,
    val importedAt: Long,
    val mimeType: String,
    val originalName: String,
    val size: Long,
    val sha256: String,
    val encryptedStoragePath: String,
    val source: MediaSource,
    val privacyStatus: PrivacyStatus,
)

@Serializable
enum class MediaSource { CAMERA, PHOTO_PICKER, DOCUMENT_PICKER, SHARE, GENERATED_EXPORT }

@Serializable
enum class PrivacyStatus { NOT_SCANNED, POSSIBLE_PERSONAL_DATA, REVIEWED_BY_USER }

@Serializable
data class PrivacyReport(
    val id: String,
    val mediaAssetId: String,
    val scannedAt: Long,
    val findings: List<PrivacyFinding>,
    val scannerVersion: String,
)

@Serializable
data class PrivacyFinding(
    val type: PrivacyFindingType,
    val confidence: Float? = null,
    val description: String,
)

@Serializable
enum class PrivacyFindingType {
    TEXT,
    POSSIBLE_NAME,
    PHONE,
    EMAIL,
    ADDRESS,
    DOCUMENT_NUMBER,
    QR_OR_BARCODE,
    FACE,
    EXIF_COORDINATES,
    OTHER,
}

@Serializable
data class OriginalMedia(val mediaAssetId: String, val immutable: Boolean = true)

@Serializable
data class ExportCopy(
    val id: String,
    val originalMediaAssetId: String,
    val createdAt: Long,
    val storagePath: String,
    val exifRemoved: Boolean,
    val redactionsApplied: Boolean,
    val resized: Boolean,
    val watermark: String? = null,
)

@Serializable
data class OutboxItem(
    val id: String,
    val payloadType: OutboxPayloadType,
    val payloadId: String,
    val exportBundlePath: String,
    val createdAt: Long,
    val status: OutboxStatus,
    val transportId: String? = null,
)

@Serializable
enum class OutboxPayloadType { INCIDENT, COMPLAINT, PROTOCOL }

@Serializable
enum class OutboxStatus { DRAFT, READY, WAITING, SENT, FAILED }
