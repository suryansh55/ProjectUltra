#pragma once

#include "code_sequence.hpp"
#include "general.hpp"

Sign line_sign_at_point(const LinComArrZ<XYEta>& equation, const PointQ& point);

Rational eval(const LinComArrZ<XYEta>& equation, const PointQ& point);

Sign rational_sign(const Rational& rat);

struct RationalLineSegment {

    PointQ point0;
    LinComArrZ<XYEta> line0;
    PointQ point1;
    LinComArrZ<XYEta> line1;

    explicit RationalLineSegment(PointQ point0_,
                                 const LinComArrZ<XYEta>& line0_,
                                 PointQ point1_,
                                 const LinComArrZ<XYEta>& line1_)
        : point0{std::move(point0_)},
          line0{line0_},
          point1{std::move(point1_)},
          line1{line1_} {}
};

boost::optional<RationalLineSegment> calculate_bounding_line_segment(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles, const LinComArrZ<XYEta>& constraint);

struct RationalPair {

    PointQ point;
    LinComArrZ<XYEta> side_line;

    explicit RationalPair(PointQ point_, const LinComArrZ<XYEta>& side_line_)
        : point{std::move(point_)},
          side_line{side_line_} {
    }
};

// PointQ is the point, and LinComArrZ<XYEta> is the line that goes from that point to the next one
using RationalPolygon = std::vector<RationalPair>;

boost::optional<RationalPolygon> calculate_bounding_polygon(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles);
