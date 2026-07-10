# User Guide Rework — Specification (DRAFT, untracked)

**Status:** Implemented (shipped in v0.14.5 — see the `## [0.14.5]` section of `CHANGELOG.md`).
**Author context:** Produced from a top-to-bottom review of the bundled guide (`app/src/main/assets/guide/en/`) and its in-app renderer (`app/src/main/java/com/mydeck/app/ui/userguide/`).
**Applies to:** MyDeck first; then ported to the Readeck for Android guide with branding differences (see §9).

---

## 1. Purpose & Scope

The guide has grown piecemeal across releases. Symptoms the user identified:
- Very dense pages with minimal navigation.
- Feature pages (Highlights, Collections) buried "several layers deep."
- Long, self-contained topics embedded as sections inside first-level pages.
- No way to search the guide for a specific feature.

This spec covers: (a) page/section/subsection reorganization, (b) specific passage moves, (c) content to remove, (d) at least a remedial search feature, (e) revalidation of the top-level TOC, and (f) mirroring all of it into the Readeck guide with branding differences.

**Out of scope:** rewriting correct content for its own sake. The goal is structure, findability, and de-duplication — not a prose overhaul.

---

## 2. Current-State Findings

### 2.1 Navigation architecture (the core problem)

| Finding | Evidence | Impact |
|---|---|---|
| **In-app TOC is hardcoded**, not data-driven | `MarkdownAssetLoader.DEFAULT_SECTIONS` lists exactly 5: Getting Started, Your Bookmarks, Reading, Organizing, Settings | Highlights & Collections are **absent from the TOC** |
| **`index.md` is unused by the app** | `UserGuideIndexViewModel` → `loadSections()` returns `DEFAULT_SECTIONS`; `index.md` is never read in-app | `index.md` (edited for the website) has silently diverged from the app's real TOC |
| **Sub-pages reachable only via inline links** | Markwon `linkResolver` navigates `*.md` links (`MarkdownRenderer.kt`); Highlights/Collections are linked only from body text in organizing.md / reading.md / your-bookmarks.md | "Several layers deep" — no direct path from the TOC |
| **No in-page navigation** | Each page renders as one long scrolling `TextView` (`UserGuideSectionScreen.kt`) | Long pages (reading.md ~171 lines, your-bookmarks.md ~247 lines) are a single unbroken scroll; no jump-to-section, no anchors |
| **No search** | No search UI or index anywhere in `ui/userguide/` | Users cannot look up a feature by name |
| **Linked-page titles derived from filename** | `onSectionNavigate` resolves title from `DEFAULT_SECTIONS`, else derives from filename | Works for now but fragile; another reason to make the section list data-driven |

### 2.2 Content duplication & redundancy

| Topic | Documented in | Problem |
|---|---|---|
| **Collections** | your-bookmarks.md §Collections (committed by C3) **and** new collections.md | **Direct duplication** — must dedupe (see §8) |
| Highlights | reading.md §Highlights (create), organizing.md §Highlights (pointer), highlights.md (full) | Overlap between reading.md create-steps and highlights.md |
| Offline / Pinned content | settings.md §Offline Reading (detailed), your-bookmarks.md §Download Status + §Selecting Multiple (Pin), reading.md §Overflow (Pin) | Scattered; no single home |
| Delete / Undo mechanics | your-bookmarks.md (Card Actions, Swipe, Multi-select), organizing.md §Deleting Bookmarks, reading.md §Overflow | Same snackbar/Undo behavior re-explained verbatim 4–5× |
| HTTP/HTTPS server security | getting-started.md §Connecting, settings.md §Server URL Security | Overlapping explanations |
| Swipe actions | your-bookmarks.md §Swipe Actions, settings.md §Bookmark List → Swipe | Behavior vs configuration split, with repetition |

### 2.3 Over-long embedded sections (candidates for their own page)

- **your-bookmarks.md §Searching and Filtering** (lines ~155–199) — long, self-contained, applies beyond the basic list.
- **your-bookmarks.md §Collections** (lines ~201–215) — already half-promoted; finish the job.
- **your-bookmarks.md §Selecting Multiple Bookmarks** (lines ~217–232) — long; conceptually "acting on many bookmarks."
- **settings.md §Offline Reading** (lines ~33–64) — a substantial standalone feature living as a Settings subsection.

---

## 3. Target Information Architecture — **AGREED: Option B, adjusted**

We're taking **Option B** — keep the long first-level pages intact (do **not** extract Searching/Filtering into a separate page, and do **not** move Multi-select out of Your Bookmarks) — with two adjustments from review:

- **Promote Labels to a top-level page.** For consistency with Highlights and Collections: all three are major features with their own surfaces, so Labels shouldn't stay buried inside Organizing while the other two are surfaced. Extract the Labels section from organizing.md into its own page; Organizing keeps a one-line pointer.
- **Split Offline Reading.** A top-level **Offline Reading** page covers the *concept and its interactions with bookmarks and content* — download-status card indicators, pinning, automatic-vs-pinned. The **Settings** page retains the *control-by-control detail* (enable toggle, storage limits, Wi-Fi-only, battery saver, storage stats, Clear All), cross-linked. Boundary: **concept + interactions → Offline Reading page; controls → Settings.** Net win: the download-status and pin explanations are currently duplicated across Your Bookmarks and Reading, so consolidating them here *removes* duplication.

