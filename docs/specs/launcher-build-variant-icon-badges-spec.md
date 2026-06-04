# Mini Spec: Launcher Build Variant Icon Badges

## Status

Draft. Keep untracked until the native reader footer branch is merged and this
work is ready to branch from `main`.

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

### Minimal snapshot-only version

Add adaptive icon overrides for snapshot flavors:

```text
app/src/githubSnapshot/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/githubSnapshot/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/githubSnapshot/res/drawable/ic_launcher_foreground_snapshot.xml
app/src/githubSnapshot/res/drawable/ic_launcher_badge_snapshot.xml

app/src/githubSnapshotHttp/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/githubSnapshotHttp/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/githubSnapshotHttp/res/drawable/ic_launcher_foreground_snapshot.xml
app/src/githubSnapshotHttp/res/drawable/ic_launcher_badge_snapshot.xml
```

The snapshot adaptive icon XML can keep the existing background and swap only
the foreground:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_snapshot" />
</adaptive-icon>
```

`ic_launcher_foreground_snapshot.xml` can be a layer-list that combines the
existing foreground with a badge drawable:

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@mipmap/ic_launcher_foreground" />
    <item
        android:bottom="12dp"
        android:drawable="@drawable/ic_launcher_badge_snapshot"
        android:gravity="bottom|end"
        android:right="12dp"
        android:width="36dp"
        android:height="36dp" />
</layer-list>
```

This keeps the base asset shared and makes the badge easy to inspect in review.
Before implementing, verify the layer-list renders correctly inside adaptive
icons on API 26+ and through Android Studio's Image Asset preview.

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

## Open Questions

- Should `githubSnapshotHttp` use exactly the same snapshot badge as
  `githubSnapshot`, relying on the app label for HTTP?
- Should API 24-25 fallback icons be badged in the first pass?
- Should debug builds get a visual treatment, or is snapshot/release enough?
- What badge color best contrasts with the current icon while still fitting
  MyDeck's visual identity?
