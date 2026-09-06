package org.yanavybori.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun AppHelpButton() {
    var opened by rememberSaveable { mutableStateOf(false) }
    var fullGuide by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { opened = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Как пользоваться приложением")
    }
    if (opened && !fullGuide) AlertDialog(
        onDismissRequest = { opened = false },
        title = { Text("С чего начать") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Откройте «Наблюдатель», создайте сессию участка и запомните пароль удаления.")
                Text("Выберите день. В чек-листе настройте «На участке / На дому», отмечайте пункты и нажимайте «Зафиксировать».")
                Text("Счётчики считают потоки, журнал хранит события, «Что происходит?» помогает найти подсказку. Жалобы — редактируемые черновики; сверки и протокол помогают проверить числа.")
                Text("Документы доступны офлайн. Записи сохраняются на устройстве; приложение ничего не отправляет. «Избиратель» и «Давление на работе» пока не готовы.")
            }
        },
        confirmButton = { TextButton(onClick = { fullGuide = true }) { Text("Полный гайд") } },
        dismissButton = { TextButton(onClick = { opened = false }) { Text("Понятно") } },
    )
    if (opened && fullGuide) AppGuideDialog(onBack = { fullGuide = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppGuideDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = remember {
        context.assets.open("app-guide.md").bufferedReader().use { it.readText() }
            .split("\n## ").drop(1).map { it.substringBefore('\n') to it.substringAfter('\n').trim() }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onBack)
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Гайд по приложению") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Краткая справка") }
                },
            )
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Column {
                        Text("Оглавление", style = MaterialTheme.typography.titleLarge)
                        sections.forEachIndexed { index, section ->
                            TextButton(onClick = { scope.launch { listState.animateScrollToItem(index + 1) } }) {
                                Text(section.first)
                            }
                        }
                    }
                }
                itemsIndexed(sections) { _, (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(body)
                        TextButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) { Text("К оглавлению") }
                    }
                }
            }
        }
    }
}
