package org.yanavybori.core.content

import android.content.Context
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetElectionPackSource(
    context: Context,
    private val root: String,
) : ElectionPackSource {
    private val assets = context.applicationContext.assets

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.isSafePackPath()) { "Недопустимый путь в Election Pack: $path" }
        try {
            assets.open("$root/$path").use { it.readBytes() }
        } catch (error: FileNotFoundException) {
            throw ElectionPackImportException.MissingFile(path, error)
        }
    }
}

