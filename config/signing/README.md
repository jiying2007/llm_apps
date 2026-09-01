# Android debug signing

`android-debug.keystore` is intentionally public, non-Play-production signing material for the **current GitHub Android release stage**.

- alias: `androiddebugkey`
- store/key password: `android`
- certificate subject: `CN=Android Debug, O=Android, C=US`
- certificate SHA-256: `26:18:E7:88:94:86:AD:EA:5F:C0:83:F7:CB:51:55:F2:EC:62:9B:AF:5D:AE:2A:74:DA:BC:3A:BE:5C:D0:2A:94`
- keystore file SHA-256: `b327cb3fd3bf5eeaeb3958737335180f5b8c664d47429fd1b9eb08d32e178a56`

GitHub downloadable APKs use this exact Android debug key so successive current-stage releases retain a stable signing identity and can upgrade one another. For the current stage, the resulting debug-signed APK is the official installable Android release artifact.

The key is not secret and must never be treated as Google Play production/upload signing material. A future Google Play production release uses the separate retained production/upload signing path.
