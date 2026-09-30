# Security policy

## Supported versions

Only the latest release on Google Play gets fixes. Security fixes ship as a patch release (for example 2.0.3).

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately through GitHub:
[Security → Report a vulnerability](https://github.com/Wickedsoni/Wifilens/security/advisories/new).

Include the version (More → About), the Android version and device, what an attacker could do, and steps to
reproduce. You'll get a reply within 7 days, and a fix or a plan within 30 days for confirmed issues. Credit is given
in the release notes unless you'd rather stay anonymous.

## What's in scope

WifiLens has no server and no account, so the attack surface is the app on the device:

- Data stored on the phone: floor plans, survey readings, scan and speed history (Room database and DataStore). The
  database is excluded from cloud backup (`app/src/main/res/xml/data_extraction_rules.xml`).
- Plan files imported as JSON through the Storage Access Framework: a crafted file must not crash the app or corrupt
  other plans.
- Exported components: the launcher activity, the Quick Settings tile and the home-screen widget.
- The speed test's HTTPS download from `speed.cloudflare.com`, the app's only network request.

Out of scope: problems in Android itself or in Cloudflare's service, and attacks that need a rooted or already
compromised device.

## How the app is protected

- Location permission is foreground only and used only because Android requires it to list Wi-Fi networks; no
  location is read, stored or sent ([privacy policy](https://wifilens.garvitmaheshwari.in/privacy/)).
- No analytics, ads or third-party SDKs that send data.
- Release builds are minified with R8 and distributed through Google Play with Play App Signing. Play's automatic
  integrity protection can add installer and tamper checks to Play-distributed copies.
- Signing keys and `keystore.properties` never enter the repository (`.gitignore`).
