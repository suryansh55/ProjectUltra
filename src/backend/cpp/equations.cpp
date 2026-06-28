// george jun11th 2021 to print the shooting vector, uncomment the lines with label_shooting_vector, (Note: 15 lines in total)

#include "equations.hpp"
#include "bounding_region.hpp"
#include "conversion.hpp"
#include "evalf.hpp"
#include "general.hpp"
#include "refine.hpp"
#include "reorder.hpp"
#include "shooting_vectors.hpp"
#include "trig_identities.hpp"
#include "unfolding.hpp"

#include <algorithm>
#include <limits>
#include <map>
#include <set>
#include <string>
#include <vector>

// WARNING: always make this class a temporary
class LeftRightVariant final : public boost::static_visitor<LeftRight> {
  private:
    const CurvesLR& curves;

  public:
    explicit LeftRightVariant(const CurvesLR& curves_)
        : curves{curves_} {
    }

    LeftRight operator()(const EquationGradient<XY, LinComArrZ<XYEta>>& eq_grad) const {
        std::ostringstream err;
        err << "line " << eq_grad.equation << " in LeftRightVariant";
        throw std::runtime_error(err.str());
    }

    LeftRight operator()(const EquationGradient<XY, LinComMapZ<Sin<LinComArrZ<XY>>>>& eq_grad) const {
        // We only return the first one
        return curves.first.at(eq_grad.equation).at(0);
    }

    LeftRight operator()(const EquationGradient<XY, LinComMapZ<Cos<LinComArrZ<XY>>>>& eq_grad) const {
        return curves.second.at(eq_grad.equation).at(0);
    }
};

static std::vector<LeftRight> stable_left_right(const IntervalPolygon& polygon, const CurvesLR& curves) {

    std::vector<LeftRight> result;
    for (const auto& int_pair : polygon) {
        const auto left_right = boost::apply_visitor(LeftRightVariant{curves}, int_pair.equation);
        result.push_back(left_right);
        

//ridderikhoff aug 27,2019 didn't print anything
       //std::cout << left_right << "\n";

    }

    return result;
}

static std::pair<LeftRight, LeftRight> unstable_left_right(const IntervalLineSegment& line_seg, const CurvesLR& curves) {

    const auto eq0 = boost::apply_visitor(LeftRightVariant{curves}, line_seg.equation0);
    const auto eq1 = boost::apply_visitor(LeftRightVariant{curves}, line_seg.equation1);

//george aug 27,2019
//std::cout << eq0 << "\n";
//std::cout << eq1 << "\n";

    return {eq0, eq1};
}

// TODO replace these with more maps
static auto stable_equations_to_string(const IntervalPolygon& polygon, const std::array<LinComArrZ<XYEta>, 3>& inverse_perm_eta, const std::array<LinComArrZ<XYPi>, 3>& inverse_perm_pi) {

    std::vector<std::string> result;
    for (const auto& int_pair : polygon) {
        const auto p = boost::apply_visitor(RearrangeVariant{inverse_perm_eta, inverse_perm_pi}, int_pair.equation);
        result.push_back(p);
    }

    return result;
}

static std::pair<std::string, std::string> unstable_equations_to_string(const IntervalLineSegment& line_seg, const std::array<LinComArrZ<XYEta>, 3>& inverse_perm_eta, const std::array<LinComArrZ<XYPi>, 3>& inverse_perm_pi) {

    const auto eq0 = boost::apply_visitor(RearrangeVariant{inverse_perm_eta, inverse_perm_pi}, line_seg.equation0);
    const auto eq1 = boost::apply_visitor(RearrangeVariant{inverse_perm_eta, inverse_perm_pi}, line_seg.equation1);

    return {eq0, eq1};
}

static Vector2<Interval> point_to_vector(const PointQ& point) {

    const Interval x = Interval{point.x} * boost::math::constants::half_pi<Interval>();
    const Interval y = Interval{point.y} * boost::math::constants::half_pi<Interval>();

    return {x, y};
}

static IntervalLineSegment convert_to_interval(const RationalLineSegment& rat_line_seg) {

    const auto point0 = point_to_vector(rat_line_seg.point0);
    const auto point1 = point_to_vector(rat_line_seg.point1);

    const EquationGradient<XY, LinComArrZ<XYEta>> line0{rat_line_seg.line0};
    const EquationGradient<XY, LinComArrZ<XYEta>> line1{rat_line_seg.line1};

    return IntervalLineSegment{point0, line0, point1, line1};
}

