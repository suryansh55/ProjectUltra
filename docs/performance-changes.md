# Performance & Stabilization Changes — LiLuMaxVary / region computation

A record of the changes made to make the LiLuMaxVary (AutoPolyVary) path usable
and faster on the target machine (an **8 GB / 8-core Apple Silicon** Mac, Apple
Silicon only). For *future* algorithmic ideas not yet done, see
[`algorithmic-optimization-opportunities.md`](algorithmic-optimization-opportunities.md).

Scope: this file covers speed/throughput/memory work. The in-app console,
code-signing, and working-directory fixes from later sessions are packaging/UX,
not performance, and are intentionally not listed here.

---

## TL;DR — what changed and why it matters

| Area | Change | Effect |
|---|---|---|
| Threading | 4 nested `boost::asio` pools in `unfolding.cpp` → `tbb::parallel_for` | Removes CPU oversubscription (~32 threads on 8 cores → one shared 8-wide arena); byte-identical output |
| Concurrency | `LARGE_CALC_GATE` semaphore `1 → 2` | Overlaps a 2nd big region calc during serial phases; modest throughput gain, **no** RSS increase |
| Memory | `MAX_INFLIGHT` in vary3/vary_cs capped at `cores*8` | Killed the ~12 GB queued-lambda OOM (was a memory-derived ~500k) |
| Memory | Direct accumulation instead of per-chunk + merge in curve gen | Halved peak memory of the storage path |
| Stability | 7 targeted fixes (freeze / SIGSEGV / OOM / non-progression) | Feature actually completes a run instead of crashing — the prerequisite for any speed work |
| Measurement | Deterministic micro-benchmark (`./gradlew benchBackend`) | Escapes LiLuMaxVary's run-to-run noise so changes are measurable to ~1% |
| Build | `-Pportable` CPU-tune toggle | Distributable dylib (`-mcpu=apple-m1`) vs fast local (`-march=native`) |

Net runtime state (verified 2026-06-20, `LARGE_CALC_GATE=2`): a full AutoPolyVary
run completes, **"CODES ARE IN COVER"**, 25 points, no crash/freeze/OOM, peak
RSS **~1.6 GB** (well under the 8 GB budget).

---

## 1. Baseline build optimizations (pre-existing)

The C++ backend is compiled `-O3 -march=native -flto -ftrapv -std=c++17`, uses
**jemalloc** in place of the system allocator (~15% gain), GMP/MPFR/MPFI for
multiprecision, and **TBB** for parallelism. These are the starting point, not
new work, but they frame everything below.

## 2. Threading: nested boost::asio pools → TBB

**Root cause of the instability.** The backend created a per-call
`boost::asio::thread_pool(hardware_concurrency())` *inside* functions that Java
already runs concurrently (storage/shot executors, sized `cores*0.5 = 4`). That
nesting spawned ~32 OS threads on 8 cores, churned thread creation, and
destabilized the native heap → SIGSEGV. boost::asio pools do **not** compose
under nesting; TBB's work-stealing arena does.

**Change.** All 4 nested asio pools in `unfolding.cpp` — `get_all_vectors`,
`generate_curves` (×2), and `generate_curves_lr` (incl. the `left_rights` hot
path called from `equations.cpp`) — were converted to
`tbb::parallel_for(blocked_range(0, task_num))` over the **same** task
decomposition, writing to the same per-block slots, with the **same**
index-ordered merge. This was a deliberately mechanical, behavior-preserving
swap: **output is byte-identical**, no memory regression.

**Why it didn't speed things up on its own.** Big codes (the length-~100
dominators) already ran one-at-a-time behind the memory gate, so the old asio
pool wasn't oversubscribed for *them* — TBB gives the same 8-way parallelism.
Oversubscription only hurt small codes (already fast). So the TBB conversion is
a **robustness/correctness win and the prerequisite** that makes raising the
concurrency gate safe.

## 3. Concurrency gate: `LARGE_CALC_GATE` 1 → 2

`PolyVaryTask.java`: a `Semaphore` gating concurrent large-region calculations,
with `LARGE_CODE_THRESHOLD = 150`. Originally **1 permit** (added to kill an OOM
where huge codes × 4 concurrent hit ~13 GB on the 8 GB box).

Once curve generation was on a single shared TBB arena, two concurrent big
calcs no longer oversubscribe the CPU, so the permit count was raised to **2**.
This fills cores that would otherwise idle during the serial
`points_and_stuff` / polygon-reduction phases.

**Measured (2026-06-20):** with gate=2, peak RSS **1615 MB** — actually *lower*
than gate=1's ~1.95 GB, exactly as the shared-arena design predicts (2 calcs
share one 8-wide arena, no per-calc heap doubling). Wall-clock ~17m 7s for 25
points vs ~20 min at gate=1 — modestly faster, within run-to-run noise.
**Decision: keep gate=2, do not push to 3** (marginal gain, and the 8 GB target
already saw system-level memory pressure). Lower back to 1 if memory pressure
returns.

