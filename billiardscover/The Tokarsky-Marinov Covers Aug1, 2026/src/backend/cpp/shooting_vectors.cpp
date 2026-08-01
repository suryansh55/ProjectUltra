#include "shooting_vectors.hpp"
#include "trig_identities.hpp"

static LinComArrZ<XYPi> shooting_angle_odd(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYPi>>& code_angles) {

    const auto& code_numbers = code_sequence.numbers();

    LinComArrZ<XYPi> shooting_angle{0, 0, 1};

    for (size_t i = 0; i < code_numbers.size(); i += 1) {
        const auto number = code_numbers.at(i);
        const auto& angle = code_angles.at(i);

        // even indices are negative
        // odd indices are positive
        if ((i % 2) == 0) {
            shooting_angle.sub(number, angle);
        } else {
            shooting_angle.add(number, angle);
        }
    }

    // Once Z is substituted out (which we have done already), all the coefficients should be
    // even. TODO should we prove this is always the case, so we don't have to check?
    // In that case, it would be nice to make the code_numbers be in a sum type for each type
    // of code number
    for (auto& coeff : shooting_angle) {
        if (coeff % 2 != 0) {
            std::ostringstream err;
            err << code_numbers << ": odd coefficient in shooting_angle_odd";
            throw std::runtime_error(err.str());
        }

        coeff /= 2;
    }

    return shooting_angle;
}

static LinComArrZ<XYEta> shooting_angle_perp(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYEta>>& code_angles) {

    const auto perp_index = *code_sequence.perp_index();

    const auto& code_numbers = code_sequence.numbers();

    const auto perp_number = code_numbers.at(perp_index);

    const auto& perp_angle = code_angles.at(perp_index);

    // theta_{perp_index} = pi / 2 - perp_number / 2 * perp_angle
    const LinComArrZ<XYEta> angle = [&]() {
        LinComArrZ<XYEta> builder{0, 0, 1};
        builder.sub(perp_number / 2, perp_angle); // perp_number is guaranteed to be even
        return builder;
    }();

    // now we derive another expression for the perp_angle and solve for the shooting angle
    LinComArrZ<XYEta> running_sum{};

    for (size_t i = 0; i < perp_index; i += 1) {
        running_sum.scale(-1);

        const auto code_number = code_numbers.at(i);
        const auto& code_angle = code_angles.at(i);

        running_sum.sub(code_number, code_angle);
    }

    LinComArrZ<XYEta> shooting_angle{};
    if (perp_index % 2 == 0) {
        // shooting_angle + running_sum = angle
        // => shooting_angle = angle - running_sum;
        shooting_angle.add(angle);
        shooting_angle.sub(running_sum);
    } else {
        // pi - shooting_angle + running_sum = angle
        // => shooting_angle = running_sum - angle + pi
        shooting_angle.add(running_sum);
        shooting_angle.sub(angle);
        shooting_angle.add(2, XYEta::Eta);
    }

    return shooting_angle;
}

std::pair<LinComMapZ<Cos<LinComArrZ<XY>>>, LinComMapZ<Sin<LinComArrZ<XY>>>> shooting_vector_odd(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYPi>>& code_angles) {

    auto shooting_angle = shooting_angle_odd(code_sequence, code_angles);

    // make it polar
    shooting_angle.scale(-1);
    shooting_angle.add(XYPi::Pi);

    // cos is x coord, sin is y coord
    const auto cos = simplify_cos_xypi(shooting_angle);
    LinComMapZ<Cos<LinComArrZ<XY>>> cos_builder{};
    cos_builder.add(cos.first, cos.second);

    const auto sin = simplify_sin_xypi(shooting_angle);
    LinComMapZ<Sin<LinComArrZ<XY>>> sin_builder{};
    sin_builder.add(sin.first, sin.second);

    return {cos_builder, sin_builder};
}

std::pair<LinComMapZ<Sin<LinComArrZ<XY>>>, LinComMapZ<Cos<LinComArrZ<XY>>>> shooting_vector_perp(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYEta>>& code_angles) {

    auto shooting_angle = shooting_angle_perp(code_sequence, code_angles);

    // make it polar
    shooting_angle.scale(-1);
    shooting_angle.add(2, XYEta::Eta);

    // sin is x coord, cos is y coord in this case
    const auto sin = simplify_cos_xyeta(shooting_angle);
    LinComMapZ<Sin<LinComArrZ<XY>>> sin_builder{};
    sin_builder.add(sin.first, sin.second);

    const auto cos = simplify_sin_xyeta(shooting_angle);
    LinComMapZ<Cos<LinComArrZ<XY>>> cos_builder{};
    cos_builder.add(cos.first, cos.second);

    return {sin_builder, cos_builder};
}