static IntervalPolygon convert_to_interval(const RationalPolygon& rat_polygon) {

    IntervalPolygon int_polygon;
    for (const auto& rat_pair : rat_polygon) {
        const auto int_point = point_to_vector(rat_pair.point);
        const EquationGradient<XY, LinComArrZ<XYEta>> side_line{rat_pair.side_line};
        int_polygon.emplace_back(int_point, side_line);
    }

    return int_polygon;
}

// TODO give all of these more consistent names
// TODO also do the refinement as the curves are generated
// that should reduce the memory usage
static boost::optional<IntervalPolygon> calculate_final_polygon(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const CurvesLR& curves) {

    const auto rational_polygon = calculate_bounding_polygon(code_numbers, code_angles);

    if (!rational_polygon) {
        return boost::none;
    }

    auto interval_polygon = convert_to_interval(*rational_polygon);
 
 //george aug 26,2019 this starts with a bounding polygon
 //   print_region(interval_polygon);
 // std::cout << std::endl;

    // Refine using the sines first
    for (const auto& kv : curves.first) {
        //std::cout << kv.first << std::endl;

        const auto maybe = refine_polygon(interval_polygon, kv.first);

        if (!maybe) {
            return boost::none;
        }

        interval_polygon = *maybe;
        //george aug 26,2019 this refines using the all equations
        //print_region(interval_polygon);
        //std::cout << std::endl;
    }

    // Now the cosines
    for (const auto& kv : curves.second) {
        //std::cout << kv.first << std::endl;

        const auto maybe = refine_polygon(interval_polygon, kv.first);

        if (!maybe) {
            return boost::none;
        }

        interval_polygon = *maybe;
        //george aug 26,2019 this refines using the all equations
       // print_region(interval_polygon);
       // std::cout << std::endl;
    }

    return interval_polygon;//note george aug 26,2019 the last stuff is the mrr region
}

