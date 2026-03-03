# Codex Agent Instructions (MyDeck Android)

Follow the project rules in `CLAUDE.md`, especially:
- Localization: any new/changed string resources must be added (English placeholders) to all language `strings.xml` files.
- Documentation: user-visible changes must be documented in the English user guide under `app/src/main/assets/guide/en/`.

## Build Variants
- **Primary Check:** Use `githubSnapshotDebug` for general compilation checks.
- **Release Check:** Use `githubReleaseDebug` to verify release-specific logic without signing.

## Verification Commands
- **Check Compilation:** `./gradlew assembleGithubSnapshotDebug`
- **Run Unit Tests:** `./gradlew testGithubSnapshotDebugUnitTest`
- **Check Lint:** `./gradlew lintGithubSnapshotDebug`
- **Room Schema Check:** `./gradlew kspDebugKotlin` (Verifies Room/Hilt code generation)

## Environment Notes
- Java Version: 17
- Android SDK: 35
- Internet access is ONLY available during the environment setup phase. All dependencies are pre-cached in `~/.gradle`.
- If a build fails due to missing signing keys, fallback to the `Debug` variant of the flavor.

If changes touch UI/resources/build config/dependencies, also run:

- `./gradlew :app:lintDebug`

## Do not run (cloud environment limits)
Avoid emulator/device-required tasks:
- `connectedAndroidTest`, `connectedDebugAndroidTest`, etc.

## Additional Notes
- Prefer Debug tasks (avoid release signing requirements).
- Keep changes focused and avoid unrelated formatting churn.