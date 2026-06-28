// Suryansh Ankur, 2026
// Refinement-order invariance experiment for the billiards backend.
//
// WHY THIS EXISTS: calculate_final_polygon refines the interval (MRR) region
// against the generated curves one at a time, sines-then-cosines, in std::map
// order. That order is kept deterministic ON PURPOSE. ~94% of the per-region
// compute kernel lives in this chain (see docs/performance-changes.md), and
// each step depends on the previous one, so it cannot be turned into a naive
// parallel_for.
//
// docs/algorithmic-optimization-opportunities.md candidate #1 asks whether the
// refinement is order-INDEPENDENT: intersection of constraints is commutative
// in the exact limit, so a tree-reduction (refine sub-batches in parallel, then
// intersect) could attack the 94% directly. The risk: refine_polygon rounds
// outward in MPFI, so the *interval* result may be order-sensitive even when
// the *exact* result is not.
//
// This harness answers that empirically, with NO change to the production path:
// for each code it recomputes the region under several reordered refinement
// sequences (full reverse, cosines-first, interleaved) and reports, per
// ordering, whether the result is bit-identical to the canonical order,
// structurally identical (same vertices + edge equations) but numerically
// shifted, or structurally different -- plus the vertex Hausdorff distance,
// which says whether the REGION ITSELF moved regardless of edge labeling.
//
//   IDENTICAL          -> reordering is provably safe for this code
//   same struct, Δ>0   -> reordering sound only within a tolerance (Q2)
//   different struct   -> edge labeling changed; check vertex Hausdorff Δ to
//                         tell a benign relabel (Δ~1e-49) from a real divergence
//
// CODES: by default reads one code per line from `experiment_codes.txt` in the
// working directory; pass a path as argv[1] (or `-Pcodes=<path>` via Gradle) to
// use another file. Each line may carry the app's usual decoration -- an index
// like "3:", a "CS (28, 220)" prefix, a ", zy" suffix -- the parser strips
// parenthesized groups and a leading "N:" and keeps the integer run. If no file
// is found, falls back to four built-in codes.
//
// Run with: ./gradlew experimentBackend            (uses experiment_codes.txt)
//           ./gradlew experimentBackend -Pcodes=tmp/my_codes.txt

#include "code_sequence.hpp"
#include "code_type.hpp"
#include "equations.hpp"

#include <boost/variant.hpp>

#include <cctype>
#include <cstdint>
#include <cstdio>
#include <exception>
#include <fstream>
#include <string>
#include <vector>

