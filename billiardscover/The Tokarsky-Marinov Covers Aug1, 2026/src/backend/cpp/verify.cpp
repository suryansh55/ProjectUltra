#include <iostream>

#include <tbb/parallel_invoke.h>

#include "evaluator.hpp"
#include "progress.hpp"
#include "region.hpp"
#include "verify.hpp"

template <template <typename> class Trig>
bool equations_positive(const std::vector<std::tuple<EqVec<Trig>, Coeff64, Coeff64>>& eqs, const PointQ& center, const Rational& radius, Evaluator& eval) {

    for (const auto& tup : eqs) {

        const auto& eq = std::get<0>(tup);
        const auto bx = std::get<1>(tup);
        const auto by = std::get<2>(tup);

        const auto pos = eval.is_positive(eq, bx + by, center, radius);

        if (!pos) {
            std::cout << "Failure: not positive" << std::endl;
            std::cout << "f(x, y) = " << eq << std::endl;
            std::cout << "bound = " << (bx + by) << std::endl;
            std::cout << "center = " << center << std::endl;
            std::cout << "radius = " << radius << std::endl;
            return false;
        }
    }

    return true;
}

template <template <typename> class Trig>
bool equations_positive(const std::vector<std::tuple<EqVec<Trig>, Coeff64, Coeff64>>& eqs,
                        const PointQ& center, const Rational& rx, const Rational& ry, Evaluator& eval) {

    for (const auto& tup : eqs) {

        const auto& eq = std::get<0>(tup);
        const auto bx = std::get<1>(tup);
        const auto by = std::get<2>(tup);

        const auto pos = eval.is_positive(eq, bx, by, center, rx, ry);

        if (!pos) {
            std::cout << "Failure: not positive" << std::endl;
            std::cout << "f(x, y) = " << eq << std::endl;
            std::cout << "bx = " << bx << std::endl;
            std::cout << "by = " << by << std::endl;
            std::cout << "center = " << center << std::endl;
            std::cout << "rx = " << rx << std::endl;
            std::cout << "ry = " << ry << std::endl;
            return false;
        }
    }

    return true;
}

static bool covers_square(const StableInfo& info, const ClosedRectangleQ& square, const uint32_t bits) {

    if (!geometry::subset(square, info.polygon)) {
        std::cout << "Failure: square is not a subset of polygon" << std::endl;
        return false;
    }

    Evaluator eval{bits};

    const auto center = square.center();
    // It's a square, so we can use width or height
    const Rational radius = square.width() / 2;

    if (!equations_positive(info.sines, center, radius, eval)) {
        std::cout << "not all sines positive" << std::endl;
        return false;
    }

    if (!equations_positive(info.cosines, center, radius, eval)) {
        std::cout << "not all cosines positive" << std::endl;
        return false;
    }

    return true;
}

static PointQ find_intersection(const PointQ& corner0, const PointQ& corner1, const LinComArrZ<XYEta>& line) {

    // a*x + b*y + c = 0
    // => x = -(b*y + c) / a
    // => y = -(a*x + c) / b

    const auto a = line.coeff(XYEta::X);
    const auto b = line.coeff(XYEta::Y);
    const auto c = line.coeff(XYEta::Eta);

    if (corner0.x == corner1.x) {
        // vertical line, solve for y
        Rational y = -(a * corner0.x + c) / b;
        return {corner0.x, std::move(y)};

    } else if (corner0.y == corner1.y) {
        // horizontal line, solve for x
        Rational x = -(b * corner0.y + c) / a;
        return {std::move(x), corner0.y};
    } else {
        std::ostringstream err{};
        err << "find_intersection: invalid points: " << corner0 << ", " << corner1;
        throw std::runtime_error(err.str());
    }
}

struct CenterRadius final {

    PointQ center;
    Rational rx;
    Rational ry;

    explicit CenterRadius(PointQ center_, Rational rx_, Rational ry_)
        : center{std::move(center_)},
          rx{std::move(rx_)},
          ry{std::move(ry_)} {}
};

