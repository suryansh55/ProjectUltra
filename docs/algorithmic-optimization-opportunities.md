# Algorithmic Optimization Opportunities — LiLuMaxVary / region computation

> Status: discussion notes for further performance work. The low-risk,
> mechanical optimizations are already done (see "Already done" below). What
> remains are **algorithmic** changes to numerically-sensitive code, each with
> a correctness risk that must be validated against the cover. These are
> written up for discussion before any are attempted.

## How we got here (measurement)

A full AutoPolyVary run is too noisy to optimize against (per-hole wall-clock
swings ~8 s → ~80 s depending on which codes a hole explores). So we added a
**deterministic micro-benchmark** — `src/bench/cpp/main.cpp`, run with
`./gradlew benchBackend` — that times `calculate_stable` on a few fixed,
hardcoded code sequences. Same input → same work, variance ~0.1%, so any real
change is directly measurable.

We then bracketed the phases of `calculate_stable` with timers. For a typical
large code (length ~92–100, ~4 s total per region):

| Phase | Time | Share |
|---|---|---|
| Unfolding construction | ~0.00002 s | ~0% |
| shooting vector + `generate_curves_lr` (**already parallelized, TBB**) | ~0.21 s | **~6%** |
| **polygon refinement chain** (`calculate_final_polygon`) | **~3.46 s** | **~94%** |
| points / equations / left-rights after the polygon | ~0.003 s | ~0% |

A single region computation peaks at **~25 MB** RSS in isolation — i.e. the
application's ~1.6 GB footprint is JVM heap + cross-hole concurrency, **not**
the C++ math kernel. So there is no native-memory win to chase here.

## The bottleneck

`calculate_final_polygon` (`src/backend/cpp/equations.cpp`, ~lines 122–169)
refines an interval polygon against every generated curve, one curve at a time:

```cpp
for (const auto& kv : curves.first)  { interval_polygon = refine_polygon(interval_polygon, kv.first); } // sines
for (const auto& kv : curves.second) { interval_polygon = refine_polygon(interval_polygon, kv.first); } // cosines
```

For a large code this is **~3,778 refinement steps** (e.g. sin = 2732,
cos = 1046), each ~0.9 ms. Each step consumes the polygon produced by the
previous step, so it is a **true data-dependency chain** — it cannot be turned
into a `parallel_for`. This is why ~94% of the kernel time is here and why no
*mechanical* parallelism win remains.

## Candidate algorithmic upgrades (for discussion with the professor)

Each is a real lever, but each is an algorithmic change to numerically
sensitive interval-arithmetic code. **All must be validated against the cover**
(a run must still report "CODES ARE IN COVER", and ideally produce byte- or
region-identical output) before being accepted.

### 1. Parallel / tree-structured refinement
Refining the polygon against a set of curves is conceptually intersecting it
with a set of constraints, which is **mathematically commutative and
associative** — the final region is the intersection regardless of order. That
suggests a tree reduction: refine disjoint sub-batches of curves in parallel,
then intersect the partial polygons (`parallel_reduce`).

- **Why it could work:** the math (intersection of half-plane-like constraints)
  is order-independent in the exact limit.
- **The risk:** the code comments explicitly state the refinement order is kept
  deterministic *on purpose*, and `refine_polygon` does incremental
  simplification under **interval (MPFI) rounding** — so the *interval* result
  may be order-sensitive even though the *exact* result is not. Two questions
  for the professor:
  1. Is the refined region guaranteed identical (as a set) under reordering and
     re-association, given outward-rounded intervals?
  2. If not identical, are the differences within tolerance / still sound for
     the cover argument?
- **Payoff if valid:** this is the only lever that attacks the 94% directly and
  could give near-linear speedup on cores.

#### Experiment findings (2026-06-28)

To answer the two questions above empirically *before* touching the production
path, we built a refinement-order invariance probe:

- `experiment_refine_order()` in `src/backend/cpp/equations.cpp` (declared in
  `equations.hpp`, result struct `RefineOrderReport`). It replicates
  `calculate_stable`'s curve construction exactly, then recomputes the region
  under several **deterministic reorderings** of the same curve set —
  `full-reverse`, `cosines-first`, `interleaved` — and compares each to the
  canonical order (sines→cosines, `std::map` order). The production
  `calculate_final_polygon` is **untouched**.
