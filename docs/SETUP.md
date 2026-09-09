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

Never commit keystores. Generate a **permanent** release keystore once (keep
it backed up offline — losing it means you can never update sideloaded
installs), then copy `keystore.properties.example` → `keystore.properties`
(gitignored) and fill it in:

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias voicetopdf -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
```

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=...
keyAlias=voicetopdf
keyPassword=...
```

`app/build.gradle.kts` reads `keystore.properties` **if present** (release
builds are then signed); if absent, release builds are unsigned — CI signs via
GitHub secrets (see `.github/workflows/ci.yml`). The same keystore later
enables migrating sideloaded testers to Play with the same signing key.

## 4. ML runtime (required for transcription, F2)

Transcription uses **whisper.cpp** (GGML). The `WhisperNative` JNI bridge is
the intended runtime seam (ADR-015). See [ml/README.md](../ml/README.md) for
the full plan. In short:

1. `bash ml/download-models.sh` → fetches `ggml-base-q8_0.bin` (~78 MB) into
   `app/src/main/assets/models/`.
2. Build `libwhisper.so` for `arm64-v8a` via CMake/NDK (see `ml/README.md`)
   and place it under `app/src/main/jniLibs/arm64-v8a/`.
3. First launch copies the model from `assets` to `filesDir/models/`
   (`WhisperTranscriber.resolveModelFile` is wired for GGML `.bin` names).
4. Verify with `./gradlew assembleDebug`.

Until steps 1–2 are done, tapping "Start recording → Stop" runs the pipeline
but fails at the STT step — expected; see `docs/ROADMAP.md` M1.

## 5. APK size budget

See `docs/NFR.md §2`. Gate: `ls -lh app/build/outputs/apk/release/app-release.apk`
must be ≤ 100 MB or the release CI job fails. Bundle `ggml-base-q8_0.bin`
(~78 MB → ~98 MB APK); `tiny` q8_0 is the low-RAM fallback.

## 6. Common issues

| Symptom | Fix |
|---------|-----|
| `System.loadLibrary("whisper")` UnsatisfiedLinkError | Build & bundle `libwhisper.so` (step 4) |
| Model file not found | Run `bash ml/download-models.sh` + copy to `filesDir/models/` |
| KSP/AGP version conflict | Keep Kotlin 2.3.x + KSP 2.3.x as pinned in the catalog |
| `kapt` requested | This project uses KSP only; do not add kapt (incompatible with built-in Kotlin) |

## 7. CI

`.github/workflows/ci.yml` runs lint, unit tests, and `assembleDebug` on every
push/PR. See `docs/TESTING.md` for the full gate table.
