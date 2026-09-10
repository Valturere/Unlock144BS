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
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` и `assembleRelease` проходят.

## Ограничения проверки

- Сценарий «Brawl Stars не установлен» проверен на уровне явной проверки PackageManager
  и UX-ветки, но официальный пакет намеренно не удалялся с телефона.
- Shizuku-сценарии неприменимы: выбранный Direct-метод работает без Shizuku.
- Приложение не измеряет фактический FPS. Оно сообщает только об успешной отправке
  override; визуальный эффект в бою подтвердил пользователь.
- Безопасный reset для receiver не найден: 0 и отрицательные значения игнорируются.
