# Spec: Fix `latest-snapshot` tag-move failure in snapshot publish

## Status

Proposal — written 2026-05-28, updated after run 26578706175 showed
the existing tag action can sometimes move the tag successfully.

## Context

Phase 2 of the [GitHub Actions release-lanes rework](github-actions-release-lanes-v0132-spec.md)
landed on `main` in PR #169. After the merge, the `Snapshot Build` workflow
ran successfully and the `publish-latest-snapshot` job completed without
errors — but the moving `latest-snapshot` GitHub Release had only
`checksums.txt` attached, no APK. The scheduled nightly run at 11:04 UTC
the same day produced the same outcome. The APK was preserved as the run's
workflow artifact (Actions tab → `tester-snapshot-release.zip`), so the
build itself was fine; the asset just never made it onto the Release.

That first visible missing-APK symptom was caused by the
`ncipollo/release-action` artifacts glob not matching the downloaded APK.
PR #170 (`fix/ci-snapshot-apk-publish`, merged 2026-05-28) addressed that
by changing the glob from `./artifacts/*.apk` to `./artifacts/**/*.apk`.

PR #170 then exposed a separate latent publish fragility: its post-merge
`main` push run failed before the release-action step, in
`joutvhu/create-tag@v1`, with `Reference already exists`. That meant the
new APK glob path was not exercised by that run, and the Release still did
not visibly change.

A later `main` push run, 26578706175 at commit `9011613`, succeeded with
the unchanged `joutvhu/create-tag@v1` step and published both the APK and
`checksums.txt`. That later success confirms PR #170's recursive glob is
correct. It also means the tag action failure is not a deterministic "every
tag move fails" bug. The remaining issue is that the third-party tag action
has opaque and fragile ref-probe behaviour: when it misclassifies an
existing tag as missing, it attempts to create a ref that already exists and
aborts the publish job.

### Why the PR #170 fix was correct but did not produce an immediate
### visible change

The recursive-glob change is sound: with `./artifacts/**/*.apk`, the
release-action step *would* match the APK at
`./artifacts/app/build/outputs/apk/githubSnapshot/release/MyDeck-….apk`.
The reason the PR #170 merge run did not produce an observable difference
on the `latest-snapshot` release is that the publish job aborted **before**
the release-action step:

- `Set up job` — ok
- `Download Snapshot Artifact` — ok
- `Compute Source Line` — ok
- `Update Latest Snapshot Release` (`joutvhu/create-tag@v1`) — **failed
  with "Reference already exists"**
- `Publish to GitHub Releases` (`ncipollo/release-action`) — never ran
- `Complete job`

PR #170 should be kept; without it, the publish step would still fail to
match the APK. It has now been validated by run 26578706175, which uploaded
both `MyDeck-20260528T134819-9011613.apk` and `checksums.txt` to the
moving Release.

## Current behaviour

The publish job has two ref-management steps:

```yaml
- name: Update Latest Snapshot Release
  uses: joutvhu/create-tag@v1
  with:
    tag_name: latest-snapshot
    tag_sha: ${{ github.sha }}
    message: latest-snapshot
    on_tag_exists: update
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

- name: Publish to GitHub Releases
  uses: ncipollo/release-action@v1.16.0
  with:
    allowUpdates: true
    commit: ${{ github.sha }}
    tag: latest-snapshot
    # ...
```

`joutvhu/create-tag@v1` exists as a separate step because
`ncipollo/release-action` has historically had inconsistent behaviour when
updating an existing release whose underlying git tag needs to move to a
new commit. Splitting tag management out into a dedicated action was an
explicit decision in the original snapshot workflow.

Observed behaviour of `joutvhu/create-tag@v1` `on_tag_exists: update`:

- **Tag does not exist** → creates the tag at `tag_sha`. Works.
- **Tag exists and already points at `tag_sha`** → no-op. Works.
- **Tag exists and points at a different SHA, and the action's existence
  probe succeeds** → force-updates the ref. This worked in run
  26578706175, moving `latest-snapshot` from `8addcdc` to `9011613`.
- **Tag exists, but the action's existence probe fails or misclassifies the
  state as missing** → the action attempts to create a new ref, the GitHub
  API responds `422 Reference already exists`, and the action fails the
  step.

