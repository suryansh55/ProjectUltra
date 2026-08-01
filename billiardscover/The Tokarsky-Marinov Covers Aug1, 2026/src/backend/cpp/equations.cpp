#include <mutex>
#include <tbb/tbb.h>

#include "division.hpp"
#include "equations.hpp"
#include "progress.hpp"
#include "region.hpp"
#include "shooting_vectors.hpp"
#include "unfolding.hpp"

// We are going to have two versions right now. I can template them later
static void remove_factor(CodeInfo& info, const EqMap<Sin>& factor, const bool pos) {

    // for Sin
    // Cos
    // Sin
    //
    // for Cos
    // Sin
    // Cos

    std::vector<std::pair<EqMap<Sin>, EqMap<Cos>>> sines{};
    std::vector<std::pair<EqMap<Cos>, EqMap<Sin>>> cosines{};

    // Find the ones we should divide out and their factored versions

    for (const auto& eq : info.sin_equations) {
        const auto opt = divide_once(eq, factor);
        if (opt) {
            auto factored = *opt;

            if (!pos) {
                // this is a negative stable info, so we need to negate the factor
                factored.scale(-1);
            }

            sines.emplace_back(eq, factored);
        }
    }

    for (const auto& eq : info.cos_equations) {
        const auto opt = divide_once(eq, factor);

        if (opt) {
            auto factored = *opt;

            if (!pos) {
                factored.scale(-1);
            }

            cosines.emplace_back(eq, factored);
        }
    }

    // Now remove the equations that we found and insert the factored ones
    for (const auto& p : sines) {
        info.sin_equations.erase(p.first);
        info.cos_equations.insert(p.second);
    }

    for (const auto& p : cosines) {
        info.cos_equations.erase(p.first);
        info.sin_equations.insert(p.second);
    }
}

// We are going to have two versions right now. I can template them later
static void remove_factor(CodeInfo& info, const EqMap<Cos>& factor, const bool pos) {

    // for Sin
    // Cos
    // Sin
    //
    // for Cos
    // Sin
    // Cos

    std::vector<std::pair<EqMap<Sin>, EqMap<Sin>>> sines{};
    std::vector<std::pair<EqMap<Cos>, EqMap<Cos>>> cosines{};

    // Find the ones we should divide out and their factored versions

    for (const auto& eq : info.sin_equations) {
        const auto opt = divide_once(eq, factor);
        if (opt) {
            auto factored = *opt;

            if (!pos) {
                // this is a negative stable info, so we need to negate the factor
                factored.scale(-1);
            }

            sines.emplace_back(eq, factored);
        }
    }

    for (const auto& eq : info.cos_equations) {
        const auto opt = divide_once(eq, factor);

        if (opt) {
            auto factored = *opt;

            if (!pos) {
                factored.scale(-1);
            }

            cosines.emplace_back(eq, factored);
        }
    }

    // Now remove the equations that we found and insert the factored ones
    for (const auto& p : sines) {
        info.sin_equations.erase(p.first);
        info.sin_equations.insert(p.second);
    }

    for (const auto& p : cosines) {
        info.cos_equations.erase(p.first);
        info.cos_equations.insert(p.second);
    }
}

static void remove_factor(CodeInfo& stable_neg_info, const CodePair& unstable, CodeInfo& stable_pos_info) {

    const auto constraint = unstable.sequence.constraint(unstable.angles);

    const auto x_coeff = constraint.coeff(XYEta::X);
    const auto y_coeff = constraint.coeff(XYEta::Y);
    const auto eta_coeff = constraint.coeff(XYEta::Eta);

    const LinComArrZ<XY> arg{x_coeff, y_coeff};

    // ax + by + ceta = 0 is the constraint
    // Rearrange, and we get ax + by = -ceta
    // if c is even, then we sin both sides, and get
    // sin(ax + by) = sin(-ceta) = 0
    // if c is odd, then we cos both sides, and get
    // cos(ax + by) = cos(-ceta) = 0
    // And that is how we get the factor

    if (eta_coeff % 2 == 0) {
        // We shouldn't need to do a simplification here (x_coeff > 0), and indeed,
        // taking out a sign may mess up whether we flip or not. Actually, as long
        // as we account for the sign when doing the division, it doesn't matter.
        const Sin<LinComArrZ<XY>> sin{arg};
        const EqMap<Sin> factor{{{sin, 1}}};

        //std::cout << triple << ", " << factor << std::endl;

        const bool same = (eta_coeff / 2) % 2 == 0;

        // neg is normally not positive (false), and then if
        // same is true, that means same != false, so neg is now
        // false, which is what we want. Ditto for pos

        // == is the predicate with the following truth table
        // T _ is _
        // F _ is !_

        const auto neg = (same == false);
        const auto pos = (same == true);

        remove_factor(stable_neg_info, factor, neg);
        remove_factor(stable_pos_info, factor, pos);
    } else {

        const Cos<LinComArrZ<XY>> cos{arg};
        const EqMap<Cos> factor{{{cos, 1}}};

        //std::cout << triple << ", " << factor << std::endl;

        const bool same = ((eta_coeff - 1) / 2) % 2 == 0;

        const auto neg = (same == false);
        const auto pos = (same == true);

        remove_factor(stable_neg_info, factor, neg);
        remove_factor(stable_pos_info, factor, pos);
    }
}

