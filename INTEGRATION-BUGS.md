# Bugs found while porting the Khuu Aug-2026 drop

Recorded 2026-08-24, during the port of `newversion/BilliardsEverythingSpecialOpt-Khuu-Aug11,2026`
into our Java 17 / C++17 tree (branch `cover-compression`).

Every entry in **Part 1** was reproduced by *running* the pre-fix code, not by reading it.

**B1–B8** are reproduced by `~/billiards-port-harnesses/BugProofs.java`, which transcribes each
original implementation verbatim and demonstrates the failure:

```bash
CP=$(./gradlew -q runtimeClasspathAsPath)
javac -cp "$CP" -d /tmp/bugproofs ~/billiards-port-harnesses/BugProofs.java
java -cp "/tmp/bugproofs:$CP" BugProofs        # expect: 9 proven, 0 not reproduced
```

(9 proven lines for 8 bugs — B6 prints a second line confirming `CS` is the *only* type affected.)

**B9** is proven by the test runner: the report count goes 11 → 17 once `static` is removed.
**B10** is proven by the compiler, which refuses to compile a second include of the header.

**Part 2** holds findings that are certain from the source but that I did not execute — they are
separated deliberately so the two kinds of confidence are not mixed.

---

## Part 1 — Reproduced by execution

### B1. `PatUtils.printPat` crashes on any negative pattern entry
**Where** `src/java/patternfinder/PatUtils.java`, `printPat` → `repeat`
**Origin** ours only — the drop already has the fix (`PatUtils.java:279`)
**Proof** `printPat([0, 2, 0, -1])` → `NegativeArraySizeException: -1`

`repeat(str, times)` does `new String(new char[times])`. A negative entry makes that
`new char[-1]`, which always throws.

**Consequence** The moment patterns were allowed to point downwards, *printing* one would kill the
operation. Every code path that renders a pattern — `Spattern.toString`, `Tpattern.patString`,
`PatternFinder`'s result text — would throw instead of producing output. Not a wrong answer: no
answer.

**Fixed** `repeat(" " + (patFactor < 0 ? -(j+1) : (j+1)), Math.abs(patFactor))` — emits a signed
index.

---

### B2. `PatUtils.addImm` cannot accept a signed index
**Where** `src/java/patternfinder/PatUtils.java`, `addImm`
**Origin** ours only — the drop already has the fix (`PatUtils.java:321`)
**Proof** `addImm([1,6,1,4], [-2], 1)` → `ArrayIndexOutOfBoundsException: Index -3 out of bounds for length 4`
(the signed intent was `[1, 4, 1, 4]`)

`muteCode[pat.get(i) - 1]` treats the entry as a 1-based index, so `-2` indexes `-3`.

**Consequence** `addImm` is how a pattern is *walked* — `SuperCheckTask` and `PatternFinder` use it to
extend a code along its pattern. With signed patterns in play, any extension of a downward pattern
crashes. B1 and B2 together mean the signed-pattern feature could not have worked at all without
both being fixed: one breaks printing, the other breaks walking.

**Fixed** index is `Math.abs(pat.get(i)) - 1`, step is `pat.get(i) < 0 ? -2 : 2`.

---

### B3. `PatternFinder.subtCodes` silently discarded mixed-direction code pairs
**Where** `src/java/patternfinder/PatternFinder.java`, `subtCodes(ImmutableIntList, ImmutableIntList)`
**Origin** ours (pre-existing design limit). **The drop FIXES this** — but removes the
odd-difference guard while doing so, which is a new bug in the drop. See
`DROP-BUGS-FOR-AUTHOR.md` item 1.
**Proof** the original returns "reject" for the pair `1 6 1 4` / `1 2 1 8`

A `ThreeState negative` flag required every non-zero step to share a sign, and bailed out otherwise.

**Consequence** This is a **silent false negative**, which is the worst shape for a research tool.
Two codes that genuinely sit on a common pattern — one code number going up while another goes down —
were reported as having no pattern between them. There is no error and no log line; the pair simply
never appears in the results. Any conclusion drawn from "PatternFinder found no pattern here" over
the lifetime of that code is unsound for mixed-direction families.

