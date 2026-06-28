# Checkpoint — refinement-order / parallel-refinement investigation

_Last updated: 2026-06-28. Paused for ~1 week; resume from "Next steps" below._

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
   parallel); refining the survivors reproduces the region **bit-identically**
   (validated, 0 mismatches). Prunes ~66 % of curves. Standalone speedup ~2–3×.

## Next steps (resume here)

1. **Prototype the cheap binding test** — replace the full-`refine_polygon`
   binding test with a conservative sign evaluation at the start polygon's
   vertices (all-same-sign ⇒ prune; interval straddles zero ⇒ keep). Confirm its
   prune ratio matches the full-refine prototype. This makes the filter ≈ free.
2. **Tree-reduce the survivor refinement** (needs a polygon∩polygon primitive)
   for the remaining factor.
3. Before anything lands in the production path: implement the rotation +
   edge-selection canonicalization, validate "CODES ARE IN COVER" + tolerance,
   and get professor sign-off (per the doc).
