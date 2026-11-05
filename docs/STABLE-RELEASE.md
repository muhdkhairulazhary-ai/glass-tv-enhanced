# Stable production signing and safe parallel migration

## Current status and the original key

PR #2 remains unmerged. The owner confirmed physical Home, accessibility and Home after restart
on PRISM+ SW7 Android 11 using HOME-test-2, while Google TV remains enabled.
The new production package is separate: **com.gathin.tvhome.production**.
It does not update, replace or share private app data with **com.gathin.tvhome.hometest**.
The Java namespace and HOME/accessibility implementation, manifest and service XML are unchanged.
The new package gives its service components new package prefixes automatically; it needs its own
accessibility consent and default-HOME selection. Its visible app name is **Glass TV Enhanced**.

No signing keystore is tracked in the repository. The only local build-environment debug keystore
examined has SHA-256 1dec043e4eb82f11077627d8f4d5fce33ea2bd51f7298e81990854b3bd85dd13;
it is not HOME-test-2's bcba7b64c48d68fb5e6d6264305d3904227e09b05d8d55c0e3a693c301f5f1a0.
No matching original key is available in the checked repository or local build environment.
An old GitHub runner/cache might still contain it, but that has not been verified and is not a
reliable release strategy. A certificate or signed APK cannot reconstruct its private signing key.
The connector cannot inspect or manage Actions secrets, so no claim is made about hidden remote keys.

The new production variant is non-debuggable, unshrunk like the working debug build, and assembled
unsigned. It never inherits the debug signing key. A separate signing job signs only with five
explicit secrets and verifies the configured certificate fingerprint. Missing secrets fail the
signing job: no generated key, no unsigned APK mislabelled as installable, and no debug-key fallback.
All application features remain; automatic ADB cache cleaning stays off as in HOME-test-2.
The optional Google TV fallback is still off by default in the new app's separate preferences.

## One-time secure owner setup (iPhone Safari)

There are no permanent signing secrets yet. A repository administrator must configure them.
The current GitHub connector has no secret-management tool. Creating code/PRs does not grant this
capability, and the ordinary Actions GITHUB_TOKEN cannot write repository Actions secrets.
No one should paste a keystore, private key, password or personal token into chat or an issue.

A private GitHub Codespace provides an iPhone-accessible terminal without installing a laptop JDK:

1. In Safari, open the repository on branch **glass-tv-stable-signing**, then **Code → Codespaces →
   Create codespace on this branch** (Request Desktop Website if needed). Codespaces availability,
   quota/billing and mobile terminal usability depend on your account. Never use a public/shared terminal.
2. The script requires Java/keytool, Python 3, OpenSSL and GitHub CLI. Use an environment with those
   installed. Do not run this script on the TV. Review `tools/signing/setup-secrets.py` first.
3. Authenticate GitHub CLI for **this repository** with permission **Secrets: read and write** and
   **Contents: read**. A fine-grained personal access token restricted to this repository can supply
   that permission. Enter it only through a secure GitHub/Codespaces credential mechanism, for example
   an account-level Codespaces secret named GH_TOKEN restricted to this repository, then restart the
   Codespace so it is injected. The default Codespaces token may lack Secrets write access. Do not
   put the token in a command, file in Git, workflow YAML or chat. Revoke the setup token afterward.
4. Run in the repository terminal:

   ```sh
   python3 tools/signing/setup-secrets.py --repo muhdkhairulazhary-ai/glass-tv-enhanced
   ```

   Choose and save both prompted passwords in your password manager. Input is hidden. This creates
   one RSA-3072 PKCS12 keystore, makes an encrypted backup, and sends its values directly to GitHub's
   encrypted Actions-secret storage through gh standard input. It never prints the private material.
   It refuses to generate another key if production secrets or its backup already exist.
5. Download **signing-backup/glass-tv-production-signing-backup.enc** and **PUBLIC-CERTIFICATE.txt**
   using the Codespaces file explorer into private iPhone Files storage. Keep an additional independent
   encrypted backup. Record the public fingerprint separately and retain both passwords securely.
   This directory is gitignored. Secrets are not readable back from GitHub; an independent backup is
   essential before deleting a Codespace. Never commit or publish the encrypted backup either.