// ---------------------------------------------------------------------------
// EXPERIMENT (Suryansh, 2026): refinement-order invariance probe.
// See equations.hpp and docs/algorithmic-optimization-opportunities.md (#1).
// Recompute the MRR region under reordered refinement sequences and compare
// each to the canonical (sines-then-cosines, map order) result. Production
// path (calculate_final_polygon above) is untouched.
// ---------------------------------------------------------------------------
namespace {

using AnyCurve = boost::variant<Equation<Sin>, Equation<Cos>>;

struct RefineVisitor final : public boost::static_visitor<boost::optional<IntervalPolygon>> {
    const IntervalPolygon& poly;
    explicit RefineVisitor(const IntervalPolygon& poly_) : poly{poly_} {}
    boost::optional<IntervalPolygon> operator()(const Equation<Sin>& c) const { return refine_polygon(poly, c); }
    boost::optional<IntervalPolygon> operator()(const Equation<Cos>& c) const { return refine_polygon(poly, c); }
};

// Refine a starting polygon against an explicit ordered list of curves.
boost::optional<IntervalPolygon> refine_seq(IntervalPolygon poly, const std::vector<AnyCurve>& seq) {
    for (const auto& c : seq) {
        const auto maybe = boost::apply_visitor(RefineVisitor{poly}, c);
        if (!maybe) {
            return boost::none;
        }
        poly = *maybe;
    }
    return poly;
}

std::string edge_equation_string(const BoundaryEquation& eq) {
    return boost::apply_visitor(EquationPrinter{}, eq);
}

std::multiset<std::string> edge_equation_multiset(const IntervalPolygon& poly) {
    std::multiset<std::string> s;
    for (const auto& v : poly) { s.insert(edge_equation_string(v.equation)); }
    return s;
}

// Max |Δ endpoint| across the x and y interval bounds of two vertices.
double vertex_endpoint_delta(const Vector2<Interval>& a, const Vector2<Interval>& b) {
    double m = 0.0;
    for (int k = 0; k < 2; ++k) {
        const Real dl = boost::multiprecision::abs(
            boost::multiprecision::lower(a[k]) - boost::multiprecision::lower(b[k]));
        const Real du = boost::multiprecision::abs(
            boost::multiprecision::upper(a[k]) - boost::multiprecision::upper(b[k]));
        m = std::max(m, dl.convert_to<double>());
        m = std::max(m, du.convert_to<double>());
    }
    return m;
}

// Symmetric nearest-neighbour (Hausdorff) distance between two vertex sets.
// Independent of edge labeling and vertex ordering, so it measures whether the
// two regions are geometrically the same even when their recorded boundary
// equations differ.
double vertex_hausdorff_delta(const IntervalPolygon& a, const IntervalPolygon& b) {
    const auto directed = [](const IntervalPolygon& from, const IntervalPolygon& to) {
        double worst = 0.0;
        for (const auto& v : from) {
            double best = std::numeric_limits<double>::infinity();
            for (const auto& w : to) {
                best = std::min(best, vertex_endpoint_delta(v.point, w.point));
            }
            worst = std::max(worst, best);
        }
        return worst;
    };
    return std::max(directed(a, b), directed(b, a));
}

// Compare one alternative ordering's result against the canonical polygon.
RefineOrderReport::Alt compare_to_canonical(const std::string& name,
                                            const boost::optional<IntervalPolygon>& maybe_alt,
                                            const IntervalPolygon& canon,
                                            const CurvesLR& curves,
                                            const std::vector<LeftRight>& lr_canon,
                                            bool lr_canon_ok) {
    RefineOrderReport::Alt alt;
    alt.name = name;
    if (!maybe_alt) {
        return alt; // ok stays false: the reordering drove the region empty
    }
    alt.ok = true;
    const IntervalPolygon& p = *maybe_alt;
    alt.size = p.size();
    alt.same_size = (p.size() == canon.size());

    // Gate #1: recompute stable_left_right on the reordered polygon and compare
    // to canonical. This is the exact vector wrapper.cpp's consistency guard
    // checks, so it tells us whether a reorder/parallel run would trip the
    // "pattern changed" throw.
    try {
        const std::vector<LeftRight> lr_alt = stable_left_right(p, curves);
        alt.left_rights_match = lr_canon_ok && (lr_alt == lr_canon);
        // Multiset comparison separates a benign cyclic rotation of the edge
        // cycle (same elements, different start vertex) from a genuine content
        // change caused by an edge relabel.
        if (lr_canon_ok && lr_alt.size() == lr_canon.size()) {
            std::vector<LeftRight> a = lr_alt;
            std::vector<LeftRight> b = lr_canon;
            std::sort(a.begin(), a.end());
            std::sort(b.begin(), b.end());
            alt.left_rights_same_multiset = (a == b);
        }
    } catch (const std::exception&) {
        alt.left_rights_threw = true;
    }

    // Multiset of edge equations is order-independent: identical structure?
    std::multiset<std::string> canon_eqs;
    std::multiset<std::string> alt_eqs;
    for (const auto& v : canon) { canon_eqs.insert(edge_equation_string(v.equation)); }
    for (const auto& v : p)     { alt_eqs.insert(edge_equation_string(v.equation)); }
    alt.same_equations = (canon_eqs == alt_eqs);

    // Which edge equations are NOT shared (multiset symmetric difference).
    std::set_difference(alt_eqs.begin(), alt_eqs.end(), canon_eqs.begin(), canon_eqs.end(),
                        std::back_inserter(alt.edges_only_in_alt));
    std::set_difference(canon_eqs.begin(), canon_eqs.end(), alt_eqs.begin(), alt_eqs.end(),
                        std::back_inserter(alt.edges_only_in_canon));
    alt.eq_diff_count = alt.edges_only_in_alt.size();

    // Geometric closeness, independent of edge labeling. This is the key deep-
    // dive metric: if it is ~1e-49 even when edge equations differ, the region
    // is the same shape and the relabel is a benign near-degenerate vertex.
    alt.vertex_hausdorff = vertex_hausdorff_delta(canon, p);

    // If structurally identical, match vertices by edge equation and measure
    // the worst endpoint disagreement; bit_identical iff that worst delta == 0.
    if (alt.same_size && alt.same_equations) {
        std::multimap<std::string, std::size_t> alt_by_eq;
        for (std::size_t i = 0; i < p.size(); ++i) {
            alt_by_eq.emplace(edge_equation_string(p[i].equation), i);
        }
        double worst = 0.0;
        bool matched_all = true;
        for (const auto& cv : canon) {
            const std::string key = edge_equation_string(cv.equation);
            const auto it = alt_by_eq.find(key);
            if (it == alt_by_eq.end()) { matched_all = false; break; }
            worst = std::max(worst, vertex_endpoint_delta(cv.point, p[it->second].point));
            alt_by_eq.erase(it);
        }
        if (matched_all) {
            alt.max_endpoint_delta = worst;
            alt.bit_identical = (worst == 0.0);
        }
    }
    return alt;
}

} // namespace

