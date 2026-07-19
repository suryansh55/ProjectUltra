# vary3cmd Quick-Win Fixes — 2026-07-12

Changes made to `BilliardsEverythingSpecialOpt-Khuu-July09,2026/` (branch `jeff-dev-1_8`, uncommitted)
to address the supercomputer job analysis showing vary3cmd averaging **4.9 threads on 10 cores**,
plus three correctness bugs found during the review.

Author of changes: Suryansh Ankur (with Claude Code).

---

## Why utilization was low (diagnosis)

With the production settings in `4A-scripts/vary3/remote/coords_settings.toml`
(`shots=10`, `max=53999`, `oso`+`osno`):

1. **The unit of parallelism is one single-threaded DFS per shot** (`iterateFireAway3` in
   `src/backend/cpp/vary3.cpp`). The internal thread pool only verifies emitted candidate codes
   (`getCodeType`), which is sparse work compared to the tree traversal.
2. **The side-sum "subdivision" in `findCodesVary3` never engaged**: it computed
   `availableThreads = numThreads / shots`, which is `1` when `cpus == shots` (10/10), so each shot
   got exactly one task covering the full range.
3. Even when it did engage, **subdivision duplicated work instead of partitioning it**:
   `fireAway3(min, max)` always traverses the tree from the root up to depth `max` and only gates
   *emission* on `depth > min`. Tree size grows rapidly with depth, so the chunk ending at
   `maxSideSum` re-walked essentially the whole tree while the shallow chunks re-walked its prefix.
4. **Tail imbalance**: 10 independent single-threaded shots finish at very different times
   (different `pos` → different pruning windows); cores go idle one by one while the slowest shot
   runs to completion. That is what drags the average from 10 busy threads down to ~4.9.

Peak useful parallelism of the current design is therefore ≈ `shots`, regardless of how many CPUs
the job requests.

---

## Correctness bugs found and fixed

These were found while reading the threading code. All three are silent — no crash, no error
message, just missing or corrupted results.

### 1. Data race on the shared result set — `src/scripts/cpp/vary.cpp`

In `findCodesVary3` and `findCodesVary4`, every pool task did `codeSeqs.insert(...)` on a shared
`std::set` **with no mutex** (the inner `fireAway*` functions lock their own `codesFoundMutex`
correctly, but the outer collection layer did not). Concurrent `std::set::insert` is undefined
behavior: it can corrupt the tree or silently lose codes.

**Fix:** each task now collects into a task-local `std::set` and merges into `codeSeqs` under a
new `codeSeqsMutex` (one short critical section per task instead of one per code).

### 2. Work posted to a joined (dead) thread pool — `src/scripts/cpp/vary.cpp`

`findCodesVary3`/`findCodesVary4` created **one** `boost::asio::thread_pool` and called
`pool.join()` at the end of the CS phase. A joined pool's threads have exited permanently, so when
`cs=true` *and* any of `oso/cns/ons/osno` were requested, the entire second phase posted tasks that
**never ran** — the run completed "successfully" with all non-CS codes silently missing.

**Fix:** each phase gets its own pool (`csPool` for the CS phase, `pool` for the vary3 phase). In
`findCodesVary4` the second phase is a single job, so it now just runs inline.

Measured impact (see Validation below): at (40.3, 59.6), max=1500, `cs=1 oso=1 osno=1`, the old
binary returned **536** codes (CS only); the fixed binary returns **1694**.

### 3. Boundary code loss in the subdivision — `src/scripts/cpp/vary.cpp`

Emission inside `iterateFireAway3` requires `min < depth < max`. Consecutive subdivision chunks
`[a, b], [b, c]` therefore emitted `(a, b)` and `(b, c)` — codes at depth exactly `b` were emitted
by **neither** chunk.

