package org.yanavybori.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import javax.net.ssl.HttpsURLConnection

class AndroidPlatformUi(private val context: Context) : PlatformUi {
    override fun applyTheme(darkTheme: Boolean) {
        val activity = context as? ComponentActivity ?: return
        val style = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

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
    override fun rememberJsonDocumentPicker(onResult: (String?) -> Unit): JsonDocumentPicker {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            onResult(it?.toString())
        }
        return remember(launcher) {
            JsonDocumentPicker { launcher.launch(arrayOf("application/json", "text/json", "text/plain")) }
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

    override fun openExternalLink(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https")) {
            Toast.makeText(context, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show()
        }
    }

    override fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Текст из приложения", text))
        Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    override fun loadPrivateText(key: String): String? =
        context.getSharedPreferences(PRIVATE_TEXT_STORE, Context.MODE_PRIVATE).getString(key, null)

    override fun savePrivateText(key: String, value: String) {
        context.getSharedPreferences(PRIVATE_TEXT_STORE, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    override suspend fun loadPrivateBytes(key: String): ByteArray? = withContext(Dispatchers.IO) {
        privateBinaryFile(key).takeIf(File::isFile)?.readBytes()
    }

    override suspend fun savePrivateBytes(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = privateBinaryFile(key)
        target.parentFile?.mkdirs()
        if (target.isFile && target.readBytes().contentEquals(bytes)) return@withContext
        val temporary = File.createTempFile("private-", ".tmp", target.parentFile)
        try {
            temporary.writeBytes(bytes)
            if (target.exists()) check(target.delete()) { "Не удалось заменить приватный файл" }
            check(temporary.renameTo(target)) { "Не удалось сохранить приватный файл" }
        } finally {
            temporary.delete()
        }
    }

    override suspend fun deletePrivateBytes(key: String) = withContext(Dispatchers.IO) {
        val target = privateBinaryFile(key)
        check(!target.exists() || target.delete()) { "Не удалось удалить приватный файл" }
    }

    override suspend fun readPickedDocument(handle: String, maxBytes: Int): PickedDocument =
        withContext(Dispatchers.IO) {
            require(maxBytes > 0)
            val uri = Uri.parse(handle)
            val name = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "recommendations.json"
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Не удалось открыть выбранный файл"
            }.use { input -> input.readWithLimit(maxBytes) }
            PickedDocument(name, bytes)
        }

    override suspend fun sanitizeImage(bytes: ByteArray, maxDimension: Int): ByteArray =
        withContext(Dispatchers.Default) {
            require(maxDimension in 256..4096) { "Некорректный размер изображения" }
            val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Выбранный файл не является поддерживаемым изображением"
            }
            val oriented = source.applyExifOrientation(bytes)
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(oriented.width, oriented.height))
            val sanitized = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * scale).toInt().coerceAtLeast(1),
                    (oriented.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                oriented
            }
            try {
                ByteArrayOutputStream().use { output ->
                    check(sanitized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                        "Не удалось очистить фотографию"
                    }
                    output.toByteArray()
                }
            } finally {
                if (sanitized !== oriented) sanitized.recycle()
                if (oriented !== source) oriented.recycle()
                source.recycle()
            }
        }

    override suspend fun signAnonymously(payload: ByteArray): AnonymousSignature =
        withContext(Dispatchers.Default) { createAnonymousSignature(payload) }

    override suspend fun fetchHttpsText(url: String, maxBytes: Int): String = withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null) {
            "Разрешены только HTTPS-ссылки без логина и пароля"
        }
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json, text/plain;q=0.8")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            require(status in 200..299) { "Сервер ответил кодом $status" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Ссылка перенаправила на небезопасный адрес"
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength <= maxBytes) { "Файл слишком большой" }
            connection.inputStream.use { it.readWithLimit(maxBytes) }
                .decodeToString(throwOnInvalidSequence = true)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun writeDocument(handle: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(Uri.parse(handle), "wt")) {
            "Не удалось открыть выбранный файл"
        }.use { it.write(bytes) }
    }

    override suspend fun readBundledFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains("..") && !path.contains('\\'))
        context.assets.open(path).use { it.readBytes() }
    }

    private fun privateBinaryFile(key: String): File {
        require(PRIVATE_BINARY_KEY.matches(key)) { "Некорректный ключ приватного файла" }
        return File(File(context.filesDir, "private_binary"), key)
    }

    private fun android.graphics.Bitmap.applyExifOrientation(bytes: ByteArray): android.graphics.Bitmap {
        val orientation = runCatching {
            androidx.exifinterface.media.ExifInterface(bytes.inputStream()).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        return if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL ||
            orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
        ) this else android.graphics.Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun createAnonymousSignature(payload: ByteArray): AnonymousSignature {
    val generator = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }
    val keyPair = generator.generateKeyPair()
    val signature = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(payload)
        sign()
    }
    return AnonymousSignature(
        algorithm = "ECDSA-P256-SHA256",
        publicKeyFormat = "X.509-SPKI",
        signatureFormat = "ASN.1-DER",
        publicKeyBase64 = Base64.encode(keyPair.public.encoded),
        signatureBase64 = Base64.encode(signature),
    )
}

private const val PRIVATE_TEXT_STORE = "yanavyborah-private-text"
private val PRIVATE_BINARY_KEY = Regex("^[a-zA-Z0-9._-]{1,160}$")

private fun java.io.InputStream.readWithLimit(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Файл слишком большой" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private class CreateDocument : ActivityResultContract<DocumentRequest, Uri?>() {
    override fun createIntent(context: Context, input: DocumentRequest): Intent =
        ActivityResultContracts.CreateDocument(input.mimeType).createIntent(context, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        ActivityResultContracts.CreateDocument("*/*").parseResult(resultCode, intent)
}