namespace {

struct NamedCode {
    std::string name;
    std::vector<std::int32_t> code;
};

// Pull the code-sequence integers out of one decorated line. Drops
// parenthesized groups (e.g. "(28, 220)") and a leading "N:" index, then keeps
// every integer token that remains. Letters (CS, zy, ...) carry no digits so
// they are ignored automatically.
std::vector<std::int32_t> parse_code_line(const std::string& raw) {
    std::string s;
    int depth = 0;
    for (const char c : raw) {
        if (c == '(' || c == '[' || c == '{') { ++depth; continue; }
        if (c == ')' || c == ']' || c == '}') { if (depth > 0) { --depth; } continue; }
        if (depth == 0) { s.push_back(c); }
    }

    // Strip a leading "N:" index if everything before the colon is digits/space.
    const auto colon = s.find(':');
    if (colon != std::string::npos) {
        bool only_digits_space = true;
        for (std::size_t i = 0; i < colon; ++i) {
            if (!std::isdigit(static_cast<unsigned char>(s[i])) &&
                !std::isspace(static_cast<unsigned char>(s[i]))) {
                only_digits_space = false;
                break;
            }
        }
        if (only_digits_space) { s = s.substr(colon + 1); }
    }

    std::vector<std::int32_t> nums;
    std::size_t i = 0;
    while (i < s.size()) {
        const bool neg = (s[i] == '-' && i + 1 < s.size() &&
                          std::isdigit(static_cast<unsigned char>(s[i + 1])));
        if (std::isdigit(static_cast<unsigned char>(s[i])) || neg) {
            std::size_t j = i + (neg ? 1 : 0);
            while (j < s.size() && std::isdigit(static_cast<unsigned char>(s[j]))) { ++j; }
            try {
                nums.push_back(static_cast<std::int32_t>(std::stol(s.substr(i, j - i))));
            } catch (const std::exception&) {
                // overflow / bad token -> skip it
            }
            i = j;
        } else {
            ++i;
        }
    }
    return nums;
}

// Read codes from a file, one per line. Lines yielding fewer than 4 integers
// (blank lines, headers) are skipped. Returns empty if the file can't be opened.
std::vector<NamedCode> load_codes_from_file(const std::string& path) {
    std::vector<NamedCode> codes;
    std::ifstream in(path);
    if (!in) { return codes; }

    std::string line;
    std::size_t lineno = 0;
    while (std::getline(in, line)) {
        ++lineno;
        std::vector<std::int32_t> nums = parse_code_line(line);
        if (nums.size() < 4) { continue; }
        codes.push_back(NamedCode{"L" + std::to_string(lineno), std::move(nums)});
    }
    return codes;
}

const std::vector<NamedCode>& builtin_codes() {
    static const std::vector<NamedCode> codes = {
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
    return codes;
}

const char* verdict(const RefineOrderReport::Alt& a) {
    if (!a.ok) { return "EMPTY (reorder drove region empty)"; }
    if (a.bit_identical) { return "IDENTICAL (bit-for-bit)"; }
    if (a.same_size && a.same_equations) { return "SAME STRUCT, shifted endpoints"; }
    return "DIFFERENT STRUCTURE (edge relabel)";
}

} // namespace

int main(int argc, char** argv) {
    const std::string path = (argc > 1) ? argv[1] : "experiment_codes.txt";

    std::vector<NamedCode> codes = load_codes_from_file(path);
    bool from_file = !codes.empty();
    if (!from_file) {
        codes = builtin_codes();
    }

    std::printf("# refinement-order invariance experiment\n");
    std::printf("# canonical order = sines then cosines, std::map order\n");
    std::printf("# each alternative is a deterministic permutation of the same curve set\n");
    if (from_file) {
        std::printf("# source: %s (%zu codes)\n\n", path.c_str(), codes.size());
    } else {
        std::printf("# source: built-in codes (no readable '%s' found)\n\n", path.c_str());
    }

    // Running tallies so a large batch gives an at-a-glance verdict.
    std::size_t n_checked = 0;
    std::size_t n_all_identical = 0;   // every alternative bit-identical
    std::size_t n_region_invariant = 0; // every alternative within 1e-30 Hausdorff
    std::size_t n_region_moved = 0;    // some alternative moved a vertex >= 1e-30
    std::size_t n_lr_rotated = 0;       // left_rights same set but rotated (fix: canonical rotation)
    std::size_t n_lr_content_changed = 0; // left_rights element changed (fix: canonical edge selection)
    double worst_hausdorff = 0.0;
    std::string worst_hausdorff_code;
    std::size_t filter_total_curves = 0;   // summed across codes
    std::size_t filter_total_binding = 0;
    std::size_t filter_match = 0;          // codes where filtered == canonical
    std::size_t filter_mismatch = 0;       // codes where it differed (should be 0)

    for (const auto& nc : codes) {
        try {
            const auto created = CodeSequence::create(nc.code);
            const CodeSequence* cs = boost::get<CodeSequence>(&created);
            if (cs == nullptr) {
                std::printf("== %s ==\n   SKIPPED (invalid code sequence)\n\n", nc.name.c_str());
                continue;
            }
            const CodeType type = cs->type();
            const RefineOrderReport r = experiment_refine_order(*cs, type);

            std::printf("== %s ==\n", nc.name.c_str());
            if (!r.built) {
                std::printf("   skipped: unsupported code type (not OSO/CS/OSNO stable)\n\n");
                continue;
            }
            if (r.empty_region) {
                std::printf("   canonical refinement produced no region; nothing to compare\n\n");
                continue;
            }

            std::printf("   curves: %zu sin + %zu cos = %zu refinement steps; canonical polygon = %zu vertices\n",
                        r.n_sin, r.n_cos, r.n_sin + r.n_cos, r.canon_size);

            const std::size_t total_curves = r.n_sin + r.n_cos;
            if (r.filter_ran && total_curves > 0) {
                const double pct_pruned = 100.0 * static_cast<double>(total_curves - r.binding_count)
                                          / static_cast<double>(total_curves);
                std::printf("   filter: %zu/%zu curves binding (%.1f%% pruned); filtered region: %s",
                            r.binding_count, total_curves, pct_pruned,
                            r.filtered_matches_canon ? "IDENTICAL to canonical"
                                                     : "DIFFERS from canonical");
                if (!r.filtered_matches_canon) {
                    std::printf(" (Hausdorff Δ = %.3e)", r.filtered_hausdorff);
                }
                std::printf("\n");
            }

            ++n_checked;
            bool all_identical = true;
            bool region_invariant = true;
            bool lr_rotated = false;       // some alt: same left_rights set, different order
            bool lr_content_changed = false; // some alt: left_rights element actually changed
            for (const auto& a : r.alts) {
                std::printf("   %-15s -> %-38s", a.name.c_str(), verdict(a));
                if (a.ok && a.same_size && a.same_equations) {
                    std::printf("  (max endpoint Δ = %.3e)", a.max_endpoint_delta);
                } else if (a.ok) {
                    std::printf("  (vertices: %zu vs %zu; %zu/%zu edge eqs differ)",
                                a.size, r.canon_size, a.eq_diff_count, r.canon_size);
                }
                if (a.ok) {
                    std::printf("\n%18svertex Hausdorff Δ = %.3e", "", a.vertex_hausdorff);
                    // Gate #1: the downstream consistency guard wrapper.cpp uses.
                    const char* lr = a.left_rights_threw ? "THREW"
                                   : a.left_rights_match ? "match (identical)"
                                   : a.left_rights_same_multiset ? "rotated (same set, diff order)"
                                   : "CONTENT CHANGED (relabel)";
                    std::printf("; left_rights: %s", lr);
                    for (const auto& e : a.edges_only_in_canon) {
                        std::printf("\n%18s  canon-only edge: %s", "", e.c_str());
                    }
                    for (const auto& e : a.edges_only_in_alt) {
                        std::printf("\n%18s  alt-only   edge: %s", "", e.c_str());
                    }
                }
                std::printf("\n");

                if (!a.bit_identical) { all_identical = false; }
                if (!a.ok || a.vertex_hausdorff >= 1e-30) { region_invariant = false; }
                if (a.ok && !a.left_rights_match) {
                    if (a.left_rights_threw || !a.left_rights_same_multiset) {
                        lr_content_changed = true;
                    } else {
                        lr_rotated = true;
                    }
                }
                if (a.vertex_hausdorff > worst_hausdorff) {
                    worst_hausdorff = a.vertex_hausdorff;
                    worst_hausdorff_code = nc.name;
                }
            }
            if (all_identical) { ++n_all_identical; }
            if (region_invariant) { ++n_region_invariant; } else { ++n_region_moved; }
            if (lr_rotated) { ++n_lr_rotated; }
            if (lr_content_changed) { ++n_lr_content_changed; }
            if (r.filter_ran) {
                filter_total_curves += (r.n_sin + r.n_cos);
                filter_total_binding += r.binding_count;
                if (r.filtered_matches_canon) { ++filter_match; } else { ++filter_mismatch; }
            }

            std::printf("\n");
            std::fflush(stdout);
        } catch (const std::exception& e) {
            std::printf("== %s ==\n   SKIPPED (%s)\n\n", nc.name.c_str(), e.what());
            std::fflush(stdout);
        }
    }

    // Summary -----------------------------------------------------------------
    std::printf("================ SUMMARY ================\n");
    std::printf("codes compared            : %zu\n", n_checked);
    std::printf("all orderings bit-identical: %zu\n", n_all_identical);
    std::printf("region-invariant (Δ<1e-30): %zu\n", n_region_invariant);
    std::printf("region MOVED (Δ>=1e-30)    : %zu\n", n_region_moved);
    std::printf("left_rights rotated only   : %zu  (same set, diff order -> fix: canonical rotation)\n", n_lr_rotated);
    std::printf("left_rights CONTENT changed: %zu  (relabel -> fix: canonical edge selection)\n", n_lr_content_changed);
    std::printf("worst vertex Hausdorff Δ   : %.3e", worst_hausdorff);
    if (!worst_hausdorff_code.empty()) {
        std::printf("  (code %s)", worst_hausdorff_code.c_str());
    }
    std::printf("\n");
    if (n_region_moved == 0 && n_checked > 0) {
        std::printf("=> region order-invariant for every code (only edge labels may flip)\n");
    } else if (n_region_moved > 0) {
        std::printf("=> %zu code(s) had a region move materially -- inspect those above\n", n_region_moved);
    }

    std::printf("\n--- parallel non-binding filter ---\n");
    std::printf("filtered == canonical      : %zu / %zu codes  (mismatches: %zu)\n",
                filter_match, filter_match + filter_mismatch, filter_mismatch);
    if (filter_total_curves > 0) {
        const double prune = 100.0 * static_cast<double>(filter_total_curves - filter_total_binding)
                             / static_cast<double>(filter_total_curves);
        std::printf("total curves               : %zu\n", filter_total_curves);
        std::printf("binding (survive filter)   : %zu  (%.1f%% pruned)\n",
                    filter_total_binding, prune);
        std::printf("=> sequential refinement chain shrinks from %zu to %zu steps overall\n",
                    filter_total_curves, filter_total_binding);
    }

    return 0;
}
