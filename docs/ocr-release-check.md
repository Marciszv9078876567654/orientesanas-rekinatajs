# Optimized OCR and OpenCV check

The release shrinker must preserve the no-argument constructors of ML Kit's
component registrars. Without them, component discovery throws
`NoSuchMethodException`, and recognition cannot initialize despite bundled models.
The targeted rule lives in `app/proguard-rules.pro`.

Release includes Android's default optimized ProGuard rules, with an explicit
OpenCV native-method naming safeguard in `app/proguard-rules.pro`. The `ocrCheck`
build inherits both rules files through `initWith(release)`. Minification and
resource shrinking remain enabled.

Build a separate diagnostic app with the same release optimization:

```powershell
.\gradlew.bat assembleOcrCheck -PtestOptimizedOcr=true
adb install -r app/build/outputs/apk/ocrCheck/app-arm64-v8a-ocrCheck.apk
adb shell am start -n com.orientesanasrekinatajs.ocrcheck/com.orientesanasrekinatajs.OcrSmokeActivity
adb logcat -d -s OcrSmoke:*
```

Check the newest log timestamps for both:

- `PASS: OpenCV native boundary detection`
- `PASS: plain OCR and control label pipeline`

The OpenCV check detects a known rectangle in a synthetic bitmap and checks its
corner coordinates, exercising the native bridge independently of ML Kit. Any
`FAIL` entry means the check failed; a debug build does not verify R8 behavior.

This uses synthetic images only and a separate `.ocrcheck` application ID; it does
not replace the installed release or read saved maps. The diagnostic activity is
only included in the opt-in `ocrCheck` build.

After building, inspect `app/build/outputs/mapping/ocrCheck/configuration.txt` for
both the default JNI rule and the scoped OpenCV rule. In the adjacent `mapping.txt`,
verify that `org.opencv.core.Mat`, `org.opencv.imgproc.Imgproc`, and
`org.opencv.core.Core`, plus their retained native methods, keep their original
names. The mapping does not label methods as `native`; compare native declarations
in the dependency with the mapped names. Release output uses the `release` folder.

Use Android Studio's signed APK build with the existing signing key to produce an
update for an installed release. `assembleRelease` alone produces unsigned APKs
because this repository does not configure release signing credentials.