## 4. Memory fixes

- **`MAX_INFLIGHT` cap (vary3 / vary_cs).** Must be `cores*8`, **not** a
  physical-memory formula. Each queued task captures a copy of the full DFS path
  vector; the old ~500k-task allowance meant ~12 GB of queued lambdas — the
  original OOM/freeze. (Documented in `CLAUDE.md` so it isn't re-introduced.)
- **Direct accumulation** instead of per-chunk-then-merge in the curve-gen
  storage path — halves peak memory.

## 5. Stabilization fixes (prerequisite for any speed work)

These stopped the feature freezing/crashing so a run can finish:

1. **Freeze** — `pixelColor()` given a 3 s timeout (was blocking forever on a
   busy FX thread).
2. **Cancel race** — removed `cancel_flag().store(false)` from inside the vary
   iterate functions; added `backend_reset_cancel()` called once at the start of
   `PolyVaryTask.call()`.
3. **SIGSEGV** — serialized / de-nested the offending nested pools (later
   superseded by the TBB conversion in §2).
4. **Non-progression** — missing `return` in `Viewer.drawAutoPolyVary`'s "filled
   by previous coordinate" branch spawned duplicate task chains; added `return`.
5. **`BoyanMenu.printCodes`** made resilient — opens `garbage.txt` once (not
   per-code), treats dump-write failures as warnings, surfaces the real OS error.

Runtime-verified 2026-06-14 (fresh boot): full run completed, no freeze/crash/OOM,
"CODES ARE IN COVER".

## 6. Measurement: deterministic benchmark harness

A full AutoPolyVary run is too noisy to optimize against (per-hole wall-clock
swings ~8 s → ~80 s depending on which codes a hole explores). So a standalone
benchmark was added:

- **Files:** `src/bench/cpp/main.cpp` + a `bench(NativeExecutableSpec)` component
  and `benchBackend` Exec task in `build.gradle`.
- **Run:** `./gradlew benchBackend`.
- **What it does:** calls `calculate_stable`/`unstable` (the dominant per-hole
  kernel) on **fixed hardcoded codes** (small / medium len 56 / big_a len 92 /
  big_b len 100), 1 warmup + 5 reps, reports min/median/mean ms + peak RSS via
  `getrusage`. Deliberately does **not** call `vary_*` (the noise source). A
  `volatile` sink stops `-O3` eliding the call.
- **Variance ~1%**, so any real change >~3% is visible.

**Baseline (2026-06-20, current tree = all fixes + TBB + gate=2):**
medium(56) **755 ms**, big_a(92) **3754 ms**, big_b(100) **3983 ms**.
Single-calc peak RSS **25 MB** — confirming the app's ~1.6 GB footprint is JVM
heap + cross-hole concurrency, **not** the C++ math kernel (so there's no
native-memory win to chase).

Also added: `backend_peak_rss_bytes()` JNA call (process-lifetime peak RSS via
`getrusage`) used by the in-app `[Benchmark]` line. (That `[Benchmark]` print is
currently commented out in `PolyVaryTask.logBenchmark()` for release; re-enable
when profiling.)

## 7. Build: portability toggle

The backend defaults to `-march=native` (tuned to the build machine, fastest
locally) but can SIGILL on a different/older Apple Silicon chip. Added
`-Pportable` → `-mcpu=apple-m1` (the Apple Silicon baseline every newer arm64
Mac supports). `package-mac.sh` builds with `-Pportable` so the shipped dylib is
portable; local dev builds stay on `-march=native`.

---

## Where the time actually goes (and why mechanical wins are tapped out)

Profiling `calculate_stable` for a big code (len 92, ~3675 ms total):

| Phase | Time | Share |
|---|---|---|
| Unfolding construction | ~0.00002 s | ~0% |
| shooting vector + `generate_curves_lr` (**TBB, already parallel**) | ~0.21 s | ~6% |
| **polygon refinement chain** (`calculate_final_polygon`) | **~3.46 s** | **~94%** |
| points / equations / left-rights after polygon | ~0.003 s | ~0% |

The 94% is `calculate_final_polygon`: ~3,778 sequential `refine_polygon` steps
(sin 2732 + cos 1046), each ~0.9 ms, each consuming the previous step's polygon
— a **true data-dependency chain**, not parallelizable with `parallel_for`. The
parallelizable part (curve generation) is already on TBB and is only ~6%.

**Conclusion:** stabilization + TBB + gate=2 is at the low-risk ceiling. The
remaining levers are algorithmic and correctness-risky (parallel/tree refine,
lower MPFI precision during refine, prune redundant curves) — all must be
validated against the cover. They're written up in
[`algorithmic-optimization-opportunities.md`](algorithmic-optimization-opportunities.md).

---

*Committed reference: most of the above landed in commit `db67072`
("Stabilize LiLuMaxVary path, add deterministic benchmark + optimization notes")
on branch `lilumaxvary-stabilization`.*
