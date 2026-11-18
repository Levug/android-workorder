# Сборка «Наряда»

Для сборки нужны JDK 17 и Android SDK 34. Откройте проект в Android Studio или выполните команды из корня репозитория.

```powershell
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :shared:test
```

APK телефона появится в `app/build/outputs/apk/debug/`, APK часов — в `wear/build/outputs/apk/debug/`. Отладочные сборки используют пакет `ru.workorder.debug`.

## Инструментальные тесты

```powershell
.\gradlew.bat :app:connectedQaAndroidTest
```

Тесты используют отдельный пакет `ru.workorder.qa` с отключённой синхронизацией часов.

## Выпуск

Для подписанной сборки задайте `storeFile`, `storePassword`, `keyAlias` и `keyPassword` в локальном файле `keystore.properties`.

```powershell
.\gradlew.bat :app:assembleRelease :wear:assembleRelease
```

Для обновления установленного приложения Android требует тот же пакет и сертификат подписи. Телефон и часы для синхронизации также должны иметь одинаковые имя пакета и подпись.
