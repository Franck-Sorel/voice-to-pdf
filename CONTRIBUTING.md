# Contributing

Thanks for helping with Voice to PDF. Small repo, big mission: offline
note-taking for students in low-connectivity regions.

## Ground rules

- Keep the offline-first guarantee: **offline-first app**, networking is only
  opt-in, least-data, PII-free telemetry + update checks (ADR-001 amended).
- `core/` is pure Kotlin and must stay Android-free.
- Follow the code style in `.editorconfig`; TypeScript-style rules from the
  global config don't apply here, but strictness does — no `Any`, named
  exports (Kotlin top-level functions/classes), interfaces over type aliases
  for object shapes.
- Run `./gradlew test` and `./gradlew lintDebug` before pushing.
- Room migrations are append-only: never edit `app/schemas/` JSON by hand or
  change an existing migration.
- Never commit keystores, `.env`, or secrets (see `.gitignore` and
  `keystore.properties.example`).

## PR workflow

1. Branch from `main` with a short, descriptive name.
2. One logical change per PR.
3. Add/update a unit test for pure logic.
4. Reference the relevant PRD requirement (e.g. "F2") in the description.
5. **PR titles use Conventional Commits** (`feat:`, `fix:`, `docs:`, `build:`,
   `ci:`, `chore:`, `refactor:`, `test:`) — enforced by the `pr-title.yml`
   workflow, and it keeps release notes clean.

## CI/CD

GitHub Actions (see `README` "CI/CD & hygiene" and `.github/workflows/`):
unit tests + lint + builds on every PR; a signed release with signature +
size verification; Dependabot dependency PRs with high-severity dependency
review; CodeQL SAST on C++/Kotlin. Branch protection on `main` (required
status checks) is recommended in the repo settings.

## Milestone help wanted

See `docs/ROADMAP.md`. Highest-value first contributions:

- whisper.cpp native build (`ml/README.md`, CMake/NDK) and CI for it
- Opt-in, minimal telemetry (NFR "Telemetry & privacy") and its tests
- Instrumented tests (Room DAO, Compose UI) and a Firebase Test Lab relay

## Code of conduct

Be kind and constructive. Everyone here is a volunteer — see
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
