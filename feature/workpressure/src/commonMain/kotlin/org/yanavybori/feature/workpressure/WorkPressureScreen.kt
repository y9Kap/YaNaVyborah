package org.yanavybori.feature.workpressure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yanavybori.core.ui.AppHelpButton
import org.yanavybori.core.ui.BackHandler
import org.yanavybori.core.ui.LocalPlatformUi

private enum class HelpSection(val label: String) {
    FIRST_STEPS("Что делать"),
    TEMPLATES("Шаблоны"),
    LAWS("Законы"),
    CONTACTS("Куда обратиться"),
}

internal data class WorkPressureTemplate(
    val id: String,
    val title: String,
    val recipient: String,
    val whenToUse: String,
    val beforeSending: List<String>,
    val body: String,
)

internal data class WorkPressureLaw(
    val title: String,
    val citation: String,
    val summary: String,
    val url: String,
)

internal data class WorkPressureContact(
    val title: String,
    val purpose: String,
    val url: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPressureScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedName by rememberSaveable { mutableStateOf(HelpSection.FIRST_STEPS.name) }
    val selected = HelpSection.valueOf(selectedName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Давление на работе") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { AppHelpButton() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VotingReminder() }
            item {
                Text(
                    "Помощник работает офлайн. Он не отправляет жалобу автоматически и не заменяет помощь юриста.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                LazyRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(HelpSection.entries) { section ->
                        FilterChip(
                            selected = selected == section,
                            onClick = { selectedName = section.name },
                            label = { Text(section.label) },
                        )
                    }
                }
            }
            when (selected) {
                HelpSection.FIRST_STEPS -> firstSteps()
                HelpSection.TEMPLATES -> {
                    item {
                        AdviceCard(
                            "Перед отправкой",
                            "Замените все поля в квадратных скобках, приложите только относящиеся к делу копии и сохраните подтверждение подачи. Не передавайте единственный экземпляр доказательства.",
                        )
                    }
                    items(workPressureTemplates, key = { it.id }) { template ->
                        ComplaintTemplateCard(template)
                    }
                }
                HelpSection.LAWS -> {
                    item {
                        AdviceCard(
                            "Проверьте редакцию",
                            "Краткие выдержки доступны без сети. Перед подачей обращения откройте ссылку и проверьте действующую редакцию и применимость нормы к конкретным выборам.",
                        )
                    }
                    items(workPressureLaws, key = { it.citation }) { law -> LawCard(law) }
                }
                HelpSection.CONTACTS -> {
                    item {
                        AdviceCard(
                            "Выберите безопасный канал",
                            "Не обязательно обращаться во все органы сразу. Если есть риск увольнения или иной мести, сначала сохраните доказательства вне рабочего устройства и обсудите порядок действий с независимым юристом или профсоюзом.",
                        )
                    }
                    items(workPressureContacts, key = { it.url }) { contact -> ContactCard(contact) }
                    item {
                        AdviceCard(
                            "Если уже уволили",
                            "Судебный срок по спору об увольнении короткий: обычно один месяц по ст. 392 ТК РФ. Не ждите ответа других ведомств перед консультацией о подаче иска.",
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.firstSteps() {
    item {
        AdviceCard(
            "Если угроза непосредственная",
            "Уйдите в безопасное место и звоните 112. Не спорьте в одиночку, если это повышает риск для вас.",
            warning = true,
        )
    }
    item {
        ChecklistCard(
            "Зафиксируйте факты",
            listOf(
                "Запишите дату, время, место, точные слова, ФИО и должности участников.",
                "Сохраните оригиналы сообщений, писем, списков и служебных поручений без редактирования.",
                "Отдельно перечислите свидетелей и способы связи с ними — только с их согласия.",
                "Сделайте резервную копию на личном устройстве. Рабочий телефон, почта и облако могут контролироваться работодателем.",
                "Если безопасно, попросите сформулировать требование письменно. Не провоцируйте конфликт ради доказательства.",
            ),
        )
    }
    item {
        ChecklistCard(
            "Не ухудшайте своё положение",
            listOf(
                "Не подписывайте заявление «по собственному желанию», если увольнение не является вашей волей.",
                "Не отдавайте оригиналы доказательств и не публикуйте персональные данные коллег.",
                "Не меняйте файлы: для обращения подготовьте копии, а оригиналы сохраните отдельно.",
                "Перед скрытой аудиозаписью или публикацией материалов уточните допустимость у юриста.",
                "Если давление продолжается, ведите хронологию каждого эпизода, включая ответ работодателя после жалобы.",
            ),
        )
    }
    item {
        ChecklistCard(
            "Что должно быть в обращении",
            listOf(
                "Кому направлено обращение и ваши данные для ответа.",
                "Только проверяемые факты в хронологическом порядке, без догадок о мотивах.",
                "В чём состояло требование, как контролировали голосование и чем угрожали.",
                "Какие материалы и свидетели подтверждают каждый факт.",
                "Конкретные просьбы: зарегистрировать, проверить, прекратить нарушение, принять меры и сообщить результат.",
                "Дата, подпись и перечень приложений; при личной подаче — отметка о принятии на вашей копии.",
            ),
        )
    }
}

@Composable
private fun VotingReminder() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Важно", fontWeight = FontWeight.Bold)
                Text(
                    "Отчёт работодателю не заменяет голосование. Ваш выбор свободный и тайный. " +
                        "Если вы решили голосовать, придите на участок в последний официальный день голосования — " +
                        "воскресенье, 20 сентября 2026 года.",
                )
            }
        }
    }
}

@Composable
private fun AdviceCard(title: String, text: String, warning: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

@Composable
private fun ChecklistCard(title: String, points: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            points.forEach { Text("• $it") }
        }
    }
}