### Target top-level TOC (data-driven; see §7)

1. **Getting Started** — connect, sign in, first load, sign out/switch servers. *Single home for the HTTP/HTTPS story.*
2. **Your Bookmarks** — list & cards, layouts, reading progress, download status, card actions, swipe, sort, long-press, **filtering** (stays here), **multi-select** (stays here), tablet/landscape.
3. **Reading** — reading view, bookmark types, view formats, typography, find-in-page, links, lightbox, details. Highlights trimmed to a brief create + pointer.
4. **Labels** — promoted; extracted from Organizing.
5. **Highlights** — promoted (already its own file).
6. **Collections** — promoted; single source (dedupe per §8).
7. **Organizing** — Favorites, Archive, Delete (Labels removed).
8. **Offline Reading** — concept + bookmark/content interactions; Settings keeps the controls.
9. **Settings** — Account, Synchronization (bookmark sync + offline **controls**), User Interface, Sharing, Logs; heavy topics (offline detail, security) reduced to short pointers to their home page.

*(About stays a drawer destination, not a guide page.)*

The **Labels → Highlights → Collections** order mirrors the "Tools" group in the navigation drawer, so the guide's feature pages read in the same order the user sees them in the app.

**Deliberately NOT done under B:** extracting Searching/Filtering into a "Finding Bookmarks" page; moving Multi-select into Organizing. Both stay in Your Bookmarks.

**Naming (resolved):** with Labels gone, "Organizing" holds Favorites/Archive/Delete — **keep the name "Organizing"** (least churn).

---

## 4. Specific Passage Moves

| Passage (current) | From | To | Notes |
|---|---|---|---|
| §Labels (whole section) | organizing.md | **Labels** (new page) | Extract; Organizing keeps a one-line pointer |
| §Collections | your-bookmarks.md | **Collections** page | Delete the embedded section; collections.md is the single source |
| §Offline Reading concept + download-status indicators + pin mechanics | settings.md, your-bookmarks.md §Download Status, reading.md §Overflow (Pin) | **Offline Reading** (new page) | Consolidate concept + interactions here (also de-dupes existing scatter) |
| Offline **control** detail (toggles, storage limits, Wi-Fi/battery, Clear All) | settings.md (stays) | Settings §Offline (controls only) | Cross-link to Offline Reading page |
| HTTP/HTTPS detail | duplicated in getting-started.md & settings.md | **Getting Started** (canonical) | settings.md §Server URL Security → short pointer |
| Highlights create-steps | reading.md §Highlights | keep brief in Reading; full detail in **highlights.md** | Reduce reading.md to "how to create + link to Highlights" |

*Staying put under B:* §Searching and Filtering and §Selecting Multiple Bookmarks remain in your-bookmarks.md.

---

## 5. Content to Remove / Trim

- **Repeated Undo/snackbar explanations.** Explain the delete→greyed→snackbar→Undo model once (Organizing §Deleting), then reference it. Applies to card delete, swipe delete, multi-select delete, collection delete.
- **Duplicated Pin-offline paragraphs** across your-bookmarks.md and reading.md — collapse to one home (Offline Reading) + short call-outs.
- **The Highlights drawer bullet** (already trimmed on `feat/collections`) is the template: drawer/list entries should be one-line "what it is," with mechanics living on the feature page. Apply the same discipline to any bullet that grew a "how it works" tail.
- **settings.md §Offline Reading "maximum storage cap" paragraph** — dense wall; break into a short rule + example when it moves to the Offline Reading page.
- **index.md prose Contents list** — once the app TOC is data-driven and index.md's role is settled (§7), remove or regenerate it so it can't drift again.

---

## 6. Search Feature

**Requirement:** "at least a remedial search." All guide content is local markdown assets, so search is fully offline and cheap.

### 6.1 Remedial (minimum viable)
- Add a **search field** to the User Guide index screen (`UserGuideIndexScreen`), or a search icon in its top bar.
- On query, load every section's markdown (`MarkdownAssetLoader` gains a `loadAllSections()` that returns `{section, headings[], body}`), strip frontmatter/markdown, and do a case-insensitive substring match over **headings first, then body**.
- Present results as a list of **(page title → matching heading / snippet)** rows; tapping a row opens that page (existing section route).
- Build a small in-memory index once (Singleton loader can cache parsed content); no persistence needed.

### 6.2 Enhancement path (optional, later)
- Scroll-to-match: pass the matched heading anchor into the section route and scroll the `TextView` to it (requires heading-offset tracking; Markwon renders to a single `TextView`, so this needs measuring or switching to a structured renderer).
- Rank results (heading match > first-paragraph > body).
- Highlight the matched term in the opened page.