RefineOrderReport experiment_refine_order(const CodeSequence& code_sequence, const CodeType code_type) {
    RefineOrderReport report;

    // --- Replicate calculate_stable's curve construction exactly. ---
    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);
    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    const Unfolding unfold{code_numbers, code_angles};

    CurvesLR curves{};
    if (code_type == CodeType::OSO) {
        const auto sv = shooting_vector_open(code_sequence, code_angles_pi);
        curves = unfold.generate_curves_lr(sv.first, sv.second);
    } else if (code_type == CodeType::CS) {
        const auto sv = shooting_vector_closed(code_sequence, code_angles_eta);
        curves = unfold.generate_curves_lr(sv.first, sv.second);
    } else if (code_type == CodeType::OSNO) {
        const auto sv = unfold.shooting_vector_general();
        curves = unfold.generate_curves_lr(sv.first, sv.second);
    } else {
        return report; // unstable / unsupported: built stays false
    }

    // Starting interval polygon, exactly as calculate_final_polygon builds it.
    const auto rational_polygon = calculate_bounding_polygon(code_numbers, code_angles);
    if (!rational_polygon) {
        report.built = true;
        report.empty_region = true;
        return report;
    }
    const IntervalPolygon start = convert_to_interval(*rational_polygon);

    // Curve keys in canonical (std::map) order.
    std::vector<Equation<Sin>> sins;
    for (const auto& kv : curves.first)  { sins.push_back(kv.first); }
    std::vector<Equation<Cos>> coss;
    for (const auto& kv : curves.second) { coss.push_back(kv.first); }
    report.n_sin = sins.size();
    report.n_cos = coss.size();

    // Canonical application sequence: all sines (map order), then all cosines.
    std::vector<AnyCurve> canon_seq;
    canon_seq.reserve(sins.size() + coss.size());
    for (const auto& c : sins) { canon_seq.emplace_back(c); }
    for (const auto& c : coss) { canon_seq.emplace_back(c); }

    const auto canon = refine_seq(start, canon_seq);
    report.built = true;
    if (!canon) {
        report.empty_region = true;
        return report;
    }
    report.canon_size = canon->size();

    // Canonical left_rights (the downstream vector wrapper.cpp guards on).
    std::vector<LeftRight> lr_canon;
    bool lr_canon_ok = true;
    try {
        lr_canon = stable_left_right(*canon, curves);
    } catch (const std::exception&) {
        lr_canon_ok = false;
    }

    // --- Alternative orderings (deterministic permutations of canon_seq). ---

    // 1. Full reverse of the entire applied sequence.
    std::vector<AnyCurve> rev(canon_seq.rbegin(), canon_seq.rend());
    report.alts.push_back(compare_to_canonical("full-reverse", refine_seq(start, rev),
                                               *canon, curves, lr_canon, lr_canon_ok));

    // 2. Groups swapped: all cosines first, then all sines (within-group order kept).
    std::vector<AnyCurve> swapped;
    swapped.reserve(canon_seq.size());
    for (const auto& c : coss) { swapped.emplace_back(c); }
    for (const auto& c : sins) { swapped.emplace_back(c); }
    report.alts.push_back(compare_to_canonical("cosines-first", refine_seq(start, swapped),
                                               *canon, curves, lr_canon, lr_canon_ok));

    // 3. Interleaved: alternate sines and cosines to maximally scramble order.
    std::vector<AnyCurve> interleaved;
    interleaved.reserve(canon_seq.size());
    {
        std::size_t i = 0;
        std::size_t j = 0;
        while (i < sins.size() || j < coss.size()) {
            if (i < sins.size()) { interleaved.emplace_back(sins[i++]); }
            if (j < coss.size()) { interleaved.emplace_back(coss[j++]); }
        }
    }
    report.alts.push_back(compare_to_canonical("interleaved", refine_seq(start, interleaved),
                                               *canon, curves, lr_canon, lr_canon_ok));

    // --- Parallel non-binding filter prototype ------------------------------
    // Keep only curves that cut the STARTING bounding polygon. By monotonicity
    // (refinement only shrinks the region), this survivor set is a superset of
    // the truly binding curves, so refining by it in canonical order reproduces
    // the canonical region exactly -- while the per-curve binding tests are
    // independent of each other and so parallelizable. Here we run the tests
    // serially (this is a measurement harness) and report the survivor count
    // and whether the filtered region matches canonical.
    {
        const std::multiset<std::string> start_edges = edge_equation_multiset(start);
        std::vector<AnyCurve> survivors;
        for (const auto& c : canon_seq) {
            const auto r = refine_seq(start, std::vector<AnyCurve>{c});
            if (!r) {
                survivors.push_back(c); // single curve drove region empty -> binding
                continue;
            }
            if (edge_equation_multiset(*r) != start_edges) {
                survivors.push_back(c);
            }
        }
        report.binding_count = survivors.size();
        report.filter_ran = true;

        const auto filtered = refine_seq(start, survivors);
        const RefineOrderReport::Alt cmp =
            compare_to_canonical("filtered", filtered, *canon, curves, lr_canon, lr_canon_ok);
        report.filtered_matches_canon =
            cmp.ok && cmp.same_size && cmp.same_equations && cmp.bit_identical;
        report.filtered_hausdorff = cmp.vertex_hausdorff;
    }

    return report;
}