// TODO this is just the closure really
static ClosedSegmentQ bounding_segment(const OpenSegmentQ& seg) {

    return {seg.start(), seg.end()};
}

static ClosedRectangleQ bounding_rectangle(const OpenConvexPolygonQ& poly) {

    const auto x = project_x(poly);
    const auto y = project_y(poly);

    // The above x and y are open intervals, so we need to create new
    // closed ones
    return {{x.lower(), x.upper()}, {y.lower(), y.upper()}};
}

static PointQ center(const ClosedRectangleQ& rect) {
    return rect.center();
}

static Rational radius_x(const ClosedRectangleQ& rect) {
    return rect.width() / 2;
}

static Rational radius_y(const ClosedRectangleQ& rect) {
    return rect.height() / 2;
}

static PointQ center(const ClosedSegmentQ& seg) {
    return seg.midpoint();
}

static Rational radius_x(const ClosedSegmentQ& seg) {
    return abs(seg.start().x - seg.end().x) / 2;
}

static Rational radius_y(const ClosedSegmentQ& seg) {
    return abs(seg.start().y - seg.end().y) / 2;
}

static CodeInfo calculate_stable_code_info(const CodePair& code_pair, const CodeType& code_type) {

    const auto& code_sequence = code_pair.sequence;
    const auto& initial_angles = code_pair.angles;

    const auto code_angles = code_sequence.angles(initial_angles);

    const auto& code_numbers = code_sequence.numbers();

    const auto opt_polygon = calculate_bounding_polygon(code_numbers, code_angles);

    const auto get_vertices = [](const auto& rat_pair) {
        return rat_pair.point;
    };
    const auto vertices = transform(*opt_polygon, get_vertices);

    const OpenConvexPolygonQ polygon{vertices};

    const auto rect = bounding_rectangle(polygon);

    const auto c = center(rect);
    const auto rx = radius_x(rect);
    const auto ry = radius_y(rect);

    // TODO throw this in a calculate_curves function
    const Unfolding unfold{code_numbers, code_angles};

    // Some of the generated equations are duplicates, so make sure they are in a std::set
    Curves equations{};
    if (code_type == CodeType::OSO) {

        const auto code_angles_pi = transform(code_angles, xyz_to_xypi);

        const auto shooting_vector = shooting_vector_odd(code_sequence, code_angles_pi);
        equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, c, rx, ry);
        //equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, polygon);

    } else if (code_type == CodeType::CS) {

        const auto code_angles_eta = transform(code_angles, xyz_to_xyeta);

        const auto shooting_vector = shooting_vector_perp(code_sequence, code_angles_eta);
        equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, c, rx, ry);
        //equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, polygon);

    } else if (code_type == CodeType::OSNO) {

        const auto shooting_vector = unfold.shooting_vector_general();
        equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, c, rx, ry);
        //equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, polygon);

    } else {
        std::ostringstream err{};
        err << "calculate_stable_code_info: unstable code " << code_sequence << " of type " << code_type;
        throw std::runtime_error(err.str());
    }

    return CodeInfo{vertices, equations.first, equations.second};
}

