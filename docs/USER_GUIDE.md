# WifiLens user guide

WifiLens has four tabs: **Analyze** (what's on the air now), **Map** (your home), **Diagnose** (what the map
predicts, and how to improve it) and **More** (glossary, settings, about).

## First start

Android only shows nearby Wi-Fi networks to apps with **precise location** permission, so WifiLens asks for it and
for Location to be on. It never reads, stores or sends your location. Wi-Fi has to be on to scan, but you don't
need to be connected. The permission screen shows what's missing and takes you to the right setting.

## Analyze

### Networks
Every network in range, with its signal (dBm), channel, band (2.4, 5 or 6 GHz) and security. Filter by band, and tap
**Sort** to switch between signal and channel. When you're connected, your network is at the top with a live
reading. Open **Show signal history** on a network to see its signal over the last 24 hours.

Android limits apps to 4 scans every 2 minutes. When you hit the limit, the refresh button shows how long to wait.

| Signal | Means |
|---|---|
| −67 dBm or better | Good: video calls and streaming are fine |
| −67 to −75 dBm | Fair: browsing is fine, video may stutter |
| below −75 dBm | Poor: expect drop-outs |

### Spectrum
How crowded each channel is (0 = clear, 100 = crowded) on the band you're using, the networks sharing or
overlapping your channel, the **best channel** to switch to, and the **busy hours** of the last day. WifiLens can't
change your router; change the channel in your router's admin page.

### Health
A plain-language check of the network you're connected to: signal, crowded channel, a better band, a stronger access
point nearby, mesh, and security problems such as open or WEP networks. Each finding says what to do. **Run health
check** adds a speed test.

## Map: draw your home

1. **Create plan**, give it a name and a size in tiles (one tile is about 1 m). A plan is one floor; make another plan
   for another floor or home.
2. **Add room area** for each room (Living Room, Kitchen…), then paint its tiles with the **Room** tool. Rooms are
   areas on the same plan.
3. Paint walls with **Wall** and pick the material: drywall, wood, glass, brick, concrete or metal. Thicker, denser
   walls block more signal.
4. Put **Door** tiles where you walk between rooms.
5. Place the **Router** where it really is, and a **Device** pin (with a name) for each place you use Wi-Fi: TV,
   desk, bed.
6. Switch to **3D** to check the plan; drag to orbit, pinch to zoom.

**Undo** and **Redo** cover grid edits. To remove a device, tap it with the Device tool and choose **Remove device**.
**Erase** clears a tile.

Plans are saved automatically. Use the plan menu to switch, rename, delete, or export and import a plan as a JSON
file (to move it to another phone).

### Walk survey (optional, more accurate)
Connect to your Wi-Fi, choose **Measure**, stand somewhere in your home and tap the tile you're standing on; hold
still while WifiLens reads the real signal. Measure spots both near and far from the router. Once you have enough
spots, **calibrate**: WifiLens fits the prediction to your home, and Diagnose shows the typical error (for example
±3.2 dB).

## Diagnose

Tap **Run diagnosis** on the Map, or open the Diagnose tab.

- **Coverage:** predicted signal on every tile (or calibrated, after a survey), the signal in each room, your weakest
  device and plain-language findings. **Share report** exports it as a PDF or image.
- **Best spot:** tries every tile as the router position and finds the one that gives your *weakest* device the most
  signal. It shows the gain in dB; **Move router here** updates the plan so you can compare.
- **Speed:** a real download test (about 8 seconds, from Cloudflare) next to the Wi-Fi link speed your phone
  negotiated, with a history by hour of day. Some school and office networks block speed tests; WifiLens tells you
  when that happens.
- **Signal:** a live meter to walk around with.

Predicted values are estimates from your drawing. They get closer to reality with accurate walls and a walk survey.

## Widget and Quick Settings tile

Long-press your home screen → **Widgets** → WifiLens to see your current signal. The tile goes in Quick Settings:
pull down twice, tap the edit pencil and drag **WifiLens** in.

## More

- **Glossary:** RSSI, dBm, channels, bands, path loss and the other terms the app uses.
- **Settings:** theme and dynamic colour, haptics, automatic scanning, and the prediction model's defaults.
- **About:** version, licences and the privacy policy.

## Privacy

No account, no ads, no analytics. Your plans, scans and history stay on your phone and aren't included in cloud
backup. The only thing WifiLens sends over the internet is the speed test's download request. Full policy:
<https://wifilens.garvitmaheshwari.in/privacy/>.

## Troubleshooting

| Problem | Try |
|---|---|
| No networks listed | Turn on Wi-Fi and Location; check WifiLens has precise location permission. |
| Refresh shows a countdown | Android's scan limit; wait for it to finish. |
| Health says "Connect to Wi-Fi" | The health check looks at the network you're connected to. |
| "Run diagnosis" is greyed out | The plan needs a room, a router and at least one device; the hint under the button says which. |
| Speed test "blocked" | The network blocks speed tests; try on your home Wi-Fi. |
| Coverage looks too good or too bad | Check wall materials, then do a walk survey to calibrate. |
