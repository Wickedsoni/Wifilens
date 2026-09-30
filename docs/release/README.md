# Release process

How a WifiLens version goes from `main` to Google Play. The Play Console paperwork is in the other files here:

| File | What it holds |
|---|---|
| [signing.md](signing.md) | The upload key, `keystore.properties`, Play App Signing |
| [store-listing.md](store-listing.md) | Name, descriptions, graphics |
| [data-safety.md](data-safety.md) | Data safety answers, with the reasoning |
| [content-rating.md](content-rating.md) | Content rating questionnaire answers |
| [closed-testing.md](closed-testing.md) | The 12-tester, 14-day closed test for new personal accounts |
| `store/` | App icon, feature graphic and phone screenshots |

## 1. Prepare the release branch

```bash
git switch main && git pull
git switch -c release/2.0.3
```

- Bump `versionCode` (must be higher than every code ever uploaded, including drafts) and `versionName` in
  `app/build.gradle.kts`.
- Add the version to [CHANGELOG.md](../../CHANGELOG.md).
- Update the store screenshots if the UI changed ([TESTING.md](../TESTING.md#store-screenshots)).

## 2. Verify

- CI green on the pull request.
- The [device checklist](../TESTING.md#device-checklist-before-each-release) on the release build.
- No open High bugs in [bug-log.md](../bug-log.md).

## 3. Build the signed bundle

Either way produces `app/release/app-release.aab` (Studio) or `app/build/outputs/bundle/release/app-release.aab`
(Gradle):

- **Android Studio:** Build → Generate Signed App Bundle or APK → Android App Bundle → the upload key → `release`.
- **Command line:** with `keystore.properties` in the repo root (see [signing.md](signing.md)),
  `./gradlew :app:bundleRelease`. Without it the build falls back to the debug key and prints a warning; Play
  rejects that bundle.

Check the bundle before uploading:

```bash
keytool -printcert -jarfile app/release/app-release.aab   # signer must be the upload key, not "Android Debug"
```

and confirm the version in Studio's APK Analyzer (or `bundletool dump manifest`).

## 4. Upload

1. Play Console → the track (closed test or production) → **Create new release**.
2. Upload the `.aab`. Play shows the version code and name; a warning about native debug symbols is expected (the
   only native code is AndroidX's stripped libraries) and can be ignored.
3. Release name: `2.0.3 (203)`. Release notes: the user-facing lines from the changelog, under 500 characters, in
   `<en-US>` tags.
4. Review and roll out.

## 5. After release

```bash
git switch main && git pull
git tag -a v2.0.3 -m "WifiLens 2.0.3" && git push origin v2.0.3
```

Create a GitHub release from the tag with the changelog entry. Delete the `release/` branch if GitHub didn't.

## Integrity protection

Play Console → App integrity → **Automatic protection** adds an installer check and anti-tamper protection to
copies installed from Play. It needs no code or server and doesn't change the privacy or data safety answers.
Builds installed from Studio or Gradle aren't affected.
