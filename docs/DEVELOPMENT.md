# Разработка «Я на выборах»

## Важный статус проекта

Часть кода, тестов, документации и демонстрационного/методического контента создавалась с помощью генеративных AI-инструментов — в том числе в режиме, который обычно называют «вайбкодингом». Это нужно учитывать при ревью: правдоподобный код или текст не считается проверенным только потому, что он компилируется.

Проект протестирован не полностью. Сейчас есть unit-тесты, Room integration/migration tests и сборка APK, однако нет полного end-to-end покрытия, широкой матрицы реальных устройств, завершённого accessibility-тестирования, полевого теста на участке, независимого security/privacy-аудита и полной юридической экспертизы контента. Любое изменение, особенно связанное с удалением данных, криптографией, медиа, экспортом или процедурными советами, требует ручной проверки специалистом соответствующего профиля.

## Требования и локальная сборка

- JDK 17;
- Android SDK Platform 36;
- Android Studio с поддержкой текущих AGP/Kotlin или Gradle Wrapper из репозитория.

`local.properties` нужен только для локального пути `sdk.dir`, игнорируется Git и не должен содержать ключи подписи или другие секреты.

```bash
./gradlew testAndroidHostTest testDebugUnitTest
./gradlew :shared:compileKotlinWasmJs wasmJsNodeTest :feature:observer:wasmJsBrowserTest :feature:workpressure:wasmJsBrowserTest
./gradlew :webApp:wasmJsBrowserTest :webApp:wasmJsBrowserDistribution
./gradlew :app:assembleDebug
```

Debug APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

Device-тесты требуют запущенный эмулятор или устройство:

```bash
./gradlew :core:database:connectedDebugAndroidTest \
  :core:content:connectedAndroidDeviceTest \
  :feature:observer:connectedAndroidDeviceTest \
  :app:connectedDebugAndroidTest
```

Перед слиянием изменения рекомендуется как минимум выполнить `testAndroidHostTest testDebugUnitTest`, `:shared:compileKotlinWasmJs wasmJsNodeTest :feature:observer:wasmJsBrowserTest` и `:app:assembleDebug`. Для изменений Room также обязательны migration/integration tests, а для пользовательского сценария — ручная проверка на устройстве.

## Архитектура

Общие модели, бизнес-логика, импорт контента, Compose UI, навигация и ViewModel
размещены в KMP-модулях с `commonMain` / `commonTest`. `shared` содержит корневой
UI и загрузку приложения. `app` — Android-host, `core:database` и `core:files` —
Android-адаптеры хранения. Точная схема и контракт подключения будущего веб-host
описаны в [ARCHITECTURE.md](ARCHITECTURE.md).

`testAndroidHostTest` запускает общие и Android host тесты KMP-модулей;
`testDebugUnitTest` — оставшиеся Android-модули. `wasmJsNodeTest` запускает тесты ядра в Node.js.
`:feature:observer:wasmJsBrowserTest` проверяет CSV, разбор шаблонов и UI-helper
функции в Chrome Headless: графическая библиотека Skiko требует браузер.
`:feature:workpressure:wasmJsBrowserTest` проверяет полноту офлайн-шаблонов,
правовых справок и ссылок на официальные приёмные.
`:webApp:wasmJsBrowserTest` проверяет браузерное хранилище сессии, чек-листа и
счётчиков, а `:webApp:wasmJsBrowserDistribution` собирает статический сайт в
`webApp/build/dist/wasmJs/productionExecutable`.
Для браузерных тестов нужен установленный Google Chrome (при нестандартном пути
задайте `CHROME_BIN`); Gradle загружает Node.js и Karma самостоятельно. `:shared:compileKotlinWasmJs` компилирует весь общий
UI для будущего браузерного клиента, не создавая сайт. Device-тесты KMP находятся
в `androidDeviceTest`, JVM-тесты с файловым I/O — в `androidHostTest`.

## Правила безопасных изменений

- Не добавляйте сеть, аналитику, crash reporting или внешнюю отправку неявно: это меняет модель приватности и требует отдельного решения.
- Не храните keystore, пароли, токены и персональные данные в Git, `local.properties`, fixtures или логах.
- Не меняйте существующую Room-схему без новой версии БД, экспортированной schema и migration test.
- Не считайте автоматический privacy scan гарантией: сейчас он ограничен и не заменяет проверку кадра человеком.
- Не формулируйте юридическую квалификацию события как установленный факт. В UI используется категория «Предполагаемое нарушение».
- Проверяйте AI-сгенерированные изменения построчно и добавляйте тест, воспроизводящий требование или исправленную ошибку.

## Версии и GitHub Releases

Workflow `.github/workflows/android-release.yml` запускается при отправке тега вида `vMAJOR.MINOR.PATCH`; тег с суффиксом, например `v1.2.0-rc.1`, публикуется как prerelease. `versionName` берётся из тега, а положительный `versionCode` — из количества коммитов до тега. Workflow запускает unit-тесты, собирает и проверяет подписанный APK, создаёт SHA-256 и публикует оба файла в GitHub Release.

Для подписи один раз создайте отдельный release-keystore и храните его резервную копию вне репозитория:

```bash
keytool -genkeypair -v \
  -keystore yanavyborah-release.jks \
  -alias yanavyborah \
  -keyalg RSA -keysize 4096 -validity 10000
```

В настройках GitHub-репозитория (`Settings → Secrets and variables → Actions`) нужны четыре repository secret:

- `ANDROID_KEYSTORE_BASE64` — keystore целиком в Base64;
- `ANDROID_KEYSTORE_PASSWORD` — пароль хранилища;
- `ANDROID_KEY_ALIAS` — alias ключа;
- `ANDROID_KEY_PASSWORD` — пароль ключа.

В Linux получить однострочное значение первого секрета можно так:

```bash
base64 -w 0 yanavyborah-release.jks
```

После настройки секретов выпуск выглядит так:

```bash
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin v0.2.0
```

Один и тот же signing key необходимо сохранять для всех будущих версий: APK, подписанный другим ключом, Android не сможет установить как обновление поверх прежнего приложения.