static boost::optional<IntervalLineSegment> calculate_final_line_segment(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const LinComArrZ<XYEta>& constraint, const CurvesLR& curves) {

    const auto rational_line_segment = calculate_bounding_line_segment(code_numbers, code_angles, constraint);

    if (!rational_line_segment) {
        return boost::none;
    }
    //std::cout << "bounding line"  << std::endl;

    //std::cout << rational_line_segment->point0 << "->" << rational_line_segment->point1 << std::endl;//XIU



    auto interval_line_segment = convert_to_interval(*rational_line_segment);
    //std::cout << interval_line_segment.point0 << "->" << interval_line_segment.point1 << std::endl;

    const EquationGradient<XY, LinComArrZ<XYEta>> constraint_grad{constraint};
//george uncomment aug 26,2019 this gives the final mrr region but not factored yet
    //print_region(interval_line_segment);

    for (const auto& kv : curves.first) {

    	//george uncomment aug 26,2019 this gives the final mrr region but not factored yet
        //std::cout << kv.first << std::endl;

        const auto maybe = refine_line_segment(interval_line_segment, kv.first, constraint_grad);

        if (!maybe) {
            return boost::none;
        }

        interval_line_segment = *maybe;

        //george uncomment aug 26,2019 this gives the final mrr region but not factored yet
       // print_region(interval_line_segment);
    }

    for (const auto& kv : curves.second) {

    	//george aug 26,2019 uncomment this gives the final mrr region but not factored yet
        //std::cout << kv.second << std::endl;

        const auto maybe = refine_line_segment(interval_line_segment, kv.first, constraint_grad);

        if (!maybe) {
            return boost::none;
        }

        interval_line_segment = *maybe;

        //george uncomment aug 26,2019 this gives the final mrr region but not factored yet
        //print_region(interval_line_segment);
    }
    /*std::cout << "after refinement"  << std::endl;
        std::cout << "point1"  << std::endl;

    std::cout << interval_line_segment.point0  << std::endl;
        std::cout << "point2"  << std::endl;

    std::cout << interval_line_segment.point1  << std::endl;
    */


    return interval_line_segment;
}

