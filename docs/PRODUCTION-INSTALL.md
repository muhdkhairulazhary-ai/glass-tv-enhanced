# Glass TV Enhanced: iPhone installation and upgrade

## Confirmed device behavior

The owner tested the merged HOME-test APK on PRISM+ SW7_PrismPlus (`umeda`), Google TV Android 11,
and confirmed the physical Home button opens Glass TV, the accessibility helper is enabled,
Home works after restarting the TV, and Google TV Launcher stays enabled.
This report applies to that tested device/configuration. The renamed APK needs a short upgrade
check on the same device; a build alone does not prove every TV or firmware works.

## Identity and behavior preserved

The delivered APK is a **signed debug APK**, version 2.3.1 (version code 5).
Its visible name is **Glass TV Enhanced**. It updates the working package
`com.gathin.tvhome.hometest`; the legacy suffix stays intentionally to preserve Android identity.

The namespace, activity classes, accessibility service components, manifest, HOME code, service XML,
fallback policy, preferences and original interface/features are unchanged from the merged working build.
In particular:

- Home helper: `com.gathin.tvhome.hometest/com.gathin.tvhome.HomeKeyService`.
- Input helper: `com.gathin.tvhome.hometest/com.gathin.tvhome.InputKeyService`.
- The helper label becomes **Glass TV Enhanced · Home button**; this is a display-name change only.
- Google TV Home fallback is still optional and **off by default on a fresh install**.
  An in-place update preserves the owner's existing choice, including on if that was part of the
  working configuration. Do not reset it merely to make it look like a fresh install.
- The same package continues to skip automatic ADB cache cleaning. On-demand Clear cache remains.
- No service enablement, HOME preference or fallback preference is rewritten by this update.

Keep `com.google.android.apps.tv.launcherx` and its HomeActivity enabled and unmodified.
Do not use the old build-and-install scripts or debloating instructions for this TV.
No TV connection, root, package disabling or system-partition changes are part of the build workflow.

## Signing and migration risks

No application ID, service identifier, signing configuration, keystore path, key alias or signing-cache
key is changed. The workflow retains `~/.android/debug.keystore` and
`glass-home-test-debug-key-v1`. It verifies the APK signature for Android 11 and includes
`SIGNING-CERTIFICATE.txt`, `APK-INFO.txt` and APK checksums in the download.

This preserves the existing setup but **does not guarantee the exact same signing certificate**:
GitHub caches are branch/ref scoped, PR caches are not generally accessible from other branches,
and caches can be evicted. A new branch may use main's cached key rather than the key used by an
older PR artifact. The TV's installed certificate is not accessible to this build, so identity
compatibility must also be checked during installation. No new signing key is deliberately introduced.

Use **Update / Replace**, with app data retained. If Android reports
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a signing mismatch, **stop**. Keep the working app
installed; do not uninstall it or clear its data. Recover the original signing keystore through an
authorized signing-maintenance process before retrying. A different application ID installs a separate
app and requires new HOME/accessibility setup; a different key under this ID blocks an ordinary update.
Neither migration is part of this PR.

A signed debug build is for personal sideloading, remains debuggable, and is not a Play Store release
or a commitment to a permanent production release key. Durable protected signing would require a
separate migration/recovery plan and explicit approval.

## Download in Safari

1. Sign into GitHub in Safari and open this repository's **Actions → Glass TV Enhanced APK**.
2. Open a successful run for `glass-tv-production` or its pull request.
3. Scroll to **Artifacts** and tap **Glass-TV-Enhanced-N** (not the reports artifact).
   If controls are missing, use **Request Desktop Website**.
4. Save the ZIP to Files and tap it to extract. It contains `Glass-TV-Enhanced.apk`,
   `INSTALL.md`, `SHA256SUMS.txt`, `SIGNING-CERTIFICATE.txt` and `APK-INFO.txt`.

No merge is needed to download the PR build. Artifacts expire after 30 days.
The same workflow runs on main and can be dispatched manually once this workflow revision is merged.
A workflow's green result means the build/tests/identity/signature checks passed.
Full lint is an informational step because of inherited incomplete translations; its report is separate.

## Update from your iPhone

1. Keep the working HOME app and Google TV installed. Keep a copy of the known-working APK for reference.
2. Use the same AirAPK transfer/install method that worked for the HOME-test APK. Select the new APK
   and **Update / Replace**, retaining app data. If your AirAPK version is text-only, use your existing
   TV file receiver's Safari upload page to transfer the binary first. An iPhone Files path is not
   a TV filesystem path.
3. Stop on a signature mismatch as described above. Do not uninstall to force the update.
4. Open **Glass TV Enhanced**. Your current theme, app ordering, HOME selection, accessibility service
   and fallback preference should persist. Android or OEM firmware can still interrupt a service during
   an update; if needed, reopen **All apps → Home button setup → Open accessibility settings** and
   verify **Glass TV Enhanced · Home button** is enabled. Do not reset other accessibility services.
5. Press physical Home from Glass TV, another app, Settings and an HDMI input. Check arrows, OK,
   long OK, Back and your usual features. After a normal restart, check Home again.
   This verifies the renamed APK on your TV without altering Google TV.

## Fresh-install HOME setup and recovery

For a fresh install only, open **All apps → Home button setup → Choose default Home app**,
select Glass TV Enhanced if offered, and enable its Home helper through Accessibility settings.
Try physical Home before enabling the optional **Google TV Home fallback**.
The fallback reacts only to Google TV's exact HomeActivity. It may briefly show Google TV and also
redirects intentional visits to that Home screen. It leaves Settings and other activities alone.

To return to Google TV, turn off the fallback in Home button setup, turn off the Glass TV Home helper
in Accessibility, and choose Google TV as default Home. Keep the known-working Monet option available.
Do not disable/uninstall Google TV, change its components or use a reset as a troubleshooting step.

The [original HOME guide](PRISM-HOME-TEST.md#test-in-this-order) retains read-only diagnostic commands
and Home setup details. `TvHome.Home` remains the local log tag; no logs are uploaded by the app.
