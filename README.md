# Unlock144BS

Небольшая native Android-утилита для Xiaomi/HyperOS, которая отправляет PowerKeeper
проверенный refresh-rate override для официального Brawl Stars.

## Как это работает

- пакет игры: `com.supercell.brawlstars`;
- получатель: `com.miui.powerkeeper`;
- action: `com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE`;
- по умолчанию отправляется `override_freshrate=144`;
- Auto Fix обнаруживает Brawl Stars через `UsageStatsManager`, отправляет override при
  входе в игру и повторяет его раз в 45 секунд, пока игра остаётся на экране.

Joyose, Shizuku и root не требуются. Приложение не изменяет Brawl Stars, PowerKeeper
или системные APK.

## Первоначальная настройка

1. Установить APK и открыть Unlock144BS.
2. Предоставить Usage Access.
3. На открывшемся экране батареи выбрать **Нет ограничений**. На HyperOS это нужно,
   чтобы система не замораживала watcher во время игры.
4. Включить Auto Fix. Приложение не запрашивает разрешение на уведомления: на новой
   установке служебная карточка скрыта, а watcher продолжает работать как foreground
   service. Управлять видимостью можно кнопкой «Открыть настройки уведомлений».

После этого Brawl Stars можно запускать обычным способом. При включённой настройке
«Запуск после перезагрузки» watcher возобновляется после первой разблокировки телефона.

Выключение Auto Fix прекращает новые broadcast-сообщения. Безопасного значения для
удаления уже сохранённого PowerKeeper override в исследованной версии 4.2.00 нет:
нулевые и отрицательные значения receiver игнорирует.

## Сборка

Проект рассчитан на Android Studio и JDK 17. Для первой release-сборки один раз
создайте постоянный локальный ключ:

```powershell
.\scripts\create-release-keystore.ps1
```

Скрипт создаёт игнорируемые Git файлы `signing/unlock144bs-release.jks` и
`keystore.properties`. Их нужно сохранить вместе в защищённой резервной копии:
без того же ключа Android не позволит устанавливать будущие обновления поверх release.

Сборка и проверки:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Минимальная версия — Android 8.0 (API 26), target/compile SDK — 36. Debug APK появляется
в `app/build/outputs/apk/debug/app-debug.apk`; подписанный release — в
`app/build/outputs/apk/release/app-release.apk` при наличии `keystore.properties`.

## Проверка на устройстве

Механизм и Auto Fix проверены на Xiaomi 14T, Android 16, PowerKeeper 4.2.00. Подробные
результаты находятся в [`docs/release-verification.md`](docs/release-verification.md),
а анализ receiver — в [`docs/research-stage-1.md`](docs/research-stage-1.md).
