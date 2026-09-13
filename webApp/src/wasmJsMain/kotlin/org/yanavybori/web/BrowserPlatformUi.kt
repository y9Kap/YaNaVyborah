@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.yanavybori.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlinx.browser.window
import org.yanavybori.core.ui.DocumentCreator
import org.yanavybori.core.ui.DocumentRequest
import org.yanavybori.core.ui.JsonDocumentPicker
import org.yanavybori.core.ui.MediaPicker
import org.yanavybori.core.ui.PickedDocument
import org.yanavybori.core.ui.PlatformUi

@JsFun("""(accept, capture) => new Promise((resolve, reject) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    if (capture) input.setAttribute('capture', 'environment');
    input.style.display = 'none';
    document.body.appendChild(input);
    let settled = false;
    const finish = (value) => {
        if (settled) return;
        settled = true;
        input.remove();
        resolve(value);
    };
    input.addEventListener('cancel', () => finish(null), { once: true });
    input.addEventListener('change', () => {
        const file = input.files && input.files[0];
        if (!file) { finish(null); return; }
        const reader = new FileReader();
        reader.onerror = () => { input.remove(); reject(reader.error || new Error('Не удалось прочитать файл')); };
        reader.onload = () => finish(JSON.stringify({
            name: file.name || 'media',
            mimeType: file.type || 'application/octet-stream',
            base64: String(reader.result).split(',', 2)[1] || ''
        }));
        reader.readAsDataURL(file);
    }, { once: true });
    input.click();
})""")
private external fun pickBrowserFile(accept: JsString, capture: Boolean): Promise<JsString?>

@JsFun("""(path) => fetch(path).then(response => {
    if (!response.ok) throw new Error('Файл не найден: ' + path);
    return response.arrayBuffer();
}).then(buffer => {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
})""")
private external fun fetchBase64(path: JsString): Promise<JsString>

@JsFun("""(url, maxBytes) => fetch(url, {
    method: 'GET',
    credentials: 'omit',
    cache: 'no-store',
    redirect: 'follow',
    referrerPolicy: 'no-referrer',
    headers: { 'Accept': 'application/json, text/plain;q=0.8' }
}).then(response => {
    if (!response.ok) throw new Error('Сервер ответил кодом ' + response.status);
    if (!String(response.url).startsWith('https://')) throw new Error('Ссылка перенаправила на небезопасный адрес');
    const declared = Number(response.headers.get('content-length'));
    if (Number.isFinite(declared) && declared > maxBytes) throw new Error('Файл слишком большой');
    return response.arrayBuffer();
}).then(buffer => {
    if (buffer.byteLength > maxBytes) throw new Error('Файл слишком большой');
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
})""")
private external fun fetchRemoteBase64(url: JsString, maxBytes: Int): Promise<JsString>

@JsFun("""(base64, fileName, mimeType) => {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}""")
private external fun downloadBase64(base64: JsString, fileName: JsString, mimeType: JsString)

@JsFun("""(url) => window.open(url, '_blank', 'noopener,noreferrer')""")
private external fun openBrowserLink(url: JsString)

@JsFun("""(text) => {
    const fallback = () => {
        const area = document.createElement('textarea');
        area.value = text;
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        document.execCommand('copy');
        area.remove();
    };
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).catch(fallback);
        return;
    }
    fallback();
}""")
private external fun copyBrowserText(text: JsString)

@JsFun("""(mode, key, value) => new Promise((resolve, reject) => {
    const request = indexedDB.open('yanavyborah-private', 1);
    request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains('bytes')) request.result.createObjectStore('bytes');
    };
    request.onerror = () => reject(request.error || new Error('Не удалось открыть приватное хранилище'));
    request.onsuccess = () => {
        const db = request.result;
        const transaction = db.transaction('bytes', mode === 'get' ? 'readonly' : 'readwrite');
        const store = transaction.objectStore('bytes');
        const operation = mode === 'get' ? store.get(key) : mode === 'put' ? store.put(value, key) : store.delete(key);
        operation.onerror = () => reject(operation.error || new Error('Ошибка приватного хранилища'));
        operation.onsuccess = () => resolve(mode === 'get' ? (operation.result ?? null) : 'ok');
        transaction.oncomplete = () => db.close();
    };
})""")
private external fun privateBytesOperation(mode: JsString, key: JsString, value: JsString): Promise<JsString?>

