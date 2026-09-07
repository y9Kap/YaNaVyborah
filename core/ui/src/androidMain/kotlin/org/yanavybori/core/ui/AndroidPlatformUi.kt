package org.yanavybori.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPlatformUi(private val context: Context) : PlatformUi {
    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        androidx.activity.compose.BackHandler(enabled, onBack)
    }

    @Composable
    override fun DisableAutofill() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill else 0
            if (Build.VERSION.SDK_INT >= 26) {
                view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            onDispose {
                if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill = previous
            }
        }
    }

    @Composable
    override fun KeepScreenOn() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }

    @Composable
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun isKeyboardVisible(): Boolean = WindowInsets.isImeVisible

    @Composable
    override fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
            onResult(it?.toString())
        }
        return remember(launcher) {
            MediaPicker { imagesOnly ->
                launcher.launch(PickVisualMediaRequest(
                    if (imagesOnly) ActivityResultContracts.PickVisualMedia.ImageOnly
                    else ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                ))
            }
        }
    }

    @Composable
    override fun rememberDocumentCreator(onResult: (String?) -> Unit): DocumentCreator {
        val launcher = rememberLauncherForActivityResult(CreateDocument()) { onResult(it?.toString()) }
        return remember(launcher) { DocumentCreator { launcher.launch(it) } }
    }

    override fun openCamera() {
        context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    override fun openDialer(phone: String) {
        val number = phone.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (number.isBlank()) {
            Toast.makeText(context, "В контакте не указан номер телефона", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "На устройстве не найдено приложение телефона", Toast.LENGTH_SHORT).show()
        }
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    override suspend fun writeDocument(handle: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(Uri.parse(handle), "wt")) {
            "Не удалось открыть выбранный файл"
        }.use { it.write(bytes) }
    }

    override suspend fun readBundledFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains("..") && !path.contains('\\'))
        context.assets.open(path).use { it.readBytes() }
    }
}

private class CreateDocument : ActivityResultContract<DocumentRequest, Uri?>() {
    override fun createIntent(context: Context, input: DocumentRequest): Intent =
        ActivityResultContracts.CreateDocument(input.mimeType).createIntent(context, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        ActivityResultContracts.CreateDocument("*/*").parseResult(resultCode, intent)
}
