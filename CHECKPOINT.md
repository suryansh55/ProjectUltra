# Checkpoint — refinement-order / parallel-refinement investigation

_Last updated: 2026-07-04. Lever 1 (cheap binding test) validated at n=160 on
branch `cheap-binding-test`; resume from "Next steps" below._

## TL;DR

We are investigating whether the `calculate_final_polygon` refinement chain
(~94 % of the per-region compute kernel) can be parallelized to make this build
dramatically faster than the stock program — while staying provably correct
against the cover. Full write-up:
`docs/algorithmic-optimization-opportunities.md` (candidate #1).

**Nothing in the production compute path was changed.** All work is a new,
isolated experiment harness. The shipped/working app is safe — see Rollback.

## Rollback (the working, shipped version)

The working version that ships in the `.dmg` is commit **`237b84d`**, which is
**`main` == `origin/main`** and is **untouched** by this investigation.

```bash
# Return to the known-good shipped version at any time:
git switch main            # or: git checkout 237b84d
```

The experiment work lives only on branch **`refine-order-experiment`** (pushed
to origin). Deleting that branch loses nothing on `main`.

## What's in this checkpoint (branch `refine-order-experiment`)

New, non-invasive experiment harness — `calculate_final_polygon` is NOT modified:

- `src/experiment/cpp/main.cpp` + Gradle target `experiment` / task
  `experimentBackend` (cloned from `bench`).
- `experiment_refine_order()` in `src/backend/cpp/equations.cpp` (declared in
  `equations.hpp`, struct `RefineOrderReport`).
- Docs updated in `docs/algorithmic-optimization-opportunities.md`.

Run it:

```bash
./gradlew experimentBackend                         # 4 built-in codes (fast)
./gradlew experimentBackend -Pcodes=dist/test       # codes pasted into dist/test
./gradlew experimentBackend -Pcodes=<file>          # one code per line; tolerant
                                                    # of CS/OSO/OSNO + index/paren decoration
```

(`dist/` and `experiment_codes.txt` are gitignored — local input data.)

## Findings so far (all empirical, validated)

1. **Region is order-invariant.** Reordering the refinement (reverse,
   cosines-first, interleaved) never moves the region: across **160 real codes**
   the worst vertex Hausdorff Δ was **1.9e-48** (last-place noise at 50-digit
   MPFI). 0 codes moved.
2. **Gate #1 (downstream `left_rights`) is not a blocker.** The
   `wrapper.cpp:425` guard throws "pattern changed" if recomputed `left_rights`
   differ. Reordering breaks that two ways, each with a deterministic fix:
   *rotation* (canonical rotation) and *content change at a degenerate vertex*
   (canonical edge selection). Both make the parallel result bit-identical.
3. **Parallel non-binding filter works.** Refinement only shrinks the polygon,
   so curves that don't cut the *starting* polygon can be discarded (tested in
   parallel); refining the survivors reproduces the region **bit-identically on
   all 160 codes** (0 mismatches), pruning **52.4 %** of curves overall
   (111,559 / 234,224 steps survive). Standalone speedup ~2× (survivor tail
   still sequential + test costs a full refine — see Next steps).
4. **Cheap sign-based binding test VALIDATED (2026-07-04, branch
   `cheap-binding-test`).** `cheap_curve_is_binding` in `equations.cpp`:
   evaluate the curve's interval sign at each start-polygon vertex (all POS ⇒
   no-op ⇒ prune; all NEG ⇒ empties region ⇒ binding; any ZERO/mixed ⇒ keep).
   Justified structurally: `refine_polygon`'s corner logic is driven entirely
   by these same vertex signs. n=160: **bit-identical region 160/160**,
   **missed binding curves = 0** (cheap survivors ⊇ full survivors on every
   code), prune 51.6 % vs 52.4 % (0.8 % conservatism), test time 179 s vs
   722 s (~4× serial; embarrassingly parallel ⇒ ~free).
   **Key consequence:** survivors are refined in canonical order, so the output
   is bit-identical ⇒ lever 1 needs NO left_rights canonicalization — that is
   only a prerequisite for the tree-parallel reorder (lever 2). Lever 1 can go
   to production on its own: ~1.4× when cores saturated (AutoPolyVary batch),
   ~2× when idle (interactive).

## Lever 1 in production (2026-07-04, branch `cheap-binding-test`)

`calculate_final_polygon` now has a flag-guarded parallel-filter path:
**opt-in via `BILLIARDS_PARALLEL_FILTER=1`** (env var; flag off = the historical
sequential loop, byte-for-byte). Curves are tested against the fixed start
polygon with `tbb::parallel_for` (TBB composes inside the vary path's asio
workers; a nested asio pool is the known freeze cause) and only survivors are
refined, in canonical order. Helpers live above `calculate_final_polygon` in
`equations.cpp` (`cheap_curve_is_binding`, `parallel_filter_enabled`).

Validation (all on this branch):
- `./gradlew testBackend`: 30/30 pass, flag off AND on (test binary run
  directly so the env var provably reached it).
- `benchBackend` now prints a full-precision output fingerprint (hashes every
  interval bound + equations + left_rights). **Flag on/off fingerprints are
  identical on all 4 bench codes** — production output is bit-identical.
- Speedup (bench, min-of-5): small 1.33×, medium 1.29×, big_a 1.28×,
  big_b **2.37×** (speedup tracks prune ratio; big_b prunes 82 %).
  Peak RSS +5 MB.

**Lever 1 COMPLETE (2026-07-05).** In-app gate passed: AutoPolyVary on
100 holes (4 shots, 3 subdivisions, 2000 moves) with the filter enabled
finished **"CODES ARE IN COVER"** in 27.2 s — a same-parameter run on the old
build took 68.5 s (indicative ~2.5×, not a controlled A/B). The filter is now
**default-ON** in `parallel_filter_enabled()`; the escape hatch is
`BILLIARDS_PARALLEL_FILTER=0` (or `./gradlew run -PparallelFilter=0`), which
forces the historical sequential loop byte-for-byte. Re-verified after the
flip: bench fingerprints identical both ways, 30/30 backend tests pass both
ways. The backend prints its ENABLED/DISABLED state at first use.

## Next steps (resume here)
2. **Tree-reduce the survivor refinement** (needs a polygon∩polygon primitive)
   for the remaining factor.
3. Before the tree-parallel reorder (lever 2) lands: implement the rotation +
   edge-selection canonicalization, validate "CODES ARE IN COVER" + tolerance,
   and get professor sign-off (per the doc). (NOT needed for lever 1.)
