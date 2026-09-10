# Stage 2/3: direct-broadcast proof of concept

Tested on the connected Xiaomi 14T on 2026-09-10.

## Result

Direct mode works. Shizuku is not needed.

The installed debug app has package `dev.leonid.unlock144bs` and a regular app UID
(`10281` in this installation). It declares no Android permissions.

After the single PoC button called `Context.sendBroadcast()`:

1. The app log recorded the send without an exception.
2. `dumpsys activity broadcasts history` identified the caller as
   `dev.leonid.unlock144bs`, UID 10281 — not ADB shell UID 2000.
3. The same history entry recorded delivery to the exported runtime receiver in
   `com.miui.powerkeeper`, UID 1000.
4. PowerKeeper's own `dumpsys activity service` log recorded
   `MSG_OVERRIDE_GAME_FRESHRATE break`, proving that the receiver handler processed
   the message while Brawl Stars was not foreground.
5. On subsequently launching Brawl Stars, PowerKeeper recorded
   `setScreenEffect pkg=com.supercell.brawlstars fps=144 cookie=254`.

The package-scoped form was also tested. Broadcast history contained
`pkg=com.miui.powerkeeper`, caller UID 10281, and delivery to the same receiver.

Sending the override twice in quick succession produced two handler entries, no
exception, and a subsequent `setScreenEffect ... fps=144` for Brawl Stars. The
handler uses `ArrayMap.put`, so the same package/rate update is idempotent.

## Remaining manual check

The system-level requested refresh rate is confirmed automatically. The PoC does
not measure rendered game FPS, so the user must confirm that an actual battle no
longer stays at 60 FPS after applying the override.
