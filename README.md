# Legion Switch Dock

A Switch-style 10-foot TV Hub & automatic dock companion built in **Kotlin + Jetpack Compose** for the **Lenovo Legion Y700 (2025 / Gen 3)**.

## Features
- **Instant TV Launch on Plug-In:** Background `DisplayListener` foreground service detects when a TV/monitor connects to the Y700's short-edge USB-C 3.2 (DP Alt Mode) port and launches `TvHubActivity` directly onto the external `displayId`.
- **Switch-Style Controller UI:** Horizontal game/app cards with D-Pad & joystick navigation (`A` Launch, `X` Unpin/Pin, `Y` All Apps Drawer, `B` Back), plus live clock, battery, Wi-Fi/Ethernet, controller badge, and TV resolution/Hz.
- **Smart Default Pins + All Apps Manager:** Automatically prioritizes installed streaming/gaming apps (Artemis, Moonlight, Steam Link, GameHub, Anikoto, YouTube, RetroArch, Dolphin, PPSSPP, NetherSX2, etc.) and lets you pin/unpin any app with your controller.
- **Docked Tablet Blackout & Remote:** Automatically dims the tablet to pure black while docked; tap anywhere to reveal volume/mute/TV-hub controls.
- **Clean Unplug:** Unplugging the USB-C cable automatically closes the TV Hub and restores the tablet screen.
- **Optional Shizuku Tweaks:** System backlight `0%` on dock and force-relaunch of already-open apps onto the TV display.

## Building the APK
### Option 1: GitHub Actions (Zero Local Setup)
1. Push this folder to a GitHub repository.
2. Open the **Actions** tab → **Build Legion Switch Dock APK**.
3. Download the `LegionSwitchDock-debug` artifact (`app-debug.apk`).

### Option 2: Android Studio
Open `LegionSwitchDock` in Android Studio and click **Build → Build APK(s)**.

### Option 3: Obtainium (auto-updates on the tablet)
Every push to `main` publishes a GitHub Release with the APK attached and a higher version number.
1. Install [Obtainium](https://github.com/ImranR98/Obtainium) on the Y700.
2. Tap **Add App** and paste `https://github.com/Brendanbam/LegionSwitchDock`.
3. Install from Obtainium — it will notify you and update the app whenever a new release is published.

## First-Time Tablet Setup
1. Install `app-debug.apk` on your Legion Y700.
2. Open **Legion Switch Dock** and grant **Display Over Other Apps** (required on Android 14/15 so the TV Hub can auto-launch when you plug in the cable).
3. *(Recommended)* In ZUI / Legion settings, set external display mode to **Extended / PC Mode** so the TV outputs native 16:9 without black bars.
4. *(Optional)* Use **Preview Hub** in the app to pin/unpin your favorite games before docking.
