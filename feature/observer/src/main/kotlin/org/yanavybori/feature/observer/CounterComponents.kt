package org.yanavybori.feature.observer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlinx.coroutines.delay
import org.yanavybori.core.model.CounterMark

@Composable
internal fun CounterLastAction(
    mark: CounterMark?,
    stoppedAt: Long? = null,
    modifier: Modifier = Modifier,
) {
    if (mark == null) {
        Text(
            "Нажатий ещё не было",
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    var now by remember(mark.id, stoppedAt) {
        mutableLongStateOf(stoppedAt ?: System.currentTimeMillis())
    }
    LaunchedEffect(mark.id, stoppedAt) {
        if (stoppedAt != null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val action = if (mark.delta > 0) "+1" else "−1"
    val elapsed = formatCounterElapsed(now - mark.timestamp)
    val label = if (stoppedAt == null) {
        "Последнее нажатие $action — $elapsed назад"
    } else {
        "Последнее нажатие $action; до остановки прошло $elapsed"
    }
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
    )
}

internal fun formatCounterElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
