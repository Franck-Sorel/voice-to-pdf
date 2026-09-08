# Setup & Build

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | AGP 9 requires JDK 17+ to run the build |
| Android Studio | Current stable (Ladybug+) | Bundles the Android SDK + SDK Manager |
| Android SDK | platform 36, build-tools 36.0.0 | AGP downloads SDK components automatically |
| Gradle | none needed | The wrapper (`./gradlew`) downloads Gradle 9.7.1 |

Verify toolchain (these versions are pinned in `gradle/libs.versions.toml`):

- AGP `9.4.0` — note: uses **built-in Kotlin**; there is deliberately no
  `org.jetbrains.kotlin.android` plugin (see `docs/DECISIONS.md` ADR-008).
- Kotlin `2.3.21`, KSP `2.3.11`, Compose BOM `2026.08.00`.
- Room `2.8.4`, Hilt `2.60.1`, Navigation Compose `2.10.0`.

## 2. First build

```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # JVM unit tests
./gradlew lintDebug            # lint
./gradlew connectedDebugAndroidTest   # instrumented (needs device/emulator)
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

> If you open the project in Android Studio, let it sync once; it will
> auto-provision the SDK and wrapper.

## 3. Signing a release build

Never commit keystores. Create `keystore.properties` locally (gitignored):

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

`app/build.gradle.kts` currently has no release signing config (unsigned
release). Add a `signingConfigs { create("release") { ... } }` block wired from
`keystore.properties` before distributing — and keep it out of VCS.

## 4. Whisper native library (required for transcription, F2)

Transcription calls `libwhisper.so` over JNI. It is **not** part of this
scaffold. Build it per [whisper/README.md](../whisper/README.md), then:

1. Place the built `.so` files under
   `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libwhisper.so`.
2. Bundle a GGML model: put `base.bin` (etc.) in
   `app/src/main/assets/models/` and copy to `filesDir/models/` on first
   launch (see `WhisperTranscriber.resolveModelFile`).
3. Verify with `./gradlew assembleDebug`.

Until step 1 is done, tapping "Start recording → Stop" runs the pipeline but
fails at `System.loadLibrary` — expected; see `docs/ROADMAP.md` M1.

## 5. APK size budget

See `docs/NFR.md §2`. Gate: `ls -lh app/build/outputs/apk/release/app-release.apk`
must be ≤ 80 MB or the release CI job fails. Decide bundled model accordingly.

## 6. Common issues

| Symptom | Fix |
|---------|-----|
| `System.loadLibrary("whisper")` UnsatisfiedLinkError | Build & bundle `libwhisper.so` (step 4) |
| Model file not found | Bundle model + copy to `filesDir/models/` |
| KSP/AGP version conflict | Keep Kotlin 2.3.x + KSP 2.3.x as pinned in the catalog |
| `kapt` requested | This project uses KSP only; do not add kapt (incompatible with built-in Kotlin) |

## 7. CI

`.github/workflows/ci.yml` runs lint, unit tests, and `assembleDebug` on every
push/PR. See `docs/TESTING.md` for the full gate table.