6. Confirm the following five **names** under repository **Settings → Secrets and variables → Actions**:

   | Secret | Meaning |
   |---|---|
   | PRODUCTION_KEYSTORE_BASE64 | Single-line base64 of the binary PKCS12 keystore; base64 itself is not encryption |
   | PRODUCTION_STORE_PASSWORD | Keystore password |
   | PRODUCTION_KEY_PASSWORD | Private key password (same password for this PKCS12 setup) |
   | PRODUCTION_KEY_ALIAS | glass-tv-production |
   | PRODUCTION_CERT_SHA256 | Public certificate SHA-256 fingerprint, lower-case hex without colons |

   If setup failed part-way, keep the encrypted backup and retry with the SAME key:

   ```sh
   python3 tools/signing/setup-secrets.py --repo muhdkhairulazhary-ai/glass-tv-enhanced --restore-backup signing-backup/glass-tv-production-signing-backup.enc
   ```

   Do not generate a replacement. The restore option deliberately writes all five values from the
   supplied backup, so use only the original backup whose public fingerprint you have recorded.
7. Open the failed **Stable production APK** push run for **glass-tv-stable-signing**, then
   **Re-run failed jobs**. The already-tested unsigned artifact must still be within its 14-day
   retention period; otherwise run a fresh trusted branch build. After merge, manual dispatch on
   that branch/main is also available. Review Actions logs for the explicit missing-secret error if
   signing still fails. No signing secrets are given to pull_request jobs, including same-repo PRs.

Use branch protection and review workflow/signing-script changes before allowing trusted branch pushes.
People who can alter trusted workflows can potentially misuse repository secrets. Fork PRs never sign;
there is no pull_request_target execution or secret-based build of untrusted PR code. For stricter control,
move these five secrets to an approved GitHub Environment and configure that environment/reviewers on
the signing job as a deliberate follow-up. Do not change any keystore/certificate for routine updates.
Future releases must keep **com.gathin.tvhome.production**, the same signing key, and an increasing
versionCode. Losing or replacing the key prevents ordinary in-place updates. No rotation is planned here.

## Build results and artifact selection

**Stable production APK** runs testProductionUnitTest, assembleProduction, strict Python checks,
package/label/non-debuggable checks and unsigned-status verification in a job with no signing secrets.
Its unsigned artifact **UNSIGNED-production-review-N** is for inspection, **not installable**.
A trusted push/manual run then signs in a separate job; the keystore is decoded to a mode-0600 temporary
file, never cached or uploaded, and removed when that step exits. Passwords are passed through environment
references, not literal command arguments. The public fingerprint is checked before APK publication.

Once the signing job succeeds, download **Glass-TV-Enhanced-production-N** in Safari while signed in:
repository → Actions → Stable production APK → successful trusted-branch run → Artifacts.
Save the ZIP to Files and tap it to extract **Glass-TV-Enhanced.apk**, INSTALL.md, APK-INFO.txt,
SIGNING-CERTIFICATE.txt and SHA256SUMS.txt. Do not use the older Debug APK/HOME-test artifacts for the
new production app. Signed artifacts expire after 30 days. Only the public certificate is included.

## Migration: keep the known-working app throughout

No tool in this change connects to or modifies your TV. These are manual owner steps after a correctly
signed production artifact exists. Do not uninstall any apps, clear data, disable Google TV or use the
legacy build-and-install/debloating scripts.

1. Keep HOME-test-2 installed, with its working helper/default-home state. Keep Google TV enabled.
   Note your old fallback setting and take notes of themes/app order you want to recreate. Separate
   Android application IDs cannot automatically share private preferences or accessibility grants.
2. Transfer/install the **signed** production APK using your existing working AirAPK/iPhone method.
   It installs alongside HOME-test-2. Do not enable its helper or change default HOME yet. Open it
   manually from the app list and check the original interface, remote D-pad/OK/Back, widgets,
   wallpapers, app launching and inputs. Its fresh fallback is off; leave it off initially.
3. Only after manual launch/features work, use **Home button setup** to select the new production
   app as default Home. Temporarily switch the old HOME-test accessibility helper OFF in the
   Accessibility UI and enable the new **Glass TV Enhanced · Home button** helper. This is a
   reversible service setting, not uninstalling or disabling the old application. Do not run two
   Home-remapping helpers at once and do not overwrite the enabled-services list via shell.
4. Test physical Home from an app, Settings, HDMI and Glass TV itself. If needed, explicitly opt into
   the new app's optional Google TV fallback after reading its warning. The implementation is the
   same exact activity match, unlock/screen gates and debounce as HOME-test-2, but this separate
   package still requires actual device validation. Google TV may briefly flash with the fallback.
5. Check standby/wake and a normal restart. Confirm the new helper stays enabled and physical Home
   still works. Keep HOME-test-2 installed even after success as your recovery route.
6. If anything fails, turn the new helper OFF, restore HOME-test-2 as default Home and re-enable its
   original helper in the UI. No uninstall, app-data clearing or Google TV changes are needed.

This is a manual parallel migration, not an in-place update of HOME-test-2. No claim is made that the
new package is device-verified until the owner tests these steps. PR #2 is intentionally left unmerged.
