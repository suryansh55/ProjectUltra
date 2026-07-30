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

#include <chrono> // for C++ profiling timers

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

// Nick Shan, July, 2026
// Return true when the polygon's bounding box diagonal is too small to matter.
// Refining further produces no visible change, so we can skip remaining curves.
static bool polygon_is_tiny(const IntervalPolygon& polygon) {
    // Threshold: 1e-12 radians. The angle space is [0, pi/2] ~ [0, 1.57],
    // so this is far below any display resolution.
    static constexpr double TINY_THRESHOLD = 1e-12;

    if (polygon.empty()) {
        return true;
    }

    Real x_low = boost::multiprecision::lower(polygon[0].point[0]);
    Real x_high = boost::multiprecision::upper(polygon[0].point[0]);
    Real y_low = boost::multiprecision::lower(polygon[0].point[1]);
    Real y_high = boost::multiprecision::upper(polygon[0].point[1]);

    for (const auto& pair : polygon) {
        const auto& x = pair.point[0];
        const auto& y = pair.point[1];

        const Real xl = boost::multiprecision::lower(x);
        const Real xh = boost::multiprecision::upper(x);
        const Real yl = boost::multiprecision::lower(y);
        const Real yh = boost::multiprecision::upper(y);

        if (xl < x_low)  x_low = xl;
        if (xh > x_high) x_high = xh;
        if (yl < y_low)  y_low = yl;
        if (yh > y_high) y_high = yh;
    }

    const Real width = x_high - x_low;
    const Real height = y_high - y_low;

    return width < TINY_THRESHOLD && height < TINY_THRESHOLD;
}

