# TV Home

A small PS5-style home screen for the bedroom Haier Google TV, inspired by
[tv-vibehack](https://github.com/Gathin23/tv-vibehack). About 40 MB of RAM and no CPU when idle.

- Apps row: add, move and remove apps on the TV (the "+" tile, or hold OK on a tile); the first-run
  defaults are in `HomeApps.DEFAULTS`. PS5 tile (switches to HDMI 1): `PS5_INPUT_ID` in `MainActivity.java`.
- Wallpaper: replace `res/drawable-nodpi/wallpaper.jpg` (1920x1080) and its blurred copy
  `wallpaper_blur.jpg` (used for the frosted panel over home) together.
- Fonts: Inter, subset to Latin and punctuation to keep the APK and RAM small.
- Build and install: `./build.sh install` (needs `.toolchain/`: JDK 17 and Android SDK platform 34 / build-tools 34 / platform-tools).
  The TV's address defaults to 192.168.0.5; set `TV_HOST` to override.

Google TV's launcher (`com.google.android.apps.tv.launcherx`) and the setup app
(`com.google.android.tungsten.setupwraith`) are both disabled, so Home resolves straight to TV Home.
With only the launcher disabled, the setup app's recovery screen takes Home and restarts itself in a
tight loop. `HomeGuardService` (an accessibility service) still replaces either screen if one is ever
re-enabled, and turns the remote's settings key (sent as `KEYCODE_NOTIFICATION` on this Haier remote)
into the quick settings panel.

Undo, in this order (re-enable Google's apps first, or the TV is left without a home screen):
`adb shell pm enable com.google.android.apps.tv.launcherx`,
`adb shell pm enable com.google.android.tungsten.setupwraith`, then
`adb shell settings delete secure enabled_accessibility_services`, then
`adb uninstall com.bedroomtv.home`.
