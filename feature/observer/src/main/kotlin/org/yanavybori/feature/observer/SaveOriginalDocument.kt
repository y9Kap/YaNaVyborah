package org.yanavybori.feature.observer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import org.yanavybori.core.model.ReferenceOriginal

internal class SaveOriginalDocument : ActivityResultContract<ReferenceOriginal, Uri?>() {
    override fun createIntent(context: Context, input: ReferenceOriginal): Intent =
        ActivityResultContracts.CreateDocument(input.mimeType).createIntent(context, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        ActivityResultContracts.CreateDocument("*/*").parseResult(resultCode, intent)
}