**Fixed** the difference is taken `line2 - line1` and the sign retained.

---

### B4. `CoverWindow.redoInfo` crashes on a code line ending in a bare `#`
**Where** `src/java/billiards/viewer/CoverWindow.java`, `redoInfo`
**Origin** BOTH — pre-existing here, and present in the drop at `CoverWindow.java:919` in a
**worse** form (see below)
**Proof** the original scan over `preInfo = "1 2 1 4 #"` → `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`

`"1 2 1 4 #".split("#")` has length **1** — Java's `split` discards trailing empty fields — so
`preLine.split("#")[1]` is out of bounds.

**Consequence** `redoInfo` rewrites `cover/info.txt` **in place** and is called at the end of every
cover calculation. A single stray `#` in `cover_stables.txt` or `cover_triples.txt` — easy to leave
behind while editing patterns by hand — aborts that rewrite partway. You lose the pattern
annotations for the run, after having paid the full cover computation.

Worth flagging separately: a naive HashMap rewrite of this method makes it **worse**, because
indexing happens up front and the throw then occurs for *any* such line rather than only one that
matches a cover code. That is the trap I nearly shipped.

**Fixed** `parts.length > 1 ? parts[1].trim() : null`, treating a bare `#` as "listed, no pattern".
Equivalence with the original scan was then proved over 72 cases (10 explicit + 2 documented
divergences + 60 fuzz rounds) by `~/billiards-port-harnesses/billiards/viewer/RedoInfoHarness.java`,
which drives the real private method by reflection.

---

