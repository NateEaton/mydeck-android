# Mini Spec: Launcher Build Variant Icon Badges

## Status

Implemented on branch `feat/launcher-snapshot-badge` (commit `dc54313f`).
First pass covers adaptive icons only; legacy density fallbacks for
API 24-25 deferred.

## Context

MyDeck currently uses one launcher icon resource for every build:

- Manifest: `android:icon="@mipmap/ic_launcher"`
- Adaptive icon XML:
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Density fallback assets:
  - `app/src/main/res/mipmap-*/ic_launcher.webp`
  - `app/src/main/res/mipmap-*/ic_launcher_round.webp`

The build already has four product flavors:

- `githubRelease` - label `MyDeck`
- `githubReleaseHttp` - label `MyDeck HTTP`
- `githubSnapshot` - label `MyDeck Snapshot`
- `githubSnapshotHttp` - label `MyDeck HTTP Snapshot`

The labels and application IDs distinguish installed variants in settings and
app info, but the launcher grid can still show visually similar icons. The main
need is to make snapshot builds distinguishable from release builds at a glance.

## Goals

- Keep the release icon unchanged.
- Make snapshot builds visually distinct in the Android launcher.
- Prefer a design that remains legible at launcher icon size.
- Avoid runtime code. Launcher icons are build-time resources.
- Keep the first implementation small and reviewable.

## Non-goals

- Rebranding the base MyDeck icon.
- Changing notification small icons.
- Changing app labels, package names, signing, or update behavior.
- Building a full icon-generation pipeline unless multiple combined overlays
  become necessary.

## Recommendation

Start with source-set resource overrides for snapshot flavors only:

- `app/src/githubSnapshot/res/...`
- `app/src/githubSnapshotHttp/res/...`

Use the same resource names as `main` so Android resource merging selects the
snapshot icon automatically for snapshot variants. This avoids manifest or
Gradle logic for the first version.

The snapshot visual treatment should be simple:

- Add a filled corner badge, dot, or ribbon.
- Use one high-contrast color that is not part of the normal release icon.
- Avoid small text such as `SNAPSHOT`; it will not read reliably.
- A single large `S` is possible, but a shape/color cue is more robust.

HTTP-specific icon treatment should be deferred unless testing shows the label
alone is not enough. If added later, use a second broad cue such as a different
badge color or background treatment. Avoid encoding both `SNAPSHOT` and `HTTP`
as text inside the icon.

## Resource Strategy

### Shared snapshot source set (as implemented)

To avoid duplicating badge assets in both snapshot flavor dirs, the resources
live in a single shared source set wired into both `githubSnapshot` and
`githubSnapshotHttp` via `sourceSets`:

```kotlin
// app/build.gradle.kts
sourceSets {
    getByName("debug").assets.srcDirs(files("$projectDir/schemas"))
    getByName("githubSnapshot").res.srcDir("src/snapshotShared/res")
    getByName("githubSnapshotHttp").res.srcDir("src/snapshotShared/res")
}
```

Files:

```text
app/src/snapshotShared/res/values/colors.xml
app/src/snapshotShared/res/drawable/ic_launcher_badge_snapshot.xml
app/src/snapshotShared/res/drawable/ic_launcher_foreground_snapshot.xml
app/src/snapshotShared/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/snapshotShared/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

The snapshot adaptive icon XML keeps the existing background and swaps only the
foreground:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_snapshot" />
</adaptive-icon>
```

`ic_launcher_foreground_snapshot.xml` is a layer-list combining the existing
foreground with the badge drawable:

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@mipmap/ic_launcher_foreground" />
    <item
        android:gravity="bottom|end"
        android:bottom="27dp"
        android:right="27dp"
        android:width="18dp"
        android:height="18dp"
        android:drawable="@drawable/ic_launcher_badge_snapshot" />
</layer-list>
```

The badge is a small yellow (`#FFEB3B`) filled circle with a thin dark
(`#1A1A1A`) stroke, sized 18dp at 27dp insets so its center sits at (72, 72)
on the 108dp adaptive canvas and the whole shape stays inside the 66dp safe
zone across all launcher mask shapes.

The original 36dp/12dp sizing from the initial draft was too large in
on-device review; halving to 18dp and re-centering on the same point
produced a cleaner notification-dot read at launcher icon size.

### Legacy fallback assets

MyDeck has `minSdk = 24`, so API 24-25 devices use the density-specific legacy
launcher assets instead of adaptive icons. There are two reasonable options:

- Generate snapshot `mipmap-*/ic_launcher.webp` and
  `mipmap-*/ic_launcher_round.webp` assets for complete API 24-25 coverage.
- Accept that API 24-25 launcher icons remain unbadged in the first pass.

Given current device targets, complete fallback coverage is better but not
strictly required for the Pixel 9/manual snapshot testing path.

## Optional Future: Generated Icons

If MyDeck later needs separate visual states for `snapshot`, `http`, and
`debug`, hand-maintaining every combination will get noisy. At that point, add
a small checked-in script or Gradle task that generates icon assets from:

- the base release icon,
- a snapshot badge definition,
- an HTTP badge/background definition,
- an optional debug badge definition.

Generated assets should be deterministic and committed, or generated as part of
the build with clear verification. Do not make the build depend on network
downloads or nonstandard local tools.

## Implementation Outline

1. Branch from freshly pulled `main`.
2. Choose final snapshot badge shape and color.
3. Add snapshot flavor resource overrides.
4. If supporting API 24-25 fully, add matching density fallback assets.
5. Build at least:
   - `./gradlew :app:assembleGithubSnapshotDebug`
   - `./gradlew :app:assembleGithubReleaseDebug`
6. Inspect APK resources or install both variants and verify:
   - release launcher icon is unchanged,
   - snapshot launcher icon has the badge,
   - HTTP snapshot uses the intended treatment,
   - notification small icon is unchanged.
7. Run normal UI/resource verification:
   - `./gradlew :app:assembleDebugAll`
   - `./gradlew :app:testDebugUnitTestAll`
   - `./gradlew :app:lintDebugAll`

## Resolved Decisions

- **Shared badge across both snapshot flavors.** `githubSnapshotHttp` uses the
  same badge as `githubSnapshot`; the app label disambiguates HTTP. Enforced
  structurally via the shared `snapshotShared/res` source set, so the two
  flavors cannot drift.
- **API 24-25 fallbacks deferred.** Legacy density `mipmap-*/ic_launcher.webp`
  overrides not added in the first pass. Current testing targets (Pixel 9) do
  not need them. Revisit if older devices enter the testing rotation.
- **No separate debug treatment.** Snapshot vs release is the meaningful
  visual distinction; adding a debug badge would be noise.
- **Badge color: yellow `#FFEB3B`.** High contrast against the deep teal
  (`#1E5F5E`) background, no overlap with the existing icon palette. A thin
  `#1A1A1A` stroke keeps it legible against light wallpapers and themed-icon
  surfaces.