The last case is exactly what the PR #170 post-merge run hit. The failure
was not PR-mode specific: PR workflows never execute `publish-latest-snapshot`.
A PR merge creates a normal `push` event on `main`, and that publish path is
shared with scheduled nightly runs. Any publish run that hits the action's
bad ref-probe path while `latest-snapshot` already exists can fail the same
way.

The action source explains why this failure mode is plausible: its
existence check catches all `getRef` errors and returns `false`. The caller
then follows the create path. If the underlying error was anything other
than a true 404/not-found response, the action turns an unknown probe
failure into a create-ref attempt against an existing ref.

Because the same action also moved the tag successfully on a later `main`
push, the safest conclusion is not that `joutvhu/create-tag@v1` cannot move
tags. It can. The problem is that its failure handling is too opaque for a
publish-critical moving tag, and a single intermittent probe failure aborts
the Release update.

## Goals

- Restore reliable tag movement for the moving `latest-snapshot` ref so
  that post-merge `main` pushes and scheduled publish runs land the new APK
  on the Release.
- Keep the publish job small, explicit, and free of third-party actions
  whose behaviour we cannot reason about from the workflow alone.
- Avoid relying on `ncipollo/release-action`'s tag-handling behaviour
  (which was the reason `joutvhu/create-tag@v1` was added in the first
  place).
- Make the change a small, surgical fix — not a publish-job rewrite.

## Non-goals

- Changing the `ncipollo/release-action` step itself (PR #170 already
  fixed its artifacts glob; nothing else there is wrong).
- Changing the build job, triggers, fork guard, retention rules, keystore
  cleanup, or anything else from Phase 2.
- Pinning third-party actions by SHA — that is Phase 3 territory.

## Proposed change

Replace the `joutvhu/create-tag@v1` step with an inline shell step that
calls the GitHub REST API directly using `gh api`. The new step does three
explicit things:

1. Probe `latest-snapshot`.
2. If the ref exists, force-update it to point at `github.sha`.
3. If and only if the probe confirms a 404/not-found state, create
   `refs/tags/latest-snapshot` pointing at `github.sha`.

Any other probe failure is treated as a real failure and stops the job.
This is the important behaviour change from `joutvhu/create-tag@v1`: an
auth, network, rate-limit, or unexpected API error must not be converted
into a create-ref attempt that obscures the actual state.

This is the behaviour the `joutvhu/create-tag@v1` step was supposed to
provide for the happy path, with stricter failure handling. Doing it inline
removes the third-party action and makes the semantics explicit in the
workflow file.

```yaml
- name: Move latest-snapshot tag
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    set -euo pipefail
    REF="tags/latest-snapshot"
    FULL_REF="refs/${REF}"
    REPO="${{ github.repository }}"
    SHA="${{ github.sha }}"

    err="${RUNNER_TEMP}/latest-snapshot-ref.err"
    if gh api "repos/${REPO}/git/ref/${REF}" >/dev/null 2>"${err}"; then
      echo "latest-snapshot exists; moving to ${SHA}"
      gh api -X PATCH "repos/${REPO}/git/refs/${REF}" \
        -f "sha=${SHA}" -F "force=true"
    elif grep -q "HTTP 404" "${err}"; then
      echo "latest-snapshot is missing; creating at ${SHA}"
      gh api -X POST "repos/${REPO}/git/refs" \
        -f "ref=${FULL_REF}" -f "sha=${SHA}"
    else
      cat "${err}" >&2
      exit 1
    fi
```

Notes on the implementation:

- `GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}` authenticates `gh api`. No
  additional permissions are required beyond the
  `permissions: contents: write` already declared on the
  `publish-latest-snapshot` job.