static boost::optional<CenterRadius> find_center_radius(const std::vector<PointQ>& primary,
                                                        const std::vector<PointQ>& secondary) {

    if (primary.empty()) {
        return boost::none;
    }

    Rational x_min = primary.at(0).x;
    Rational x_max = primary.at(0).x;

    Rational y_min = primary.at(0).y;
    Rational y_max = primary.at(0).y;

    for (size_t i = 1; i < primary.size(); ++i) {
        const auto& point = primary.at(i);

        if (point.x < x_min) {
            x_min = point.x;
        } else if (point.x > x_max) {
            x_max = point.x;
        }

        if (point.y < y_min) {
            y_min = point.y;
        } else if (point.y > y_max) {
            y_max = point.y;
        }
    }

    // Now do the same for the secondary
    for (const auto& point : secondary) {

        if (point.x < x_min) {
            x_min = point.x;
        } else if (point.x > x_max) {
            x_max = point.x;
        }

        if (point.y < y_min) {
            y_min = point.y;
        } else if (point.y > y_max) {
            y_max = point.y;
        }
    }

    Rational rx = (x_max - x_min) / 2;
    Rational ry = (y_max - y_min) / 2;

    Rational cx = x_min + rx;
    Rational cy = y_min + ry;

    return CenterRadius{PointQ{std::move(cx), std::move(cy)}, std::move(rx), std::move(ry)};
}

struct TripleCenterRadius final {

    boost::optional<CenterRadius> stable_neg;
    boost::optional<CenterRadius> unstable;
    boost::optional<CenterRadius> stable_pos;

    explicit TripleCenterRadius(boost::optional<CenterRadius> stable_neg_,
                                boost::optional<CenterRadius> unstable_,
                                boost::optional<CenterRadius> stable_pos_)
        : stable_neg{std::move(stable_neg_)},
          unstable{std::move(unstable_)},
          stable_pos{std::move(stable_pos_)} {}
};

static boost::optional<std::array<Sign, 4>> find_signs(const LinComArrZ<XYEta>& line, const std::array<PointQ, 4>& corners) {

    std::array<Sign, 4> signs{};
    for (size_t i = 0; i < corners.size(); ++i) {

        const auto result = eval(line, corners.at(i));

        // We need to ensure that the points are sufficiently close to the line to remain in the same
        // quadrant, so to speak. We need that -2 < result < 2, or that abs(result) < 2.
        if ((result <= -2) || (result >= 2)) {
            return boost::none;
        }

        signs.at(i) = rational_sign(result);
    }

    return signs;
}

static std::tuple<std::vector<PointQ>, std::vector<PointQ>, std::vector<PointQ>>
find_points(const std::array<PointQ, 4>& corners, const std::array<Sign, 4>& signs, const LinComArrZ<XYEta>& line) {

    std::vector<PointQ> negatives{}; // 0-4 points
    std::vector<PointQ> zeros{};     // 0, 1, or 2 points
    std::vector<PointQ> positives{}; // 0-4 points

    const auto size = corners.size();

    for (size_t i = 0; i < size; ++i) {

        const auto& corner0 = corners.at(i);
        const auto& corner1 = corners.at((i + 1) % size);

        const auto sign0 = signs.at(i);
        const auto sign1 = signs.at((i + 1) % size);

        if (sign0 == Sign::Neg && sign1 == Sign::Neg) {
            negatives.push_back(corner0);

        } else if (sign0 == Sign::Neg && sign1 == Sign::Zero) {
            negatives.push_back(corner0);

        } else if (sign0 == Sign::Neg && sign1 == Sign::Pos) {
            negatives.push_back(corner0);

            zeros.push_back(find_intersection(corner0, corner1, line));

        } else if (sign0 == Sign::Zero && sign1 == Sign::Neg) {
            zeros.push_back(corner0);

        } else if (sign0 == Sign::Zero && sign1 == Sign::Zero) {
            zeros.push_back(corner0);

        } else if (sign0 == Sign::Zero && sign1 == Sign::Pos) {
            zeros.push_back(corner0);

        } else if (sign0 == Sign::Pos && sign1 == Sign::Neg) {
            positives.push_back(corner0);

            zeros.push_back(find_intersection(corner0, corner1, line));

        } else if (sign0 == Sign::Pos && sign1 == Sign::Zero) {
            positives.push_back(corner0);

        } else if (sign0 == Sign::Pos && sign1 == Sign::Pos) {
            positives.push_back(corner0);
        } else {
            std::ostringstream err{};
            err << "triple_intersection: unknown signs " << static_cast<size_t>(sign0) << ", " << static_cast<size_t>(sign1);
            throw std::runtime_error(err.str());
        }
    }

    return std::make_tuple(std::move(negatives), std::move(zeros), std::move(positives));
}

