# Play Console: Data safety answers

Matches the code in 2.0.0 (checked 2026-09-29) and [PRIVACY.md](../../PRIVACY.md).

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Nothing leaves the device. Plans, readings, scan history, speed results and settings are stored locally only. Play doesn't count on-device processing, or Android backup, as collection. |
| Is all user data encrypted in transit? | Not asked once the answer above is No. (The one request, the speed test, is HTTPS.) | |
| Do you provide a way for users to request that their data be deleted? | Not asked (no data collected). Users delete plans in Settings, or uninstall. | |

Things a reviewer may look at:
- **Location permission** (fine + coarse, foreground only): Android needs it to list Wi-Fi networks. No GPS read, no location stored or sent. No background location, so no location declaration form.
- **Speed test:** downloads test data from `speed.cloudflare.com`. Nothing is uploaded; Cloudflare sees the IP address like any web request. This is not "sharing" in Play's sense (no user data is sent).
- **Backup:** the database (plans, readings, scan history) is excluded from Android cloud backup (`app/src/main/res/xml/data_extraction_rules.xml`).
- **Privacy policy URL:** `https://wickedsoni.github.io/Wifilens/privacy/` (GitHub Pages from `docs/privacy/`).
