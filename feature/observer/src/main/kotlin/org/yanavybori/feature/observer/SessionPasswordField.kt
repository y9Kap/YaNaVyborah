package org.yanavybori.feature.observer

import android.os.Build
import android.view.View
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Call once per form/window, including dialogs which have their own Compose host. */
@Composable
internal fun DisableSessionAutofill() {
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
internal fun SessionPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        singleLine = true,
    )
}
