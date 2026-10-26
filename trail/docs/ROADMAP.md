# Trail roadmap

These are not in the app. They are the next pieces that would make the diary more useful without changing the privacy stance: on device first, upload only what Settings already allows.

## Place naming

Visits are coordinates today. A later step can attach a name you type (“home”, “the park”) or a reverse-geocode result that stays on the phone. Uploaded transition `desc` could use that name only outside private zones.

## Photo matching to visits

Match pictures already on the device to a visit by time and, if the picture has a location, by distance. The match would be a local link in the diary. Photos would not be uploaded with the OwnTracks batch.

## Health Connect steps

Read step counts for the same interval as a trip, so a walk can show steps next to distance. Health Connect permission would be a new, separate prompt. Step totals would stay on device unless a future payload field is explicitly added.

## Voice notes

A short note attached to a visit or a trip, stored in the encrypted database. Recording would need the microphone permission, which Trail does not request today. Notes would be part of export, not of the default webhook body.

## Daily weather

A once-a-day observation for the day’s first stay, stored beside the diary. Any weather provider is another network call and needs to be named in the privacy note before it is added. The default remains “no extra network besides the webhook, Play services, and map tiles.”

## Visit stats

Counts and time-in-place for the week and the month, computed from Room. No new collection. Useful for seeing how often a short outing happens.

## Family-outing mode

A manual flag on a trip: this outing included other people, with an optional label. It would not track anyone else’s phone. The flag is a note on your own diary so the assistant can tell a family outing from a solo errand.