@JsFun("""(base64, maxDimension) => new Promise((resolve, reject) => {
    const binary = atob(base64);
    const input = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) input[i] = binary.charCodeAt(i);
    createImageBitmap(new Blob([input])).then(bitmap => {
        const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(bitmap.width * scale));
        canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        const context = canvas.getContext('2d', { alpha: false });
        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        bitmap.close();
        canvas.toBlob(blob => {
            if (!blob) { reject(new Error('Не удалось очистить фотографию')); return; }
            const reader = new FileReader();
            reader.onerror = () => reject(reader.error || new Error('Не удалось прочитать фотографию'));
            reader.onload = () => resolve(String(reader.result).split(',', 2)[1] || '');
            reader.readAsDataURL(blob);
        }, 'image/jpeg', 0.88);
    }).catch(() => reject(new Error('Выбранный файл не является поддерживаемым изображением')));
})""")
private external fun sanitizeBrowserImage(base64: JsString, maxDimension: Int): Promise<JsString>

@JsFun("""(base64) => (async () => {
    if (!globalThis.crypto || !crypto.subtle) throw new Error('Криптографическая подпись недоступна в этом браузере');
    const binary = atob(base64);
    const payload = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) payload[i] = binary.charCodeAt(i);
    const keys = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
    const rawSignature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, keys.privateKey, payload));
    if (rawSignature.length !== 64) throw new Error('Неожиданный формат криптографической подписи');
    const derInteger = source => {
        let offset = 0;
        while (offset < source.length - 1 && source[offset] === 0) offset++;
        let value = source.slice(offset);
        if ((value[0] & 0x80) !== 0) value = Uint8Array.from([0, ...value]);
        return Uint8Array.from([0x02, value.length, ...value]);
    };
    const r = derInteger(rawSignature.slice(0, 32));
    const s = derInteger(rawSignature.slice(32));
    const signature = Uint8Array.from([0x30, r.length + s.length, ...r, ...s]);
    const publicKey = new Uint8Array(await crypto.subtle.exportKey('spki', keys.publicKey));
    const encode = bytes => {
        let value = '';
        const chunk = 0x8000;
        for (let i = 0; i < bytes.length; i += chunk) value += String.fromCharCode(...bytes.subarray(i, i + chunk));
        return btoa(value);
    };
    return JSON.stringify({ publicKey: encode(publicKey), signature: encode(signature) });
})()""")
private external fun signBrowserPayload(base64: JsString): Promise<JsString>

internal class BrowserPlatformUi : PlatformUi {
    private val json = Json { ignoreUnknownKeys = true }
    private val documentRequests = mutableMapOf<String, DocumentRequest>()

    override fun applyTheme(darkTheme: Boolean) {
        window.document.documentElement?.setAttribute("data-color-scheme", if (darkTheme) "dark" else "light")
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        val currentOnBack = rememberUpdatedState(onBack)
        DisposableEffect(enabled) {
            if (!enabled) return@DisposableEffect onDispose { }
            val listener: (Event) -> Unit = listener@{ event ->
                if ((event as? KeyboardEvent)?.key == "Escape") currentOnBack.value()
            }
            window.addEventListener("keydown", listener)
            onDispose { window.removeEventListener("keydown", listener) }
        }
    }

    @Composable override fun DisableAutofill() = Unit
    @Composable override fun KeepScreenOn() = Unit
    @Composable override fun isKeyboardVisible(): Boolean = false

    @Composable
    override fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker {
        val scope = rememberCoroutineScope()
        val currentOnResult = rememberUpdatedState(onResult)
        return remember(scope) {
            MediaPicker { imagesOnly ->
                scope.launch {
                    runCatching {
                        val accept = if (imagesOnly) "image/*" else "image/*,video/*"
                        pickBrowserFile(accept.toJsString(), false).await<JsString?>()?.toString()
                            ?.let { raw -> BrowserFileRegistry.put(json.decodeFromString<PickedBrowserFile>(raw)) }
                    }.fold(
                        onSuccess = { currentOnResult.value(it) },
                        onFailure = { currentOnResult.value(null) },
                    )
                }
            }
        }
    }

    @Composable
    override fun rememberJsonDocumentPicker(onResult: (String?) -> Unit): JsonDocumentPicker {
        val scope = rememberCoroutineScope()
        val currentOnResult = rememberUpdatedState(onResult)
        return remember(scope) {
            JsonDocumentPicker {
                scope.launch {
                    runCatching {
                        pickBrowserFile("application/json,.json,text/plain".toJsString(), false)
                            .await<JsString?>()?.toString()
                            ?.let { raw -> BrowserFileRegistry.put(json.decodeFromString<PickedBrowserFile>(raw)) }
                    }.fold(
                        onSuccess = { currentOnResult.value(it) },
                        onFailure = { currentOnResult.value(null) },
                    )
                }
            }
        }
    }

