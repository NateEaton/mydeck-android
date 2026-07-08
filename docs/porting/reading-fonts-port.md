# Port checklist — Reading-view fonts

Per-feature checklist under the harness in [`mydeck-readeck-port.md`](./mydeck-readeck-port.md).
Direction-agnostic, finalized against the shipped source in **Readeck for Android** (where the
feature landed first). For a Readeck→MyDeck port, Readeck is SOURCE.

## Commits (SOURCE = Readeck for Android, branch `feat/reading-fonts`)

Nine commits, oldest → newest:

- `a6d42d7` reading-view fonts — native set, picker, licenses
- `695209a` fix: register FontLicensesRoute in the expanded (tablet) nav graph
- `3938af1` flip reader fonts to opt-in native set; picker debug logging
- `8802c93` wider coverage — Latin-ext + Cyrillic, real bold; Gentium 7.000
- `2584d3e` docs: coverage/fidelity analysis (decision record)
- `7e5b324` docs: font-picker redesign spec
- `8d504e4` font picker redesign — dedicated Select font sheet
- `c737834` top-of-list drag resistance (superseded by `b05d29e`)
- `b05d29e` refine font picker — chip cloud, scroll-safe dismiss, shared sheet height

Because there are 9 commits with heavy **binary font churn** (files added then re-added across
commits) and an internally-superseded step (`c737834`), **prefer copying the final file states
over cherry-picking the range** (methodology §5.2). Baselines are identical (§0.2), so the
source-branch version of each code file drops in clean. Strings/CHANGELOG/guide stay surgical.

**No schema change → §4 is a no-op, skip it.**

## §2 branding values used: **NONE**

Still zero branding edits — call it out so a porter doesn't invent one:

- The setting label **"Include native Readeck fonts"** and every "Readeck" in the
  strings/changelog/guide refer to the **Readeck server's reader** (both apps talk to it) —
  *not* the Android app's brand. Port **verbatim**; do **not** rename to "MyDeck".
- The diff touches no `applicationId`, flavors, `AndroidManifest.xml`, `build.gradle.kts`,
  OAuth constants, URI schemes, `app_name`, icons, or colors.

## Gotchas

1. **No rebrand** — "Readeck" here is the server, not the app.
2. **`AppShell.kt` has two nav graphs (compact + expanded/tablet).** `FontLicensesRoute` must
   be registered in **both** — commit `695209a` fixed missing the expanded one, which crashed
   **About → Font licenses** on tablets (`IllegalArgumentException: Destination with route
   FontLicensesRoute cannot be found`). **Verify on a tablet emulator.**
3. **Font assets are ~80 subset woff2 files**, named `<slug>-<subset>-<weight>.woff2`
   (subset ∈ latin / latin-ext / cyrillic; weight 400 or 500 for Cormorant, plus 700 bold).
   `WebViewTypographyBridge` builds the `@font-face` src from that exact naming — **copy the
   whole `assets/fonts/` dir verbatim; do not rename and do not re-subset** (the files are the
   deliverable). Re-subsetting is only needed if re-sourcing, which the port does not do.

## File manifest (final state)

**Copy verbatim — code (identical baselines):**
- `domain/model/TypographySettings.kt` (enum + `fileSlug`/`hasCyrillic`/`regularWeight`, `FontVisibility` CORE/ALL, ordered lists)
- `io/prefs/SettingsDataStoreImpl.kt`
- `ui/detail/ReaderSettingsBottomSheet.kt` (typography sheet + Select font sheet)
- `ui/detail/TypographyUtils.kt`
- `ui/detail/WebViewTypographyBridge.kt` (multi-subset/weight `@font-face` generation)
- `ui/navigation/Routes.kt`
- `ui/settings/UiSettingsScreen.kt`, `ui/settings/UiSettingsViewModel.kt`
- `ui/about/AboutScreen.kt`, `ui/about/AboutViewModel.kt`
- `ui/shell/AppShell.kt` *(both nav graphs — gotcha 2)*
- **New:** `ui/settings/FontLicensesScreen.kt`

