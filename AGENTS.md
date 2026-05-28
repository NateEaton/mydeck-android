# Codex Agent Instructions (MyDeck Android)

Follow the project rules in `CLAUDE.md`, especially:
- Localization: any new/changed string resources must be added (English placeholders) to all language `strings.xml` files.
- Documentation: user-visible changes must be documented in the English user guide under `app/src/main/assets/guide/en/`.

## Default verification (run after changes)
Use the Gradle wrapper only:

Run verification tasks serially, not in parallel. Parallel Gradle runs in this project can collide in KSP/generated outputs and produce false failures.

- `./gradlew :app:assembleDebugAll`
- `./gradlew :app:testDebugUnitTestAll`

If changes touch UI/resources/build config/dependencies, also run:

- `./gradlew :app:lintDebugAll`

## Do not run (cloud environment limits)
Avoid emulator/device-required tasks:
- `connectedAndroidTest`, `connectedDebugAndroidTest`, etc.

## Triggering a snapshot build from a cloud environment

If your sandbox does not allow `gh workflow run` (authentication scope, network restrictions, etc.):

1. Push your branch and open a PR as normal.
2. Note in the PR description that a snapshot build is needed and name the branch.
3. The repo maintainer will dispatch **Snapshot Build** against that branch manually from the Actions tab.

Do not fail silently — surface the request in the PR description so the maintainer can act.

## Notes
- Prefer Debug tasks (avoid release signing requirements).
- Keep changes focused and avoid unrelated formatting churn.
