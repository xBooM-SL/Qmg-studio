# About the signing key

`app/qmgstudio.jks` is committed on purpose so every build is signed with the same key. That is what lets a new APK
install as an **update** over the previous one.

Because the key (and its password) are public, anybody can build an APK that Android will accept as an update to
QMG Studio. Only install QMG Studio APKs from https://github.com/xBooM-SL/Qmg-studio (Releases / Actions).

If you fork this project for your own distribution, generate your own key
(`keytool -genkeypair -v -keystore my.jks -alias my -keyalg RSA -keysize 2048 -validity 36500`), keep it **out of git**,
and store it as GitHub Secrets instead.
