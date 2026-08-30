# Bugs found in the Aug 11, 2026 drop

Found while porting `BilliardsEverythingSpecialOpt-Khuu-Aug11,2026` into our Java 17 / C++17 tree
during August 2026. Reported 2026-08-24.

**Scope note.** Only bugs I could locate **in the drop's own source** are listed. I found other
defects during the port, but they turned out to be pre-existing in *our* tree — and in five cases the
drop already contains the fix. Those are listed at the end under *Things the drop gets right*.

Line numbers refer to the drop as received.

---

## 1. `subtCodes` no longer rejects an odd difference — silently produces a wrong pattern

**File** `src/java/patternfinder/PatternFinder.java` — `subtCodes(ImmutableIntList, ImmutableIntList)`

```java
final int[] result = new int[line1.size()];
for (int i = 0; i < line1.size(); i++) {
    int diff = line2.get(i) - line1.get(i);
    result[i] = diff / 2;                 // <-- no check that diff is even
}
```

The previous version guarded this with `if (value != (value / 2) * 2) return Optional.empty();`.
A pattern step must be a multiple of two; an odd difference means the two codes are not on a common
pattern at all.

**Reproduction** — `line1 = 1 2 1 4`, `line2 = 1 3 1 4` (differ by 1 at index 1):

```
drop's subtCodes pattern = [0, 0, 0, 0]   <- index 1 reads as "no change", but the codes DO differ
previous behaviour       = Optional.empty()  (pair correctly rejected)
```

Integer division truncates `1 / 2` to `0`, so the pattern claims nothing moves.

**It then chains into a crash.** That all-zero pattern is passed to `new Spattern(...)`, whose
`pat.max() == 0` "Bad pattern" guard is commented out in this drop:

```java
public Spattern(ImmutableIntList pat, ImmutableIntList ex) {
    // if (pat.max() == 0) {
    // throw new RuntimeException("Bad pattern");
    // }
```

With an all-zero pattern, `makeBase` finds no coefficients, leaves `base` empty, and the constructor's
`CodeSequence.create(this.base).get()` calls `.get()` on a `Left` — an unhandled exception with no
useful message.

*(The commented-out guard was presumably in the way because `pat.max() == 0` is also true for an
all-negative pattern, which is legal now. `pat.allSatisfy(n -> n == 0)` expresses the original intent
and works with signed patterns.)*

**Consequence** Two codes that are not on a common pattern are either recorded as sharing an
all-zero pattern, or take the run down. Worst case is the quiet one: a wrong pattern in the results.

---

## 2. `redoInfo` throws on a code line ending in a bare `#`

**File** `src/java/billiards/viewer/CoverWindow.java:919`

```java
if (preLine.contains("#")) {
    preInfoMap.put(trimmed, preLine.split("#")[1].trim());   // <-- [1] may not exist
}
```

`"1 2 1 4 #".split("#")` has length **1** — Java's `split` discards trailing empty fields — so
`[1]` is out of bounds.

**Reproduction**
```
ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
```

**This is worse than in the version it replaced.** The old code indexed `[1]` inside the per-row
scan, so it only threw when such a line actually *matched* a cover code. Building the map up front
means **any** bare-`#` line in `cover_stables.txt` / `cover_triples.txt` throws, matched or not.

**Consequence** `redoInfo` rewrites `cover/info.txt` in place at the end of every cover calculation.
One stray `#` — easy to leave while hand-editing patterns — aborts that rewrite, losing the pattern
annotations after the full cover cost has already been paid.

**Suggested fix** `final String[] parts = preLine.split("#"); ... parts.length > 1 ? parts[1].trim() : null`

Two behaviour changes in the same rewrite worth a second look, independent of the crash:
- **duplicates**: plain `put` makes the **last** occurrence win; the scan it replaced `break`s on the
  **first**. `putIfAbsent`, or a `containsKey` check, preserves the old semantics.
- **empty suffix**: `if (suffix != null && !suffix.isEmpty())` drops the `" # "` that the old code
  emitted for a line with `#` and nothing after it. Reasonable, but it is a change.

---

## 3. `Triple.hashCode()` is identity-based while `equals` is value-based

**File** `src/java/billiards/pattern/Triple.java:77`

```java
@Override
public int hashCode() {
    return super.hashCode();      // identity
}

@Override
public boolean equals(Object obj) {
    final Triple other = (Triple) obj;
    return (other.negativeStable.equals(this.negativeStable)) && ...   // value
}
```

**Reproduction** two equal `Triple`s added to a `HashSet` → `size() == 2`.

