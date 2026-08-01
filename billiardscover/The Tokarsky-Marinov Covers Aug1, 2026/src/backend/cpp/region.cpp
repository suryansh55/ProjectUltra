#include "region.hpp"
#include "inequalities.hpp"

static PointQ intersection(const LinComArrZ<XYEta>& line1, const LinComArrZ<XYEta>& line2) {

    // line1 = a1*x + b1*y + c1
    // line2 = a2*x + b2*y + c2
    // [ a1 b1 ] [ x ] = [ - c1 ]
    // [ a2 b2 ] [ y ] = [ - c2 ]
    // Now solve using Cramer's rule

    // Calculating the following determinants can overflow for 32 bit integers, so we
    // convert them to 64 bits before doing the math.
    const Coeff64 a1 = line1.coeff<XYEta::X>();
    const Coeff64 b1 = line1.coeff<XYEta::Y>();
    const Coeff64 c1 = line1.coeff<XYEta::Eta>();

    const Coeff64 a2 = line2.coeff<XYEta::X>();
    const Coeff64 b2 = line2.coeff<XYEta::Y>();
    const Coeff64 c2 = line2.coeff<XYEta::Eta>();

    const auto det = a1 * b2 - b1 * a2;

    // GMP always expects the denominator to be unsigned, which is not the case
    // for us. As a workaround, divide by the determinant instead.
    // This will throw an exception on division by zero, which indicates
    // an inconsistent system.
    const Rational first = Rational{b1 * c2 - b2 * c1} / det;
    const Rational second = Rational{a2 * c1 - a1 * c2} / det;

    return {first, second};
}

Rational eval(const LinComArrZ<XYEta>& equation, const PointQ& point) {

    // c1 * x + c2 * y + c3 * eta
    const auto x_coeff = equation.coeff<XYEta::X>();
    const auto y_coeff = equation.coeff<XYEta::Y>();
    const auto eta_coeff = equation.coeff<XYEta::Eta>();

    const Rational result = x_coeff * point.x + y_coeff * point.y + eta_coeff;

    return result;
}

Sign rational_sign(const Rational& rat) {

    const auto sign = rat.sign();

    if (sign > 0) {
        return Sign::Pos;
    }

    if (sign < 0) {
        return Sign::Neg;
    }

    if (sign == 0) {
        return Sign::Zero;
    }

    // Strictly speaking, this shouldn't be necessary, since integers obey
    // trichotomy, but the type system is unaware of that, so I'll leave
    // this in here.
    throw std::runtime_error(rat.str() + " has no sign");
}

Sign line_sign_at_point(const LinComArrZ<XYEta>& equation, const PointQ& point) {
    return rational_sign(eval(equation, point));
}

#define CASE3(x, y, z) if (zero_zero_sign == x && pi_zero_sign == y && zero_pi_sign == z)

