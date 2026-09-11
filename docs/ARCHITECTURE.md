# Kotlin Multiplatform архитектура

## Границы данных

`ContentDatabase` содержит только импортируемые определения Election Pack. При успешном обновлении её содержимое заменяется одной Room-транзакцией. До транзакции импортёр проверяет manifest, поддерживаемую версию схемы, безопасные относительные пути, наличие обязательных разделов, SHA-256 и внутренние ссылки.

`UserDatabase` содержит сессии наблюдения, состояния чек-листа, журнал, жалобы, счётчики и отдельные timestamp-отметки, результаты сверок, snapshots протоколов, метаданные медиа и privacy reports. Обновление Election Pack не открывает и не очищает эту БД.

Выбранная `ObservationSession` хранится в Preferences DataStore. Остальные данные UI получает как `Flow` через repository-интерфейсы из `core:common`; Compose не обращается к DAO.

## Модули и платформы

`shared`, `core:model`, `core:common`, `core:content`, `core:crypto`, `core:search`,
`core:navigation`, `core:ui` и все `feature:*` — KMP-библиотеки с целями Android и
`wasmJs`. Общий код и тесты находятся в `commonMain` / `commonTest`.
Compose Multiplatform разделяет существующие экраны, тему, навигацию и ViewModel.
`webApp` — браузерный Kotlin/Wasm-host со статическим HTML, браузерными
адаптерами и локальным хранилищем пользовательских данных.

```text
app (Android Activity, Application, сборка APK)
  -> shared (SharedAppContainer, bootstrap, YaNaVyborahRoot)
       -> feature:* -> core:common -> core:model
       -> core:content -> core:crypto (SHA-256)
       -> core:ui / core:navigation
  -> core:database (Android Room + DataStore)
  -> core:files (Android приватные файлы + обработка изображений)
  -> core:crypto/androidMain (Android Keystore + потоковый AES-GCM)
  -> core:ui/androidMain (AndroidPlatformUi)
```

Android использует официальный `com.android.kotlin.multiplatform.library` в KMP
библиотеках и `com.android.application` в `app`. `core:database` и `core:files`
сохраняют `com.android.library`: это Android-адаптеры, их API не импортируется
общими экранами. Room-схемы, имена БД, настройки DataStore, application ID,
ключ Keystore и формат шифрованных файлов сохранены.

`AppContainer` создаёт Android-репозитории и передаёт их интерфейсы через
`ObserverDependencies` в `SharedAppContainer`. Последний отвечает за импорт
пакета и состояние загрузки; Android Application запускает его bootstrap.
Закрывает платформенные базы их владелец — Android `AppContainer`.

`PlatformUi` отделяет выбор медиа, создание и запись документов, камеру,
телефон, декодирование изображений, чтение комплектных файлов, системный Back,
удержание экрана включённым, видимость клавиатуры и защиту форм от автозаполнения. Android Activity предоставляет
`AndroidPlatformUi` через `LocalPlatformUi`. URI/дескрипторы передаются как
непрозрачные строки, байты — как `ByteArray`. CSV и проверка SHA-256 оригиналов
остаются в общем коде. Системный диалог позволяет отменить экспорт.

Комплектные материалы и гайд имеют один источник:
`shared/src/commonMain/resources`. Android подключает каталог как assets.
`AssetElectionPackSource` находится в `core:content/androidMain`; проверка и
разбор пакета в `commonMain` принимают любой `ElectionPackSource`.

## Веб-приложение

`webApp` подключает `:shared` и предоставляет единый `BrowserStore`, реализующий
repository-интерфейсы из `core:common`. Сессии, чек-листы, журнал, жалобы,
счётчики, сверки и протокол сохраняются в `localStorage`; Room и DataStore в
браузере не используются. Пароль удаления в хранилище остаётся только в виде
SHA-256 digest, привязанного к идентификатору сессии.

`BrowserPlatformUi` реализует выбор фото и видео, скачивание CSV и комплектных
документов, ссылки на телефон и чтение общего Election Pack. Импортированное
медиа намеренно живёт только до перезагрузки страницы: браузерный host не выдаёт
это временное хранилище за эквивалент Android Keystore. Остальные пользовательские
данные переживают перезагрузку страницы. Обе платформы используют один набор
экранов, бизнес-правил и методического контента; синхронизации между устройствами
нет.

Основа конфигурации: [Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin)
и [Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html).

## Однонаправленный UI

`ObserverViewModel` принимает действия UI, вызывает repository/domain слой и публикует единый `ObserverUiState`. Сохранённые Room/DataStore изменения возвращаются в состояние через `Flow`. Одноразовые ошибки показываются через snackbar.

## Медиа и криптография

Пользователь явно выбирает фото или видео системным Photo Picker. Файл временно читается в app cache, получает SHA-256 и privacy report, затем шифруется AES-GCM ключом Android Keystore в `files/private_media`. В БД сохраняется только метаинформация и приватный путь. В общую галерею приложение файл не записывает. В сценарии предполагаемого нарушения пользователь сначала снимает материал внешней системной камерой и дожидается сохранения, затем выбирает его для защищённого импорта и только после этого заполняет отчёт.

`OriginalMedia` и `ExportCopy` разведены на уровне модели. Создание редактируемых экспортных копий (удаление EXIF, маскирование областей, resize/watermark) оставлено следующей итерации; оригинал текущий код не изменяет.

## Транспорт

В `core:model` есть `OutboxItem`, а в `core:common` — абстракция `Transport`. Реализаций и сетевых разрешений в каркасе нет. Будущий транспорт не должен попадать в `feature:observer`. CSV-выгрузка журнала является отдельным локальным экспортом через системный выбор файла и ничего не отправляет автоматически.
