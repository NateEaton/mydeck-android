# Cross-repo port methodology — MyDeck ⇄ Readeck for Android

**Status:** Durable, reusable process doc. Not tied to any one feature.
**Scope:** MyDeck and Readeck for Android are **sibling apps built from the same codebase**. Readeck for Android was forked from MyDeck at a known commit (see §0.2 for the anchor), and — deliberately — **they still share the exact same Kotlin source package (`com.mydeck.app`)**. This was done partly as an homage to MyDeck's heritage, but mostly to make porting between them trivial. A fix or feature landed in one frequently needs porting to the other; this doc is the direction-agnostic harness for doing that.

> **Keep this doc mirrored in BOTH repos.** Either repo can be the source of a change. When you edit this methodology, copy it to the other repo.

---

## 0. Fixed facts — the ground truth (DO NOT re-derive these)

The two apps are the same codebase with a thin branding skin. Internalize this before porting; it means **the default outcome of a port is "the files are byte-identical except a small, closed set of branding differences."**

1. **Same source package: `com.mydeck.app` in BOTH repos, on purpose.** The `namespace`, the `com/mydeck/app/...` directory tree, and every `package`/`import` are identical across the two apps. **There is never a package/import rewrite.** (`applicationId` differs — see §1 — but that is build config, not source.)
2. **Shared git history — the fork point is a known commit (the anchor).** Readeck for Android was forked from MyDeck at the last common ancestor, **MyDeck commit `2923470`** ("Archived specs for features and fixes in v0.14.0", 2026-06-05). Readeck's own history begins one commit later at **`a570865`** ("Rebrand as Readeck for Android", 2026-06-08), which only skins branding on top. Use `2923470` as the common-ancestor anchor. Because the rebrand touched only branding, files untouched by it remain **byte-identical** in both repos to this day — so a source diff applies cleanly and unchanged files copy verbatim.
3. **The default port is: copy byte-identical, then patch the branding.** Assume every changed/new source file copies as-is. The only edits are the enumerated branding points in §2. Do not go re-confirming package roots, channel ids, task names, or locale sets — they are fixed (§1) and identical.
4. **The ONE thing that needs real work: data-model / Room changes.** These are NEVER copied. Regenerate the model/migration in the TARGET at its own DB version and regenerate its schema JSON (§4). Everything else is copy-plus-branding.
5. **Cross-repo git is available.** The repos are local siblings; add the other as a remote to cherry-pick across them (§3). Do not conclude "no shared history" just because `git merge-base` fails across two separate checkouts — fetch first.

If a port ever turns out NOT to match these facts (e.g. a source file is unexpectedly different from the target baseline), that is a signal worth surfacing — not something to silently work around.

---

## 1. Repo reference table (filled — look up, don't discover)

Assign roles per port (§ Roles below). To port, read the **TARGET's** column. These values are stable; update this table only if a repo actually changes one.

| Axis | MyDeck | Readeck for Android |
|---|---|---|
| Source package (`namespace`, dirs, `package`/`import`) | `com.mydeck.app` | `com.mydeck.app` **(same — never rewrite)** |
| `applicationId` | `com.mydeck.app` | `org.readeck.apps.android` |
| Build flavors | `githubSnapshot`, `githubSnapshotHttp`, `githubRelease`, `githubReleaseHttp` | `snapshot`, `stable` |
| Aggregate verify tasks | `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll` | **same names** |
| Install on device | `scripts/install-phone.sh` | `scripts/install-phone.sh` **(same)** |
| Notification channel ids / internal constants | `FullSyncNotificationChannelId` etc. | **identical (same codebase)** |
| Locale folders | `values/` + `-de-rDE, -es-rES, -fr, -gl-rES, -pl, -pt-rPT, -ru, -uk, -zh-rCN` | **same set** |
| `app_name` / `appLabel` | "MyDeck" | "Readeck" |
| OAuth client constants (`OAuth*UseCase`: `CLIENT_URI`, `SOFTWARE_ID`, `CLIENT_NAME`) | `https://github.com/NateEaton/mydeck-android`, `com.mydeck.app`, `"MyDeck Android — …"` | `https://codeberg.org/readeck/readeck-android`, `org.readeck.apps.android`, `"Readeck for Android — …"` |
| OAuth redirect scheme (custom-scheme features) | `mydeck://…` | `readeck://…` |
| Host / `origin` remote | **GitHub** — `git@github.com:NateEaton/mydeck-android.git` (+ `upstream` jensomato/ReadeckApp, `readeck-local`, `codeberg`) | **Codeberg** — `git@codeberg.org:readeck/readeck-android.git` |
| CI / release automation | **GitHub Actions** — `.github/workflows/{checks,release,snapshot}.yml`; snapshot builds dispatched from the Actions tab | **Local** — no GitHub Actions; verification + builds run via `scripts/{ci-verify,build-snapshot,build-release,verify-release}.sh` (Codeberg host) |
| Fork anchor (common ancestor) | `2923470` "Archived specs … v0.14.0" (2026-06-05) | first rebrand commit `a570865` "Rebrand as Readeck for Android" (2026-06-08) |

## 2. Branding transform points — the ONLY things that ever change

This is a **closed list**. If a diff touches something not on it, it is repo-agnostic logic and copies verbatim.

