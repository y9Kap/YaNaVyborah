package org.yanavybori.shared

import org.yanavybori.core.ui.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.navigation.RootRoute
import org.yanavybori.core.ui.AppCard
import org.yanavybori.core.ui.APP_THEME_PREFERENCE_KEY
import org.yanavybori.core.ui.AppThemeMode
import org.yanavybori.core.ui.AppHelpButton
import org.yanavybori.core.ui.DemoBanner
import org.yanavybori.core.ui.LocalPlatformUi
import org.yanavybori.core.ui.YaNaVyborahTheme
import org.yanavybori.feature.observer.ObserverFeature
import org.yanavybori.feature.settings.SettingsScreen
import org.yanavybori.feature.voter.VoterScreen
import org.yanavybori.feature.workpressure.WorkPressureScreen

@Composable
fun YaNaVyborahRoot(container: SharedAppContainer) {
    val platform = LocalPlatformUi.current
    var themeMode by remember(platform) {
        mutableStateOf(AppThemeMode.fromStored(platform.loadPrivateText(APP_THEME_PREFERENCE_KEY)))
    }
    SideEffect { platform.applyTheme(themeMode == AppThemeMode.DARK) }

    YaNaVyborahTheme(darkTheme = themeMode == AppThemeMode.DARK) {
        SelectionContainer {
            RootContent(
                container = container,
                themeMode = themeMode,
                onThemeChange = { mode ->
                    platform.savePrivateText(APP_THEME_PREFERENCE_KEY, mode.name)
                    themeMode = mode
                },
            )
        }
    }
}

@Composable
private fun RootContent(
    container: SharedAppContainer,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
) {
    val platform = LocalPlatformUi.current
    val bootstrap by container.bootstrapState.collectAsStateWithLifecycle()
    val manifest by container.electionPackRepository.observeActiveManifest()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var routeName by rememberSaveable { mutableStateOf(RootRoute.HOME.name) }
    val route = RootRoute.valueOf(routeName)

    BackHandler(enabled = route != RootRoute.HOME) {
        routeName = RootRoute.HOME.name
    }

    when (val current = bootstrap) {
        BootstrapState.Loading -> LoadingScreen()
        is BootstrapState.Failed -> FailureScreen(current.message) {
            scope.launch { container.bootstrapElectionPack() }
        }
        BootstrapState.Ready -> when (route) {
            RootRoute.HOME -> HomeScreen(manifest, { routeName = it.name })
            RootRoute.OBSERVER -> ObserverFeature(container.observerDependencies) { routeName = RootRoute.HOME.name }
            RootRoute.VOTER -> VoterScreen(
                onBack = { routeName = RootRoute.HOME.name },
                onWorkPressure = { routeName = RootRoute.WORK_PRESSURE.name },
            )
            RootRoute.WORK_PRESSURE -> WorkPressureScreen { routeName = RootRoute.HOME.name }
            RootRoute.SETTINGS -> SettingsScreen(
                manifest = manifest,
                applicationVersion = container.applicationVersion,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                exportFileName = ::userDataExportFileName,
                buildExport = { exportedAt ->
                    container.buildUserDataExport(platform, exportedAt)
                },
                onBack = { routeName = RootRoute.HOME.name },
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("Проверяем локальный Election Pack", modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun FailureScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не удалось открыть локальный пакет", style = MaterialTheme.typography.titleLarge)
        Text(message, modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(manifest: ElectionPackManifest?, navigate: (RootRoute) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Я на выборах", fontWeight = FontWeight.Bold) },
                actions = {
                    AppHelpButton()
                    IconButton(onClick = { navigate(RootRoute.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Офлайн-помощник", style = MaterialTheme.typography.headlineMedium) }
            if (manifest?.isDemo == true) item { DemoBanner() }
            item {
                AppCard(
                    "Наблюдатель",
                    "Помощник на участке, журнал, ситуации, сверки и протокол.",
                    { navigate(RootRoute.OBSERVER) },
                )
            }
            item {
                AppCard(
                    "Избиратель",
                    "Права и безопасность, личный план, рекомендации и добровольный анонимный архив бюллетеня.",
                    { navigate(RootRoute.VOTER) },
                )
            }
            item {
                AppCard(
                    "Давление на работе",
                    "Безопасные шаги, шаблоны обращений, законы и официальные приёмные.",
                    { navigate(RootRoute.WORK_PRESSURE) },
                )
            }
            item {
                Text(
                    "Все пользовательские данные остаются на устройстве. Автоматической отправки нет.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
