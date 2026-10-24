# About the signing key

`app/qmgstudio.jks` is committed so every build is signed with the same key. That is what lets a new APK
install as an **update** over the previous one.

Because that key (and its password) are public, anybody can build an APK that Android will accept as an update to
QMG Studio. Only install QMG Studio APKs from https://github.com/xBooM-SL/Qmg-studio (Releases / Actions).

## Using a private key (recommended)

The build can sign with a private key kept in GitHub Secrets instead of the key in the repo.
If the secret `QMG_KEYSTORE_BASE64` exists, the workflow uses it. If not, it falls back to the committed key.

1. Create a key (keep the file **out of git**):
   `keytool -genkeypair -v -keystore my.jks -alias my -keyalg RSA -keysize 2048 -validity 36500`
2. Encode it: `base64 -w0 my.jks`
3. In the repository go to *Settings -> Secrets and variables -> Actions* and add:
   * `QMG_KEYSTORE_BASE64` : the base64 text from step 2
   * `QMG_STORE_PASSWORD` : the keystore password
   * `QMG_KEY_ALIAS` : the alias (`my` in the example)
   * `QMG_KEY_PASSWORD` : the key password

**Warning:** Android only installs an update when it is signed with the same key as the installed app. After you switch
to a new key, people who have the old build must uninstall it once and install the new APK; in-app updates work again
from then on. Once everybody has moved, delete `app/qmgstudio.jks` and the fallback in `app/build.gradle.kts`.

If you fork this project for your own distribution, use your own key and never commit it.
