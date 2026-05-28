# Spec: Fix `latest-snapshot` tag-move failure in snapshot publish

## Status

Proposal — written 2026-05-28, not yet implemented.

## Context

Phase 2 of the [GitHub Actions release-lanes rework](github-actions-release-lanes-v0132-spec.md)
landed on `main` in PR #169. After the merge, the `Snapshot Build` workflow
ran successfully and the `publish-latest-snapshot` job completed without
errors — but the moving `latest-snapshot` GitHub Release had only
`checksums.txt` attached, no APK. The scheduled nightly run at 11:04 UTC
the same day produced the same outcome. The APK was preserved as the run's
workflow artifact (Actions tab → `tester-snapshot-release.zip`), so the
build itself was fine; the asset just never made it onto the Release.

Two independent latent bugs in the publish job are responsible:

1. **The `ncipollo/release-action` artifacts glob did not match the APK.**
   Identified and addressed in PR #170 (`fix/ci-snapshot-apk-publish`,
   merged 2026-05-28). The fix changed the glob from `./artifacts/*.apk`
   to `./artifacts/**/*.apk`.
2. **The `joutvhu/create-tag@v1` step refuses to move the
   `latest-snapshot` tag when the existing ref points at a different
   commit than the new one.** This is the present spec.

The second bug surfaced only after the first was fixed: the PR #170 merge
into `main` was the first publish-run since 2026-05-28 03:49 UTC that tried
to move the `latest-snapshot` tag to a commit different from the one the
tag was already at. That run failed with `Reference already exists`, the
publish step never executed, and the Release was therefore not updated —
including the new APK glob path that PR #170 was meant to validate.

### Why the PR #170 fix is still correct but did not produce a visible
### change

The recursive-glob change is sound: with `./artifacts/**/*.apk`, the
release-action step *would* match the APK at
`./artifacts/app/build/outputs/apk/githubSnapshot/release/MyDeck-….apk`.
The reason the change did not produce an observable difference on the
`latest-snapshot` release is that the publish job aborted **before** the
release-action step:

- `Set up job` — ok
- `Download Snapshot Artifact` — ok
- `Compute Source Line` — ok
- `Update Latest Snapshot Release` (`joutvhu/create-tag@v1`) — **failed
  with "Reference already exists"**
- `Publish to GitHub Releases` (`ncipollo/release-action`) — never ran
- `Complete job`

PR #170 should be kept; without it, the publish step would still fail to
match the APK even once the tag-move step is fixed.

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
- **Tag exists and points at a different SHA** → the action attempts to
  POST a new ref, the GitHub API responds `422 Reference already exists`,
  and the action fails the step.

The third case is exactly the one we hit every time `main` advances and the
`latest-snapshot` tag needs to follow. We hit it cleanly on PR #170's
post-merge run (run 26576183210): the tag was at `8addcdc` from the
previous publish, the new merge commit was `12d30b2`, and the action
errored on the move.

The 2026-05-28 03:49 UTC `docs:` commit publish run is the only one today
that observed the action doing a successful tag move (from a stale 2026-04
commit to `30f82dd`). That single success appears to have been because the
prior tag state had not been refreshed by an earlier publish run on the
same day, and the action's update path happens to work in that
configuration. The behaviour is inconsistent enough that it cannot be
relied on.

## Goals

- Restore reliable tag movement for the moving `latest-snapshot` ref so
  that every post-merge and scheduled publish run lands the new APK on the
  Release.
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
calls the GitHub REST API directly using `gh api`. The new step does
exactly two things:

1. If `refs/tags/latest-snapshot` does not exist, create it pointing at
   `github.sha`.
2. If it does exist, force-update it to point at `github.sha`.

This is the behaviour the `joutvhu/create-tag@v1` step was supposed to
provide. Doing it inline removes the third-party action and makes the
semantics explicit in the workflow file.

```yaml
- name: Move latest-snapshot tag
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    REF="refs/tags/latest-snapshot"
    REPO="${{ github.repository }}"
    SHA="${{ github.sha }}"
    if gh api "repos/${REPO}/git/${REF}" >/dev/null 2>&1; then
      gh api -X PATCH "repos/${REPO}/git/${REF}" \
        -f "sha=${SHA}" -F "force=true"
    else
      gh api -X POST "repos/${REPO}/git/refs" \
        -f "ref=${REF}" -f "sha=${SHA}"
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
- The probe (`gh api .../git/${REF}`) returns 200 if the ref exists and
  404 otherwise. The conditional avoids attempting a PATCH against a
  non-existent ref (which would return 422 and obscure the actual state).
- No annotated tag object is created. `joutvhu/create-tag@v1` was
  optionally creating an annotated tag with `message: latest-snapshot`,
  but for a moving prerelease marker, a lightweight ref is enough and
  matches the semantics of the snapshot model (there is no per-release
  changelog to embed in the tag).

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
     should show either `gh api -X PATCH …` (tag move) or `gh api -X
     POST …` (first-time create) followed by a 200/201 response payload.
   - Reach `Publish to GitHub Releases` and exit 0.
   - Not log `##[warning]Artifact pattern :./artifacts/**/*.apk did not
     match any files`.
2. `gh api repos/NateEaton/mydeck-android/git/refs/tags/latest-snapshot`
   should return the fix-PR merge commit SHA, not `8addcdc` or any
   earlier commit.
3. `gh release view latest-snapshot --json assets` should show **two**
   assets: a `MyDeck-<timestamp>-<sha>.apk` and `checksums.txt`. Both
   should have `created_at` timestamps within seconds of the run's
   `Publish to GitHub Releases` step.
4. The release body's source line should identify the fix-PR merge:
   `**Source:** main push @ <short-sha> — <UTC timestamp>`.
5. The next scheduled nightly run (07:23 UTC the following day) should
   succeed identically and update the release body to
   `**Source:** scheduled nightly @ <short-sha> — <UTC timestamp>`.
   Note that on schedule runs where `main` has not advanced, the tag-move
   step will hit the PATCH path with `sha == current ref.object.sha` —
   this is still a valid PATCH and should no-op cleanly on the API side.

If post-merge step 3 still shows only `checksums.txt`, the
`ncipollo/release-action` glob path needs further investigation. The
current hypothesis is that PR #170's recursive glob is sufficient and was
simply never exercised due to the upstream tag-move failure.

## Risks and notes

- **Third-party action removed.** This removes `joutvhu/create-tag@v1`
  from `snapshot.yml`. Phase 3's SHA-pinning list shrinks by one entry.
  No other workflow uses it.
- **Lightweight vs. annotated tag.** The new ref is lightweight (no tag
  object, no message body). Anyone running `git show latest-snapshot`
  will see the underlying commit directly rather than a tag object with
  the `latest-snapshot` message. This matches normal CI moving-tag
  practice and has no functional consequence for the Release.
- **Force-move is intentional.** The `force=true` on PATCH is required
  for any non-fast-forward move, which is the common case here whenever
  `main` advances. Restricting this would prevent the publish job from
  doing its job.
- **No keystore exposure change.** This step is in
  `publish-latest-snapshot`, which has never had access to signing
  secrets. The §3/§8 Phase 2 protections are not affected.

## Out of scope

- Replacing or pinning `ncipollo/release-action`. That belongs in Phase 3.
- Any change to `checks.yml` or `release.yml`. Phase 3 will revisit
  `release.yml`'s tagging and prerelease handling.
- Any change to the build-job-side of `snapshot.yml`.