### B5. `Triple.hashCode()` was identity-based while `equals` was value-based
**Where** `billiards/pattern/Triple.java` (the drop's new file)
**Origin** the drop
**Proof** a class with value `equals` and `super.hashCode()` puts two equal instances in a `HashSet`
and the set reports **size 2**

**Consequence** Every hash-based collection of `Triple` silently keeps duplicates. Deduplicating
triples is exactly what this class is for, so a "distinct triples" count or set would be wrong, with
no symptom other than numbers that are quietly too large.

**Fixed** `hashCode` derived from the three code sequences. Asserted in
`~/billiards-port-harnesses/PatternHarness.java` ("equal triples dedupe in a HashSet").

---

### B6. The drop's C++ `standard()` loses one space of padding for `CS`
**Where** `src/backend/cpp/utils.cpp`, `standard()` (drop version)
**Origin** the drop
**Proof** the condition `type == CS || type != OSNO` yields **1** space for `CS`; the Java
`Utils.standard` it was ported from yields **2**. The two agree on `OSO`, `OSNO`, `ONS` and `CNS`, so
`CS` is the only divergence.

The condition is a tautology: `type == CS` implies `type != OSNO`, so the whole thing reduces to
`type != OSNO`.

**Consequence** Cosmetic but persistent: the type name is padded to the width of `OSNO` so the
`(length, sum)` column lines up. Every closed-stable row would be misaligned by one character in
output that Java and C++ both write into the *same* cover files and logs.

**Fixed** extracted as `code_type_padding(CodeType)` and covered by 4 new Boost tests (C++ suite
30 → 34). The extraction was necessary, not stylistic: **`CS` and `OSNO` instances cannot be built
from short code numbers** — brute force over lengths 2–7 with values to 24, plus structured
closed-form candidates, only ever yields `CNS`/`OSO`/`ONS` — so a test using real
`ClassifiedCodeSequence` objects can never reach the branch that was wrong.

---

### B7. The drop's appender threads are non-daemon, and nothing stops them
**Where** `IterateToLimitWindow` / `TextAreaAppender` (drop version), plus `Viewer.shutdown()` at
upstream `Viewer.java:9098`
**Origin** the drop
**Proof** two parts, both checked:
1. `Executors.newSingleThreadScheduledExecutor()` produces a **non-daemon** thread (asserted at runtime).
2. `Viewer.shutdown()` is **defined** at upstream line 9098 and has **zero callers anywhere in the
   drop** — an exhaustive grep across `src/` returns nothing.

**Consequence** The drop creates five such schedulers, one per text area, each polling at 20 Hz for
the lifetime of the window. Ported verbatim, **opening the IterateToLimit window once means the JVM
never exits** — the app appears to quit, all windows close, and the process lingers. The author added
the method to stop them and then never wired it up, so the intended safeguard is inert.

**Fixed** one shared **daemon** scheduler for all five appenders, and `Main.stop() →
viewer.shutdown() → iterateToLimitWindow.shutdown()` actually wired. Verified: the app quits with
exit code 0 and no process remains, both from source and from the packaged `.dmg`.

---

### B8. A throwing `scheduleAtFixedRate` task is dropped silently, forever
**Where** the appender flush loop
**Origin** JDK behaviour the drop's design is exposed to
**Proof** a task that increments a counter and throws ran **exactly once** over 400 ms at a 20 ms
period, then never again — with no output.

**Consequence** If a flush ever threw, that text area would stop updating for the rest of the session
with no error anywhere. The window would look like it had simply stopped finding codes.

**Fixed** the flush body is wrapped in try/catch that reports and keeps the schedule alive.

---

### B9. Six existing unit tests had never run
**Where** `src/test/java/billiards/codeseq/CodeSequenceTest.java`, `ClassifiedCodeSequenceTest.java`
**Origin** BOTH — the drop's copies have 5 + 1 `public static void test...` methods too
**Proof** test-report XML count went from **11 tests across 2 classes** to **17 across 4** after
removing `static` — the two `codeseq` classes produced **no XML at all** beforehand.

Their `@Test` methods were declared `public static`. JUnit 5 does not discover static test methods,
and reports no error, no failure and no skip — they are simply absent.

**Consequence** The only Java tests the project had were silently inert. Any regression in
`CodeSequence` validation, canonicalisation, ordering or code-type classification — the foundation
the whole codebase computes on — would have gone uncaught while the build reported success. All 6
pass once discovered, so nothing was actually broken; the loss was the safety net, for however long
it had been that way.

---

### B10. `classified_code_sequence.hpp` had no include guard
**Where** `src/backend/headers/classified_code_sequence.hpp`
**Origin** ours only — the drop's copy HAS `#pragma once`
**Proof** compiler error on including it a second time:
`error: redefinition of 'ClassifiedCodeSequence'` … `note: unguarded header; consider using #ifdef guards or #pragma once`

**Consequence** The build breaks the moment any translation unit reaches this header by two paths.
It had survived only because `utils.hpp` happened to be the single includer; adding one `#include`
in a new test file was enough to break compilation. A latent trap for the next person adding a file.

**Fixed** `#pragma once` added. Every other header in the tree audited — all guarded.

---

## Part 2 — Certain from the source, but not executed

Kept separate on purpose. Each is a plain reading of the code with no ambiguity, but I did not build
a reproduction, so they are not in Part 1.

### C1. `Main.stop()` destroyed the native pool before draining the executor
**Ours only — the drop's version is correct.** `src/java/billiards/viewer/Main.java`. The original destroyed the SQLite `ConnectionPool` and *then*
called `executor.shutdown()` (which does not wait). Background tasks borrow connections from that
pool, so a task still running during quit could use a destroyed native object — a use-after-free on
the C++ side, whose symptom would be an intermittent crash on exit. **Fixed**: drain the executor
with a 30 s bound first, and skip the destroy entirely if workers are still alive.

### C2. `PatternFinder.singAction` tested the same index twice
**Ours only — the drop has `lines.get(j)` at line 691.**
`if (lines.get(i).isEmpty() || lines.get(i).isEmpty())` — `j` was intended for the second. An empty
line at position `j` was therefore never skipped, so it was passed to `subtCodes` as if it were a
code. **Fixed**.

### C3. The triple parse path accepted codes it had just failed to parse
**Present in the drop too** (`PatternFinder.java:343-353`).
`PatternFinder`, OBO parsing. `valid` starts `true` and is only set `false` inside
`if (codeSeq.isRight())`. When `ClassifiedCodeSequence.create` returns **Left** — the code is illegal
— `valid` stays `true` and the triple is admitted. The single-code branch immediately above rejects
that case, so the two paths disagreed. Harmless before, but `Spattern`'s new constructor derives a
`CodeSequence` from what it is given, so such a triple would take the whole run down. **Fixed**: an
unparseable part now rejects the triple.

### C4. 49 executor shutdowns block the JavaFX application thread
`Viewer.java` and `CycleVaryWindow.java`. Every one of the 49 `Utils.safeShutdownExecutor(x)` call
sites sits inside a `setOnSucceeded` / `setOnCancelled` / `setOnFailed` handler, i.e. on the FX
thread — and the 1-argument overload waits `600 s`, then calls `shutdownNow()` and waits `600 s`
again. Worst case is **20 minutes of frozen UI** per vary or draw completion. This is a plausible
contributor to the freezes tracked in the LiLuMaxVary work.

**Partly addressed.** 18 sites where nothing order-dependent follows are now
`shutdownExecutorAsync`. **31 remain synchronous** (25 in `Viewer.java`, 6 in `CycleVaryWindow.java`)
because roughly 11 of them are followed by `renderRegions(onScreenSequences, ...)` under the comment
*"only render the screen after everything has been loaded"* — there the blocking drain is
load-bearing. Fixing those properly needs a continuation helper (drain off-thread, then
`Platform.runLater(render)`), which is new API beyond the drop; simply lowering the timeout instead
risks `shutdownNow()` interrupting live GMP/MPFR work and truncating results. **Needs a decision.**

### C5. `standard()`'s callers cannot reach two of its branches — a testability gap, not a bug
Recorded because it constrains any future change: no short legal code sequence classifies as `CS` or
`OSNO`, so those two padding branches are unreachable from any test built on real
`ClassifiedCodeSequence` objects. Keep `code_type_padding` separately testable.

---

## Part 3 — Inherited, not verified here

### D1. `fireAway4` calls `makeStarts` with arguments in the wrong order
`src/backend/cpp/vary4.cpp`. `makeStarts(billiard, movesMax, cores, …)` against parameters
`(depth, maxDepth)`, so it returns after a single node whenever `cores <= movesMax`. **Present in
both trees** — ours and the drop — and predates this port. Left untouched: changing it alters search
behaviour and needs the professor's sign-off. Carried forward from earlier notes; **not
independently re-verified in this port.**

### D2. Two defects in the drop deliberately not ported
- `backend_peak_rss_bytes` is guarded by `HAS_SYS_RESOURCE_H` but the macro is defined as
  `HAVE_SYS_RESOURCE_H`, so it always returns −1.
- `PolyVaryTask`'s cached `PixelReader` is a one-time snapshot, so it misses regions drawn mid-run.

Recorded from the drop's source; not reproduced here, since neither was brought across.

---

## Summary

| | Count |
|---|---|
| Reproduced by execution | **10** (B1–B10) |
| Certain from source, not executed | 5 (C1–C5) |
| Inherited / not verified here | 3 (D1, D2) |

**Origin split of B1–B10, each checked against the drop's source:**

| | Bugs |
|---|---|
| Ours only — the drop already fixes it | B1, B2, B10 (+ C1, C2) |
| Ours only — the drop's *feature* fixes it, but adds a new bug doing so | B3 |
| Present in BOTH trees | B4 (worse in the drop), B9, C3 |
| The drop's own | B5, B6, B7, B8 |

**A sendable report containing only the drop's bugs is `DROP-BUGS-FOR-AUTHOR.md`.** Do not send this
file — roughly half of what is in it is ours.

**The two I would act on next**, in order:
1. **C4** — the 31 remaining FX-thread blocks. Needs a call on the continuation helper.
2. **B3's history** — mixed-direction pattern pairs were silently dropped for as long as that code
   stood. Worth re-running PatternFinder over any dataset where "no pattern found" was taken as a
   result.