**Consequence** Every hash-based collection of `Triple` silently keeps duplicates. Since
deduplicating triples is what the type is for, a "distinct triples" count comes out too large with no
symptom.

**Also** `equals` casts before any type check, so `equals(somethingElse)` throws
`ClassCastException` and `equals(null)` throws `NullPointerException`. An `instanceof` guard fixes
both.

---

## 4. C++ `standard()` loses one space of padding for `CS`

**File** `src/backend/cpp/utils.cpp:83`

```cpp
if(type == CodeType::CS || type != CodeType::OSNO) oss << " ";
```

The condition is a tautology: `type == CS` implies `type != OSNO`, so it reduces to `type != OSNO`
and `CS` gets **one** space. The Java `Utils.standard` this was ported from gives `CS` **two**:

```java
if (codeStr.equals(" - CS"))        codeStr += "  ";   // two
else if (!codeStr.equals(" - OSNO")) codeStr += " ";   // one
```

**Verified** the two implementations agree on `OSO`, `OSNO`, `ONS` and `CNS` — `CS` is the only
divergence.

**Consequence** The type name is padded to the width of `OSNO` so the `(length, sum)` column lines
up. Every closed-stable row is misaligned by one character, in output that the Java and C++ sides
both write into the *same* cover files and logs.

**Testing note** `CS` and `OSNO` instances cannot be built from short code numbers — I brute-forced
lengths 2–7 with values up to 24, plus structured closed-form candidates, and only ever got
`CNS`/`OSO`/`ONS`. So no test built on real `ClassifiedCodeSequence` objects can reach either branch.
Extracting the padding into its own function (e.g. `code_type_padding(CodeType)`) makes all five
branches directly testable.

---

## 5. Five non-daemon threads per window, and the method that stops them is never called

**Files** `src/java/billiards/viewer/IterateToLimitWindow.java` (`TextAreaAppender`),
`src/java/billiards/viewer/Viewer.java:9098`

```java
class TextAreaAppender {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
```

Instantiated five times, once per text area, each polling at 20 Hz for the window's lifetime.

**Verified, both halves:**
1. `Executors.newSingleThreadScheduledExecutor()` produces a **non-daemon** thread (asserted at
   runtime).
2. `Viewer.shutdown()` is **defined** at `Viewer.java:9098` and has **zero callers** — an exhaustive
   grep across `src/` finds none. Nothing in `Main.stop()` reaches it.

**Consequence** Opening the IterateToLimit window once means **the JVM never exits**. All windows
close, the app looks quit, and the process lingers holding five polling threads. The safeguard exists
but is inert.

**Suggested fix** either wire `Main.stop() -> viewer.shutdown()`, or make the scheduler threads
daemons so a missed shutdown cannot hang the JVM — ideally both. One shared scheduler for all five
appenders also drops the thread count from five to one.

---

## 6. The appender's flush is unguarded, so one exception silences a text area for the session

**File** `src/java/billiards/viewer/IterateToLimitWindow.java` — `TextAreaAppender` constructor

```java
scheduler.scheduleAtFixedRate(() -> {
    if (!queue.isEmpty()) { ... Platform.runLater(...); }
}, 0, 50, TimeUnit.MILLISECONDS);
```

`ScheduledExecutorService` **permanently cancels** a periodic task that throws, and reports nothing.

**Reproduction** a task incrementing a counter and throwing, at a 20 ms period over 400 ms, ran
**exactly once** — then never again, with no output.

**Consequence** If a flush ever threw, that text area stops updating for the rest of the session with
no error anywhere. It looks like the run simply stopped finding codes.

**Suggested fix** wrap the flush body in `try/catch` and log.

---

## 7. Appending to a trimmed text area glues the first new entry onto the last restored line

**File** `src/java/billiards/viewer/IterateToLimitWindow.java`

`addToContent` appends via `textArea.appendText(batch)`, while the loader ends with:

```java
textArea.setText(newContent.toString().trim());   // no trailing newline
```

So after content is restored from `iterToLimit.txt`, the first appended entry continues the last
restored line:

```
1 6 1 8 & 2 41  - CS  (28, 220) 1 5 12 ... & ...
```

**Consequence** One corrupted line per text area per session, in a file that is saved back to disk
and re-parsed on the next load — so it persists.

**Suggested fix** in the flush, insert a newline when the area is non-empty and does not already end
with one.

---

## 8. A triple whose code fails to parse is still accepted

**File** `src/java/patternfinder/PatternFinder.java:343-353`

