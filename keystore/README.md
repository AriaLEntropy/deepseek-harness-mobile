This directory holds the **preview sideload** signing key for GitHub Release APKs.

It is not an Play Store upload key. The password is public on purpose so
`v*` tags can be built by GitHub Actions without extra secrets. Do not reuse
this keystore if you later publish to Google Play.
