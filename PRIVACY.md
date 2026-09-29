# WifiLens privacy policy

Effective 29 September 2026. Applies to WifiLens 2.0 for Android (`com.wickedcoder.wifilens`).

**Short version:** WifiLens has no account, no ads, no analytics and no tracking. Everything it measures stays on
your phone. The only thing it sends over the internet is the optional speed test.

## What WifiLens stores, on your phone only

- **Floor plans** you draw: rooms, walls, doors, the router and device pins, and walk-survey readings.
- **Wi-Fi scan history:** for the networks around you, their name (SSID), access-point address (BSSID), signal
  strength, band and channel. Kept for 7 days; an hourly channel-busyness summary is kept for 30 days.
- **Speed-test results:** speed, time, and the signal and band you were on. The newest 500 are kept.
- **Settings** such as the signal-model values and haptics.

None of this is uploaded. The database holding plans, readings and scan history is excluded from Android's cloud
backup; a direct phone-to-phone transfer can copy it. You can delete a plan in Settings, and uninstalling WifiLens
(or clearing its storage) deletes everything.

## Permissions

- **Location (precise):** Android only shows nearby Wi-Fi networks to apps with this permission. WifiLens uses it
  to read signal strength and channels. It does not read your GPS position, and it never records or sends your
  location. WifiLens does not ask for background location.
- **Wi-Fi and network state, change Wi-Fi state:** to read the connection and to start a Wi-Fi scan.
- **Internet:** only for the speed test.
- **Run at startup, keep awake:** used by Android's WorkManager to prune old history once a day and to refresh the
  home-screen widget.

## Internet use: the speed test

When you start a speed test, WifiLens downloads test data from Cloudflare (`speed.cloudflare.com`) and measures how
fast it arrives. Nothing is uploaded. As with any web request, Cloudflare receives your IP address; see
[Cloudflare's privacy policy](https://www.cloudflare.com/privacypolicy/). The result is stored on your phone only.

## Widget and Quick Settings tile

They show your current signal strength, band, channel and link speed, and never the network name, so they need no
background location.

## Sharing

A coverage report (PDF or image) is created only when you tap Share report, and goes only to the app you pick.
JSON plan exports work the same way.

## Children

WifiLens is a general utility and is not directed at children. It collects no personal data from anyone.

## Changes and contact

WifiLens 2.0 is the final version; if this policy ever changes, the new version will be published at this address
with a new date. Questions: open an issue at <https://github.com/Wickedsoni/Wifilens/issues>.
