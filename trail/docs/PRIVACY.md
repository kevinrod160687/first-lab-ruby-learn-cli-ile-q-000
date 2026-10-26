# Trail privacy

This is what the app collects, where it goes, and how long it stays. It is written for the person whose phone it runs on.

## What is collected

On the phone, Trail stores:

- Location fixes: latitude, longitude, accuracy, altitude, speed, bearing, and the time of the fix.
- Battery percent and whether the connection was wifi, mobile, or other, attached to each fix.
- The activity label when Play services provides one: still, walking, running, on a bicycle, or in a vehicle.
- Visits (a stay of about 5 minutes inside about 100 meters) and trips between visits, including distance and mode.
- Private zones you add: a name, a center, a radius, and whether points there should be dropped or coarsened before upload.
- A random device id created on the phone, a two-letter tracker id (default `KV`), and upload bookkeeping (sent, pending, last error).

Trail does not read contacts, photos, microphone, or accounts. It does not use the Android advertising id.

## Where it goes

**This phone.** Fixes, visits, trips, and zones are written to an encrypted database. The webhook URL and the Authorization header are written to encrypted preferences. Nothing is uploaded until a webhook is saved.

**Your webhook.** About once an hour (or when you tap Upload now), unsent records are posted with HTTPS to the URL you entered. The body is described in the README. Private zones are applied at this step only:

- Drop: the point is not sent, and it will not be retried.
- Coarsen: the sent coordinates are snapped to a grid of about 1 kilometer, and the accuracy field is raised to match. The copy on the phone is unchanged.

The server at that URL is yours, or whoever you gave that URL to. Trail has no other backend.

**Google Play services.** Fused location and activity recognition are Google libraries. Using them means the device talks to Google Play services as it would for any app that asks for those signals. Trail does not add a separate analytics package.

**OpenStreetMap.** The Today map uses osmdroid. When there is a route to show, tile images are requested from the public OSM tile servers. Those requests reveal that a device loaded a map of that area. If there are no points today, the map is not started. The user agent is `Trail/<version> (personal diary tracker)`.

There are no ads and no third-party crash or analytics SDKs.

## Sharing and export

Export GPX and Export JSON build a file from the on-device diary at full precision, including inside private zones, and hand it to the Android share sheet. Trail does not choose the destination. The file sits in the app cache until the share completes.

## Retention

The default is 90 days. Settings can switch to 30 days, 365 days, or forever. A daily job deletes points, visits, and trips older than that cutoff. Forever means the job does not delete them.

**Delete all location data** removes points, visits, trips, zones, and the last-upload status. It leaves the webhook URL and header in place so you do not have to type them again. **Forget webhook** clears the URL, the header, and any certificate pin.

Pause stops new fixes until you resume, or until the number of hours you picked has passed. Pause does not delete what was already stored.

## Backups

Android backup is turned off, and the backup rules exclude the database and preferences. Uninstalling the app deletes the diary and the webhook settings.

## What you should do

- Set location to **Allow all the time** only if you want background walks recorded. The app asks for that on a separate screen and explains why.
- Add a private zone for home if home should not appear on the webhook.
- Use a webhook you control, and an Authorization value you can revoke. Rotation steps are in [SECURITY.md](SECURITY.md).
