package org.yanavybori.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.yanavybori.core.common.Clock
import org.yanavybori.core.common.IdGenerator
import org.yanavybori.core.common.MediaImportRequest
import org.yanavybori.core.common.MediaRepository
import org.yanavybori.core.common.PrivacyScanner
import org.yanavybori.core.common.SystemClock
import org.yanavybori.core.common.UuidGenerator
import org.yanavybori.core.crypto.CryptoManager
import org.yanavybori.core.database.MediaDao
import org.yanavybori.core.database.toEntity
import org.yanavybori.core.database.toModel
import org.yanavybori.core.model.MediaAsset
import org.yanavybori.core.model.PrivacyFinding
import org.yanavybori.core.model.PrivacyFindingType
import org.yanavybori.core.model.PrivacyReport
import org.yanavybori.core.model.PrivacyStatus

class PrivateMediaRepository(
    context: Context,
    private val mediaDao: MediaDao,
    private val cryptoManager: CryptoManager,
    private val privacyScanner: PrivacyScanner,
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
) : MediaRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val mediaDirectory = File(appContext.filesDir, "private_media")

    override fun observeMedia(): Flow<List<MediaAsset>> =
        mediaDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun import(request: MediaImportRequest): MediaAsset = withContext(Dispatchers.IO) {
        val uri = request.contentUri.toUri()
        val metadata = resolver.queryMetadata(uri)
        val id = ids.newId()
        val temp = File.createTempFile("media-import-", ".tmp", appContext.cacheDir)
        val encrypted = File(mediaDirectory.apply { mkdirs() }, "$id.enc")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use(input::copyTo)
            } ?: error("Не удалось открыть выбранный файл")
            val hash = FileInputStream(temp).use(cryptoManager::sha256)
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val report = privacyScanner.scan(temp.absolutePath, id, mimeType)
            FileInputStream(temp).use { input ->
                FileOutputStream(encrypted).use { output -> cryptoManager.encrypt(input, output) }
            }
            val importedAt = clock.now()
            val asset = MediaAsset(
                id = id,
                createdAt = importedAt,
                importedAt = importedAt,
                mimeType = mimeType,
                originalName = request.originalNameHint ?: metadata.name ?: "imported-$id",
                size = metadata.size ?: temp.length(),
                sha256 = hash,
                encryptedStoragePath = encrypted.absolutePath,
                source = request.source,
                privacyStatus = if (report.findings.isEmpty()) {
                    PrivacyStatus.NOT_SCANNED
                } else {
                    PrivacyStatus.POSSIBLE_PERSONAL_DATA
                },
            )
            mediaDao.upsert(asset.toEntity())
            mediaDao.upsertPrivacyReport(report.toEntity())
            asset
        } catch (error: Throwable) {
            encrypted.delete()
            throw error
        } finally {
            temp.delete()
        }
    }

    override suspend fun get(id: String): MediaAsset? = mediaDao.get(id)?.toModel()

    override suspend fun privacyReport(mediaAssetId: String): PrivacyReport? =
        mediaDao.privacyReport(mediaAssetId)?.toModel()

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val asset = mediaDao.get(id)?.toModel() ?: return@withContext
        val encryptedFile = File(asset.encryptedStoragePath)
        check(!encryptedFile.exists() || encryptedFile.delete()) {
            "Не удалось удалить зашифрованный медиафайл"
        }
        mediaDao.deletePrivacyReports(id)
        mediaDao.delete(id)
    }

    private data class SourceMetadata(val name: String?, val size: Long?)

    private fun android.content.ContentResolver.queryMetadata(uri: Uri): SourceMetadata {
        var name: String? = null
        var size: Long? = null
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return SourceMetadata(name, size)
    }
}

class LocalPrivacyScanner(
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
) : PrivacyScanner {
    override suspend fun scan(
        localPlainFilePath: String,
        mediaAssetId: String,
        mimeType: String,
    ): PrivacyReport = withContext(Dispatchers.IO) {
        val findings = buildList {
            if (mimeType.startsWith("image/")) {
                runCatching {
                    if (ExifInterface(localPlainFilePath).latLong != null) {
                        add(
                            PrivacyFinding(
                                type = PrivacyFindingType.EXIF_COORDINATES,
                                confidence = 1f,
                                description = "В метаданных изображения обнаружены координаты. Перед экспортом создайте очищенную копию.",
                            ),
                        )
                    }
                }
                add(
                    PrivacyFinding(
                        type = PrivacyFindingType.OTHER,
                        description = "В кадре могут находиться персональные данные. Автоматическая DEMO-проверка не распознаёт лица и текст.",
                    ),
                )
            }
        }
        PrivacyReport(
            id = ids.newId(),
            mediaAssetId = mediaAssetId,
            scannedAt = clock.now(),
            findings = findings,
            scannerVersion = "local-demo-1",
        )
    }
}
