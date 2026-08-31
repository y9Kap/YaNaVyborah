# Архитектура каркаса

## Границы данных

`ContentDatabase` содержит только импортируемые определения Election Pack. При успешном обновлении её содержимое заменяется одной Room-транзакцией. До транзакции импортёр проверяет manifest, поддерживаемую версию схемы, безопасные относительные пути, наличие обязательных разделов, SHA-256 и внутренние ссылки.

`UserDatabase` содержит сессии наблюдения, состояния чек-листа, журнал, жалобы, счётчики и отдельные timestamp-отметки, результаты сверок, snapshots протоколов, метаданные медиа и privacy reports. Обновление Election Pack не открывает и не очищает эту БД.

Выбранная `ObservationSession` хранится в Preferences DataStore. Остальные данные UI получает как `Flow` через repository-интерфейсы из `core:common`; Compose не обращается к DAO.

## Поток зависимостей

```text
feature:* -> core:common -> core:model
feature:* -> core:ui / core:navigation

app -> feature:*
app -> Room repository implementations / importer / private files

core:database -> core:common + core:model
core:content  -> core:common + core:model + core:crypto
core:files    -> core:common + core:model + core:database + core:crypto
```

Ручной `AppContainer` является composition root. Это dependency injection без code generation: feature Observer получает только набор интерфейсов `ObserverDependencies`.

## Однонаправленный UI

`ObserverViewModel` принимает действия UI, вызывает repository/domain слой и публикует единый `ObserverUiState`. Сохранённые Room/DataStore изменения возвращаются в состояние через `Flow`. Одноразовые ошибки показываются через snackbar.

## Медиа и криптография

Пользователь явно выбирает фото или видео системным Photo Picker. Файл временно читается в app cache, получает SHA-256 и privacy report, затем шифруется AES-GCM ключом Android Keystore в `files/private_media`. В БД сохраняется только метаинформация и приватный путь. В общую галерею приложение файл не записывает. В сценарии предполагаемого нарушения пользователь сначала снимает материал внешней системной камерой и дожидается сохранения, затем выбирает его для защищённого импорта и только после этого заполняет отчёт.

`OriginalMedia` и `ExportCopy` разведены на уровне модели. Создание редактируемых экспортных копий (удаление EXIF, маскирование областей, resize/watermark) оставлено следующей итерации; оригинал текущий код не изменяет.

## Транспорт

В `core:model` есть `OutboxItem`, а в `core:common` — абстракция `Transport`. Реализаций и сетевых разрешений в каркасе нет. Будущий транспорт не должен попадать в `feature:observer`. CSV-выгрузка журнала является отдельным локальным экспортом через системный выбор файла и ничего не отправляет автоматически.
