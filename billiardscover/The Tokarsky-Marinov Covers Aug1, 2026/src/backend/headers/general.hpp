#pragma once

#include "geometry/algorithms.hpp"
#include "math/lin_com_arr.hpp"
#include "math/lin_com_map.hpp"
#include "math/lin_com_vec.hpp"
#include "math/symbols.hpp"
#include "math/trig.hpp"
#include "numbers.hpp"
#include "util.hpp"

// These are the only geometry types we use in the program, and we explicitly instantiate
// them in the cpp file. Using the extern suppresses instantiation here and in any file
// that includes this header.
using PointQ = geometry::Point<Rational>;
using OpenSegmentQ = geometry::Segment<Rational, geometry::Topology::Open>;
using OpenConvexPolygonQ = geometry::ConvexPolygon<Rational, geometry::Topology::Open>;
using ClosedSegmentQ = geometry::Segment<Rational, geometry::Topology::Closed>;
using ClosedRectangleQ = geometry::Rectangle<Rational, geometry::Topology::Closed>;
using ClosedConvexPolygonQ = geometry::ConvexPolygon<Rational, geometry::Topology::Closed>;

extern template class geometry::Point<Rational>;
extern template class geometry::Segment<Rational, geometry::Topology::Open>;
extern template class geometry::ConvexPolygon<Rational, geometry::Topology::Open>;
extern template class geometry::Segment<Rational, geometry::Topology::Closed>;
extern template class geometry::Rectangle<Rational, geometry::Topology::Closed>;
extern template class geometry::ConvexPolygon<Rational, geometry::Topology::Closed>;

template <typename T>
using LinComArrZ = math::LinComArr<T, Coeff32>;
extern template class math::LinComArr<XY, Coeff32>;
extern template class math::LinComArr<XYPi, Coeff32>;
extern template class math::LinComArr<XYEta, Coeff32>;

template <typename T>
using LinComMapZ = math::LinComMap<T, Coeff32>;
extern template class math::LinComMap<Sin<LinComArrZ<XY>>, Coeff32>;
extern template class math::LinComMap<Cos<LinComArrZ<XY>>, Coeff32>;

template <template <typename> class Trig>
using EqMap = LinComMapZ<Trig<LinComArrZ<XY>>>;

template <template <typename> class Trig>
using EqVec = math::LinComVec<Trig<math::LinComArr<XY, Coeff16>>, Coeff16>;
extern template class math::LinComVec<Sin<math::LinComArr<XY, Coeff16>>, Coeff16>;
extern template class math::LinComVec<Cos<math::LinComArr<XY, Coeff16>>, Coeff16>;

using Curves = std::pair<std::set<EqMap<Sin>>, std::set<EqMap<Cos>>>;

template <typename T>
std::vector<T> double_if_odd(const std::vector<T>& vec) {

    std::vector<T> copy = vec;

    if (vec.size() % 2 != 0) {
        copy.insert(std::end(copy), std::begin(vec), std::end(vec));
    }

    return copy;
}

template <typename T, typename S>
std::vector<std::pair<T, S>> zip(const std::vector<T>& first, const std::vector<S>& second) {

    const auto size = std::min(first.size(), second.size());

    std::vector<std::pair<T, S>> result{};

    for (size_t i = 0; i < size; ++i) {
        result.emplace_back(first.at(i), second.at(i));
    }

    return result;
}

template <typename T, typename Func>
auto transform(const std::vector<T>& vec, const Func& func) {

    // We don't initialize it with the number of elements, because that
    // default constructs the elements, which may not be default constructible.
    std::vector<decltype(func(vec.at(0)))> result{};
    std::transform(std::cbegin(vec), std::cend(vec), std::back_inserter(result), func);

    return result;
}

enum class Sign {
    Neg,
    Zero,
    Pos,
};

std::ostream& operator<<(std::ostream& os, const Sign sign);

LinComArrZ<XYEta> xyz_to_xyeta(const XYZ symbol);

LinComArrZ<XYPi> xyz_to_xypi(const XYZ symbol);

LinComArrZ<XYEtaPhi> xyz_to_xyetaphi(const XYZ symbol);

template <typename Eq>
std::pair<Coeff64, Coeff64> gradient_bounds(const Eq& eq);

extern template std::pair<Coeff64, Coeff64> gradient_bounds(const EqMap<Sin>& eq);
extern template std::pair<Coeff64, Coeff64> gradient_bounds(const EqMap<Cos>& eq);
extern template std::pair<Coeff64, Coeff64> gradient_bounds(const EqVec<Sin>& eq);
extern template std::pair<Coeff64, Coeff64> gradient_bounds(const EqVec<Cos>& eq);

std::array<ClosedSegmentQ, 2> subdivide(const ClosedSegmentQ& seg);
std::array<ClosedRectangleQ, 4> subdivide(const ClosedRectangleQ& rect);
