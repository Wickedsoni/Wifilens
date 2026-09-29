# Signing and Play App Signing

WifiLens uses **Play App Signing**: Google holds the app signing key, and you sign uploads with your own **upload
key**. If the upload key is ever lost, Play support can reset it, and the app stays updatable.

## 1. Create the upload key (once, on your PC)
Keep it outside the repo, and back up the file and both passwords somewhere safe, such as a password manager.

```
mkdir "%USERPROFILE%\keys"
"C:\Program Files\Java\jdk-21\bin\keytool" -genkeypair -v -keystore "%USERPROFILE%\keys\wifilens-upload.jks" -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

## 2. Point the build at it
Create `keystore.properties` in the repo root. It's git-ignored; never commit it.

```
storeFile=C:/Users/<you>/keys/wifilens-upload.jks
storePassword=<store password>
keyAlias=upload
keyPassword=<key password>
```

Then `./gradlew :app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`, signed with the upload
key. Without the file the build falls back to the debug key and warns that the AAB can't be uploaded.

## 3. First upload
Create the app in Play Console. Under **Test and release → App integrity**, keep "Let Google manage and protect your
app signing key", then upload the AAB to the closed testing track ([closed-testing.md](closed-testing.md)).

## 4. Optional: signed bundles from CI
Add four repository secrets (Settings → Secrets and variables → Actions):
- `UPLOAD_KEYSTORE_BASE64`: the .jks as base64 (`certutil -encode wifilens-upload.jks out.txt`, then delete the
  `-----BEGIN/END CERTIFICATE-----` lines)
- `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`

CI then signs its `app-release.aab` artifact with the upload key. Without them it still builds the bundle,
debug-signed, as a check.
