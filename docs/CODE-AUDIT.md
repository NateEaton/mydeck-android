# CODE-AUDIT.md

A specification for tasking an independent LLM agent to audit a repository for
"AI slop" — and, just as importantly, to *not* drown a maintainer in noise when
the codebase is already reasonably sound.

The core design problem this addresses: an unconstrained "tell me what's wrong"
prompt produces a maximalist report because the agent has no cost model for the
reviewer's time and no threshold for what is worth flagging. The fix is to bake
the rigor into the specification itself — define severity, require evidence, cap
volume, and force a triage judgment rather than a catalog.

---

## 1. Purpose and Stance

The audit exists to answer one question:

> **Can a human developer adopt and maintain this code without first paying down
> a hidden debt of machine-generated cruft?**

The agent is not a perfectionist linter. It is a senior engineer doing a
pre-adoption review on a codebase *presumed to be competent*. Its default
assumption is that the code is fine. It flags only deviations a thoughtful human
maintainer would genuinely want addressed. Stylistic preferences, hypothetical
improvements, and "you could also" suggestions are out of scope.

The goal is to support a community of human-maintained applications: code should
be adoptable, comprehensible, and refinable incrementally toward zero slop.

---

## 2. Definition of "Slop"

To prevent scope creep, slop is defined narrowly as artifacts characteristic of
low-rigor or unreviewed machine generation, falling into these categories:

| # | Category | What it is | Example signal |
|---|---|---|---|
| 1 | **Dead / orphan code** | Unreachable functions, unused imports/vars, commented-out blocks left in place, functions never called | Defined-but-never-referenced symbols |
| 2 | **Redundant implementation** | Multiple functions doing the same thing; reinvented stdlib/library functionality; copy-paste variants that should be one parameterized unit | Three near-identical helpers |
| 3 | **Poorly developed algorithms** | Needlessly quadratic loops, repeated recomputation, naive approaches where an idiomatic one is obvious and warranted | Nested loops over the same collection |
| 4 | **Inappropriate quoting / attribution** | Verbatim copied code without license/attribution; hardcoded snippets that look lifted from docs or forums; license-incompatible inclusions | Comment fragments, unusual style islands |
| 5 | **Hallucinated / vestigial scaffolding** | Config for unused services, env vars never read, abstractions with one implementation, "future-proofing" layers wrapping nothing | An interface with a single impl and no DI |
| 6 | **Comment slop** | Comments restating the code, narrating the obvious, or describing work the agent *intended* rather than what the code does | `// increment i by 1` |
| 7 | **Inconsistent conventions** | Mixed naming/error-handling/structure suggesting stitched-together generations | camelCase and snake_case in one module |
| 8 | **Phantom robustness** | Try/catch that swallows everything, defensive checks for impossible states, validation of already-validated input | `catch (e) {}` |

**Note on category 5 (vestigial scaffolding) in actively-developed projects.**
This category misfires most easily on early-stage code. An empty file, a stub
service, a single-implementation interface, or unused config may be deliberate
groundwork for a planned phase rather than slop. When the project shows signs of
active development (recent commits, a roadmap, phase markers in docs), the agent
must frame such findings as *"is this planned scaffolding or abandoned?"* rather
than recommending deletion outright. Default to the charitable reading; flag the
ambiguity instead of asserting it is dead.

Anything not reducible to one of these is **out of scope** and must not be
reported. A genuine bug that isn't a slop pattern goes in a separate, clearly
labeled "Incidental" section and does not count toward the slop assessment.

---

## 3. Severity Model

Every finding carries exactly one severity. The agent must assign these
honestly; inflation defeats the purpose.

- **S1 — Adoption blocker.** Maintainer would reject the code until fixed (e.g.,
  uncredited license-incompatible code, an algorithm that won't scale to stated
  use).
- **S2 — Should fix before incremental work.** Actively impedes comprehension or
  future change.
- **S3 — Worth a cleanup pass.** Real but low-risk; batch it.
- **S4 — Note only.** Mention once as a pattern; do not enumerate every instance.

---

## 4. Evidence Requirement

No finding without evidence. Each must include file path, line range, the
offending snippet (≤10 lines), and a one-sentence justification tied to a
§2 category. **Speculation is prohibited** — if the agent cannot point at code,
it cannot make the claim. Phrases like "there may be" or "consider whether" are
disallowed.

