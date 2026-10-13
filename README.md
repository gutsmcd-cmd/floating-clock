# Floating Clock / 浮かぶ時計

A small clock that floats over any app. Free forever · no ads · no login · **no internet permission**.

どのアプリの上にも、小さな時計。ずっと無料・広告なし・ログインなし・通信なし。

## Features
- Small draggable clock over every app (HH:mm, optional seconds, date and battery %)
- Size presets Tiny (default) / Small / Medium / Large, plus **pinch to resize** while unlocked, text colour (white, black, green, yellow, custom hex), optional see-through pill, opacity, 12/24 h
- **Lock position**: taps pass straight through to the app underneath (while locked the window is capped at 80% opacity — Android 12+ drops pass-through touches over more opaque overlays)
- Snap to the nearer edge after dragging; position and settings are remembered
- Show / hide from the Quick Settings tile, the notification, or the app
- Only ticks once a minute (or once a second if seconds are on) and stops while the screen is off
- In-app English / 日本語 switch

## Install (sideload)
Download `floating-clock.apk` from the [Free Apps site](https://gutsmcd-cmd.github.io/free-apps/) and open it.
Allow "Display over other apps". If that switch is greyed out ("restricted setting", Android 13+ for apps not from the Play Store):
App info → ⋮ → **Allow restricted settings** → confirm, then come back and allow again. The app shows these steps with an Open App info button.

## Build
Kotlin, no AndroidX, minSdk 26, target 37.
```
./gradlew lint test assembleRelease
```
Release signing reads a git-ignored `keystore.properties`.

## Privacy
Permissions: draw over other apps, foreground service (special use: the visible clock), notifications. No network, no analytics, no data collected. Settings stay on the device.

MIT License.
