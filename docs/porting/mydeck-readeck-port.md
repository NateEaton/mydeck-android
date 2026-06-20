# Cross-repo port methodology — MyDeck ⇄ Readeck for Android

**Status:** Durable, reusable process doc. Not tied to any one feature.
**Scope:** MyDeck (`com.mydeck.app`) and Readeck for Android (`org.readeck.apps.android`) are sibling apps that share most of their offline/sync/create/reader code. A fix or feature landed in one frequently needs to be ported to the other. This doc is the **direction-agnostic** harness for doing that; per-feature port checklists are separate, short docs that this harness produces.

> **Keep this doc mirrored in BOTH repos.** Either repo can be the source of a change, so whichever one you're working in should carry an up-to-date copy. When you edit this methodology, copy it to the other repo.

---

## 1. Roles (not fixed repos)

A port has two roles. **Neither is permanently MyDeck or Readeck** — assign them per port:
- **SOURCE** — the repo where the change already landed (the commits/PR you are porting *from*).
- **TARGET** — the repo you are modifying (porting *into*).

The first port of the offline-content rework was SOURCE = MyDeck, TARGET = Readeck (the issues surfaced in MyDeck). Future ports will often be the reverse (SOURCE = Readeck). Do not hard-code a direction.

## 2. Setup assumption (the assisting agent)

You (the assisting model) are running **in the TARGET repo** — that is the working copy you edit, build, and verify. You have **read access to the SOURCE repo** (a sibling checkout, or via `gh`/`git` against its remote). You read the source change there; you never edit it.

**Confirm before touching code:**
1. Which repo am I in? → that is the **TARGET**.
2. Where is the **SOURCE** repo, and what is the exact change set? (branch, PR number, or commit range.)
3. Is the port a **cherry-pick** or a **manual re-apply**? (§4.)
4. Fill the **divergence map** (§3) with the *actual* values read from each repo — do not assume.

If any of these is unclear, ask before proceeding.

## 3. Divergence map (the transform points)

These differ between the repos and must be transformed when code crosses either direction. **Read the real values from each repo at port time — the table below names the *axes*, with known MyDeck values; confirm the counterpart in the other repo rather than guessing.**

| Axis | MyDeck | Readeck for Android |
|---|---|---|
| `applicationId` | `com.mydeck.app` | `org.readeck.apps.android` |
| Source package root (`com/.../...` dirs + `package`/`import`) | `com.mydeck.app` | *(confirm in repo)* |
| Build flavors / variant task names | `githubSnapshot`, `githubSnapshotHttp` (e.g. `assembleGithubSnapshotDebug`) | *(confirm — do NOT copy MyDeck flavor names)* |
| Aggregate verify tasks | `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll` | *(confirm — these "All" tasks may be MyDeck-only)* |
| Notification channel ids | e.g. `SYNC_NOTIFICATION_CHANNEL_ID = "FullSyncNotificationChannelId"` | *(confirm)* |
| Branding strings/assets (`app_name`, icons, store copy) | "MyDeck" + icons | "Readeck" + upstream branding — **never port verbatim** |
| Localized string sets | `values/` + the project's locale folders | *(confirm the locale set matches; it may differ)* |
| Install/run on device | `scripts/install-phone.sh` (+ project's device setup) | *(confirm the target's run path)* |
| Remotes | `origin NateEaton/mydeck-android` (+ upstream) | *(confirm)* |

## 4. Cherry-pick vs manual re-apply

Decide once per port:
- **Cherry-pick** — viable only if the repos share enough git history that the source commits apply with mostly path/package fixups. Faster. After applying, you still run the §6 transforms (package rewrite, channel ids, flavors, strings, branding) and reconcile migrations (§5).
- **Manual re-apply** — when histories have diverged: re-implement the change file-by-file in the target, using the source diff as the spec. Slower but unavoidable when cherry-pick produces noise.

Determine viability by checking shared history between SOURCE and TARGET (`git log`/merge-base if both are reachable). When in doubt, manual re-apply of the *logic* + transforms is the safe default.

## 5. Schema / migration reconciliation (highest-risk gotcha)

Room DB versions and migration registration are **repo-specific**:
- A migration added in SOURCE as `vN → vN+1` almost certainly maps to a **different version number** in TARGET (its current DB version differs). Renumber it.
- Regenerate the TARGET's exported schema JSON for its new version (don't copy SOURCE's `N.json`).
- Register the migration in the TARGET's database module and add/port its migration test.
- Verify the column/table additions don't already exist under a different name in TARGET.

## 6. Port procedure (direction-agnostic)

1. **Enumerate** the SOURCE change set (commits/files). Use any inline "keep repo-agnostic / flag divergence" notes the source author left (they mark exactly the transform points).
2. **Classify each change:** *repo-agnostic logic* (applies verbatim) · *divergence-touching* (package refs, channel ids, flavors, manifest, branding, strings — transform via §3) · *target-specific* (UI/branding that legitimately differs — adapt, don't copy).
3. **Apply** the logic (cherry-pick or manual), then run the §3 transforms.
4. **Reconcile migrations** (§5) if any schema changed.
5. **Re-localize:** add the new strings to the TARGET's string set, in *its* locale folders. **Do not** `git checkout <ref> -- strings.xml` wholesale — that restores the entire file from the source tree and silently drops strings the target added independently. Insert the specific new keys surgically.
6. **Docs:** port user-guide updates into the TARGET's guide (its wording/branding).
7. **Verify:** the TARGET's build/test/lint (its task names, not MyDeck's) green; on-device check the ported behavior where feasible (especially anything touching workers/scheduler/migrations).
8. **Commit/PR** in the TARGET per its conventions. Per repo rules, propose state-changing git/PR commands for the maintainer rather than running them.

## 7. Recurring gotchas (the durable lessons)

- **Renumber + regenerate migrations** (§5) — never copy a version number across repos.
- **Channel ids and flavor/variant task names differ** — don't copy `githubSnapshotDebug` or a MyDeck channel constant verbatim.
- **Branding strings/assets** (`app_name`, store copy, icons) are *intentional* divergences — never port.
- **Package/import rewrite** across the whole moved set (`com.mydeck.app.*` ⇄ the target root).
- **Manifest divergence** — permissions and foreground-service types may already differ; merge, don't overwrite.
- **Surgical string inserts**, never whole-file restores from another tree (clobbers independently-added keys).
- **Locale set may differ** — confirm the target's languages before assuming the same N files.

## 8. Per-feature port checklists

This methodology is the reusable harness. Each concrete port gets its own short checklist (e.g. `docs/porting/offline-content-rework-port.md`) listing that feature's commits, files, and specific divergences — **finalized against the shipped code** (after the source PR merges), and seeded from the inline divergence flags the source author left while the work was fresh.

## 9. Start-of-port checklist (copy/run this each time)

- [ ] Confirm TARGET = the repo I'm in; SOURCE located and read-accessible.
- [ ] Source change set identified (branch/PR/commit range).
- [ ] Cherry-pick vs manual re-apply decided (§4).
- [ ] Divergence map (§3) filled with real values from both repos.
- [ ] TARGET current DB version + migration registration point identified (§5).
- [ ] TARGET build/test/lint task names + on-device run path identified.
- [ ] Localization locale set in TARGET confirmed.