std::string get_bounding_vertices(const CodePair& code_pair) {

    const auto& code_sequence = code_pair.sequence;
    const auto& initial_angles = code_pair.angles;

    const auto code_angles = code_sequence.angles(initial_angles);

    const auto& code_numbers = code_sequence.numbers();

    const auto opt_polygon = calculate_bounding_polygon(code_numbers, code_angles);

    const auto get_vertices = [](const auto& rat_pair) {
        return rat_pair.point;
    };

    const auto vertices = transform(*opt_polygon, get_vertices);


    std::string toPrint = "";

    for(unsigned int i = 0; i < vertices.size(); i++){
        toPrint += std::to_string(mpz_get_si(numerator(vertices[i].x).backend().data())) + "/" + std::to_string(mpz_get_si(denominator(vertices[i].x).backend().data()));
        toPrint += ", ";
        toPrint += std::to_string(mpz_get_si(numerator(vertices[i].y).backend().data())) + "/" + std::to_string(mpz_get_si(denominator(vertices[i].y).backend().data())) + "~";
    }

    return toPrint;
}

static CodeInfo calculate_unstable_code_info(const CodePair& code_pair, const CodeType code_type) {

    const auto& code_sequence = code_pair.sequence;
    const auto& initial_angles = code_pair.angles;

    const auto code_angles = code_sequence.angles(initial_angles);

    const auto& code_numbers = code_sequence.numbers();

    const auto constraint = code_sequence.constraint(initial_angles);

    const auto rational_segment = calculate_bounding_line_segment(code_numbers, code_angles, constraint);

    const std::vector<PointQ> segment{rational_segment->point0, rational_segment->point1};

    const OpenSegmentQ seg{segment.at(0), segment.at(1)};

    const auto closed_seg = bounding_segment(seg);

    const auto c = center(closed_seg);
    const auto rx = radius_x(closed_seg);
    const auto ry = radius_y(closed_seg);

    const Unfolding unfold{code_numbers, code_angles};

    // Some of the generated equations are duplicates, so make sure they are in a std::set
    Curves equations{};

    if (code_type == CodeType::CNS) {

        const auto code_angles_eta = transform(code_angles, xyz_to_xyeta);

        const auto shooting_vector = shooting_vector_perp(code_sequence, code_angles_eta);
        equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, c, rx, ry);

    } else if (code_type == CodeType::ONS) {

        const auto shooting_vector = unfold.shooting_vector_general();
        equations = unfold.generate_curves(shooting_vector.first, shooting_vector.second, initial_angles, c, rx, ry);

    } else {
        std::ostringstream err{};
        err << "calculate_unstable_code_info: stable code " << code_sequence << " of type " << code_type;
        throw std::runtime_error(err.str());
    }

    return CodeInfo{segment, equations.first, equations.second};
}

CodeInfo calculate_code_info(const CodePair& code_pair) {
    const auto code_type = code_pair.sequence.type();
    if (is_stable(code_type)) {
        return calculate_stable_code_info(code_pair, code_type);
    } else {
        return calculate_unstable_code_info(code_pair, code_type);
    }
}

std::map<size_t, std::pair<CodePair, StableInfo>> load_single_infos(const std::vector<CodePair>& singles) {

    std::mutex mut{};
    std::map<size_t, std::pair<CodePair, StableInfo>> single_infos{};

    Progress progress{"Loading singles: ", singles.size()};

    const auto lambda = [&](const size_t i) {
        const auto index = singles.size() - i - 1;

        const auto& stable = singles.at(index);

        const auto code_info = calculate_code_info(stable);
        StableInfo stable_info{code_info};

        std::lock_guard<std::mutex> lock{mut};
        single_infos.emplace(index, std::make_pair(stable, std::move(stable_info)));

        ++progress;
    };

    tbb::parallel_for(size_t{0}, singles.size(), lambda);

    return single_infos;
}

std::map<size_t, std::pair<TriplePair, TripleInfo>> load_triple_infos(const std::vector<TriplePair>& triples) {

    std::mutex mut{};
    std::map<size_t, std::pair<TriplePair, TripleInfo>> triple_infos{};

    Progress progress{"Loading triples: ", triples.size()};

    const auto lambda = [&](const size_t i) {
        const auto index = triples.size() - i - 1;

        const auto& triple = triples.at(index);

        auto stable_neg_info = calculate_code_info(triple.stable_neg);
        const auto unstable_info = calculate_code_info(triple.unstable);
        auto stable_pos_info = calculate_code_info(triple.stable_pos);

        remove_factor(stable_neg_info, triple.unstable, stable_pos_info);

        TripleInfo triple_info{StableInfo{stable_neg_info}, UnstableInfo{unstable_info}, StableInfo{stable_pos_info}};

        std::lock_guard<std::mutex> lock{mut};
        triple_infos.emplace(index, std::make_pair(triple, std::move(triple_info)));

        ++progress;
    };

    tbb::parallel_for(size_t{0}, triples.size(), lambda);

    return triple_infos;
}