**Fix:** the subdivision is removed entirely (it also duplicated traversal, see diagnosis #3).
Each shot is now one task over the full `[minSideSum, maxSideSum]` range, which is exactly the
semantics the production configuration (`shots >= cpus`) was already getting.

---

## Exact changes by file

### `BilliardsEverythingSpecialOpt-Khuu-July09,2026/src/scripts/cpp/vary.cpp`

- Includes: added `<mutex>`, `<chrono>`, `<sstream>`; replaced `<bits/std_thread.h>` (a libstdc++
  internal header, non-portable) with the standard `<thread>`.
- `findCodesVary3`:
  - added `codeSeqsMutex`; all result merging is now task-local set → locked merge;
  - CS phase runs on its own `csPool` (fix for bug #2);
  - vary3 phase runs on a fresh `pool`, **one task per shot** over the full side-sum range
    (removes the subdivision — fixes bug #3 and the duplicated traversal);
  - each shot prints a timing line when it finishes:
    `// shot 3/10 done in 41.2s, 128 raw codes`
    so SLURM `.out` files now show per-shot load imbalance directly.
- `findCodesVary4`:
  - same mutex + task-local merge in the CS phase, on its own `csPool` (fixes #1 and #2);
  - the single vary4 job runs inline instead of being posted to a pool.

### `BilliardsEverythingSpecialOpt-Khuu-July09,2026/src/backend/cpp/vary_cs.cpp`

- `iterateFireAwayCS2`:
  - `MAX_INFLIGHT` is now `max(4, cores) * 8` — replacing
    `compute_max_inflight(usage_fraction, 16384)`, which scaled the queue bound to a fraction of
    **physical machine memory** and allowed 10⁵+ queued lambda closures, each holding a copy of the
    code vector (`2*max*4` bytes). This is the same OOM fix already applied to `vary3.cpp`
    (and to the main billiards_everything repo).
  - The core count now honors `SLURM_CPUS_PER_TASK` (falling back to
    `std::thread::hardware_concurrency()`), so the verification pool is sized to the job's
    allocation instead of the whole node. This also matches `vary3.cpp`.
  - The `while (inflight >= MAX_INFLIGHT)` throttle loop now checks `cancel_flag()` so a
    cancellation can't stall inside the wait (parity with `vary3.cpp`; known bug pattern from the
    main repo).
  - Note: `get_total_physical_memory()` / `compute_max_inflight()` are left in place but are no
    longer called from this file.

### `BilliardsEverythingSpecialOpt-Khuu-July09,2026/build.gradle` (line ~407, mac-aarch64 test target only)

- Removed `-lboost_system` from the **test executable** link line. Homebrew Boost ≥ 1.69 no longer
  ships `libboost_system` (it is header-only); the shared-library link had already been fixed the
  same way in Dec 2025 but the test target was missed, so `./gradlew testBackend` could not link on
  macOS. No effect on Linux/cluster builds (separate branch of the config).

### `BilliardsEverythingSpecialOpt-Khuu-July09,2026/gradlew`

- Restored from git (`git checkout -- gradlew`) and re-marked executable. The folder transfer had
  converted it to CRLF line endings, which breaks `sh`/`bash` execution everywhere. (The whole tree
  shows as modified in `git status` for the same CRLF reason; `git diff --ignore-cr-at-eol` shows
  the real changes.)

Files **not** touched: `src/backend/cpp/vary3.cpp` (already had the MAX_INFLIGHT cap and
SLURM-aware cores), `src/scripts/cpp/vary3cmd.cpp`, `vary4cmd.cpp`, `varyAutoPolyCmd.cpp`,
`src/scripts/Makefile`, everything under `4A-scripts/`.

---

## Validation performed (locally, macOS)

Built `libbackend.dylib` via `sh gradlew backendSharedLibrary`, then compiled **two** `vary3cmd`
binaries against that same library: one from the pre-fix `vary.cpp` (git HEAD) and one from the
fixed version. Compared full sorted code sets (output order is irrelevant — codes are printed from
a `std::set`).

| Test | Settings (x=40.3, y=59.6, min=0, max=1500) | Old binary | New binary | Result |
|---|---|---|---|---|
| Equivalence | shots=16 (≥ cores, so old subdivision inactive), oso+osno | 1301 codes | 1301 codes | **Identical sets** |
| Dead-pool bug | shots=4, cs+oso+osno | 536 codes (CS only) | 1694 codes | New ⊃ old; old lost all 1158 non-CS codes |
| Boundary bug | shots=2 (old subdivision active), oso+osno | 1052 codes | 1055 codes | New ⊃ old; the 3 missing codes sit exactly on old chunk boundaries (OSNO(454,**750**), OSNO(542,**750**), OSNO(836,**1312**) for chunks split at 750/1125/1312) |

Also: the backend test suite passes — `./gradlew testBackend`, **30/30 test cases, no errors** —
after all changes.

The A/B harness lives in the session scratchpad (`ab_test.sh`), not in the repo.

---

## Deploying on the supercomputer

Unchanged procedure (per Austin's/the email's instructions): recompile **on the cluster** —
backend first, then `make` in `src/scripts` (Linux branch of the Makefile, `-DCOMPUTE_CANADA`),
and keep `libbackend.so` next to `vary3cmd`. Replace the copy in
`4A-scripts/common/remote/bin/`.

Operational recommendations:

1. **Set `cpus` ≈ `shots` in `coords_settings.toml`** (currently `cpus=32`, `shots=10`). Until the
   DFS itself is parallelized, the program cannot use more than ~`shots` cores; requesting 32
   wastes ~22 of them and worsens the reported utilization.
2. After the first run with the new binary, read the `// shot N/M done in Xs` lines in the `.out`
   files — they quantify the per-shot imbalance and tell us how much the planned DFS
   parallelization will recover.

---

# BFS-frontier DFS parallelization — 2026-07-19

Implemented the subtree-split described under "Next step" below the quick wins. Context: the
2026-07-19 cluster run with the quick-win binary confirmed the diagnosis — at (0.114189, 13.1082),
max=39999, shots=10 on 16 threads, wall time (4849.8s) equaled the slowest shot exactly, average
utilization ~8.3/16 threads, tail imbalance only ~20%. The entire remaining cost is the
single-threaded per-shot DFS.

## Design

New `fireAway3Parallel(min, max, x, y, positions[], reqType)` in `src/backend/cpp/vary3.cpp`
(declared in `src/backend/headers/vary3.hpp`); `iterateFireAway3`/`fireAway3` are untouched (they
remain the GUI path and the A/B reference).

- **Phase A (sequential, cheap):** each shot's tree is expanded breadth-first until
  ~`64 × cores` pending nodes exist. A node carries `(depth, specMin, specMax, sideSum,
  billiard, code-prefix)` — everything the DFS needs, all cheaply copyable. Nodes *expanded*
  during BFS are emission-checked in place (this is the shallow-code trap: skipping it silently
  loses every code above the frontier).
- **Phase B (parallel):** every frontier node from every shot becomes one DFS task on **one
  shared `boost::asio::thread_pool`**. Each task uses the same Frame-stack traversal as
  `iterateFireAway3` (memory O(depth), not O(depth²)) and emission-checks its own root, so every
  tree node is checked exactly once. Candidate verification (`getCodeType`) runs inline in the
  task — the traversal is already parallel, so the old verify-pool and its `MAX_INFLIGHT`
  throttle are unnecessary in this path.
- **Depth cap (default 4096):** a narrow line that has not branched by that depth stops growing
  in Phase A and is handed to a task as-is; bounds BFS memory (each pending node carries its full
  code prefix) and Phase-A time. Correctness does not depend on the cap value (validated below).
- Emission is purely node-local (depth, spec window, side sum, billiard, path), so no partition
  of the tree can change the result set.

`findCodesVary3` in `src/scripts/cpp/vary.cpp` now calls `fireAway3Parallel` once for all shots
instead of posting one `fireAway3` task per shot. Env knobs:

| Variable | Default | Meaning |
|---|---|---|
| `BILLIARDS_PARALLEL_DFS` | `1` | `0` restores the one-task-per-shot path (escape hatch + A/B switch) |
| `BILLIARDS_FRONTIER_TARGET` | `64 × cores` | pending subtrees per shot before Phase B starts |
| `BILLIARDS_FRONTIER_DEPTH_CAP` | `4096` | max BFS depth for a non-branching line |

Output lines change: instead of `// shot N/M done in Xs`, the parallel path prints
`// vary3 parallel dfs: <k> subtrees across <m> shots on <t> threads`, a per-shot
`// shot N/M subtrees done, <c> raw codes, <s>s cpu` as each shot's last subtree finishes, per-shot
raw-code counts, and `// vary3 phase done in <s>s`. All still `//`-prefixed, so `.out` parsing is
safe.

## Validation (macOS, 8 cores = 4P+4E; same binary, env-toggled A/B, full sorted code sets)

| Test | Settings | Result |
|---|---|---|
| shots ≥ cores | (40.3, 59.6) max=1500 shots=16 oso+osno | **Identical**, 1301 codes |
| shots < cores | same, shots=4 | **Identical**, 1158 codes; 19.5s → 15.9s (1.22×) |
| CS + parallel phases | same, shots=4, cs+oso+osno | **Identical**, 1694 codes |
| shots=2 | same, shots=2 | **Identical**, 1055 codes; 18.7s → 8.1s (**2.31×**, 201%→623% CPU) |
| skinny cluster point | (0.114189, 13.1082) max=6000 shots=3 | **Identical** (0 codes at this depth — expected); 8.3s → 6.0s (1.38×) |
| forced cap=50 | (40.3, 59.6) max=1500 shots=4 | **Identical** — emission across the cap boundary intact |
| forced cap=3, target=7 | same | **Identical** — near-whole tree via cap-limited subtree tasks |

Backend test suite: 30/30, no errors. Local speedups are E-core-depressed (extra user-time is the
4 efficiency cores running slower, not extra work); uniform cluster cores should land nearer the
ideal `sum(shot times)/cores`.

## Expected cluster impact & deployment

For the 2026-07-19 job shape (40,218 core-seconds of traversal): ~16 cores → ~2,500s (~42 min,
1.9×) instead of 81 min. **The `cpus ≈ shots` recommendation below is now obsolete** — the run
scales past `shots` cores, so `cpus=32` in `coords_settings.toml` is useful again (~21 min for the
same job).

Deploy exactly as before: recompile on the cluster — backend first, then `make` in `src/scripts` —
and keep `libbackend.so` next to `vary3cmd` in `4A-scripts/common/remote/bin/`. First run
recommendation: submit one job with `BILLIARDS_PARALLEL_DFS=0` and one without, same coords, and
diff the sorted non-`//` output as a final on-cluster equivalence check.

---

## Next step (IMPLEMENTED 2026-07-19 — see section above)

Subtree-split parallelization of the DFS: expand each shot's tree breadth-first to a shallow
frontier (hundreds–thousands of nodes, each carrying its `(specMin, specMax, sideSum, billiard,
code-prefix)` state — all already copyable, `Frame` copies it today), then run every frontier
subtree as an independent task on **one shared pool across all shots**. This partitions the
traversal (no duplication), keeps all cores busy to the end (no tail), and scales past
`shots` cores. Validation plan: code-set equality against the current binary across a parameter
matrix, same method as above.