- `applicationId` and the **flavor block** (names/suffixes) in `app/build.gradle.kts`.
- `app_name` / `appLabel` / launcher icons / store copy / colors.
- **OAuth client-registration constants** (`CLIENT_URI`, `SOFTWARE_ID`, `CLIENT_NAME`) — use the TARGET's values from §1.
- Any **brand-prefixed URL or custom URI scheme** (e.g. `mydeck://` ⇄ `readeck://`). Prefer sourcing these from `BuildConfig`/`manifestPlaceholders` so the port changes one place.
- **User-guide + CHANGELOG wording** (the app is named in prose) — adapt to the target's name.

Everything else — logic, DTOs, ViewModels, Compose UI, tests, DAOs, workers, string *keys* — is identical and copies byte-for-byte.

**Never ports across repos** (per-repo infra, not branding): CI config (MyDeck's `.github/workflows/*` has no Readeck counterpart — Readeck verifies via local `scripts/*.sh`), Room schema versions + migrations (§4), signing/keystore config. A source change that only touches these does not port at all; a change that touches them *and* logic ports only the logic.

## 3. Roles + cross-repo git

- **SOURCE** = repo the change landed in (read-only; never edit). **TARGET** = repo you're porting into (the one you're working in). Neither is permanently MyDeck or Readeck.
- To cherry-pick or read source commits from the TARGET, add the sibling as a local remote **with tag-following disabled** and fetch:
  `git remote add --no-tags <source> <path-or-url> && git fetch <source>` (a local path like `/Users/nathan/development/MyDeck` works). Then `git cherry-pick <sha>` or `git show <sha>:<path>`.
  **The `--no-tags` flag is mandatory.** A default fetch auto-follows the source repo's tags into the target's shared tag namespace (release tags, archive/* tags, backups — dozens of refs that don't belong there), and `git remote remove` does not remove fetched tags. If a fetch ever does import stray tags, do not bulk-delete by pattern: list them explicitly and have the maintainer approve the exact deletion command.
- Because the source package is identical (§0), cherry-picks apply with no path/package fixups — only the §2 branding edits and any §4 model regeneration remain.
- **Remove the remote once done.** A remote added solely to cherry-pick for a port is scratch, not durable repo config — once the port is complete (committed/PR proposed), `git remote remove <source>`. Exception: if another port from the same source is already queued in the same session, leave it until the last queued port from that source completes, then remove it. After removing the remote, verify no tags came over: `git tag -l` should show no refs originating from the source repo.

## 4. Data-model / migration reconciliation (the only real gotcha)

Room DB versions and migration registration are **repo-specific and never copied**:
- A migration added in SOURCE as `vN → vN+1` maps to a **different version number** in TARGET — renumber it to TARGET's next version.
- **Regenerate** the TARGET's exported schema JSON for its new version (don't copy SOURCE's `N.json`).
- Register the migration in TARGET's database module; add/port its migration test.
- Confirm the added columns/tables don't already exist under a different name in TARGET.

If a port has no schema change, this section is a no-op — skip it entirely.

## 5. Port procedure

1. **Identify** SOURCE change set (branch/PR/commit range) and read its diff.
2. **Copy** the changed/new files (verbatim — cherry-pick per §3, or copy the source-branch version of each file since baselines are identical per §0.2). New files drop in as-is; modified files' baselines match, so the source version is a clean superset.
3. **Apply the §2 branding edits** — the only edits. For `build.gradle.kts` and `AndroidManifest.xml`, do NOT copy wholesale (flavors/applicationId/manifest entries differ); merge the added blocks.
4. **Regenerate models** (§4) if any schema changed.
5. **Localize:** insert the new string *keys* into the TARGET's `values/` + locale files surgically. Never `git checkout <ref> -- strings.xml` (clobbers independently-added keys). Keys and English values port as-is.
6. **Docs:** port guide + CHANGELOG additions in the target's wording.
7. **Verify:** `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll` green (same names in both). On-device check via `scripts/install-phone.sh`, especially anything touching workers/scheduler/migrations.
8. **Commit/PR** per the target's conventions. Per repo rules, **propose** state-changing git/PR commands for the maintainer rather than running them.

## 6. Per-feature port checklists

This methodology is the reusable harness. Each concrete port gets a short checklist (e.g. `docs/porting/<feature>-port.md`) listing that feature's commits, files, and the specific §2 branding values used — finalized against the shipped source code. Seed it from any inline "keep repo-agnostic / flag divergence" notes the source author left.

**Once a port is complete, the checklist doc must exist in BOTH repos** — same rule as this methodology doc (see the note at the top of this file). Copy the finalized checklist into the other repo's `docs/porting/` alongside it. It's fine to leave that copy uncommitted if the other repo has its own pending doc cleanup in flight — just don't skip the copy.

## 7. Start-of-port checklist (most boxes are pre-answered by §0–§1)

- [ ] TARGET = the repo I'm in; SOURCE located and read-accessible (add as remote if cherry-picking, §3).
- [ ] Source change set identified (branch/PR/commit range).
- [ ] Confirmed the changed source files match §0.2 (source base == target current) — if not, surface it.
- [ ] Does the change touch the data model? If yes → §4 (regenerate). If no → skip §4.
- [ ] Branding values for the TARGET pulled from §1.