// Nick Shan, July, 2026
// TODO give all of these more consistent names
// TODO also do the refinement as the curves are generated
// that should reduce the memory usage
static boost::optional<IntervalPolygon> calculate_final_polygon(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const CurvesLR& curves) {

    static constexpr size_t PARALLEL_THRESHOLD = 1000;
    static constexpr size_t MAX_PARALLEL_THREADS = 8;

    const auto t0 = std::chrono::steady_clock::now();

    const auto rational_polygon = calculate_bounding_polygon(code_numbers, code_angles);

    if (!rational_polygon) {
        return boost::none;
    }

    const auto t_bounding_end = std::chrono::steady_clock::now();

    auto interval_polygon = convert_to_interval(*rational_polygon);
 
 //george aug 26,2019 this starts with a bounding polygon
 //   print_region(interval_polygon);
 // std::cout << std::endl;

    const size_t total_sin = curves.first.size();
    const size_t total_cos = curves.second.size();
    const size_t total_curves = total_sin + total_cos;

    // --- Sequential path (few curves: under ~1s, not worth parallelism overhead) ---
    if (total_curves <= PARALLEL_THRESHOLD) {

        size_t sin_idx = 0;
        for (const auto& kv : curves.first) {
            if (polygon_is_tiny(interval_polygon)) {
                break;
            }

            const auto c0 = std::chrono::steady_clock::now();
            const auto maybe = refine_polygon(interval_polygon, kv.first);
            const auto c1 = std::chrono::steady_clock::now();
            const auto c_ms = std::chrono::duration_cast<std::chrono::milliseconds>(c1 - c0).count();
            //if (c_ms > 200) {
            //    std::cout << "[CPP]   sin[" << sin_idx << "/" << curves.first.size()
            //              << "] verts=" << interval_polygon.size()
            //              << " time=" << c_ms << "ms" << std::endl;
            //}

            if (!maybe) {
                return boost::none;
            }

            interval_polygon = *maybe;
            ++sin_idx;
        }

        const auto t_sin_end = std::chrono::steady_clock::now();

        size_t cos_idx = 0;
        for (const auto& kv : curves.second) {
            if (polygon_is_tiny(interval_polygon)) {
                break;
            }

            const auto c0 = std::chrono::steady_clock::now();
            const auto maybe = refine_polygon(interval_polygon, kv.first);
            const auto c1 = std::chrono::steady_clock::now();
            const auto c_ms = std::chrono::duration_cast<std::chrono::milliseconds>(c1 - c0).count();
            //if (c_ms > 200) {
            //    std::cout << "[CPP]   cos[" << cos_idx << "/" << curves.second.size()
            //              << "] verts=" << interval_polygon.size()
            //              << " time=" << c_ms << "ms" << std::endl;
            //}

            if (!maybe) {
                return boost::none;
            }

            interval_polygon = *maybe;
            ++cos_idx;
        }

        const auto t_end = std::chrono::steady_clock::now();

        const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t0).count();
        //if (total_ms > 100) {
        //    const auto bounding_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_bounding_end - t0).count();
        //    const auto sin_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_sin_end - t_bounding_end).count();
        //    const auto cos_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_sin_end).count();
        //    std::cout << "[CPP] calculate_final_polygon"
        //              << " curves=" << curves.first.size() << "s+" << curves.second.size() << "c"
        //              << " bounding=" << bounding_ms << "ms"
        //              << " sin_refine=" << sin_ms << "ms"
        //              << " cos_refine=" << cos_ms << "ms"
        //              << " total=" << total_ms << "ms" << std::endl;
        //}

        return interval_polygon;
    }

    // --- Parallel batch path (many curves: split across threads) ---

    unsigned int n_threads = std::thread::hardware_concurrency();
    if (n_threads == 0) n_threads = 4;
    if (n_threads > MAX_PARALLEL_THREADS) n_threads = MAX_PARALLEL_THREADS;

    // Extract curve pointers into flat vectors for easy interleaved partitioning.
    // Interleaving gives each thread a representative mix of curves, which
    // balances the work better than contiguous chunking.
    std::vector<const Equation<Sin>*> sin_ptrs;
    sin_ptrs.reserve(total_sin);
    for (const auto& kv : curves.first) {
        sin_ptrs.push_back(&kv.first);
    }
    std::vector<const Equation<Cos>*> cos_ptrs;
    cos_ptrs.reserve(total_cos);
    for (const auto& kv : curves.second) {
        cos_ptrs.push_back(&kv.first);
    }

    struct Batch {
        std::vector<const Equation<Sin>*> sin_curves;
        std::vector<const Equation<Cos>*> cos_curves;
    };
    std::vector<Batch> batches(n_threads);
    for (size_t i = 0; i < sin_ptrs.size(); ++i) {
        batches[i % n_threads].sin_curves.push_back(sin_ptrs[i]);
    }
    for (size_t i = 0; i < cos_ptrs.size(); ++i) {
        batches[i % n_threads].cos_curves.push_back(cos_ptrs[i]);
    }

    // Process each batch independently, starting from the same initial polygon.
    // Each batch applies a subset of the curves in order to produce a partial result.
    boost::asio::thread_pool pool(n_threads);
    std::vector<boost::optional<IntervalPolygon>> batch_results(n_threads);

    for (unsigned int t = 0; t < n_threads; ++t) {
        boost::asio::post(pool, [&, t]() {
            auto poly = interval_polygon;
            auto& batch = batches[t];

            for (const auto* curve : batch.sin_curves) {
                if (polygon_is_tiny(poly)) break;
                auto maybe = refine_polygon(poly, *curve);
                if (!maybe) return;
                poly = std::move(*maybe);
            }
            for (const auto* curve : batch.cos_curves) {
                if (polygon_is_tiny(poly)) break;
                auto maybe = refine_polygon(poly, *curve);
                if (!maybe) return;
                poly = std::move(*maybe);
            }

            batch_results[t] = std::move(poly);
        });
    }

    pool.join();

    const auto t_batch_end = std::chrono::steady_clock::now();

    // Intersect the batch results. Since all constraints commute,
    // intersecting the partial polygons yields the same result as
    // processing every curve sequentially.
    boost::optional<IntervalPolygon> result;
    for (auto& r : batch_results) {
        if (!r) {
            return boost::none;
        }
        if (!result) {
            result = std::move(r);
        } else {
            auto maybe = intersect_polygons(std::move(*result), std::move(*r));
            if (!maybe) {
                return boost::none;
            }
            result = std::move(maybe);
        }
    }

    interval_polygon = std::move(*result);

    const auto t_end = std::chrono::steady_clock::now();

    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t0).count();
    //if (total_ms > 100) {
    //    const auto bounding_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_bounding_end - t0).count();
    //    const auto batch_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_batch_end - t_bounding_end).count();
    //    const auto merge_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_batch_end).count();
    //    std::cout << "[CPP] calculate_final_polygon"
    //              << " curves=" << total_sin << "s+" << total_cos << "c"
    //              << " bounding=" << bounding_ms << "ms"
    //              << " batches=" << batch_ms << "ms"
    //              << " intersect=" << merge_ms << "ms"
    //              << " parallel=" << n_threads
    //              << " total=" << total_ms << "ms" << std::endl;
    //}

    return interval_polygon;
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

    const auto t0 = std::chrono::steady_clock::now();

    const auto polygon = calculate_final_polygon(code_numbers, code_angles, curves);

    if (!polygon) {
        return boost::none;
    }

    const auto t_poly_end = std::chrono::steady_clock::now();
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

    const auto t_end = std::chrono::steady_clock::now();

    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t0).count();

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

    const auto t_start = std::chrono::steady_clock::now();

    const auto code_numbers = code_sequence.numbers();
    const auto code_angles = code_sequence.angles(XYZ::X, XYZ::Y);

    const auto code_angles_eta = falgo::transform(code_angles, xyz_to_xyeta);
    const auto code_angles_pi = falgo::transform(code_angles, xyz_to_xypi);

    // Note: it is possible that we could calculate the bounding polygon first to
    // check if it is empty. This way, we can return an empty optional without
    // finding the unfolding

    const auto t_unfold_start = std::chrono::steady_clock::now();
    const Unfolding unfold{code_numbers, code_angles};
    const auto t_unfold_end = std::chrono::steady_clock::now();

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
    const auto t_curves_end = std::chrono::steady_clock::now();

    const auto result = points_and_stuff_stable(code_numbers, code_angles, curves);

    const auto t_end = std::chrono::steady_clock::now();

    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    //if (total_ms > 100) {
    //    const auto unfold_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_unfold_end - t_unfold_start).count();
    //    const auto curves_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_curves_end - t_unfold_end).count();
    //    const auto compute_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_curves_end).count();
    //    std::cout << "[CPP] calculate_stable type=" << static_cast<int>(code_type)
    //              << " len=" << code_numbers.size()
    //              << " unfold=" << unfold_ms << "ms"
    //              << " curves=" << curves_ms << "ms"
    //              << " compute=" << compute_ms << "ms"
    //              << " total=" << total_ms << "ms" << std::endl;
    //}

    return result;
}

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type) {

    const auto t_start = std::chrono::steady_clock::now();

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

    const auto result = points_and_stuff_unstable(code_numbers, code_angles, constraint, curves);

    const auto t_end = std::chrono::steady_clock::now();
    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    //if (total_ms > 100) {
    //    std::cout << "[CPP] calculate_unstable type=" << static_cast<int>(code_type)
    //              << " len=" << code_numbers.size()
    //              << " total=" << total_ms << "ms" << std::endl;
    //}

    return result;
}