- Driver `src/experiment/cpp/main.cpp`, run with **`./gradlew experimentBackend`**.
- Metrics per ordering: same vertex count? identical multiset of edge equations?
  bit-identical endpoints? and — independent of edge labeling — the **vertex
  Hausdorff distance** (symmetric nearest-neighbour distance between the two
  vertex sets), which measures whether the *region itself* moved.

Result on 4 real codes from a LiLuMaxVary run (lengths 20 / 65 / 92 / 96):

| Code | Steps | Edge-equation multiset | **Vertex Hausdorff Δ** |
|---|---|---|---|
| small | 176 | identical | ≤ 1.2e-50 |
| medium | 1473 | **1 of 5 edges relabeled** | **≤ 5.3e-50** |
| big_a | 3778 | identical | ≤ 4.3e-50 |
| big_b | 3768 | identical | ≤ 2.0e-49 |

**Interpretation.** The MRR **region is geometrically order-invariant** — every
vertex stays put to ~1e-49, i.e. last-place noise at 50-digit MPFI precision,
across all codes and all reorderings. For `medium` the *symbolic* boundary
labeling differs (canonical records a `cos(...)` curve as one edge; the
reordered runs record a `sin(...)` curve there), yet the vertex set is identical
to 5e-50. This is a **benign relabel at a near-degenerate vertex**: two distinct
generated curves bind the same boundary segment simultaneously, and the
refinement order only decides which coincident curve gets recorded as the edge.

**Answers to the professor's questions (provisional, n=4):**
1. *Region identical as a set under reordering?* **Geometrically yes** (vertex
   set invariant to ~1e-49). Only the symbolic edge label can flip, at
   degenerate vertices — the region does not move.
2. *Differences within tolerance / cover-safe?* The geometric differences
   (~1e-49) are far below any plausible cover tolerance, and the cover argument
   already relies on outward-rounded intervals to absorb exactly this.

**Therefore #1 looks viable for the region computation**, gated on two checks
before implementation:
- **Downstream label-sensitivity.** The edge equations feed `stable_left_right`
  / `LeftRightVariant` (`curves.first.at(eq).at(0)`). A sin↔cos relabel at a
  degenerate vertex could change or throw that lookup even though the region is
  unchanged — so the parallel reduction must **canonicalize edge selection** (or
  be proven label-stable) before adoption.
- **Validation method.** Output won't be byte-identical (~1e-49 endpoint drift),
  so #1 must be validated by "CODES ARE IN COVER" + a tolerance check, *not* a
  byte diff.

**Scaled confirmation (2026-06-28, n=160).** The harness reads codes from a
file (`./gradlew experimentBackend -Pcodes=<file>`; tolerant of the app's
`CS (len, count) …` / index / `, zy` decoration). Ran it over the full output
of a 100-hole AutoPolyVary run — **160 real codes** (83 CS + 56 OSO + 21 OSNO),
including codes with thousands of refinement steps:

| Metric | Value |
|---|---|
| codes compared | 160 |
| region-invariant (vertex Hausdorff Δ < 1e-30) | **160 / 160** |
| region MOVED (Δ ≥ 1e-30) | **0** |
| worst vertex Hausdorff Δ over the whole run | **1.9e-48** |
| codes hitting the benign edge-relabel | 12 (all still region-invariant) |