@Composable
private fun ComplaintTemplateCard(template: WorkPressureTemplate) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }
    val platform = LocalPlatformUi.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Кому: ${template.recipient}", style = MaterialTheme.typography.labelLarge)
            Text(template.whenToUse)
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
                Text(if (expanded) "Скрыть шаблон" else "Открыть шаблон", Modifier.padding(start = 8.dp))
            }
            if (expanded) {
                Text("Перед отправкой", fontWeight = FontWeight.SemiBold)
                template.beforeSending.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                SelectionContainer {
                    Text(template.body, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { platform.copyText(template.body) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("Скопировать шаблон", Modifier.padding(start = 8.dp))
                }
                Text(
                    "Буфер обмена может быть доступен другим приложениям. В шаблоне нет ваших данных — заполняйте его в доверенном редакторе.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LawCard(law: WorkPressureLaw) {
    val platform = LocalPlatformUi.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(law.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(law.citation, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(law.summary)
            OutlinedButton(onClick = { platform.openExternalLink(law.url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("Открыть действующую редакцию", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ContactCard(contact: WorkPressureContact) {
    val platform = LocalPlatformUi.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(contact.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(contact.purpose)
            OutlinedButton(onClick = { platform.openExternalLink(contact.url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("Открыть официальную приёмную", Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal val workPressureTemplates = listOf(
    WorkPressureTemplate(
        id = "crime-report",
        title = "Заявление о возможном преступлении",
        recipient = "Территориальный орган Следственного комитета, прокуратура или МВД",
        whenToUse = "Когда требование голосовать определённым образом сопровождалось контролем, принуждением, угрозами или использованием должностного положения.",
        beforeSending = listOf(
            "Укажите только факты, которые наблюдали лично или можете подтвердить.",
            "Не утверждайте заранее, что преступление доказано: просите проверить признаки нарушения.",
            "Перечислите приложения и попросите сообщить регистрационный номер.",
        ),
        body = """
            В [наименование территориального органа]
            от [ФИО, адрес для ответа, телефон или электронная почта]

            ЗАЯВЛЕНИЕ О ВОЗМОЖНОМ ПРЕСТУПЛЕНИИ

            [Дата] в [время] в [место / организация] [ФИО и должность лица] потребовал(а) от меня [точное содержание требования: участвовать в выборах, голосовать определённым образом, предоставить фотографию или иной отчёт].

            Требование сопровождалось следующими действиями или угрозами: [дословно или максимально точно]. Для контроля использовались: [списки, чат, фотография бюллетеня, отчёт руководителю, иное]. Связь требования со служебным положением: [кто кому подчиняется, каким ресурсом или полномочием пользовалось лицо].

            Свидетели: [ФИО и контакты при наличии согласия]. Материалы: [сообщения, письма, записи, документы]. Последствия или повторные эпизоды: [описание].

            Прошу зарегистрировать сообщение, провести проверку изложенных обстоятельств, в том числе на наличие признаков воспрепятствования свободному осуществлению избирательных прав с принуждением или использованием служебного положения (ст. 141 УК РФ), принять решение в установленном порядке и уведомить меня о результате.

            Приложения: [перечень копий].
            [Дата] [Подпись] [Расшифровка]
        """.trimIndent(),
    ),
    WorkPressureTemplate(
        id = "election-commission",
        title = "Обращение в избирательную комиссию",
        recipient = "Избирательная комиссия субъекта РФ или вышестоящая комиссия",
        whenToUse = "Чтобы сообщить о нарушении добровольности и тайны голосования и попросить комиссию принять меры в пределах её компетенции.",
        beforeSending = listOf(
            "Назовите конкретные выборы, регион и организацию.",
            "При срочной ситуации не ограничивайтесь одним каналом: сроки по избирательным спорам могут исчисляться днями.",
            "Попросите зарегистрировать обращение и сообщить, куда оно направлено по компетенции.",
        ),
        body = """
            В [наименование избирательной комиссии]
            от [ФИО, адрес для ответа, телефон или электронная почта]

            ОБРАЩЕНИЕ О ПРИНУЖДЕНИИ ИЗБИРАТЕЛЯ

            В связи с проведением [наименование выборов] сообщаю о следующих обстоятельствах. [Дата, время] в организации [название, адрес] [ФИО и должность] потребовал(а) от [меня / работников] [описание требования]. В качестве подтверждения требовалось [фото заполненного бюллетеня / отчёт о явке / иное]. За отказ было обещано или допускалось [точные слова и действия].

            Подтверждающие материалы и свидетели: [перечень]. Ранее о ситуации сообщалось: [кому, когда, результат].

            Полагаю, что изложенное может нарушать принципы свободного, добровольного участия и тайного голосования, установленные п. 3 ст. 3 и ст. 7 Федерального закона № 67-ФЗ.

            Прошу зарегистрировать обращение, незамедлительно проверить изложенные обстоятельства, принять меры в пределах компетенции, при необходимости направить материалы в правоохранительный орган и сообщить мне регистрационный номер и принятое решение.

            Приложения: [перечень копий].
            [Дата] [Подпись] [Расшифровка]
        """.trimIndent(),
    ),
    WorkPressureTemplate(
        id = "labor-inspectorate",
        title = "Жалоба в государственную инспекцию труда",
        recipient = "Государственная инспекция труда по месту работодателя",
        whenToUse = "Когда за отказ участвовать в принуждении применили дисциплинарное взыскание, ухудшили условия труда, лишили выплат или угрожают увольнением.",
        beforeSending = listOf(
            "Приложите трудовой договор, приказы, уведомления и расчётные листки, если они относятся к делу.",
            "Разделите избирательное принуждение и конкретные трудовые последствия.",
            "Если уже уволили, одновременно срочно уточните порядок обращения в суд.",
        ),
        body = """
            В Государственную инспекцию труда в [регион]
            от [ФИО, адрес для ответа, телефон или электронная почта]

            ЖАЛОБА НА НАРУШЕНИЕ ТРУДОВЫХ ПРАВ

            Я работаю в [организация, должность] с [дата]. [Дата] [ФИО и должность руководителя] потребовал(а) [описание требования, связанного с участием в выборах или голосованием]. Я [описать реакцию без лишних оценок].

            После этого работодатель совершил или пригрозил совершить следующие действия: [взыскание, изменение графика, лишение выплаты, давление с целью написать заявление, увольнение, иное]. Документы работодателя: [номер и дата приказа / уведомления]. Как эти действия связаны с отказом выполнить требование: [последовательность событий, слова, свидетели].

            Прошу проверить соблюдение запрета дискриминации в сфере труда и законность указанных действий работодателя, выдать предписание об устранении выявленных нарушений при наличии оснований, привлечь виновных лиц к ответственности в пределах компетенции и сообщить мне результаты рассмотрения.

            Прошу учитывать риск ответных действий работодателя и не раскрывать сведения сверх необходимого для проверки без предусмотренных законом оснований.

            Приложения: [перечень копий].
            [Дата] [Подпись] [Расшифровка]
        """.trimIndent(),
    ),
    WorkPressureTemplate(
        id = "dismissal-court-outline",
        title = "Каркас иска при увольнении",
        recipient = "Районный или городской суд — подсудность и требования лучше уточнить у юриста",
        whenToUse = "Если увольнение уже оформлено. Это каркас для срочной подготовки с юристом, а не готовый универсальный иск.",
        beforeSending = listOf(
            "Проверьте месячный срок по ст. 392 ТК РФ и дату, с которой он исчисляется в вашей ситуации.",
            "Уточните подсудность, цену иска, перечень участников и приложений.",
            "Сформулируйте отдельные требования о восстановлении, выплатах и компенсации только при наличии оснований.",
        ),
        body = """
            В [наименование суда]
            Истец: [ФИО, адрес, контакты]
            Ответчик: [работодатель, адрес, идентификаторы]

            ИСКОВОЕ ЗАЯВЛЕНИЕ
            о восстановлении на работе и связанных требованиях

            Я работал(а) у ответчика с [дата] в должности [должность]. Приказом № [номер] от [дата] трудовой договор прекращён по основанию [точная формулировка]. Копию приказа / трудовую книжку я получил(а) [дата].

            Увольнению предшествовали события: [хронология требования голосовать определённым образом, отказа и действий работодателя]. Незаконность увольнения подтверждается следующим: [нарушение основания или процедуры, противоречия в документах, связь с дискриминационным мотивом]. Доказательства: [перечень].

            С учётом применимых норм ТК РФ прошу: [признать увольнение незаконным]; [восстановить в должности]; [взыскать средний заработок за время вынужденного прогула]; [иные требования после проверки юристом].

            Приложения: [приказ, трудовой договор, доказательства, расчёт, подтверждение направления копии ответчику, иное].
            [Дата] [Подпись]
        """.trimIndent(),
    ),
)

internal val workPressureLaws = listOf(
    WorkPressureLaw(
        "Свободное и добровольное участие",
        "Пункт 3 статьи 3 Федерального закона от 12.06.2002 № 67-ФЗ",
        "Никто не вправе воздействовать на гражданина, чтобы принудить его участвовать или не участвовать в выборах либо воспрепятствовать свободному волеизъявлению.",
        "https://www.consultant.ru/document/cons_doc_LAW_37119/bd263f61eaf68bb21a5e2f07a9343f9df3eed719/",
    ),
    WorkPressureLaw(
        "Тайна голосования",
        "Статья 7 Федерального закона от 12.06.2002 № 67-ФЗ",
        "Голосование должно исключать возможность какого-либо контроля за волеизъявлением гражданина.",
        "https://www.consultant.ru/document/cons_doc_LAW_37119/f2e0d679f738cb4e569627d81bb5a0104caf60d9/",
    ),
    WorkPressureLaw(
        "Обжалование нарушений избирательных прав",
        "Статьи 75 и 78 Федерального закона от 12.06.2002 № 67-ФЗ",
        "Решения и действия, нарушающие избирательные права, могут обжаловаться в комиссию или суд. Для избирательных споров установлены специальные, часто очень короткие сроки.",
        "https://www.consultant.ru/document/cons_doc_LAW_37119/60dc1c108c93c107e537f5500fdb79ce7bf0b752/",
    ),
    WorkPressureLaw(
        "Воспрепятствование избирательным правам",
        "Статья 141 Уголовного кодекса РФ",
        "Норма предусматривает ответственность за воспрепятствование свободному осуществлению избирательных прав и нарушение тайны голосования; среди квалифицирующих обстоятельств названы принуждение, угроза и использование служебного положения.",
        "https://www.consultant.ru/document/cons_doc_LAW_10699/7f9d47f037e2f1e9d101b64df440d4e14f6bdbc8/",
    ),
    WorkPressureLaw(
        "Запрет дискриминации в сфере труда",
        "Статья 3 Трудового кодекса РФ",
        "Трудовые права не могут ограничиваться по политическим убеждениям и другим обстоятельствам, не связанным с деловыми качествами работника.",
        "https://www.consultant.ru/document/cons_doc_LAW_34683/0d18caafb87d28222d0cb617c21634cc407ee0f5/",
    ),
    WorkPressureLaw(
        "Основания увольнения работодателем",
        "Статья 81 Трудового кодекса РФ",
        "Работодатель может прекратить договор по своей инициативе только по предусмотренным законом основаниям и с соблюдением применимой процедуры.",
        "https://www.consultant.ru/document/cons_doc_LAW_34683/6a7ba42d8fda3a1ba186a9eb5c806921998ae7d1/",
    ),
    WorkPressureLaw(
        "Способы защиты трудовых прав",
        "Статья 352 Трудового кодекса РФ",
        "К способам защиты относятся государственный контроль, профсоюзная защита, самозащита и судебная защита.",
        "https://www.consultant.ru/document/cons_doc_LAW_34683/4b07ec615bc5f7ca99a620c9e9767f845ea9a463/",
    ),
    WorkPressureLaw(
        "Срок обращения в суд",
        "Статья 392 Трудового кодекса РФ",
        "По спору об увольнении срок обычно составляет один месяц; для других трудовых требований сроки отличаются. Уточняйте начало и возможность восстановления срока применительно к вашей ситуации.",
        "https://www.consultant.ru/document/cons_doc_LAW_34683/eff4cd3e27ee6ffdc716306e3cab5c403c3c2dcb/",
    ),
    WorkPressureLaw(
        "Требования к письменному обращению",
        "Статьи 7 и 12 Федерального закона от 02.05.2006 № 59-ФЗ",
        "Общее обращение должно содержать адресата, данные заявителя для ответа, суть, подпись и дату. Для выборов и уголовно-процессуальных сообщений могут действовать специальные правила и сроки.",
        "https://www.consultant.ru/document/cons_doc_LAW_59999/c75556cf6fc05793e3c6315a7101fb59e6af9b02/",
    ),
)

internal val workPressureContacts = listOf(
    WorkPressureContact(
        "Следственный комитет России",
        "Сообщение о возможном преступлении, в том числе о принуждении с использованием должностного положения. Выберите территориальное управление по месту события.",
        "https://sledcom.ru/reception",
    ),
    WorkPressureContact(
        "Генеральная прокуратура России",
        "Обращение о нарушении закона или бездействии органа; портал позволяет выбрать компетентную прокуратуру.",
        "https://epp.genproc.gov.ru/web/gprf/internet-reception",
    ),
    WorkPressureContact(
        "МВД России",
        "Сообщение в полицию, особенно если звучат угрозы или ситуация требует немедленной регистрации. При непосредственной опасности звоните 112.",
        "https://мвд.рф/request_main",
    ),
    WorkPressureContact(
        "Онлайнинспекция.рф",
        "Официальный сервис Роструда для обращений о трудовых нарушениях и незаконном увольнении.",
        "https://онлайнинспекция.рф/problems",
    ),
    WorkPressureContact(
        "Роструд",
        "Информация о порядке направления обращения и контактах территориальных инспекций труда.",
        "https://rostrud.gov.ru/room/obrashcheniya-grazhdan/",
    ),
    WorkPressureContact(
        "Центральная избирательная комиссия России",
        "Интернет-приёмная и переход к региональным комиссиям. Для конкретных выборов предпочтительна компетентная комиссия субъекта или вышестоящая комиссия.",
        "http://www.cikrf.ru/reception/",
    ),
)
