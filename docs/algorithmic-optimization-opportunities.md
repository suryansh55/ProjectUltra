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