### 4.1 Verification Confidence (filesystem-less runs)

Some findings depend on a *negative* claim — "this symbol is never called,"
"this file is unused," "dynamic injection makes these static values redundant."
These are only as reliable as the agent's ability to see the whole codebase.

- When the agent has tool access (e.g., Claude Code, Codex), it must verify such
  claims with an actual repo-wide search before flagging, and exclude public API,
  entrypoints, reflection targets, and string-referenced symbols.
- When the agent works only from a static bundle (e.g., a Repomix copy in Google
  AI Studio) and cannot run tools, any finding that recommends **deletion** must
  be marked `[verify]` and phrased as a candidate, not a certainty. The agent
  states the basis ("not referenced anywhere in this bundle") and notes that a
  local `grep`/`rg` confirmation is required before acting. The bundle may have
  excluded files; absence from it is not proof of absence from the repo.

The cost of a wrong deletion recommendation is real, so the bar for asserting
"dead" or "redundant" without verification is higher than for any other finding.

---

## 5. Volume Controls

This is the key anti-slop-report mechanism.

- **Hard cap:** At most **25 itemized findings** total. If more exist, report the
  25 highest-severity and state the estimated remainder per category as a count,
  not a list.
- **Pattern collapsing:** Repeated instances of one pattern are a *single*
  finding with a representative example and an occurrence count — never N
  separate entries.
- **No-finding is a valid outcome:** If the code is clean, the correct report is
  short and says so. Returning few or zero findings is a **success state**, not a
  failure to try hard.

### 5.1 Cost-Bounded Execution and the Duplication Gate

To keep audits inside token/rate limits, the agent screens cheaply first and may
**early-exit** with a verdict once it is confident no S1/S2 slop exists, instead
of running the full deep pass. But early-exit has one hard precondition:

> **The agent may NOT early-exit until cross-file redundancy (category 2) has
> been screened across the WHOLE repo, not just the audited slice.**

This gate exists because redundancy is the one category that hides *between*
files the slice never opens — duplicated helpers, copy-pasted blocks, repeated
UI logic. A slice review and per-file tooling will miss it. Acceptable ways to
satisfy the gate, in order of preference:

1. A repo-wide copy-paste detector (`jscpd`, `pmd cpd`, or equivalent) run in the
   tooling pass.
2. If no such tool is present or installable, an explicit whole-repo structural
   scan for duplication (e.g., grouping files by similar size/structure and
   comparing candidate clusters) — and the report must state that duplication was
   checked manually rather than by tooling, so the reader knows the confidence
   level.

Only after the gate is satisfied and zero S1/S2 candidates remain may the agent
short-circuit. If the gate cannot be satisfied at all, the agent says so and does
not claim category 2 is Clean — it marks it "Not fully verified."

### 5.2 Tooling Consent

Before installing or running any third-party analysis tool (linters, dead-code
finders, duplication detectors), the agent asks the user once, listing the
specific tools it proposes and why. The user may approve, decline, or limit to
read-only/no-install tools. If declined, the agent falls back to built-in search
(`rg`/`grep`) and manual structural checks, and notes in the report that
tool-based verification was unavailable — which lowers confidence on the
negative-claim and duplication categories accordingly. The agent never installs
tooling silently.

---

## 6. The Verdict

The report opens with a one-line verdict from a fixed set, so the result is
actionable at a glance:

- **CLEAN** — Adopt as-is. No slop above S4.
- **MINOR** — Adopt; schedule a short cleanup pass (est. effort given).
- **MODERATE** — Address S1/S2 before building on it.
- **HIGH** — Likely developed without rigor; recommend a structured remediation
  plan rather than incremental fixes.

Effort is always expressed as a rough human estimate (e.g., "~2 hrs", "~1 day")
so the reviewer can decide before reading the detail.

---

## 7. Report Structure (fixed)

1. **Verdict** + one-paragraph rationale + total effort estimate
2. **Health table:** per-category status (Clean / Minor / Issues) + count
3. **Itemized findings**, sorted by severity descending (≤25)
4. **Incidental** non-slop bugs (clearly separated, optional)
5. **Remediation order** (the sequence to address findings, not a rewrite)

