# Trail threat model

Trail stores a continuous location diary and a credential that can post that diary to a webhook. This note is the threat model for that design. It names assets, what can go wrong, what the app does about it, and how to rotate the token.

## Assets

| Asset | Where it lives |
| --- | --- |
| Raw points, visits, trips | SQLCipher database `trail.db` |
| Database passphrase | EncryptedSharedPreferences file `trail_secrets`, key `db_passphrase` |
| Webhook URL, Authorization header, optional certificate pin | Same encrypted preferences file |
| Device id (random UUID, not Android ID) and other settings | Same file |
| Map tile cache | osmdroid cache on disk |
| In-flight upload body | Memory, then the TLS session to the webhook |

The Android Keystore holds the master key that encrypts `trail_secrets`. The SQLCipher key is a 32-byte random value created on the device and stored in that file. It is not derived from a password the person types, and it is not in the APK.

## Threats and mitigations

### Lost or stolen phone

Someone with the unlocked phone can open Trail and read the diary, export it, or change the webhook. Full-disk encryption and the lock screen are the main controls. Trail does not add a second app PIN.

Someone with a locked phone, or a copy of the files without the Keystore keys, does not get the passphrase or the webhook token in the clear. `android:allowBackup="false"` is set, and the backup and data-extraction rules exclude databases, shared preferences, files, external storage, and root. A cloud backup should not contain the diary or the token.

Wipe in Settings deletes points, visits, trips, zones, and sync state. It does not delete the webhook settings. Use **Forget webhook** before handing the phone on if the token should go too. Uninstalling the app removes both, because the data is not backed up.

### Interception on the network

Cleartext is forbidden in the network security config (`cleartextTrafficPermitted="false"`) and `android:usesCleartextTraffic="false"`. The client also refuses any webhook URL whose scheme is not `https`, and it refuses a URL that contains user-info so the token is not stuck in a query or a log of the URL.

The Authorization value is sent only as a header, and only when it is non-blank. OkHttp is built without a logging interceptor. Failure strings are passed through a redactor that strips the URL and the header value before they are stored as the last upload error. `UploadConfig.toString()` and `TrailSettings.toString()` replace those fields with `[redacted]`.

TLS uses the system trust store. That stops a casual network observer. It does not stop a device that has a user-installed CA, or a webhook operator who is the attacker (see below).

### Leaked token

The token is the Authorization header. If it leaks, anyone who also knows the URL can post forged batches or, depending on the server, read whatever that header authorizes.

Trail’s controls are storage and logging, not server-side scope:

- The header is not in the database rows, the batch id, or the JSON body.
- It is read at send time, so a new value applies to the next attempt, including retries of an old batch.
- Logcat is not given the value. Release builds also strip `Log.v` and `Log.d`.
- The URL validator rejects credentials embedded in the URL.

What Trail cannot do is see GitHub, screenshots, or a backup of a server. Treat the header like a password. Do not commit it, and do not paste it into an issue.

### Malicious or compromised webhook

The webhook is chosen by the person using the phone. Trail will send the diary to whatever HTTPS URL is saved. A hostile URL can collect every batch. Private zones are the control for places that should not leave the phone: drop them, or coarsen them to about a kilometer, before upload. Zones do not apply to a GPX or JSON export the person shares themselves.

A compromised server that still presents a trusted certificate will receive new batches until the URL or token is changed. Certificate pinning is optional for this case. In Settings, a pin looks like `sha256/<base64 SHA-256 of the leaf certificate>`. OkHttp’s `CertificatePinner` then requires that pin for the webhook host. A blank pin means “system CAs only”. A non-blank pin that does not match that shape fails closed and is not echoed in the error.

Pinning a host that sits behind a CDN or that rotates certificates will break uploads until the pin is updated. Pin the certificate you actually terminate TLS with, and plan for rotation. Trail does not fetch pins from the network.

### Other people, and other apps

Permissions are limited to location (fine, coarse, and background), a location foreground service, activity recognition, notifications, boot, plus the network and power pieces the design needs: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. There is no contacts, microphone, camera, or SMS permission.

The tracking service and the activity receiver are not exported. The boot receiver is exported because `BOOT_COMPLETED` requires it, and it only starts tracking when tracking is enabled, not paused, and a location permission is already granted. The FileProvider is not exported and only exposes `cache/exports/` for the share sheet.

There is no analytics, ads, Firebase, or crash-reporting SDK. Play services location and osmdroid are the two libraries that contact a network other than the configured webhook. See [PRIVACY.md](PRIVACY.md).

Release builds set `isMinifyEnabled`, `isShrinkResources`, and `isDebuggable = false`. A debug build is debuggable by design and should not be the build you carry daily if you can sign a release.

## Token rotation

1. Create a new secret on the server. Prefer a scope that can only accept Trail batches.
2. Open Trail, Settings, replace the Authorization header, and save. The next upload, including a retry, uses the new value. Batch ids do not contain the old token.
3. Revoke the old secret on the server.
4. If the URL itself was exposed, change the URL too. Forget webhook clears the URL, the header, and the pin.

Do not put the new token in the URL. Trail will reject it, and anything that logs URLs would keep it.

## What this does not claim

Unit tests do not load SQLCipher or the Android Keystore. Production code does: `DatabaseFactory` calls `System.loadLibrary("sqlcipher")` and `SecretStore` opens EncryptedSharedPreferences with a Keystore `MasterKey`. A determined attacker with root on an unlocked device can still read process memory and the decrypted database. Trail’s goal is to keep the diary and the token out of backups, logs, cleartext traffic, and casual access to the files.
