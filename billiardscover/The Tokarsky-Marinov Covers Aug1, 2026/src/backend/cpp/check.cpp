#include <iostream>

#include "check.hpp"
#include "cover.hpp"
#include "equations.hpp"
#include "evaluator.hpp"

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

// Convert the givin amount of digits to a roughly equivalent amount of bits
static uint32_t digits_to_bits(const uint32_t digits) {
    // This is the function that boost uses, and so we'll keep it for now

    // log2(10) ~ 1000/301

    return (digits * 1000) / 301 + ((digits * 1000) % 301 ? 2 : 1);
}

std::string check_square(const int64_t numerx, const int64_t numery, const int64_t denom, const CodeSequence& code_seq,
                         const InitialAngles& initial_angles, const std::string& cover_dir) {

    const StableInfo code_info{calculate_code_info(CodePair{code_seq, initial_angles})};

    const Rational cx = Rational{numerx} / denom;
    const Rational cy = Rational{numery} / denom;

    const Rational r = Rational{1, 2} / denom;

    const Rational x_min = cx - r;
    const Rational x_max = cx + r;

    const Rational y_min = cy - r;
    const Rational y_max = cy + r;

    const ClosedRectangleQ square{{x_min, x_max}, {y_min, y_max}};

    const auto digits = load_digits(cover_dir);

    const auto bits = digits_to_bits(digits);

    const auto cover = covers_square(code_info, square, bits);

    std::ostringstream oss{};

    oss << "Code Sequence = " << code_seq << '\n';
    oss << "Initial Angles = " << initial_angles << '\n';
    oss << "Center = (" << cx << ", " << cy << ")\n";
    oss << "Radius = " << r << '\n';
    //oss << "Lower Bound = " << static_cast<double>((cx + cy) * r / 1000) << '\n';

    oss << "Covered = " << cover << '\n';

    return oss.str();
}
