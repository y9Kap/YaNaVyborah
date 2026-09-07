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
    @Composable fun rememberDocumentCreator(onResult: (String?) -> Unit): DocumentCreator
    fun openCamera()
    fun openDialer(phone: String)
    fun decodeImage(bytes: ByteArray): ImageBitmap?
    suspend fun writeDocument(handle: String, bytes: ByteArray)
    suspend fun readBundledFile(path: String): ByteArray
}

fun interface MediaPicker { fun launch(imagesOnly: Boolean) }
fun interface DocumentCreator { fun launch(request: DocumentRequest) }
data class DocumentRequest(val fileName: String, val mimeType: String)

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> {
    error("The application host must provide PlatformUi")
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) =
    LocalPlatformUi.current.BackHandler(enabled, onBack)
