#include "general.hpp"

template class geometry::Point<Rational>;
template class geometry::Segment<Rational, geometry::Topology::Open>;
template class geometry::ConvexPolygon<Rational, geometry::Topology::Open>;
template class geometry::Segment<Rational, geometry::Topology::Closed>;
template class geometry::Rectangle<Rational, geometry::Topology::Closed>;
template class geometry::ConvexPolygon<Rational, geometry::Topology::Closed>;

template class math::LinComArr<XY, Coeff32>;
template class math::LinComArr<XYPi, Coeff32>;
template class math::LinComArr<XYEta, Coeff32>;

template class math::LinComMap<Sin<LinComArrZ<XY>>, Coeff32>;
template class math::LinComMap<Cos<LinComArrZ<XY>>, Coeff32>;

template class math::LinComVec<Sin<math::LinComArr<XY, Coeff16>>, Coeff16>;
template class math::LinComVec<Cos<math::LinComArr<XY, Coeff16>>, Coeff16>;

std::ostream& operator<<(std::ostream& os, const Sign sign) {
    switch (sign) {
    case Sign::Neg:
        return os << "neg";
    case Sign::Zero:
        return os << "zero";
    case Sign::Pos:
        return os << "pos";
    }

    throw std::runtime_error(invalid_enum_value("Sign", sign));
}

LinComArrZ<XYEta> xyz_to_xyeta(const XYZ symbol) {
    switch (symbol) {
    case XYZ::X:
        return LinComArrZ<XYEta>{1, 0, 0};
    case XYZ::Y:
        return LinComArrZ<XYEta>{0, 1, 0};
    case XYZ::Z:
        // z = -x - y + 2*eta
        return LinComArrZ<XYEta>{-1, -1, 2};
    }

    throw std::runtime_error(invalid_enum_value("XYZ", symbol));
}

LinComArrZ<XYPi> xyz_to_xypi(const XYZ symbol) {
    switch (symbol) {
    case XYZ::X:
        return LinComArrZ<XYPi>{1, 0, 0};
    case XYZ::Y:
        return LinComArrZ<XYPi>{0, 1, 0};
    case XYZ::Z:
        // z = -x - y + pi
        return LinComArrZ<XYPi>{-1, -1, 1};
    }

    throw std::runtime_error(invalid_enum_value("XYZ", symbol));
}

LinComArrZ<XYEtaPhi> xyz_to_xyetaphi(const XYZ symbol) {
    switch (symbol) {
    case XYZ::X:
        return LinComArrZ<XYEtaPhi>{1, 0, 0, 0};
    case XYZ::Y:
        return LinComArrZ<XYEtaPhi>{0, 1, 0, 0};
    case XYZ::Z:
        // z = -x - y + 2*eta
        return LinComArrZ<XYEtaPhi>{-1, -1, 2, 0};
    }

    throw std::runtime_error(invalid_enum_value("XYZ", symbol));
}

// Here would be the perfect place to use unsigned ints, but we don't have overflow checking for
// them sadly
template <typename Eq>
std::pair<Coeff64, Coeff64> gradient_bounds(const Eq& eq) {

    Coeff64 sum_x = 0;
    Coeff64 sum_y = 0;

    for (const auto& kv : eq) {

        const auto coeff = kv.second;

        const auto& arg = kv.first.arg;
        const auto x_coeff = arg.coeff(XY::X);
        const auto y_coeff = arg.coeff(XY::Y);

        sum_x += math::abs(coeff) * math::abs(x_coeff);
        sum_y += math::abs(coeff) * math::abs(y_coeff);
    }

    return {sum_x, sum_y};
}

template std::pair<Coeff64, Coeff64> gradient_bounds(const EqMap<Sin>& eq);
template std::pair<Coeff64, Coeff64> gradient_bounds(const EqMap<Cos>& eq);
template std::pair<Coeff64, Coeff64> gradient_bounds(const EqVec<Sin>& eq);
template std::pair<Coeff64, Coeff64> gradient_bounds(const EqVec<Cos>& eq);

// Split the segment in half
std::array<ClosedSegmentQ, 2> subdivide(const ClosedSegmentQ& seg) {

    const auto midpoint = seg.midpoint();

    const ClosedSegmentQ first{seg.start(), midpoint};
    const ClosedSegmentQ second{midpoint, seg.end()};

    // This is first, second
    return {{first, second}};
}

// +-----+-----+
// |     |     |
// |     |     |
// +-----+-----+
// |     |     |
// |     |     |
// +-----+-----+
// Split the rect into four quarters
std::array<ClosedRectangleQ, 4> subdivide(const ClosedRectangleQ& rect) {

    const auto center = rect.center();

    const ClosedRectangleQ upper_left{{rect.interval_x().lower(), center.x},
                                      {center.y, rect.interval_y().upper()}};

    const ClosedRectangleQ upper_right{{center.x, rect.interval_x().upper()},
                                       {center.y, rect.interval_y().upper()}};

    const ClosedRectangleQ lower_left{{rect.interval_x().lower(), center.x},
                                      {rect.interval_y().lower(), center.y}};

    const ClosedRectangleQ lower_right{{center.x, rect.interval_x().upper()},
                                       {rect.interval_y().lower(), center.y}};

    // This is UL, UR, LL, LR
    return {{upper_left, upper_right, lower_left, lower_right}};
}