**Copy verbatim — tests:**
- `test/.../io/prefs/TypographySettingsTest.kt`
- `test/.../ui/detail/WebViewTypographyBridgeTest.kt`
- `test/.../ui/settings/UiSettingsViewModelTest.kt`

**Binary assets — replace the whole `app/src/main/assets/fonts/` set:**
- **Delete** MyDeck's existing bundled woff2 (incl. `noto-serif-regular.woff2`,
  `source-serif-4-regular.woff2`, and any old single-weight files).
- **Copy in** the SOURCE's **80** subset files (latin/ext/cyrillic × regular/bold for the
  15 families; the 10 cyrillic-capable ones carry a cyrillic subset). Simplest: clear the dir
  and copy the source dir wholesale.

**Surgical — strings (`values/` + 9 locale folders); never `git checkout` these:**
- **Add 7 keys** (English identical in all 10 files — copy exact values from SOURCE
  `values/strings.xml`): `ui_settings_font_visibility_title`,
  `ui_settings_font_visibility_description`, `about_font_licenses`,
  `about_font_licenses_subtitle`, `font_licenses_intro`, `select_font_title`,
  `select_font_done`.
- **Remove 6 now-unused keys** from all 10 files (grep first): `font_system_default`,
  `font_noto_serif`, `font_literata`, `font_source_serif`, `font_noto_sans`,
  `font_jetbrains_mono`.

**Surgical — docs (merge into target's existing files; wording ports as-is):**
- `CHANGELOG.md` — the reading-fonts `## [Unreleased] → Added` entries.
- `assets/guide/en/reading.md` — the **Font** paragraph (current-font chip → Select font sheet).
- `assets/guide/en/settings.md` — the **Include native Readeck fonts** bullet in Reading.

**Optional — SOURCE decision records** (`docs/specs/reading-fonts-coverage-and-fidelity.md`,
`docs/specs/font-picker-redesign.md`): porter's discretion; reference Readeck branch names.

## Feature summary (what "done" looks like)

- **Default font set (CORE):** System Default, Literata, Cantarell, Cormorant Garamond,
  Recursive, Bitter, Gentium, Old Standard, JetBrains Mono. The **Include native Readeck
  fonts** setting (Settings → UI → Reading, **off by default**) adds Lora, Public Sans,
  Merriweather, Inter, IBM Plex Serif, Luciole, Atkinson Hyperlegible.
- **Coverage:** latin + latin-ext + cyrillic subsets, **real Bold (700)**; italic stays faux.
  Cyrillic on 10 fonts (not Cantarell/Recursive/Public Sans/Atkinson/Luciole). CJK → system.
- **Picker:** typography sheet shows the current font as a chip → opens a **Select font**
  bottom sheet (FlowRow chip cloud, each font in its own typeface, live-apply, Done/swipe to
  close, bottom fade for overflow). Both reader sheets share one fixed height
  `min(340dp, 90% × screen)`.
- **Font licenses** page under **About**.

## Verification (§7)
- `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll` — all green
  (new keys must exist in all 10 locale files or `MissingTranslation` fails).
- `scripts/install-phone.sh`: current-font chip → Select font sheet, live-apply, real **bold**
  in article body, the **Include native Readeck fonts** toggle.
- **Cyrillic/Polish spot check** (e.g. a Russian and a Polish Wikipedia article): a
  cyrillic-capable font renders in-font; a latin-only font falls back cleanly (no tofu).
- **Tablet emulator:** **About → Font licenses** opens without crashing (gotcha 2), and the
  reader sheets look right in portrait *and* landscape (shared fixed-height model).

## Notes carried from the SOURCE build
- Cormorant Garamond's normal weight is **Medium (500)** (Regular reads too light); bold is 700.
- **Gentium is SIL Gentium 7.000** (family renamed from the older "Gentium Plus"). Luciole is
  **CC BY 4.0** (only non-OFL font); all others **SIL OFL 1.1** — attributions in
  `FontLicensesScreen`.
- Font display names are the enum `displayName` values (brand nouns), intentionally not string
  resources.
- Deferred (not part of this port): an app-wide bottom-sheet consistency pass (animate
  button-dismiss + a single close/back convention) — tracked separately.