// Calculate where the constraint intersects the (0,0) (0, 2*eta), (2*eta, 0) triangle
static boost::optional<RationalLineSegment> initial_line_segment(const LinComArrZ<XYEta>& constraint) {

    // The most basic constraints are that x, y, z > 0
    // This gives us the open triangle with coordinates (0, 0), (2, 0), and (0, 2).
    const LinComArrZ<XYEta> x{1, 0, 0};   // x > 0
    const LinComArrZ<XYEta> y{0, 1, 0};   // y > 0
    const LinComArrZ<XYEta> z{-1, -1, 2}; // z = - x - y + 2*eta > 0

    const auto zero_zero_sign = line_sign_at_point(constraint, {0, 0});
    const auto pi_zero_sign = line_sign_at_point(constraint, {2, 0});
    const auto zero_pi_sign = line_sign_at_point(constraint, {0, 2});

    // The line segment must be part of the interior of the triangle
    // Anything lying along the edges must get thrown out

    // As tedious as this seems, we need to deal with all 3^3 = 27 cases individually

    CASE3(Sign::Neg, Sign::Neg, Sign::Neg) {
        return boost::none;
    }

    CASE3(Sign::Neg, Sign::Neg, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Neg, Sign::Neg, Sign::Pos) {
        const auto x_inter = intersection(constraint, x);
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{x_inter, x, z_inter, z};
    }

    CASE3(Sign::Neg, Sign::Zero, Sign::Neg) {
        return boost::none;
    }

    CASE3(Sign::Neg, Sign::Zero, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Neg, Sign::Zero, Sign::Pos) {
        const auto x_inter = intersection(constraint, x);
        return RationalLineSegment{x_inter, x, {2, 0}, z};
    }

    CASE3(Sign::Neg, Sign::Pos, Sign::Neg) {
        const auto y_inter = intersection(constraint, y);
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{y_inter, y, z_inter, z};
    }

    CASE3(Sign::Neg, Sign::Pos, Sign::Zero) {
        const auto y_inter = intersection(constraint, y);
        return RationalLineSegment{{0, 2}, x, y_inter, y};
    }

    CASE3(Sign::Neg, Sign::Pos, Sign::Pos) {
        const auto x_inter = intersection(constraint, x);
        const auto y_inter = intersection(constraint, y);
        return RationalLineSegment{x_inter, x, y_inter, y};
    }

    CASE3(Sign::Zero, Sign::Neg, Sign::Neg) {
        return boost::none;
    }

    CASE3(Sign::Zero, Sign::Neg, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Zero, Sign::Neg, Sign::Pos) {
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{{0, 0}, y, z_inter, z};
    }

    CASE3(Sign::Zero, Sign::Zero, Sign::Neg) {
        return boost::none;
    }

    CASE3(Sign::Zero, Sign::Zero, Sign::Zero) {
        // Mathematically impossible
        throw std::runtime_error("initial_line_segment: zero-zero-zero");
    }

    CASE3(Sign::Zero, Sign::Zero, Sign::Pos) {
        return boost::none;
    }

    CASE3(Sign::Zero, Sign::Pos, Sign::Neg) {
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{{0, 0}, y, z_inter, z};
    }

    CASE3(Sign::Zero, Sign::Pos, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Zero, Sign::Pos, Sign::Pos) {
        return boost::none;
    }

    CASE3(Sign::Pos, Sign::Neg, Sign::Neg) {
        const auto x_inter = intersection(constraint, x);
        const auto y_inter = intersection(constraint, y);
        return RationalLineSegment{x_inter, x, y_inter, y};
    }

    CASE3(Sign::Pos, Sign::Neg, Sign::Zero) {
        const auto y_inter = intersection(constraint, y);
        return RationalLineSegment{{0, 2}, x, y_inter, y};
    }

    CASE3(Sign::Pos, Sign::Neg, Sign::Pos) {
        const auto y_inter = intersection(constraint, y);
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{y_inter, y, z_inter, z};
    }

    CASE3(Sign::Pos, Sign::Zero, Sign::Neg) {
        const auto x_inter = intersection(constraint, x);
        return RationalLineSegment{x_inter, x, {2, 0}, y};
    }

    CASE3(Sign::Pos, Sign::Zero, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Pos, Sign::Zero, Sign::Pos) {
        return boost::none;
    }

    CASE3(Sign::Pos, Sign::Pos, Sign::Neg) {
        const auto x_inter = intersection(constraint, x);
        const auto z_inter = intersection(constraint, z);
        return RationalLineSegment{x_inter, x, z_inter, z};
    }

    CASE3(Sign::Pos, Sign::Pos, Sign::Zero) {
        return boost::none;
    }

    CASE3(Sign::Pos, Sign::Pos, Sign::Pos) {
        return boost::none;
    }

    std::ostringstream err{};
    err << "initial_line_segment: unknown signs " << static_cast<size_t>(zero_zero_sign) << ", " << static_cast<size_t>(pi_zero_sign) << ", " << static_cast<size_t>(zero_pi_sign);
    throw std::runtime_error(err.str());
}

#define CASE2(x, y) if (sign0 == x && sign1 == y)

static boost::optional<RationalLineSegment> refine_line_segment(const LinComArrZ<XYEta>& line, const RationalLineSegment& line_segment, const LinComArrZ<XYEta>& constraint) {

    const auto& point0 = line_segment.point0;
    const auto& point1 = line_segment.point1;

    const auto sign0 = line_sign_at_point(line, point0);
    const auto sign1 = line_sign_at_point(line, point1);

    const auto& line0 = line_segment.line0;
    const auto& line1 = line_segment.line1;

    CASE2(Sign::Neg, Sign::Neg) {
        return boost::none;
    }

    CASE2(Sign::Neg, Sign::Zero) {
        return boost::none;
    }

    CASE2(Sign::Neg, Sign::Pos) {
        const auto inter = intersection(constraint, line);
        return RationalLineSegment{inter, line, point1, line1};
    }

    CASE2(Sign::Zero, Sign::Neg) {
        return boost::none;
    }

    CASE2(Sign::Zero, Sign::Zero) {
        return boost::none;
    }

    CASE2(Sign::Zero, Sign::Pos) {
        return line_segment;
    }

    CASE2(Sign::Pos, Sign::Neg) {
        const auto inter = intersection(constraint, line);
        return RationalLineSegment{point0, line0, inter, line};
    }

    CASE2(Sign::Pos, Sign::Zero) {
        return line_segment;
    }

    CASE2(Sign::Pos, Sign::Pos) {
        return line_segment;
    }

    std::ostringstream err{};
    err << "refine_line_segment: unknown signs " << static_cast<size_t>(sign0) << ", " << static_cast<size_t>(sign1);
    throw std::runtime_error(err.str());
}