---

## 8. Reference Agent Prompt

The prompt below is the portable, agent-neutral version. Tool-specific variants
(Claude Code, Google AI Studio, Codex) wrap this same core.

```
You are a senior software engineer performing an adoption review of a code
repository. Your task is to assess whether this codebase contains "AI slop"
— artifacts of low-rigor or unreviewed machine generation — to the degree
that a human maintainer would hesitate to adopt and incrementally maintain it.

OPERATING ASSUMPTION
Assume this code was written by a competent engineer until evidence shows
otherwise. Your default is that it is fine. You are screening for deviations
worth a human's attention, not cataloguing every imperfection. A short report
with few findings is a SUCCESS, not a failure. Do not invent work.

SCOPE — flag ONLY these slop categories:
  1. Dead / orphan code (unreachable, unused, commented-out, never-called)
  2. Redundant implementation (duplicate logic, reinvented library functions)
  3. Poorly developed algorithms (needless complexity, obvious worse choice)
  4. Inappropriate quoting/attribution (uncredited copied code, license issues)
  5. Hallucinated / vestigial scaffolding (config, abstractions wrapping nothing)
     - In actively-developed projects, empty stubs / single-impl interfaces /
       unused config may be PLANNED groundwork. Frame these as "planned or
       abandoned?" rather than recommending deletion. Default to charitable.
  6. Comment slop (comments restating code or describing intended work)
  7. Inconsistent conventions (signs of stitched-together generations)
  8. Phantom robustness (swallowed exceptions, impossible-state checks)

Anything outside these categories is OUT OF SCOPE. A real bug that is not a
slop pattern goes only in the separate "Incidental" section.

RULES
- Every finding needs: file path, line range, snippet (<=10 lines), and a
  one-sentence justification naming one category above. No evidence, no finding.
- Collapse repeated instances of one pattern into a SINGLE finding with one
  example and an occurrence count. Never list the same pattern N times.
- Assign exactly one severity per finding:
    S1 Adoption blocker | S2 Fix before building on it |
    S3 Worth a cleanup pass | S4 Note only
- Maximum 25 itemized findings. If more exist, report the top 25 by severity
  and give remaining counts per category.
- No speculation. No "consider whether", "there may be", "you might want to".
  If you cannot point at specific lines, do not raise it.
- For NEGATIVE claims (never called / unused / redundant): if you have tool
  access, verify with a repo-wide search before flagging and exclude public API,
  entrypoints, and reflection/string-referenced symbols. If you work only from a
  static bundle and cannot run tools, mark any deletion recommendation [verify],
  state the basis ("not referenced in this bundle"), and note local confirmation
  is required first. Absence from the bundle is not proof of absence from the repo.

OUTPUT (this structure exactly):
  1. VERDICT: one of CLEAN / MINOR / MODERATE / HIGH, plus one paragraph of
     rationale and a rough total human effort estimate (e.g. "~2 hrs").
  2. HEALTH TABLE: each of the 8 categories with status
     (Clean / Minor / Issues) and a finding count.
  3. FINDINGS: itemized, sorted by severity descending. Use the format:
       [S#] <category> — <file>:<lines> (xN if collapsed)
       <snippet>
       Why: <one sentence>
  4. INCIDENTAL (optional): non-slop bugs noticed, clearly labeled as such.
  5. REMEDIATION ORDER: the recommended sequence to address findings, as a
     short ordered list. Do NOT rewrite the code.

Begin by stating the verdict. Be terse. Precision over volume.
```

---

## 9. Why this design controls volume

The volume problem is structural, not a matter of asking nicely. Four mechanisms
enforce restraint:

1. The **presumption of competence** sets the agent's prior so it isn't hunting.
2. The **closed category list** prevents the open-ended "anything else wrong"
   that generates pages.
3. The **pattern-collapsing + 25-cap** turns "847 instances" into "1 finding
   ×847".
4. The **evidence + no-speculation rules** strip out the hypothetical
   suggestions that pad most LLM reviews.

The **verdict + effort estimate** lets the reviewer triage in ten seconds
whether the report is even worth reading in full — which for a rigorously built
app is often "CLEAN, done."
