package org.yanavybori.feature.observer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import org.yanavybori.core.ui.AndroidPlatformUi
import org.yanavybori.core.ui.LocalPlatformUi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.ui.YaNaVyborahTheme

@RunWith(AndroidJUnit4::class)
class SessionPasswordUiTest {
    @get:Rule val compose = createComposeRule()
    private val session = ObservationSession("test", "pack", "42", startedAt = 1,
        currentVotingDay = "day", currentStage = "voting", hasDeletionPassword = true)

    @Test fun restoring_ui_never_restores_deletion_password() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TestTheme { DeleteSessionDialog(session, {}, { _, _ -> }, {}) }
        }
        compose.onNodeWithText("Удалить", useUnmergedTree = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("fixture-pass-123")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Удалить", useUnmergedTree = false).assertIsNotEnabled()
    }

    @Test fun reopening_dialog_requires_fresh_password() {
        val shown = mutableStateOf(true)
        compose.setContent {
            TestTheme { if (shown.value) DeleteSessionDialog(session, {}, { _, _ -> }, {}) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("fixture-pass-123")
        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { shown.value = true }
        compose.onNodeWithText("Удалить", useUnmergedTree = false).assertIsNotEnabled()
    }
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlatformUi provides AndroidPlatformUi(LocalContext.current)) {
        YaNaVyborahTheme(content = content)
    }
}
