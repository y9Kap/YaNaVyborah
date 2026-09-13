package org.yanavybori.shared

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.yanavybori.core.common.formatFileTimestamp
import org.yanavybori.core.model.MediaAsset
import org.yanavybori.core.model.UserDataSnapshot
import org.yanavybori.core.ui.PlatformUi
import org.yanavybori.feature.voter.VOTER_STATE_KEY
import org.yanavybori.feature.voter.voterBallotPhotoStorageKeys

private val exportJson = Json {
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun SharedAppContainer.buildUserDataExport(platform: PlatformUi, exportedAt: Long): ByteArray {
    val snapshot = userDataRepository.snapshot()
    val voterStateRaw = platform.loadPrivateText(VOTER_STATE_KEY)
    val voterState = voterStateRaw?.let { raw ->
        runCatching { exportJson.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }
    } ?: JsonNull

    val mediaFiles = snapshot.mediaAssets.map { asset ->
        val bytes = requireNotNull(observerDependencies.mediaRepository.loadOriginal(asset.id)) {
            "Не найден пользовательский файл «${asset.originalName}»"
        }
        asset to bytes
    }
    val ballotPhotos = voterBallotPhotoStorageKeys(voterStateRaw).map { storageKey ->
        val bytes = requireNotNull(platform.loadPrivateBytes(storageKey)) {
            "Не найдена сохранённая фотография бюллетеня"
        }
        storageKey to bytes
    }

    return encodeUserDataExport(
        applicationVersion = applicationVersion,
        exportedAt = exportedAt,
        snapshot = snapshot,
        voterState = voterState,
        mediaFiles = mediaFiles,
        ballotPhotos = ballotPhotos,
    )
}

@OptIn(ExperimentalEncodingApi::class)
internal fun encodeUserDataExport(
    applicationVersion: String,
    exportedAt: Long,
    snapshot: UserDataSnapshot,
    voterState: JsonElement,
    mediaFiles: List<Pair<MediaAsset, ByteArray>>,
    ballotPhotos: List<Pair<String, ByteArray>>,
): ByteArray {
    val bundle = JsonObject(
        mapOf(
            "format" to JsonPrimitive("org.yanavybori.user-data-export"),
            "formatVersion" to JsonPrimitive(1),
            "applicationVersion" to JsonPrimitive(applicationVersion),
            "exportedAt" to JsonPrimitive(exportedAt),
            "observerData" to exportJson.encodeToJsonElement(snapshot),
            "observerFiles" to JsonArray(mediaFiles.map { (asset, bytes) -> exportedFile(asset, bytes) }),
            "voterData" to voterState,
            "ballotPhotos" to JsonArray(ballotPhotos.map { (storageKey, bytes) ->
                JsonObject(
                    mapOf(
                        "storageKey" to JsonPrimitive(storageKey),
                        "dataBase64" to JsonPrimitive(Base64.encode(bytes)),
                    ),
                )
            }),
        ),
    )
    return exportJson.encodeToString<JsonElement>(bundle).encodeToByteArray()
}

fun userDataExportFileName(now: Long): String =
    "ya-na-vyborah-user-data-${formatFileTimestamp(now)}.json"

@OptIn(ExperimentalEncodingApi::class)
private fun exportedFile(asset: MediaAsset, bytes: ByteArray) = JsonObject(
    mapOf(
        "id" to JsonPrimitive(asset.id),
        "name" to JsonPrimitive(asset.originalName),
        "mimeType" to JsonPrimitive(asset.mimeType),
        "sha256" to JsonPrimitive(asset.sha256),
        "dataBase64" to JsonPrimitive(Base64.encode(bytes)),
    ),
)
