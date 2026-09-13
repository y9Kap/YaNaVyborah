package org.yanavybori.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** Operations supplied by the application host. Handles are opaque to shared UI. */
interface PlatformUi {
    @Composable fun BackHandler(enabled: Boolean, onBack: () -> Unit)
    @Composable fun DisableAutofill()
    @Composable fun KeepScreenOn()
    @Composable fun isKeyboardVisible(): Boolean
    @Composable fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker
    @Composable fun rememberJsonDocumentPicker(onResult: (String?) -> Unit): JsonDocumentPicker
    @Composable fun rememberDocumentCreator(onResult: (String?) -> Unit): DocumentCreator
    fun openCamera()
    fun openDialer(phone: String)
    fun openExternalLink(url: String)
    fun copyText(text: String)
    fun decodeImage(bytes: ByteArray): ImageBitmap?
    fun loadPrivateText(key: String): String?
    fun savePrivateText(key: String, value: String)
    suspend fun loadPrivateBytes(key: String): ByteArray?
    suspend fun savePrivateBytes(key: String, bytes: ByteArray)
    suspend fun deletePrivateBytes(key: String)
    suspend fun readPickedDocument(handle: String, maxBytes: Int): PickedDocument
    /** Re-encodes an image without source metadata and limits its dimensions. */
    suspend fun sanitizeImage(bytes: ByteArray, maxDimension: Int): ByteArray
    /** Creates a fresh unlinkable signing key and returns a detached signature. */
    suspend fun signAnonymously(payload: ByteArray): AnonymousSignature
    suspend fun fetchHttpsText(url: String, maxBytes: Int): String
    suspend fun writeDocument(handle: String, bytes: ByteArray)
    suspend fun readBundledFile(path: String): ByteArray
}

fun interface MediaPicker { fun launch(imagesOnly: Boolean) }
fun interface JsonDocumentPicker { fun launch() }
fun interface DocumentCreator { fun launch(request: DocumentRequest) }
data class DocumentRequest(val fileName: String, val mimeType: String)
data class PickedDocument(val name: String, val bytes: ByteArray)
data class AnonymousSignature(
    val algorithm: String,
    val publicKeyFormat: String,
    val signatureFormat: String,
    val publicKeyBase64: String,
    val signatureBase64: String,
)

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> {
    error("The application host must provide PlatformUi")
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) =
    LocalPlatformUi.current.BackHandler(enabled, onBack)
