# Stage 1: PowerKeeper broadcast research

Device tested: Xiaomi 14T (`2406APNFAG`), Android 16 / API 36.

PowerKeeper package: `com.miui.powerkeeper`, version 4.2.00 (40200), system UID 1000.

## Receiver

`com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE` is handled by the anonymous inner
`BroadcastReceiver` `DisplayFrameSetting$1`, owned by
`com.miui.powerkeeper.statemachine.DisplayFrameSetting`.

It is dynamically registered with these relevant instructions in the device APK:

```text
filter.addAction("com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE")
context.registerReceiver(receiver, filter, 2)
```

On current Android APIs, flag `2` is `Context.RECEIVER_EXPORTED`. The overload has
no broadcast permission argument, so PowerKeeper does not require a sender
permission for this receiver.

The handler reads:

```text
override_pkg_name: String
override_freshrate: int
```

It validates that the package name is non-empty and the requested rate is
positive, maps the rate to a supported value, stores it in `mPrivGames`, and
re-evaluates the current foreground app when the target is foreground.

## Runtime evidence

`dumpsys activity broadcasts` showed the action registered by a receiver in the
PowerKeeper process. Sending the known ADB command produced a broadcast-history
entry delivered to exactly that runtime `BroadcastFilter`.

An explicit component cannot target this anonymous dynamic receiver. An implicit
broadcast is therefore required. `Intent.setPackage("com.miui.powerkeeper")` is
optional and will be tested separately; it may narrow delivery without changing
the receiver API.

The next stage verifies delivery from a normal third-party application UID. A
successful return from `sendBroadcast()` alone is not treated as proof.