static void convex_counterexample_checker(const IntervalPolygon& polygon) {

    const auto size = polygon.size();
    for (const auto i : falgo::range(size)) {

        const auto& point = polygon.at(i).point;

        const auto prev_i = i == 0 ? size - 1 : i - 1;
        const auto& eq0 = polygon.at(prev_i).equation;
        const auto& eq1 = polygon.at(i).equation;

        const auto gradient0 = boost::apply_visitor(GradientVariant{point}, eq0);
        const auto gradient1 = boost::apply_visitor(GradientVariant{point}, eq1);

        // z component of the 3d cross product
        const Interval cross = gradient0[0] * gradient1[1] - gradient0[1] * gradient1[0];

        // Are parallel if the cross is zero

        const auto s = sign(cross);

        if (s == Sign::ZERO) {
            std::ostringstream oss{};
            oss << "possible non-convex counterexample found:" << std::endl
                << boost::apply_visitor(EquationPrinter{}, eq0) << std::endl
                << boost::apply_visitor(EquationPrinter{}, eq1) << std::endl
                << point << std::endl;
            throw std::runtime_error(oss.str());
        }
    }
}
//
static boost::optional<Stable> points_and_stuff_stable(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const CurvesLR& curves) {

    const auto polygon = calculate_final_polygon(code_numbers, code_angles, curves);

    if (!polygon) {
        return boost::none;
    }

    convex_counterexample_checker(*polygon);

    const auto all_points = calculate_all_points(*polygon);
    const auto perm = permute_angles(all_points);
    const auto rearranged_points = rearrange_points(all_points, perm);

    const auto inverse_perm = invert_permutation(perm);
    const auto inverse_perm_eta = falgo::transform(inverse_perm, xyz_to_xyeta);
    const auto inverse_perm_pi = falgo::transform(inverse_perm, xyz_to_xypi);

    const auto rearranged_equations = stable_equations_to_string(*polygon, inverse_perm_eta, inverse_perm_pi);
    //print the equations here
    /*for (const auto& equation: rearranged_equations){
        std::cout << equation << std::endl;
    }*/
    const auto left_rights = stable_left_right(*polygon, curves);

    const InitialAngles initial_angles{std::get<0>(inverse_perm), std::get<1>(inverse_perm)};

    return Stable{initial_angles, rearranged_points, rearranged_equations, left_rights};
}

static boost::optional<Unstable> points_and_stuff_unstable(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const LinComArrZ<XYEta>& constraint, const CurvesLR& curves) {

    const auto line_segment = calculate_final_line_segment(code_numbers, code_angles, constraint, curves);

    if (!line_segment) {
        return boost::none;
    }

    // TODO replace this with an array instead of vector and optimize that
    const auto all_points = calculate_all_points(*line_segment);
    const auto perm = permute_angles(all_points);
    const auto rearranged_points = rearrange_points(all_points, perm);

    const auto inverse_perm = invert_permutation(perm);
    const auto inverse_perm_eta = falgo::transform(inverse_perm, xyz_to_xyeta);
    const auto inverse_perm_pi = falgo::transform(inverse_perm, xyz_to_xypi);

    /*
    const auto rearranged_constraint = [&]() {
        auto rearranged = rearrange_enum_com(constraint, inverse_perm_eta);
        rearranged.divide_content();
        rearranged.divide_unit();
        return rearranged;
    }();
    */
    //for(const auto& point : rearranged_points){
        //std::cout<< point<< std::endl;
   // }
    const auto rearranged_equations = unstable_equations_to_string(*line_segment, inverse_perm_eta, inverse_perm_pi);

    const auto left_right = unstable_left_right(*line_segment, curves);

    const InitialAngles initial_angles{std::get<0>(inverse_perm), std::get<1>(inverse_perm)};

    return Unstable{initial_angles,
                    rearranged_points.at(0), rearranged_points.at(1),
                    rearranged_equations.first, rearranged_equations.second,
                    left_right.first, left_right.second};
}

