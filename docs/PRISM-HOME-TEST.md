# Safe HOME test on PRISM+ Google TV

Target: PRISM+ SW7_PrismPlus (`umeda`), Android 11. **Not device-tested.**
Keep Google TV (`com.google.android.apps.tv.launcherx`) and its HomeActivity enabled.
No root, system changes, debloating or TV connection is part of the build workflow.
Do not run `launcher/build-and-install.sh`, its PowerShell equivalent, or the old
README debloating instructions for this test: those installers disable the stock launcher.

## What the investigation establishes

The original manifest already declares MAIN + HOME + DEFAULT and uses `singleTask`.
Those declarations allow a HOME task but do not control an OEM remote policy.
The original HomeKeyService filters ALL_APPS and Settings, **not HOME**, and its
window-state callback does nothing. InputKeyService only filters TV input. Neither
helper could recover an ordinary Home press explicitly routed to Google TV.
InputKeyService was also declared unexported; both helpers now declare direct boot
awareness for Android 11 firmware that rejects non-encryption-aware services.
They do nothing before user unlock, and do not open the launcher on service bind.
Direct boot awareness does not guarantee that OEM firmware will retain or bind them.

Setting the HOME role or preferred activity affects normal HOME resolution; it
cannot override an explicit component launch by firmware. A higher intent-filter
priority is not a reliable third-party override of a system app. This change
therefore does not inflate priority, change target SDK to evade policy, disable
packages, or repeatedly change the default launcher.

Monet working on this TV shows that an alternative route exists on the reported
firmware. Its source, configuration and runtime trace were not available for this
investigation, so its mechanism and the exact PRISM+ routing cause are **unproven**.
`resolve-activity` alone is not a physical Home-button test; the resumed activity
and the real remote behavior are the evidence that matters.

## What this build changes

- Handle HOME and ALL_APPS when Android delivers them to the user-enabled helper.
  Ordinary apps cannot require firmware to deliver HOME to accessibility.
- Launch MainActivity explicitly with MAIN/HOME and NEW_TASK/CLEAR_TOP; retain
  `singleTask`, update the stored intent in `onNewIntent`, and log lifecycle/task IDs.
- Add **All apps → Home button setup**, navigable with the remote. It offers the
  system HOME-role picker (Android 10+) or Home settings, Accessibility settings,
  a fallback switch and local diagnostics. No grants are made silently.
- Optional Google TV fallback: only the exact window event for
  `com.google.android.apps.tv.launcherx.home.HomeActivity` requests Glass TV.
  It defaults off; requires user consent and a bound Home helper; excludes a
  sleeping/locked TV, Settings and other activities; rate-limits requests to one
  per 1.5 seconds. No polling, new service, window-content retrieval or boot launch.
  It reacts after Google TV appears, so Google TV may flash. An accessibility event
  does not identify a physical button; intentional visits to that Home screen are
  redirected too. Turn off the fallback before intentionally using Google TV Home.
- Debug builds use `com.gathin.tvhome.hometest` and label **Glass TV Enhanced · HOME
  test**. The existing `com.gathin.tvhome` and its data remain separate. The app's
  Java activity classes still start with `com.gathin.tvhome`.
- The test build skips automatic ADB cache cleaning. The original on-demand Clear
  cache feature remains available; avoid it during the HOME test. Release builds
  retain the original automatic cleaning behavior.
- Existing glass visuals, wallpapers, widgets, app ordering, D-pad/OK/Back, input
  handling and screensaver remain in place. Test-install preferences/themes are
  separate; the existing installation's settings are not copied automatically.

## Download using only iPhone

The changed branch must first be pushed to your repository or merged from a PR.
In Safari, open the repository → **Actions → Debug APK (safe HOME test)**.
A push to `prism-home-fix`, `main`, or a `fix/**` branch, or a PR, starts it automatically; once the workflow is on the default branch,
**Run workflow** can start it manually. Enable Actions in your fork if prompted.
Wait for a green run, open it, scroll to **Artifacts**, and tap
**Glass-TV-Enhanced-HOME-test-N**. GitHub requires you to be signed in to download.
If controls are missing, use Safari's **Request Desktop Website**.

Save the ZIP to Files and tap it to extract. Inside are
`Glass-TV-Enhanced-HOME-test.apk`, these instructions and SHA256SUMS.txt.
Artifacts expire after 30 days; run the workflow again if needed.
The separate reports artifact contains test/lint reports, not the installable APK.

## Install safely

1. Keep Google TV and the existing Glass TV installed and enabled. Do not run any
   stock-launcher disabling instructions from the older guides.
