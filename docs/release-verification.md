# Release verification — Xiaomi 14T

Дата проверки: 2026-09-10. Устройство: Xiaomi 14T (`2406APNFAG`), Android 16
(API 36), PowerKeeper 4.2.00 (40200).

## Подтверждено

- Direct broadcast из обычного application UID доставляется exported dynamic receiver
  PowerKeeper без root, Shizuku и receiver permission.
- PowerKeeper записывает `setScreenEffect pkg=com.supercell.brawlstars fps=144`.
- Пользователь подтвердил в реальном бою рост FPS с 60 примерно до 144.
- Auto Fix обнаруживает вход Brawl Stars в foreground и немедленно отправляет override.
- При режиме «Авто» повтор зарегистрирован через 45.15 секунды; повторные broadcast
  идемпотентны и не вызвали видимых проблем.
- При включённой оптимизации батареи HyperOS заморозил foreground service. После выбора
  «Нет ограничений» heartbeat и повторы продолжили выполняться во время игры.
- Auto Fix OFF остановил service; за контрольные 48 секунд в игре число отправок не
  изменилось.
- После reboot и первой разблокировки `BOOT_COMPLETED` запустил foreground service;
  последующий запуск Brawl Stars вызвал новый override, подтверждённый PowerKeeper.
- При запрещённом `POST_NOTIFICATIONS` foreground service остаётся активным, heartbeat
  обновляется, а карточка Unlock144BS отсутствует в шторке. Начальное применение,
  повтор примерно через 45 секунд и запуск после reboot сохранили работоспособность.
- Приложение не запрашивает разрешение на уведомления. Экран приложения показывает
  текущую видимость и открывает системные настройки уведомлений; Android при этом может
  показывать service в системном списке активных приложений.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` и `assembleRelease` проходят.

## Архитектурное решение для уведомления

Auto Fix остаётся foreground service: это единственный проверенный вариант, который
своевременно замечает обычный запуск Brawl Stars и выдерживает ограничения HyperOS.
Без foreground service нет надёжного системного события о запуске Activity другого
приложения: UsageStats предоставляет историю для опроса, периодический WorkManager
имеет минимальный интервал 15 минут, а exact alarm предназначен для точных
пользовательских расписаний, а не постоянного опроса.

На Android 13+ foreground service можно запустить без разрешения
`POST_NOTIFICATIONS`. При запрете его уведомление не показывается в шторке, хотя
service остаётся видимым в системном Task Manager.

Официальная документация Android:

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Background work restrictions](https://developer.android.com/develop/background-work/background-tasks/bg-work-restrictions)

## Release-подпись

- Алгоритм ключа: RSA 4096 bit.
- SHA-256 сертификата:
  `D9:DF:51:EC:2C:7C:F5:8D:58:D5:CC:3E:D9:41:65:CE:B3:BD:E8:A9:9F:50:4B:ED:73:3C:B3:2E:FB:7C:AC:9C`.
- `apksigner verify --verbose --print-certs` подтвердил APK Signature Scheme v2 и
  одного signer.
- Файл ключа и пароли не входят в Git. Для обновлений обязательна защищённая резервная
  копия `signing/unlock144bs-release.jks` вместе с `keystore.properties`.

## Чистая проверка подписанного release

- Debug-пакет удалён, `app-release.apk` установлен с нуля постоянной release-подписью.
- На fresh install `POST_NOTIFICATIONS` имел состояние `granted=false`; приложение не
  показало системный запрос разрешения.
- Onboarding пройден через реальные экраны HyperOS: Usage Access и режим батареи
  «Нет ограничений». После возврата Auto Fix включился, foreground service стал
  активным, а служебная карточка осталась скрытой.
- Обычный launcher-запуск Brawl Stars вызвал initial override в 16:43:28 и periodic
  override в 16:44:13 — интервал 45,139 секунды. Диагностика зарегистрировала выход
  игры из foreground без ошибок.
- После перезагрузки HyperOS доставил `BOOT_COMPLETED` с задержкой; в 16:48:50 watcher
  автоматически возобновился. Повторный launcher-запуск игры в 16:50:26 немедленно
  вызвал новый override 144.
- После перезагрузки подтверждены одновременно: активный foreground service,
  запрещённый `POST_NOTIFICATIONS` и отсутствие карточки Unlock144BS в шторке.

## Ограничения проверки

- Сценарий «Brawl Stars не установлен» проверен на уровне явной проверки PackageManager
  и UX-ветки, но официальный пакет намеренно не удалялся с телефона.
- Shizuku-сценарии неприменимы: выбранный Direct-метод работает без Shizuku.
- Приложение не измеряет фактический FPS. Оно сообщает только об успешной отправке
  override; визуальный эффект в бою подтвердил пользователь.
- Безопасный reset для receiver не найден: 0 и отрицательные значения игнорируются.