// TODO make a calculate_equations function or something or other
boost::optional<Stable> calculate_stable(const CodeSequence& code_sequence, const CodeType code_type) {

    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);

    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    // Note: it is possible that we could calculate the bounding polygon first to
    // check if it is empty. This way, we can return an empty optional without
    // finding the unfolding

    const Unfolding unfold{code_numbers, code_angles};

    // Some of the generated equations are duplicates, so we put them into a std::set
    // first. Order matters, because I want the order on which the polygon is
    // reduced to be deterministic.
    CurvesLR curves{};
    // george jun11th 2021 to print the shooting vector, uncomment the lines with label_shooting_vector
    if (code_type == CodeType::OSO) {

        // the return type of equations is determined at runtime, so we can't template
        // this code
        const auto shooting_vector = shooting_vector_open(code_sequence, code_angles_pi);
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second);

    } else if (code_type == CodeType::CS) {

        const auto shooting_vector = shooting_vector_closed(code_sequence, code_angles_eta);
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second);

    } else if (code_type == CodeType::OSNO) {

        const auto shooting_vector = unfold.shooting_vector_general();
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second);

    } else {
        throw std::runtime_error("unstable code type passed to stable case");
    }

    return points_and_stuff_stable(code_numbers, code_angles, curves);
}

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type) {

    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);

    const auto constraint = code_sequence.constraint(XYZ::X, XYZ::Y);

    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    // Note: it is possible that we could calculate the bounding polygon first to
    // check if it is empty. This way, we can return an empty optional without
    // finding the unfolding

    const Unfolding unfold{code_numbers, code_angles};

    // Some of the generated equations are duplicates, so we put them into a std::set
    // first. Order matters, because I want the order on which the polygon is
    // reduced to be deterministic.
    CurvesLR curves{};
    // george jun11th 2021 to print the shooting vector, uncomment the lines with label_shooting_vector
    if (code_type == CodeType::CNS) {

        const auto shooting_vector = shooting_vector_closed(code_sequence, code_angles_eta);
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second);

    } else if (code_type == CodeType::ONS) {

        const auto shooting_vector = unfold.shooting_vector_general();
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second);

    } else {
        throw std::runtime_error("stable code type in unstable case");
    }


    return points_and_stuff_unstable(code_numbers, code_angles, constraint, curves);
}

boost::optional<Stable> calculate_stable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights) {

    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);

    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    // Note: it is possible that we could calculate the bounding polygon first to
    // check if it is empty. This way, we can return an empty optional without
    // finding the unfolding

    const Unfolding unfold{code_numbers, code_angles};

    CurvesLR curves{};

    // george jun11th 2021 to print the shooting vector, uncomment the lines with label_shooting_vector
    if (code_type == CodeType::OSO) {

        const auto shooting_vector = shooting_vector_open(code_sequence, code_angles_pi);
        // Passing the left_rights is the only difference
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second, left_rights);

    } else if (code_type == CodeType::CS) {

        const auto shooting_vector = shooting_vector_closed(code_sequence, code_angles_eta);
        // Passing the left_rights is the only difference
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second, left_rights);

    } else if (code_type == CodeType::OSNO) {

        const auto shooting_vector = unfold.shooting_vector_general();
        // Passing the left_rights is the only difference
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second, left_rights);

    } else {
        throw std::runtime_error("unstable code type passed to stable case");
    }
    auto result = points_and_stuff_stable(code_numbers, code_angles, curves);


    return result;
}

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights) {

    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);

    const auto constraint = code_sequence.constraint(XYZ::X, XYZ::Y);

    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    // Note: it is possible that we could calculate the bounding polygon first to
    // check if it is empty. This way, we can return an empty optional without
    // finding the unfolding

    const Unfolding unfold{code_numbers, code_angles};

    // Some of the generated equations are duplicates, so we put them into a std::set
    // first. Order matters, because I want the order on which the polygon is
    // reduced to be deterministic.
    CurvesLR curves{};
    // george jun11th 2021 to print the shooting vector, uncomment the lines with label_shooting_vector
    if (code_type == CodeType::CNS) {

        const auto shooting_vector = shooting_vector_closed(code_sequence, code_angles_eta);
        // Passing the left_rights is the only difference
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second, left_rights);

    } else if (code_type == CodeType::ONS) {

        const auto shooting_vector = unfold.shooting_vector_general();
        // Passing the left_rights is the only difference
        //std::cout << shooting_vector.first << " , " << shooting_vector.second << std::endl; //label_shooting_vector
        curves = unfold.generate_curves_lr(shooting_vector.first, shooting_vector.second, left_rights);

    } else {
        throw std::runtime_error("stable code type in unstable case");
    }
    auto result = points_and_stuff_unstable(code_numbers, code_angles, constraint, curves);
    return result;
}

// TODO split the stable and unstable functions apart into two files
// Perhaps do that with the refinement/boundary stuff too?