Across every code and every reordering the **region never moved** — worst
disagreement anywhere was 1.9e-48, last-place noise at 50-digit MPFI. The
edge-relabel appeared on 12/160 codes but was benign in all of them. The
order-invariance of the *region* is now well-supported empirically; the only
open item before implementing #1 is the **downstream label-stability gate**
above (canonicalize edge selection so a relabel can't perturb `stable_left_right`).

#### Gate #1 investigation: downstream label-stability (2026-06-28)

The consistency guard in `wrapper.cpp:425` recomputes a code's `Stable` and
**throws "The pattern changed do it in the slow way!"** if the recomputed
`left_rights` don't equal the expected vector. So `left_rights` is NOT cosmetic
— a reorder/parallel run that changes it would spuriously trip this guard.

The experiment now recomputes `stable_left_right` under each reordering and
compares. The break splits cleanly into two effects, each with a deterministic
fix (neither is a fundamental blocker):

| Effect | Cause | Fix |
|---|---|---|
| **rotation** (common) | same region + same `left_rights` *set*, but the edge cycle starts at a different vertex → vector is a cyclic permutation | **canonical rotation** — rotate the edge cycle to a canonical anchor before emitting; pure post-processing, no geometry change |
| **content change** (rare; the relabel codes) | a coincident curve at a degenerate vertex is recorded as the edge → a `left_rights` element actually changes | **canonical edge selection** — deterministic tie-break: among curves binding the same edge within tolerance, pick the one canonical map order would (smallest key) → bit-identical result |

With both canonicalizations the parallel result is bit-identical to canonical,
so the guard never trips. **Full run n=160:** 139 codes rotation-only, 8 codes
content-change, 13 already bit-identical. So ~87 % of codes need only the cheap
canonical rotation; the canonical edge-selection tie-break matters for just 8 of
160 (the degenerate-vertex relabel cases).

#### Parallel non-binding filter — prototype (candidate #1 ∪ #3, 2026-06-28)

**Key monotonicity fact:** refinement only ever *shrinks* the polygon, so a
curve that does not cut the **starting** bounding polygon can never cut any
later sub-polygon. Therefore every curve can be tested against the *fixed*
starting polygon **in parallel**, the non-binding ones discarded, and only the
surviving (binding) curves refined sequentially. The survivor set is a superset
of the truly-binding curves, so refining by it reproduces the exact region — no
polygon∩polygon primitive required.

Prototyped in `experiment_refine_order` (the binding test currently *is* a
`refine_polygon` against the start; survivor count + correctness reported).
**Validated at scale (n=160):** filtered region **bit-identical to canonical on
all 160 codes** (0 mismatches), pruning **52.4 %** of curves overall (111,559 of
234,224 refinement steps survive). So the sequential chain is cut roughly in
half before any parallelism is applied.

**Speedup reading (important):** as prototyped the binding test costs a full
refine, and the survivor tail is still sequential, so the standalone win is only
~2–3× (scales with prune ratio). The two changes that make it matter:
1. **Cheap binding test** — to decide "does curve C cut polygon P0" just
   evaluate C's sign at P0's vertices (all same sign ⇒ prune); a handful of
   evals vs a full clip, so the filter step becomes ~free and cost collapses
   toward just the survivor refines. Must be conservative under interval
   arithmetic: if an endpoint interval straddles zero, keep the curve. The
   prototype already proves the *set* this cheap test must reproduce.
2. **Tree-reduce the survivors** for the remaining factor (this is where the
   polygon∩polygon primitive would come in).

#### Cheap sign-based binding test — VALIDATED (lever 1, 2026-07-04)

Implemented as `cheap_curve_is_binding` in `equations.cpp` (experiment
namespace). The structural justification is stronger than "conservative
heuristic": `refine_polygon`'s entire corner logic is *driven by*
`curve_sign_at_point` at the polygon's vertices (POS side kept, NEG discarded),
so the vertex-sign sweep replicates the full test's binding decision exactly,
except at ZERO vertices where we keep the curve instead of resolving gradients.
Classification: all vertices strictly POS ⇒ no-op ⇒ prune; all strictly NEG ⇒
curve empties the region ⇒ binding; any ZERO or mixed ⇒ binding. Early-exits on
the first mixed pair.

**n=160 validation (full corpus, 234,224 curve steps):**
- cheap-filtered region **bit-identical to canonical on 160/160 codes**;
- **missed binding curves = 0** — the cheap survivor set was a superset of the
  full filter's on every code (the safety invariant);
- prune ratio **51.6 %** vs the full filter's 52.4 % (1,790 extra curves kept,
  ≈ 0.8 % conservatism cost);
- test wall time **179 s vs 722 s** (~4× serially). Both tests are dominated by
  the same 168-bit `mpfi_sin` vertex evaluations, hence 4× not 100× — but the
  cheap test has zero cross-curve dependencies, so across N cores it is ~4N×
  and the filter becomes negligible next to the survivor tail.

**Consequence for staging:** refining survivors *in canonical order* is
bit-identical to the canonical chain, so lever 1 needs **no left_rights
canonicalization at all** — rotation/edge-selection fixes are only prerequisites
for the tree-parallel reorder (lever 2). Lever 1 can therefore be
production-integrated on its own with zero downstream-guard risk: expected
~1.4× when cores are already saturated (AutoPolyVary batch), approaching ~2×
when cores are idle (interactive single-region use). Threading note: the vary
path already runs inside a `boost::asio` pool — inner parallelism for the
filter should use TBB (composable work-stealing), NOT a nested asio pool (the
known freeze cause; see CLAUDE.md).