- `gh api -X PATCH .../git/refs/tags/latest-snapshot` with `force=true`
  is the documented GitHub API call for moving an existing ref to a new
  commit
  ([Update a reference](https://docs.github.com/en/rest/git/refs#update-a-reference)).
  `-F "force=true"` passes a JSON boolean rather than a string.
- `gh api -X POST .../git/refs` with `ref=refs/tags/latest-snapshot` and
  `sha=<commit>` is the documented call for creating a new tag ref
  ([Create a reference](https://docs.github.com/en/rest/git/refs#create-a-reference)).
- The probe (`gh api .../git/ref/tags/latest-snapshot`) returns 200 if the
  ref exists and 404 if it does not. Only the 404 case enters the create
  path. Any other failed probe is surfaced as the real failure.
- No annotated tag object is created. `joutvhu/create-tag@v1` creates a tag
  object on its missing-tag path, but its ref creation still points directly
  at `tag_sha`. The public `latest-snapshot` ref is therefore already a
  lightweight moving marker; the inline step just avoids creating an unused
  tag object.

The step replaces `Update Latest Snapshot Release` in `snapshot.yml`. The
`Publish to GitHub Releases` step (`ncipollo/release-action`) is left
unchanged. Its `tag: latest-snapshot` and `commit: ${{ github.sha }}`
inputs will see the ref already in the correct state and update the
release row accordingly.

## Validation

Because this is a publish-time fix, only main pushes and scheduled runs
exercise it. The PR's own PR-mode run does not (publish-latest-snapshot
is gated to `push to main` or `schedule`).

Pre-merge: workflow YAML parses, no other steps in `snapshot.yml` are
touched, no reference to `joutvhu/create-tag` remains anywhere in the
workflow.

Post-merge:

1. The fix-PR merge to `main` creates a new commit; the publish run for
   that commit should:
   - Complete `Move latest-snapshot tag` without error. The run log
     should show either `latest-snapshot exists; moving to <sha>` or
     `latest-snapshot is missing; creating at <sha>`, followed by the
     corresponding API response.
   - Reach `Publish to GitHub Releases` and exit 0.
   - Not log `##[warning]Artifact pattern :./artifacts/**/*.apk did not
     match any files`.
2. `gh api repos/NateEaton/mydeck-android/git/refs/tags/latest-snapshot`
   should return the fix-PR merge commit SHA.
3. `gh release view latest-snapshot --json assets` should show **two**
   assets: a `MyDeck-<timestamp>-<sha>.apk` whose short SHA matches the
   fix-PR merge commit, and `checksums.txt`. Both should have `created_at`
   timestamps within seconds of the run's `Publish to GitHub Releases`
   step.
4. The release body's source line should identify the fix-PR merge:
   `**Source:** main push @ <short-sha> — <UTC timestamp>`.
5. The next scheduled nightly run (07:23 UTC the following day) should
   succeed identically and update the release body to
   `**Source:** scheduled nightly @ <short-sha> — <UTC timestamp>`.
   Note that on schedule runs where `main` has not advanced, the tag-move
   step will hit the PATCH path with `sha == current ref.object.sha` —
   this is still a valid PATCH and should no-op cleanly on the API side.

If post-merge step 3 regresses to only `checksums.txt`, that is a new
release-action/artifact-path problem. The current baseline already proves
PR #170's recursive glob is sufficient: run 26578706175 published both the
APK and checksum assets.

## Risks and notes

- **Third-party action removed.** This removes `joutvhu/create-tag@v1`
  from `snapshot.yml`. Phase 3's SHA-pinning list shrinks by one entry.
  No other workflow uses it.
- **No annotated tag object.** The inline step only creates or updates the
  ref. This matches the effective current behaviour of `latest-snapshot`,
  whose ref points directly at the commit SHA rather than at the annotated
  tag object created by the action's missing-tag path.
- **Force-move is intentional.** The `force=true` on PATCH is required
  for any non-fast-forward move, which is the common case here whenever
  `main` advances. Restricting this would prevent the publish job from
  doing its job.
- **Fail closed on unknown probe errors.** The create path is only valid
  after a confirmed 404. This prevents a transient `getRef` failure from
  becoming another `Reference already exists` failure.
- **No keystore exposure change.** This step is in
  `publish-latest-snapshot`, which has never had access to signing
  secrets. The §3/§8 Phase 2 protections are not affected.

## Out of scope

- Replacing or pinning `ncipollo/release-action`. That belongs in Phase 3.
- Any change to `checks.yml` or `release.yml`. Phase 3 will revisit
  `release.yml`'s tagging and prerelease handling.
- Any change to the build-job-side of `snapshot.yml`.