static boost::optional<TripleCenterRadius>
triple_intersection(const TripleInfo& info, const LinComArrZ<XYEta>& line, const ClosedRectangleQ& square) {

    const std::array<PointQ, 4> corners{{square.lower_left(),
                                         square.lower_right(),
                                         square.upper_right(),
                                         square.upper_left()}};

    const auto signs = find_signs(line, corners);

    if (!signs) {
        return boost::none;
    }

    std::vector<PointQ> negatives{};
    std::vector<PointQ> zeros{};
    std::vector<PointQ> positives{};

    std::tie(negatives, zeros, positives) = find_points(corners, *signs, line);

    if (!negatives.empty()) {

        // Each negative point must be strictly inside the negative polygon
        for (const auto& point : negatives) {
            if (!geometry::element(point, info.stable_neg_info.polygon)) {
                return boost::none;
            }
        }

        // And each zero must be within the closure of the polygon
        for (const auto& point : zeros) {
            if (!geometry::element_or_boundary(point, info.stable_neg_info.polygon)) {
                return boost::none;
            }
        }
    }

    // Each zero point must be inside the line segment
    for (const auto& point : zeros) {
        if (!geometry::element(point, info.unstable_info.segment)) {
            return boost::none;
        }
    }

    if (!positives.empty()) {

        // All positive points must be strictly inside the polygon
        for (const auto& point : positives) {
            if (!geometry::element(point, info.stable_pos_info.polygon)) {
                return boost::none;
            }
        }

        // Ditto
        for (const auto& point : zeros) {
            if (!geometry::element_or_boundary(point, info.stable_pos_info.polygon)) {
                return boost::none;
            }
        }
    }

    auto stable_neg = find_center_radius(negatives, zeros);
    auto unstable = find_center_radius(zeros, {});
    auto stable_pos = find_center_radius(positives, zeros);

    return TripleCenterRadius{std::move(stable_neg), std::move(unstable), std::move(stable_pos)};
}

