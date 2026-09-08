# Contributing

Thanks for helping with Voice to PDF. Small repo, big mission: offline
note-taking for students in low-connectivity regions.

## Ground rules

- Keep the offline-first guarantee: **no network permission, no cloud calls in
  the default flow** (ADR-001). CI enforces the absence of
  `android.permission.INTERNET`.
- `core/` is pure Kotlin and must stay Android-free.
- Follow the code style in `.editorconfig`; TypeScript-style rules from the
  global config don't apply here, but strictness does — no `Any`, named
  exports (Kotlin top-level functions/classes), interfaces over type aliases
  for object shapes.
- Run `./gradlew test` and `./gradlew lintDebug` before pushing.
- Room migrations are append-only: never edit `app/schemas/` JSON by hand or
  change an existing migration.

## PR workflow

1. Branch from `main` with a short, descriptive name.
2. One logical change per PR.
3. Add/update a unit test for pure logic.
4. Reference the relevant PRD requirement (e.g. "F2") in the description.

## Milestone help wanted

See `docs/ROADMAP.md`. Highest-value first contributions:

- 16 kHz resampling in `MediaCodecPcmDecoder`
- sherpa-onnx ML runtime integration (`ml/README.md`) and CI for it
- Instrumented tests (Room DAO, Compose UI)

## Code of conduct

Be kind and constructive. Everyone here is a volunteer.