```java
boolean valid = true;
for (int j = 0; j < 3; j++) {
    final Either<InvalidCodeSequence, ClassifiedCodeSequence> codeSeq =
            ClassifiedCodeSequence.create(codePart);
    if (codeSeq.isRight()) {
        if (!correctType(codeSeq.get().codeType)) valid = false;
    }
    // no else -- a Left leaves valid == true
}
if (valid) tripTasks.add(code);
```

When `create` returns **Left** — the code is illegal — `valid` stays `true` and the triple is
admitted. The single-code branch immediately above this one *does* reject that case, so the two paths
disagree.

**Consequence** Previously harmless. It stops being harmless in this drop, because `Spattern`'s
constructor now derives a `CodeSequence` from what it is handed, so such a triple throws from inside
a double loop over all line pairs and takes the whole run down.

---

## 9. Six of the seven `codeseq` unit tests never run

**Files** `src/test/java/billiards/codeseq/CodeSequenceTest.java`,
`ClassifiedCodeSequenceTest.java`

```java
@Test
public static void testEmptyCodeSequence() { ... }
```

JUnit 5 does **not** discover `static` test methods, and reports no error, no failure and no skip —
they are simply absent from the run.

**Verified** in our copy of the same files, removing `static` took the report from **11 tests** to
**17**; the two `codeseq` classes had produced **no XML output at all** beforehand. Your copy has 5
static methods in `CodeSequenceTest` and 1 in `ClassifiedCodeSequenceTest`
(`testReportedLongOsnoCalculateInput` is correctly non-static, so it does run).

**Consequence** The tests covering `CodeSequence` validation, canonicalisation, ordering and
code-type classification are inert. A regression in that foundation passes CI silently. All six pass
once discovered, so nothing is currently broken — what is missing is the safety net.

**Fix** drop `static`.

---

## 10. `backend_peak_rss_bytes` always returns −1

**File** `src/backend/cpp/wrapper.cpp`

```cpp
line   37:  #define HAVE_SYS_RESOURCE_H 1
line 1409:  #if defined(HAS_SYS_RESOURCE_H)
line 1421:  #endif // HAS_SYS_RESOURCE_H
```

Defined as `HAVE_`, tested as `HAS_`. The guarded block never compiles in.

**Consequence** Peak-RSS reporting silently returns −1 everywhere, so any memory diagnostics built on
it are meaningless. Given that memory pressure is the known failure mode in the vary path, this is
the instrument you would most want working.

---

## 11. `PolyVaryTask`'s cached `PixelReader` is a one-time snapshot

**File** `src/java/billiards/viewer/PolyVaryTask.java`

The `PixelReader` is captured once and reused, so it does not observe regions drawn after capture.

**Consequence** `pixelColor()` misses regions drawn mid-run, so points already covered look
uncovered and get re-varied.

*Noted from reading the source — unlike items 1–10 I did not build a reproduction for this one.*

---

# Things the drop gets right that our tree got wrong

Listed for completeness, and so the report is not mistaken for a one-way audit. In each case I took
the drop's version.

| Area | Our tree | The drop |
|---|---|---|
| `PatUtils.printPat` | `repeat(" " + (j+1), pat.get(j))` — `NegativeArraySizeException` on a negative entry | correctly emits a signed index with `Math.abs(patFactor)` |
| `PatUtils.addImm` | `muteCode[pat.get(i) - 1]` — `ArrayIndexOutOfBoundsException` on a signed index | `Math.abs(pat.get(i)) - 1` with a `±2` step |
| `subtCodes` sign handling | required every non-zero step to share a sign, so mixed-direction pairs were **silently rejected** | signs retained — a real correctness improvement (see item 1 for the guard that went with it) |
| `Main.stop()` | destroyed the native SQLite pool **before** shutting down the executor — a potential use-after-free on exit | drains the executor with a 30 s bound first, and skips the destroy if workers remain |
| `PatternFinder.singAction` | `lines.get(i).isEmpty() \|\| lines.get(i).isEmpty()` — same index twice | `lines.get(j).isEmpty()` |
| `classified_code_sequence.hpp` | no include guard — a second `#include` breaks the build | `#pragma once` present |

---

# How these were verified

Items 1–10 were reproduced by **running** the code, not by reading it. Each original implementation
was transcribed verbatim into a small harness that demonstrates the failure; the `redoInfo` rewrite
was additionally checked against the previous implementation over 72 cases (10 explicit + 2
documented divergences + 60 fuzz rounds) to separate the crash from intended behaviour changes.

Item 11 is from source inspection only, and is marked as such.

Happy to share the harnesses or walk through any of these.