static bool covers_square(const TripleInfo& info, const LinComArrZ<XYEta>& line, const ClosedRectangleQ& square, const uint32_t bits) {

    const auto geo = triple_intersection(info, line, square);

    if (!geo) {
        return false;
    }

    Evaluator eval{bits};

    // Stable Neg
    if (geo->stable_neg) {

        const auto& cr = *geo->stable_neg;

        if (!equations_positive(info.stable_neg_info.sines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all sines positive" << std::endl;
            return false;
        }

        if (!equations_positive(info.stable_neg_info.cosines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all cosines positive" << std::endl;
            return false;
        }
    }

    // Unstable
    if (geo->unstable) {

        const auto& cr = *geo->unstable;

        if (!equations_positive(info.unstable_info.sines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all sines positive" << std::endl;
            return false;
        }

        if (!equations_positive(info.unstable_info.cosines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all cosines positive" << std::endl;
            return false;
        }
    }

    // Stable Pos
    if (geo->stable_pos) {

        const auto& cr = *geo->stable_pos;

        if (!equations_positive(info.stable_pos_info.sines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all sines positive" << std::endl;
            return false;
        }

        if (!equations_positive(info.stable_pos_info.cosines, cr.center, cr.rx, cr.ry, eval)) {
            std::cout << "not all cosines positive" << std::endl;
            return false;
        }
    }

    return true;
}

struct CountLeaves final : public boost::static_visitor<uint64_t> {

    uint64_t operator()(const cover::Empty) const {
        return 1;
    }

    uint64_t operator()(const cover::Single&) const {
        return 1;
    }

    uint64_t operator()(const cover::Triple&) const {
        return 1;
    }

    uint64_t operator()(const cover::Divide& divide) const {

        uint64_t sum = 0;
        for (const auto& quarter : divide.quarters.get()) {
            sum += boost::apply_visitor(*this, quarter);
        }

        return sum;
    }
};

class CoverVerifier final : public boost::static_visitor<bool> {

  private:
    const ClosedRectangleQ& square;
    const OpenConvexPolygonQ& polygon;
    const std::map<size_t, std::pair<CodePair, StableInfo>>& single_infos;
    const std::map<size_t, std::pair<TriplePair, TripleInfo>>& triple_infos;
    const uint32_t bits;
    Progress& progress;

  public:
    explicit CoverVerifier(const ClosedRectangleQ& square_, const OpenConvexPolygonQ& polygon_,
                           const std::map<size_t, std::pair<CodePair, StableInfo>>& single_infos_,
                           const std::map<size_t, std::pair<TriplePair, TripleInfo>>& triple_infos_,
                           const uint32_t bits_,
                           Progress& progress_)
        : square{square_},
          polygon{polygon_},
          single_infos{single_infos_},
          triple_infos{triple_infos_},
          bits{bits_},
          progress{progress_} {}

    bool operator()(const cover::Empty) const {

        const auto inter = geometry::intersects(square, polygon);

        if (inter) {
            std::cout << "Failure: empty square intersects polygon" << std::endl;
        }

        ++progress;

        return !inter;
    }

    bool operator()(const cover::Single& single) const {

        const auto& stable_info = single_infos.at(single.index).second;

        const auto cover = covers_square(stable_info, square, bits);

        ++progress;

        return cover;
    }

    bool operator()(const cover::Triple& triple) const {

        const auto& triple_pair = triple_infos.at(triple.index).first;
        const auto& triple_info = triple_infos.at(triple.index).second;

        const auto line = triple_pair.unstable.sequence.constraint(triple_pair.unstable.angles);

        const auto cover = covers_square(triple_info, line, square, bits);

        ++progress;

        return cover;
    }

    bool operator()(const cover::Divide& divide) const {

        const auto quarter_squares = subdivide(square);
        const auto& quarter_covers = divide.quarters.get();

        // These values get overwritten, so just initialize them to false
        bool covered0 = false;
        bool covered1 = false;
        bool covered2 = false;
        bool covered3 = false;

        const auto l0 = [&] {
            const CoverVerifier verifier{std::get<0>(quarter_squares), polygon, single_infos, triple_infos, bits, progress};
            covered0 = boost::apply_visitor(verifier, quarter_covers.get<0>());
        };

        const auto l1 = [&] {
            const CoverVerifier verifier{std::get<1>(quarter_squares), polygon, single_infos, triple_infos, bits, progress};
            covered1 = boost::apply_visitor(verifier, quarter_covers.get<1>());
        };

        const auto l2 = [&] {
            const CoverVerifier verifier{std::get<2>(quarter_squares), polygon, single_infos, triple_infos, bits, progress};
            covered2 = boost::apply_visitor(verifier, quarter_covers.get<2>());
        };

        const auto l3 = [&] {
            const CoverVerifier verifier{std::get<3>(quarter_squares), polygon, single_infos, triple_infos, bits, progress};
            covered3 = boost::apply_visitor(verifier, quarter_covers.get<3>());
        };

        tbb::parallel_invoke(l0, l1, l2, l3);

        return covered0 && covered1 && covered2 && covered3;
    }
};

// Convert the givin amount of digits to a roughly equivalent amount of bits
static uint32_t digits_to_bits(const uint32_t digits) {
    // This is the function that boost uses, and so we'll keep it for now

    // log2(10) ~ 1000/301

    return (digits * 1000) / 301 + ((digits * 1000) % 301 ? 2 : 1);
}

bool verify_cover(const ClosedRectangleQ& square, const OpenConvexPolygonQ& polygon,
                  const std::map<size_t, std::pair<CodePair, StableInfo>>& single_infos,
                  const std::map<size_t, std::pair<TriplePair, TripleInfo>>& triple_infos,
                  const cover::Cover& cover,
                  const uint32_t digits) {

    if (!geometry::subset(polygon, square)) {
        std::cout << "Error: the polygon is not a subset of the cover square" << std::endl;
        return false;
    }

    const auto bits = digits_to_bits(digits);

    const auto leaves = boost::apply_visitor(CountLeaves{}, cover);

    Progress progress{"Checking cover: ", leaves};

    const CoverVerifier verifier{square, polygon, single_infos, triple_infos, bits, progress};

    const auto covered = boost::apply_visitor(verifier, cover);

    return covered;
}