#### Lever 1 IN PRODUCTION — flag-guarded (2026-07-04)

`calculate_final_polygon` gained an opt-in parallel-filter path, enabled by
`BILLIARDS_PARALLEL_FILTER=1`; with the flag unset the historical sequential
loop runs unchanged. Binding tests fan out over `tbb::parallel_for`; survivors
refine in canonical order. Validated: 30/30 backend tests pass flag on and off;
`benchBackend`'s new full-precision output fingerprint is **identical flag
on/off on all bench codes** (bit-identical production output); measured
speedups small 1.33×, medium 1.29×, big_a 1.28×, big_b 2.37× (tracks prune
ratio). Outstanding: in-app "CODES ARE IN COVER" run, then the default-on
decision.

#### Lever 1 COMPLETE — default-on (2026-07-05)

The final gate passed: an in-app AutoPolyVary run on 100 holes (4 shots,
3 subdivisions, 2000 moves) with the filter enabled finished
**"CODES ARE IN COVER"** in 27.2 s; a same-parameter run on the pre-filter
build took 68.5 s (indicative ~2.5×, different session so not a controlled
A/B). The filter is now **default-ON** in `parallel_filter_enabled()`
(`equations.cpp`). Escape hatch: `BILLIARDS_PARALLEL_FILTER=0` — or
`./gradlew run -PparallelFilter=0`, since the Gradle daemon does not reliably
forward shell env vars — forces the historical sequential loop byte-for-byte.
The backend prints its ENABLED/DISABLED state at first use so every run is
attributable to the right code path. Re-verified after the flip: bench
fingerprints identical both ways on all 4 codes; 30/30 backend tests pass both
ways. Remaining work in this section is lever 2 only.

Combined target: filter ≈ free, sequential chain cut to ~1/3, then parallelize
that third — potentially an order of magnitude on the big codes where the 94 %
lives. All of this still requires cover-validation + professor sign-off before
landing in the production path; the experiment harness (`src/experiment/`,
`experiment_refine_order` in `equations.cpp`) is the safe place to develop it —
`calculate_final_polygon` is untouched.

### 2. Lower interval precision during refinement
The refinement chain runs at full MPFI precision throughout. If a lower
precision suffices for the intermediate refinement steps (with a final
high-precision pass, or with verification that the cover still holds),
per-step cost drops.

- **Risk:** soundness — interval bounds must remain conservative (outward
  rounded) so the region is never under-approximated. Needs the professor's
  judgment on the minimum precision that keeps the cover proof valid.
- **Payoff:** proportional per-step speedup across all ~3,778 steps; lower risk
  than #1 because it doesn't reorder anything.

### 3. Reduce the number of refinement steps
~3,778 curves per large code is a lot of steps. Duplicates are already removed
(curves are de-duplicated into maps), but there may be **redundant or
non-binding constraints** — curves that never actually clip the polygon — that
could be pruned or pre-filtered before the serial chain.

- **Risk:** must prove a pruned curve genuinely cannot affect the final region;
  a wrongly dropped constraint would enlarge the region and could break the
  cover.
- **Payoff:** linear in the number of steps removed; complements #1 and #2.

### Note / cross-reference
There is already an in-code TODO at `equations.cpp:120`:
"*also do the refinement as the curves are generated — that should reduce the
memory usage*". Fusing generation and refinement is related to #3 and worth
raising in the same discussion.

## Already done (low-risk, mechanical — no further action needed)
- Stabilized the feature on an 8 GB machine (no freeze / crash / OOM).
- Converted the 4 nested `boost::asio` thread pools in `unfolding.cpp`
  (curve generation) to `tbb::parallel_for` — removes CPU oversubscription;
  this is the ~6% phase and it is already parallel.
- Raised the large-calc concurrency gate 1 → 2 (overlaps holes; runtime
  verified, peak RSS unchanged at ~1.6 GB).
- Added the deterministic benchmark harness (`src/bench/`, `./gradlew benchBackend`)
  as the permanent tool for measuring any of the above changes.