boost::optional<Stable> calculate_stable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights) {

    const auto t_start = std::chrono::steady_clock::now();

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
    const auto result = points_and_stuff_stable(code_numbers, code_angles, curves);

    const auto t_end = std::chrono::steady_clock::now();
    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    //if (total_ms > 100) {
    //    std::cout << "[CPP] calculate_stable (lr) type=" << static_cast<int>(code_type)
    //              << " len=" << code_numbers.size()
    //              << " total=" << total_ms << "ms" << std::endl;
    //}

    return result;
}

boost::optional<Unstable> calculate_unstable(const CodeSequence& code_sequence, const CodeType code_type, const std::vector<LeftRight>& left_rights) {

    const auto t_start = std::chrono::steady_clock::now();

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
    const auto result = points_and_stuff_unstable(code_numbers, code_angles, constraint, curves);

    const auto t_end = std::chrono::steady_clock::now();
    const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    //if (total_ms > 100) {
    //    std::cout << "[CPP] calculate_unstable (lr) type=" << static_cast<int>(code_type)
    //              << " len=" << code_numbers.size()
    //              << " total=" << total_ms << "ms" << std::endl;
    //}

    return result;
}

// TODO split the stable and unstable functions apart into two files
// Perhaps do that with the refinement/boundary stuff too?
