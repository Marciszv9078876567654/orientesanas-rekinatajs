# Optimized OCR check

The release shrinker must preserve the no-argument constructors of ML Kit's
component registrars. Without them, component discovery throws
`NoSuchMethodException`, and recognition cannot initialize despite bundled models.
The targeted rule lives in `app/proguard-rules.pro`.

Build a separate diagnostic app with the same release optimization:

```powershell
.\gradlew.bat assembleOcrCheck -PtestOptimizedOcr=true
adb install -r app/build/outputs/apk/ocrCheck/app-arm64-v8a-ocrCheck.apk
adb shell am start -n com.orientesanasrekinatajs.ocrcheck/com.orientesanasrekinatajs.OcrSmokeActivity
adb logcat -d -s OcrSmoke:*
```

Check the newest log timestamp for `PASS: plain OCR and control label pipeline`.
This uses synthetic images only and a separate `.ocrcheck` application ID; it does
not replace the installed release or read saved maps. The diagnostic activity is
only included in the opt-in `ocrCheck` build.

Use Android Studio's signed APK build with the existing signing key to produce an
update for an installed release. `assembleRelease` alone produces unsigned APKs
because this repository does not configure release signing credentials.