2. Use your existing AirAPK Wi-Fi ADB connection and its APK install/upload feature
   **if your version supports it**; choose the APK in Files. iOS cannot install an
   Android APK itself. A text-only ADB shell cannot access an iPhone Files path.
3. If AirAPK only has shell input, transfer the extracted APK using a file-transfer
   app already available on the TV (for example, open a TV file receiver's upload
   page in Safari). Copy the actual TV-side file path from the receiver. A shell
   install then uses `pm install -r` followed by that quoted TV-side path. Do not
   paste an iPhone `/var/mobile/...` path into the TV shell. No local HTTP server,
   public APK hosting or laptop is required if your receiver provides browser upload.
4. If neither AirAPK APK transfer nor a TV browser/file receiver is available, these
   four named apps alone do not supply a binary-transfer mechanism. Add a receiver
   from the TV Play Store or use an existing TV browser download path first; do not
   assume a private GitHub artifact URL will work with an unauthenticated TV download.
5. Open **Glass TV Enhanced · HOME test** from Google TV's app list. It installs
   alongside the original launcher, so a signing-key mismatch with the original is
   avoided. If a later *test* update reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
   remove only the HOME test app through Settings and reinstall; this erases test
   preferences, not the original launcher's data. Do not remove Google TV.

Optional manual start, entered in AirAPK's **TV shell** (no `adb shell` prefix):

```sh
am start -n com.gathin.tvhome.hometest/com.gathin.tvhome.MainActivity -a android.intent.action.MAIN -c android.intent.category.HOME
```

## Test in this order

1. Open All apps → **Home button setup → Choose default Home app**. Select the
   distinctly labelled HOME test app if the firmware offers the choice.
2. Keep fallback off initially. Press physical Home while in Glass TV, then from
   YouTube, from Settings and from an HDMI input. Record what appears. An ADB
   `input keyevent` is not equivalent to the remote and is not the acceptance test.
3. From Home button setup, open Accessibility settings and enable the **Home button**
   helper belonging to **Glass TV Enhanced · HOME test**. Give it a few seconds.
   During the test, use only this HOME-remapping helper; temporarily turn off any
   Monet/original Glass HOME-remapping service in the UI to avoid competing handlers.
   Do not replace the enabled-services list with a shell command; that can turn off
   accessibility tools you need. InputKeyService is optional and does not fix HOME.
4. Repeat the physical remote tests. If Google TV still opens, return to the test
   app and explicitly enable **Google TV Home fallback** in Home button setup.
   Read its warning, then repeat. A short Google TV flash may remain.
5. Check arrows, OK, long OK (app move), Back, your widgets, input/HDMI switching,
   video playback, voice search, cast and standby/wake. After a normal reboot,
   check that the Home helper is still **bound**, then repeat the physical tests.
   Do not declare success solely from a successful build, HOME role or resolver.

Useful read-only diagnostics, one command at a time in AirAPK:

```sh
am get-current-user
settings --user current get secure enabled_accessibility_services
dumpsys accessibility
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
dumpsys activity activities
logcat -d -v time -s TvHome.Home:I ActivityTaskManager:I AndroidRuntime:E
```

Look for `HomeKeyService connected` and for the test component under **Bound
services** in `dumpsys accessibility`. An enabled setting alone does not prove bind.
`key=KEYCODE_HOME` means the helper received HOME. `stock window=... redirect=true`
means fallback requested a launch; it does not prove the TV accepted it.
`onNewIntent` / `onResume` with the same task ID shows reuse of the existing HOME
task. Match the current resumed activity to the test package in the activity dump.
No key log plus a stock window log suggests firmware intercepts HOME before the
helper. No connected log points to binding, service competition or lifecycle issues.
Review/redact unrelated package and activity information before sharing dumps.
No logs are uploaded by the app.

## Recovery and limits

Turn off **Google TV Home fallback** in the test app's Home button setup, then turn
off the test Home helper in Accessibility and select Google TV or your known-working
Monet in the default-home UI. Google TV stays installed and enabled throughout.
If navigation is difficult, this TV-shell command opens Accessibility settings:

```sh
am start -a android.settings.ACCESSIBILITY_SETTINGS
```

Turn off the test helper there. To open the reported stock Home directly after
disabling the helper/fallback:

```sh
am start -n com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity
```

If a Home press still goes to Google TV, or the service does not stay bound, keep
Monet as your working Home and open Glass TV as an app. Ordinary app permissions
cannot override a firmware component launch or guarantee interception of reserved
HOME keys. The fallback also depends on firmware emitting the exact window event
and allowing accessibility activity starts. These changes cannot guarantee either.
A different Google TV activity class requires diagnostic evidence before expanding
the match; do not redirect the entire launcher package, which could trap Settings.
The exact limitation on this PRISM+ remains to be established by the device logs.
