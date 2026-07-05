#pragma once

#include "database/viewer.hpp"

#include <cstddef>
#include <string>
#include <vector>

// ---------------------------------------------------------------------------
// EXPERIMENT (Suryansh, 2026): refinement-order invariance probe.
//
// calculate_final_polygon refines an interval polygon against the generated
// curves one at a time, sines-then-cosines, in std::map order. The source
// comment keeps that order deterministic ON PURPOSE. The open question (see
// docs/algorithmic-optimization-opportunities.md, candidate #1) is whether the
// final region is order-INDEPENDENT — if so, the 94%-of-runtime refinement
// chain could be tree-parallelized. Intersection is commutative in the exact
// limit, but refine_polygon rounds outward in MPFI, so the *interval* result
// may be order-sensitive even when the *exact* result is not.
//
// experiment_refine_order recomputes the polygon under several reordered
// refinement sequences and reports, per ordering, whether the result matches
// the canonical order. Pure investigation: the production path is untouched.
// ---------------------------------------------------------------------------
struct RefineOrderReport final {
    bool built = false;          // curves + canonical polygon constructed ok
    bool empty_region = false;   // canonical refinement returned no region
    std::size_t n_sin = 0;       // # sine curves refined against
    std::size_t n_cos = 0;       // # cosine curves refined against
    std::size_t canon_size = 0;  // canonical polygon vertex count

    // --- Parallel non-binding filter prototype (candidate #1 ∪ #3) ---
    // Refinement only shrinks the polygon, so a curve that does not cut the
    // STARTING bounding polygon can never cut any later sub-polygon. So we can
    // test every curve against the fixed start IN PARALLEL, keep only the
    // binding ones, and refine the (hopefully tiny) survivor set sequentially.
    // These fields measure how many curves survive and whether refining by just
    // the survivors reproduces the canonical region exactly.
    bool filter_ran = false;
    std::size_t binding_count = 0;       // curves that cut the starting polygon
    bool filtered_matches_canon = false; // refine-by-survivors == canonical (bit-identical)
    double filtered_hausdorff = 0.0;     // vertex distance filtered vs canonical

    // --- Cheap sign-based binding test (lever 1) ---
    // Same filter idea, but the per-curve test is one interval sign evaluation
    // per start-polygon vertex instead of a full refine_polygon. refine_polygon
    // keeps the POS side, so all-strictly-POS => refinement is a no-op (prune),
    // all-strictly-NEG => the curve empties the region (binding), and any ZERO
    // (interval straddling zero) or mixed signs => keep. Conservative by
    // construction: ZERO never resolves via gradients, it just keeps the curve.
    bool cheap_filter_ran = false;
    std::size_t cheap_binding_count = 0;   // survivors of the cheap test
    bool cheap_matches_canon = false;      // refine-by-cheap-survivors == canonical (bit-identical)
    double cheap_hausdorff = 0.0;          // vertex distance cheap-filtered vs canonical
    std::size_t cheap_missed_binding = 0;  // kept by full filter, pruned by cheap -- MUST be 0
    std::size_t cheap_extra_kept = 0;      // kept by cheap, pruned by full (conservatism cost)
    double full_filter_seconds = 0.0;      // wall time of the full-refine binding tests
    double cheap_filter_seconds = 0.0;     // wall time of the cheap sign binding tests

    struct Alt final {
        std::string name;            // ordering label
        bool ok = false;             // refinement completed without going empty
        std::size_t size = 0;        // resulting vertex count
        bool same_size = false;      // == canonical vertex count
        bool same_equations = false; // identical multiset of edge equations
        bool bit_identical = false;  // matched vertices identical to the bit
        std::size_t eq_diff_count = 0;   // # edge equations not shared with canonical
        double max_endpoint_delta = 0.0; // max |Δ endpoint| over equation-matched vertices
        // Geometric distance independent of edge labeling: symmetric nearest-
        // neighbour (Hausdorff) distance between the two vertex sets. Tiny here
        // even when edge equations differ => the region is geometrically the
        // same and the edge relabel is benign (a near-degenerate vertex).
        double vertex_hausdorff = 0.0;
        std::vector<std::string> edges_only_in_canon; // boundary curves canonical has, alt lost
        std::vector<std::string> edges_only_in_alt;   // boundary curves alt gained
        // Gate #1: does the reordering change stable_left_right output? That is
        // the vector wrapper.cpp equality-checks ("pattern changed do it the
        // slow way"). left_rights_match == true means the reorder is safe for
        // the downstream consistency guard even if an edge was relabeled.
        bool left_rights_match = false;        // exact ordered vector equality (what the guard checks)
        bool left_rights_same_multiset = false; // same elements, possibly rotated -> fixable by canonical rotation
        bool left_rights_threw = false;        // LeftRightVariant threw (line edge / missing key)
    };
    std::vector<Alt> alts;
};

RefineOrderReport experiment_refine_order(const CodeSequence& code_sequence, const CodeType code_type);

boost::optional<Stable> calculate_stable(const CodeSequence& code_sequence, const CodeType code_type);

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type);

boost::optional<Stable> calculate_stable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights);

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights);
