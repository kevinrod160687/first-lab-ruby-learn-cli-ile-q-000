# Trail release checklist

Package id `dev.kevin.trail`. Version name and `versionCode` live in `trail/app/build.gradle.kts`. The first build is `1.0.0` / `1`.

## Versioning

- Bump `versionName` for anything you install over an older copy (`1.0.1`, `1.1.0`).
- Bump `versionCode` by 1 for every APK you expect Android to treat as newer. Android compares `versionCode`, not the name.
- Tag a release `trail-vX.Y.Z` (for example `trail-v1.0.0`) when you want the release job to sign an APK. The tag job is documented in the workflow file.

## Signing secrets

Release signing reads the environment. Local and CI builds stay unsigned unless these are present. Names only:

| Name | What it is |
| --- | --- |
| `TRAIL_KEYSTORE_FILE` | Path to the `.jks` or `.keystore` file on the machine that runs Gradle. CI writes this from the secret below. |
| `TRAIL_KEYSTORE_BASE64` | GitHub Actions secret. Base64 of the keystore file. Not an env var the Gradle build reads directly. |
| `TRAIL_KEYSTORE_PASSWORD` | Store password. |
| `TRAIL_KEY_ALIAS` | Key alias inside the store. |
| `TRAIL_KEY_PASSWORD` | Key password. |

Add the four GitHub Actions secrets (`TRAIL_KEYSTORE_BASE64`, `TRAIL_KEYSTORE_PASSWORD`, `TRAIL_KEY_ALIAS`, `TRAIL_KEY_PASSWORD`) under the repository settings. Do not put the files or the values in git. `trail/.gitignore` ignores `*.jks`, `*.keystore`, `keystore.properties`, and `.env`.

Create a keystore on a machine you trust, for example:

```bash
keytool -genkeypair -v -keystore trail-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias trail
base64 -w0 trail-release.jks
```

Paste the base64 into `TRAIL_KEYSTORE_BASE64`. Keep the `.jks` file outside the repository. The workflow decodes it in the runner temp directory and does not print the passwords.

A release build without those variables still runs R8 and is not debuggable, but it is unsigned. `assembleRelease` is the task. Debug builds used for the first sideload are `assembleDebug` and are signed with the automatic debug keystore.

## Sideload on Android

1. Copy `app-debug.apk` (or a signed release APK) to the phone, or use `adb install -r app-debug.apk`.
2. If the phone blocks the install, allow installs from that source in system settings. This app is not distributed through Play for this setup.
3. Open Trail. Read the location explanation and continue. Grant precise location when Android asks.
4. Open the background-location screen from the card on Today and set location to **Allow all the time**. Android will not offer that on the first prompt. Without it, updates stop when the app leaves the screen and a short walk can be missed.
5. Allow notifications so the low-key tracking notice, and its Pause action, can show.
6. Allow activity recognition so sampling can slow down when you are still. If you deny it, Trail keeps the faster moving rate.
7. In Settings, tap **Allow unrestricted use** under battery. On the system screen, set Trail to unrestricted / not optimized. Doze and app standby can still delay the hourly upload. The foreground service is what keeps location running. **Upload now** sends pending points without waiting for the interval, and it ignores the Wi-Fi-only and low-battery switches for that one run.
8. Enter the webhook URL (`https://…`) and the Authorization header value. Save. Confirm the Today screen shows a pending count that drops after an upload, or tap **Upload now**.
9. Optional: add a home zone (drop or coarsen), set retention, and set a certificate pin (`sha256/…`) if you use one.
10. Reboot the phone once. Tracking should return if it was enabled, not paused, and location permission is still granted.

## CI

The workflow is `.github/workflows/trail.yml`. GitHub only runs workflows stored under `.github/workflows/`, so that file is not inside `trail/`. It is limited to changes under `trail/**` and to that workflow file.

On pull requests and pushes that touch `trail/**` (or this workflow) it builds, runs unit tests, Android lint, detekt, gitleaks, and a Trivy scan of a CycloneDX SBOM. The SBOM is the runtime classpath of the app and the core library, which is what ships in the APK, not the Android Gradle Plugin. The job uploads the debug APK as the artifact `trail-debug-apk`.

The release job runs only for tags named `trail-v*`. The tag has to point at a commit whose diff matches the path filter, so tag the commit that contains the Trail change rather than an empty follow-up.