    @Composable
    override fun rememberDocumentCreator(onResult: (String?) -> Unit): DocumentCreator {
        val currentOnResult = rememberUpdatedState(onResult)
        return remember {
            DocumentCreator { request ->
                val handle = org.yanavybori.core.common.UuidGenerator.newId()
                documentRequests[handle] = request
                currentOnResult.value(handle)
            }
        }
    }

    override fun openCamera() {
        pickBrowserFile("image/*".toJsString(), true)
    }

    override fun openDialer(phone: String) {
        val safe = phone.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (safe.isNotBlank()) window.location.href = "tel:$safe"
    }

    override fun openExternalLink(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) {
            openBrowserLink(url.toJsString())
        }
    }

    override fun copyText(text: String) {
        copyBrowserText(text.toJsString())
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()

    override fun loadPrivateText(key: String): String? =
        window.localStorage.getItem("yanavyborah.private.$key")

    override fun savePrivateText(key: String, value: String) {
        window.localStorage.setItem("yanavyborah.private.$key", value)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun loadPrivateBytes(key: String): ByteArray? {
        requirePrivateBinaryKey(key)
        val encoded = privateBytesOperation("get".toJsString(), key.toJsString(), "".toJsString())
            .await<JsString?>()?.toString() ?: return null
        return Base64.decode(encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun savePrivateBytes(key: String, bytes: ByteArray) {
        requirePrivateBinaryKey(key)
        privateBytesOperation("put".toJsString(), key.toJsString(), Base64.encode(bytes).toJsString()).await<JsString?>()
    }

    override suspend fun deletePrivateBytes(key: String) {
        requirePrivateBinaryKey(key)
        privateBytesOperation("delete".toJsString(), key.toJsString(), "".toJsString()).await<JsString?>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun readPickedDocument(handle: String, maxBytes: Int): PickedDocument {
        require(maxBytes > 0)
        val file = requireNotNull(BrowserFileRegistry.take(handle)) { "Выбранный файл больше недоступен" }
        val bytes = Base64.decode(file.base64)
        require(bytes.size <= maxBytes) { "Файл слишком большой" }
        return PickedDocument(file.name, bytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun sanitizeImage(bytes: ByteArray, maxDimension: Int): ByteArray {
        require(maxDimension in 256..4096) { "Некорректный размер изображения" }
        val encoded = sanitizeBrowserImage(Base64.encode(bytes).toJsString(), maxDimension).await<JsString>().toString()
        return Base64.decode(encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signAnonymously(payload: ByteArray): org.yanavybori.core.ui.AnonymousSignature {
        val raw = signBrowserPayload(Base64.encode(payload).toJsString()).await<JsString>().toString()
        val result = json.decodeFromString<BrowserAnonymousSignature>(raw)
        return org.yanavybori.core.ui.AnonymousSignature(
            algorithm = "ECDSA-P256-SHA256",
            publicKeyFormat = "X.509-SPKI",
            signatureFormat = "ASN.1-DER",
            publicKeyBase64 = result.publicKey,
            signatureBase64 = result.signature,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetchHttpsText(url: String, maxBytes: Int): String {
        val normalized = url.trim()
        require(normalized.startsWith("https://") && !normalized.substringAfter("https://").substringBefore('/').contains('@')) {
            "Разрешены только HTTPS-ссылки без логина и пароля"
        }
        val encoded = fetchRemoteBase64(normalized.toJsString(), maxBytes).await<JsString>().toString()
        return Base64.decode(encoded).decodeToString(throwOnInvalidSequence = true)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun writeDocument(handle: String, bytes: ByteArray) {
        val request = requireNotNull(documentRequests.remove(handle)) { "Операция сохранения отменена" }
        downloadBase64(
            Base64.encode(bytes).toJsString(),
            request.fileName.toJsString(),
            request.mimeType.toJsString(),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun readBundledFile(path: String): ByteArray {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains("..") && !path.contains('\\'))
        val encoded = fetchBase64(path.toJsString()).await<JsString>().toString()
        return Base64.decode(encoded)
    }

    private fun requirePrivateBinaryKey(key: String) {
        require(Regex("^[a-zA-Z0-9._-]{1,160}$").matches(key)) { "Некорректный ключ приватного файла" }
    }
}

@kotlinx.serialization.Serializable
private data class BrowserAnonymousSignature(val publicKey: String, val signature: String)