boost::optional<RationalLineSegment> calculate_bounding_line_segment(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const LinComArrZ<XYEta>& constraint) {

    const auto inequalities = calculate_bounding_inequalities(code_numbers, code_angles);

    auto line_segment = initial_line_segment(constraint);

    if (!line_segment) {
        return boost::none;
    }

    for (const auto& line : inequalities) {
        line_segment = refine_line_segment(line, *line_segment, constraint);
        if (!line_segment) {
            return boost::none;
        }
    }

    return line_segment;
}

static boost::optional<RationalPolygon> refine_polygon(const RationalPolygon& polygon, const LinComArrZ<XYEta>& line) {

    std::vector<Sign> signs{};
    for (const auto& rat_pair : polygon) {
        const auto sign = line_sign_at_point(line, rat_pair.point);
        signs.push_back(sign);
    }

    RationalPolygon new_polygon{};

    const size_t size = polygon.size();

    for (size_t i = 0; i < size; i += 1) {
        const auto& point = polygon.at(i).point;
        const auto& side_line = polygon.at(i).side_line;

        const auto sign0 = signs.at(i);
        const auto sign1 = signs.at((i + 1) % size);

        // TODO change this to CASE2
        if (sign0 == Sign::Neg && sign1 == Sign::Neg) {
            continue;
        } else if (sign0 == Sign::Neg && sign1 == Sign::Zero) {
            continue;
        } else if (sign0 == Sign::Neg && sign1 == Sign::Pos) {
            const auto inter = intersection(side_line, line);
            new_polygon.emplace_back(inter, side_line);

        } else if (sign0 == Sign::Zero && sign1 == Sign::Neg) {
            new_polygon.emplace_back(point, line);
        } else if (sign0 == Sign::Zero && sign1 == Sign::Zero) {
            new_polygon.emplace_back(point, side_line);
        } else if (sign0 == Sign::Zero && sign1 == Sign::Pos) {
            new_polygon.emplace_back(point, side_line);

        } else if (sign0 == Sign::Pos && sign1 == Sign::Neg) {
            new_polygon.emplace_back(point, side_line);
            const auto inter = intersection(side_line, line);
            new_polygon.emplace_back(inter, line);
        } else if (sign0 == Sign::Pos && sign1 == Sign::Zero) {
            new_polygon.emplace_back(point, side_line);
        } else if (sign0 == Sign::Pos && sign1 == Sign::Pos) {
            new_polygon.emplace_back(point, side_line);

        } else {
            std::ostringstream err{};
            err << "refine_polygon: unknown signs " << static_cast<size_t>(sign0) << ", " << static_cast<size_t>(sign1);
            throw std::runtime_error(err.str());
        }
    }

    // This polygon must be an open set, so polyons with just two points are
    // empty sets (since those would just be a straight line).
    if (new_polygon.size() < 3) {
        return boost::none;
    }

    return new_polygon;
}

boost::optional<RationalPolygon> calculate_bounding_polygon(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles) {

    // each inequality is > 0
    const auto inequalities = calculate_bounding_inequalities(code_numbers, code_angles);

    const LinComArrZ<XYEta> x{1, 0, 0};   // x > 0
    const LinComArrZ<XYEta> y{0, 1, 0};   // y > 0
    const LinComArrZ<XYEta> z{-1, -1, 2}; // z = - x - y + 2*eta > 0

    // The initial bounding polygon
    RationalPolygon polygon{RationalPair{{0, 0}, y}, RationalPair{{2, 0}, z}, RationalPair{{0, 2}, x}};

    for (const auto& line : inequalities) {

        const auto new_polygon = refine_polygon(polygon, line);

        if (!new_polygon) {
            return boost::none;
        }

        polygon = *new_polygon;
    }
    return polygon;
}
