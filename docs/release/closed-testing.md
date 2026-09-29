# Closed test before production (12 testers, 14 days)

Personal developer accounts created after 13 November 2023 must run a closed test with **at least 12 testers who
stay opted in for 14 days in a row** before they can apply for production access.

## Set up in Play Console
1. **Test and release → Testing → Closed testing → Create track** (or use "Alpha").
2. **Testers:** add a Google Group or an email list with 12 or more Gmail addresses. 15 gives a margin for drop-outs.
3. **Create release:** upload `app-release.aab` (2.0.0, versionCode 200), paste the release notes below, and roll out.
4. Copy the **opt-in link** from the track's Testers tab.

## Message to testers
> Hi! I'm publishing WifiLens, a free, ad-free Wi-Fi analyzer, and Google needs 12 people to test it for 14 days first.
> 1. Open this link on your Android phone, signed in with the Gmail I added: <opt-in link>
> 2. Tap "Become a tester", then "Download it on Google Play", and install.
> 3. Please keep it installed for at least 14 days and open it a few times: scan, draw a room, run a speed test.
> 4. Found a bug or have an idea? Reply to this message, or use "Send feedback" on the Play page.
> Thank you!

## Release notes (en-US, 500 characters max)
WifiLens 2.0: a new Material 3 design, multiple floor plans with import and export, a walk survey with calibration,
signal and speed history, a one-tap health check, a home-screen widget and Quick Settings tile, and shareable PDF or
image coverage reports.

## After 14 days
Open **Dashboard → Apply for production**, answer the questions about the test (what testers did, what you changed),
and submit. Review usually takes a few days. Then promote the same release to Production.
