// Deterministic compute micro-benchmark for the billiards backend.
//
// WHY THIS EXISTS: a full LiLuMaxVary / AutoPolyVary run explores a
// workload-dependent set of codes, so its wall-clock swings run-to-run
// (per-hole times observed from ~8 s to ~80 s). That noise makes it
// impossible to tell whether a backend change (MPFR cache freeing,
// asio->TBB conversions, parallelism tweaks) actually helped.
//
// This harness sidesteps the noise: it calls calculate_stable /
// calculate_unstable on a handful of FIXED, hardcoded code sequences.
// That function is the dominant per-hole compute kernel (it drives
// generate_curves_lr, calculate_bounding_inequalities, points_and_stuff
// and calculate_final_polygon) and it is deterministic on a fixed code.
// Same input -> same work every run, so min/median timings are directly
// comparable across builds.
//
// It deliberately does NOT call the vary_* entry points: those are the
// non-deterministic part we are trying to escape.
//
// Run with: ./gradlew benchBackend

#include "code_sequence.hpp"
#include "code_type.hpp"
#include "equations.hpp"
#include "numbers.hpp"

#include <boost/optional.hpp>

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <exception>
#include <iterator>
#include <numeric>
#include <string>
#include <vector>

#include <sys/resource.h>

namespace {

struct NamedCode {
    std::string name;
    std::vector<CodeNumber> code;
};

// Process-lifetime peak RSS in MB (same source as backend_peak_rss_bytes:
// getrusage). macOS reports ru_maxrss in bytes; Linux in kilobytes.
double peak_rss_mb() {
    rusage ru{};
    getrusage(RUSAGE_SELF, &ru);
#if defined(__APPLE__)
    return static_cast<double>(ru.ru_maxrss) / (1024.0 * 1024.0);
#else
    return static_cast<double>(ru.ru_maxrss) / 1024.0;
#endif
}

// volatile sink so -O3 cannot elide the calculate_* call as dead code.
volatile std::size_t g_sink = 0;

double time_one_call(const CodeSequence& cs, const CodeType type) {
    const auto start = std::chrono::steady_clock::now();
    if (is_stable(type)) {
        const auto stable = calculate_stable(cs, type);
        g_sink += stable ? 1u : 0u;
    } else {
        const auto unstable = calculate_unstable(cs, type);
        g_sink += unstable ? 1u : 0u;
    }
    const auto end = std::chrono::steady_clock::now();
    return std::chrono::duration<double, std::milli>(end - start).count();
}

} // namespace

int main() {
    // Real codes lifted from a LiLuMaxVary run: a small one for a fast
    // signal, a medium, and two big dominators. Swap these freely - any
    // valid code sequence works; the harness reports what it resolved to.
    const std::vector<NamedCode> codes = {
        {"small", {1,5,16,6,21,1,5,18,6,18,5,1,21,6,16,5,1,19,6,19}},
        {"medium", {1,5,14,4,10,4,16,4,10,4,14,4,10,4,16,4,10,4,14,5,1,17,4,10,4,14,5,1,18,
                    1,5,14,4,10,4,17,1,5,14,5,1,17,4,10,4,14,5,1,18,1,5,14,4,10,4,17}},
        {"big_a", {1,5,14,4,12,4,14,5,1,19,6,18,6,20,6,18,6,20,6,18,6,19,1,5,14,4,12,4,14,5,1,
                   19,6,18,6,21,1,5,18,6,20,6,17,1,6,1,17,6,20,6,17,1,6,1,17,5,1,21,6,16,4,10,
                   4,16,6,21,1,5,17,1,6,1,17,6,20,6,17,1,6,1,17,6,20,6,18,5,1,21,6,18,6,19}},
        {"big_b", {1,5,15,1,6,1,19,6,17,1,6,1,17,5,1,21,6,16,4,10,4,15,1,6,1,19,6,17,1,6,1,17,
                   5,1,21,6,16,4,10,4,16,6,21,1,5,17,1,6,1,17,6,19,1,6,1,15,4,10,4,16,6,21,1,5,
                   17,1,6,1,17,6,19,1,6,1,15,5,1,19,6,19,1,5,15,1,6,1,19,6,18,6,19,1,6,1,15,5,1,
                   19,6,19}},
    };

    const int warmup = 1;
    const int reps = 5;

    std::printf("# deterministic backend compute benchmark\n");
    std::printf("# calculate_stable/unstable on fixed codes; %d reps (+%d warmup discarded)\n",
                reps, warmup);
    std::printf("# times in ms; 'min' is the cleanest signal (least OS interference)\n\n");
    std::printf("%-8s %4s %7s %10s %10s %10s\n",
                "code", "len", "type", "min", "median", "mean");

    for (const auto& nc : codes) {
        try {
            const CodeSequence cs{nc.code};
            const CodeType type = cs.type();
            const int len = static_cast<int>(std::distance(cs.begin(), cs.end()));
            const char* const ts = is_stable(type) ? "stable" : "unstab";

            for (int i = 0; i < warmup; ++i) {
                (void) time_one_call(cs, type);
            }

            std::vector<double> samples;
            samples.reserve(static_cast<std::size_t>(reps));
            for (int i = 0; i < reps; ++i) {
                samples.push_back(time_one_call(cs, type));
            }

            std::sort(samples.begin(), samples.end());
            const double tmin = samples.front();
            const double tmed = samples[samples.size() / 2];
            const double tmean =
                std::accumulate(samples.begin(), samples.end(), 0.0) /
                static_cast<double>(samples.size());

            std::printf("%-8s %4d %7s %10.1f %10.1f %10.1f\n",
                        nc.name.c_str(), len, ts, tmin, tmed, tmean);
            std::fflush(stdout);
        } catch (const std::exception& e) {
            std::printf("%-8s  SKIPPED (%s)\n", nc.name.c_str(), e.what());
            std::fflush(stdout);
        }
    }

    std::printf("\npeak RSS: %.1f MB\n", peak_rss_mb());
    return 0;
}