### 6.3 Localization
- New search UI strings must be added to all 10 locale `strings.xml` files with English placeholders (per project l10n rule). Guide *content* stays English-only for now (translations handled separately).

---

## 7. TOC Mechanism Fix (revalidation)

The TOC must become **data-driven and complete** so features stop being orphaned:

1. **Single source of truth.** Either (a) derive `DEFAULT_SECTIONS` from `index.md`'s `TOC:` frontmatter at load time, or (b) keep a Kotlin list but treat it as the authoritative TOC and regenerate/remove `index.md` accordingly. Pick one so app and website cannot diverge again. *Recommendation: parse `index.md` frontmatter → the website and app share it.*
2. **Add the missing entries** (in drawer order): Labels, Highlights, Collections, then Offline Reading.
3. **Order** per §3.
4. **Icons:** `getSectionIcon()` in `UserGuideIndexScreen` currently hardcodes icons per filename — extend for the new pages, or move icon choice into the section data.
5. **Consider light grouping** (e.g., a divider between "Using MyDeck" and "Configuration") — optional; the flat list is acceptable if complete.

---

## 8. Immediate Cleanup on `feat/collections` (do before this branch's doc commit)

This branch currently has **two** Collections docs:
- **your-bookmarks.md §Collections** (committed by C3) — embedded, matches the current flat structure.
- **collections.md** (new, uncommitted, this session) — standalone page + `index.md`/`organizing.md` pointers.

They must not both ship. **Decision (resolved):** for **this branch**, keep Collections as the **embedded section in your-bookmarks.md** (matches today's flat structure, zero code change). **Drop** the standalone `collections.md` and its `index.md` / organizing.md pointers. The promotion of Collections to a standalone top-level page is deferred to the guide rework (§3), where it lands alongside Labels/Highlights as a proper page with a `DEFAULT_SECTIONS` entry.

Everything else from this session (Highlights bullet trim, the your-bookmarks.md Collections **drawer bullet**, README feature line, highlights.md refresh note) is orthogonal and ships as-is.

---

## 9. Cross-Repo: Readeck Guide

The **same structural rework** applies to the Readeck for Android guide. Follow the port harness (`docs/porting/mydeck-readeck-port.md`) — structure ports; branding does not.

### 9.1 Branding / terminology deltas (do not port verbatim)
- **App name:** "MyDeck" → "Readeck".
- **List terminology:** confirm Readeck's naming (e.g. "My List" vs "Deck"/"Unread") and use the target's terms.
- **HTTP-enabled APK story:** MyDeck-specific packaging; confirm Readeck's equivalent (may differ or not exist).
- **Tailscale / reverse-proxy example, package ids, store copy, links, icons:** target's own.
- **Locale set:** confirm Readeck's supported languages before assuming the same 10.

### 9.2 Feature-availability deltas
- **Collections is not yet ported to Readeck.** The Readeck reorg should **reserve the Collections slot in the TOC structure but not add Collections content** until the feature lands. When Collections ports, drop in the (re-branded) collections.md and flip on its TOC entry — no re-architecting.
- Any other MyDeck-only feature pages should be gated the same way.

### 9.3 Timing
- The user will revisit this spec just before finalizing the Readeck release (v1.0.0 per current plan; confirm at execution). Apply the app-side TOC/search code changes and the content reorg together so both repos land structurally aligned.

---

## 10. Future-Proofing

Features are landing around this rework (nav drawer enhancements, offline-content changes, the Collections port). To avoid re-introducing the same mess:

- **Data-driven TOC (§7) is the key enabler** — new feature pages become a one-line addition, not a code+doc archaeology exercise.
- **One-topic-one-home rule:** each feature has a single canonical page; other pages link to it rather than re-explaining.
- **Nav-drawer enhancements will change the "Navigating" section** (custom collection-backed views, an Offline view, reorderable entries). The guide's drawer description must be updated when that ships — and because Collections/Offline will then be first-class drawer entries, their guide pages should already exist (this rework creates them).
- **Bullet discipline:** navigation/list bullets stay one-line; mechanics live on feature pages (the Highlights-bullet fix is the pattern).

---

## 11. Execution Phasing & Validation

**Phase 1 — TOC + search plumbing (code):**
- Make `DEFAULT_SECTIONS` data-driven / reconcile with `index.md`; add missing pages; extend `getSectionIcon`.
- Add remedial search (§6.1) + locale strings.
- Verify: `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll`; device-check the guide TOC, deep links, and search.

**Phase 2 — content reorg (assets):**
- Create/relocate pages per §3–§4; dedupe per §5; fix the Collections duplication per §8.
- English `en/` only; translations handled separately.

**Phase 3 — Readeck port:**
- Apply §9 with branding deltas; reserve/omit not-yet-ported feature pages.

**Validation checklist:**
- Every TOC entry opens; every inline `.md` link resolves.
- No topic documented in two places as primary content.
- Search finds each feature by name and opens the right page.
- Longest pages have a clear heading structure (even without in-page anchors).
- `index.md` (website) and the app TOC agree.
```
