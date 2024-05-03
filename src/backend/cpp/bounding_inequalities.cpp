//#include "bad_angles.hpp"
#include "conversion.hpp"

#include "bounding_inequalities.hpp"

static std::vector<LinComArrZ<XYEtaPhi>> calculate_angles(const std::vector<std::pair<CodeNumber, XYZ>>& code_nums_angles) {

    std::vector<LinComArrZ<XYEtaPhi>> angles{};

    // phi = shooting_angle
    const LinComArrZ<XYEtaPhi> shooting_angle{0, 0, 0, 1};

    angles.push_back(shooting_angle);

    LinComArrZ<XYEtaPhi> running_sum{};

    for (size_t i = 0; i < code_nums_angles.size() - 1; ++i) {
        running_sum.scale(-1);

        const auto number = code_nums_angles.at(i).first;
        const auto angle = code_nums_angles.at(i).second;

        running_sum.sub(number, xyz_to_xyetaphi(angle));

        LinComArrZ<XYEtaPhi> new_angle{};
        if (i % 2 == 0) {
            new_angle.add(2, XYEtaPhi::Eta); // Pi = 2*Eta
            new_angle.sub(XYEtaPhi::Phi);
            new_angle.add(running_sum);
        } else {
            new_angle.add(XYEtaPhi::Phi);
            new_angle.add(running_sum);
        }

        angles.push_back(new_angle);
    }

    return angles;
}

// each equation must be > 0
static std::array<LinComArrZ<XYEtaPhi>, 4> calculate_even_equations(const CodeNumber code_number, const XYZ code_angle, const LinComArrZ<XYEtaPhi>& theta) {

    // number = 2*n (even)
    const CodeNumber n = code_number / 2;

    // 0 < theta
    LinComArrZ<XYEtaPhi> first{};
    first.add(theta);

    // theta + (n - 1)*angle < pi / 2
    // 0 < pi / 2 - theta - (n - 1)*angle
    LinComArrZ<XYEtaPhi> second{};
    second.add(XYEtaPhi::Eta);
    second.sub(theta);
    second.sub(n - 1, xyz_to_xyetaphi(code_angle));

    // pi / 2 < theta + (n + 1) * angle
    // 0 < theta + (n + 1) * angle - pi / 2
    LinComArrZ<XYEtaPhi> third{};
    third.add(theta);
    third.add(n + 1, xyz_to_xyetaphi(code_angle));
    third.sub(XYEtaPhi::Eta);

    // theta + (2n) *angle < pi
    // 0 < pi - theta - number*angle
    LinComArrZ<XYEtaPhi> fourth{};
    fourth.add(2, XYEtaPhi::Eta);
    fourth.sub(theta);
    fourth.sub(code_number, xyz_to_xyetaphi(code_angle));

    return {{first, second, third, fourth}};
}

static std::array<LinComArrZ<XYEtaPhi>, 4> calculate_odd_equations(const CodeNumber code_number, const XYZ code_angle, const LinComArrZ<XYEtaPhi>& theta) {

    // number = 2*n + 1 (odd)
    const CodeNumber n = (code_number - 1) / 2;

    // 0 < theta
    LinComArrZ<XYEtaPhi> first{};
    first.add(theta);

    // theta + n*angle < pi / 2
    // 0 < pi / 2 - theta - n*angle
    LinComArrZ<XYEtaPhi> second{};
    second.add(XYEtaPhi::Eta);
    second.sub(theta);
    second.sub(n, xyz_to_xyetaphi(code_angle));

    // pi / 2 < theta + (n + 1)*angle
    // 0 < theta + (n + 1)*angle - pi / 2
    LinComArrZ<XYEtaPhi> third{};
    third.add(theta);
    third.add(n + 1, xyz_to_xyetaphi(code_angle));
    third.sub(XYEtaPhi::Eta);

    // theta + (2n + 1)*angle < pi
    // 0 < pi - theta - number*angle
    LinComArrZ<XYEtaPhi> fourth{};
    fourth.add(2, XYEtaPhi::Eta);
    fourth.sub(theta);
    fourth.sub(code_number, xyz_to_xyetaphi(code_angle));

    return {{first, second, third, fourth}};
}

static LinComArrZ<XYEta> remove_phi(const LinComArrZ<XYEtaPhi>& equation) {

    const auto phi = equation.coeff<XYEtaPhi::Phi>();

    if (phi != 0) {
        throw std::runtime_error("remove_phi: non-zero phi coeff");
    }

    const auto x = equation.coeff<XYEtaPhi::X>();
    const auto y = equation.coeff<XYEtaPhi::Y>();
    const auto eta = equation.coeff<XYEtaPhi::Eta>();

    return LinComArrZ<XYEta>{x, y, eta};
}

static std::set<LinComArrZ<XYEta>> eliminate_phi(const std::set<LinComArrZ<XYEtaPhi>>& positive_phi, const std::set<LinComArrZ<XYEtaPhi>>& negative_phi) {

    // 0 < equation for each equation in the inequalities
    // so add the ones with negative theta to the ones with positive theta
    // this is sorted so the iteration order when refining the region is always the same
    std::set<LinComArrZ<XYEta>> zero_phi{};
    for (const auto& positive_equation : positive_phi) {
        for (const auto& negative_equation : negative_phi) {

            const auto zero_equation = LinComArrZ<XYEtaPhi>::add(positive_equation, negative_equation);

            auto no_phi = remove_phi(zero_equation);

            // Some of the equations turn out to be common factor multiples of each other,
            // so this gets rid of duplicates
            no_phi.divide_content();

            zero_phi.insert(no_phi);
        }
    }

    return zero_phi;
}

static std::set<LinComArrZ<XYEta>> first_inequalities(const std::vector<std::pair<CodeNumber, XYZ>>& code_nums_angles) {

    const auto theta_angles = calculate_angles(code_nums_angles);

    // We need this to be a set, since some of the equations can be duplicates
    std::set<LinComArrZ<XYEtaPhi>> positive_phi{};
    std::set<LinComArrZ<XYEtaPhi>> negative_phi{};

    // unfortunately, C++ doesn't have a zip feature, so
    // we have to just iterate
    for (size_t i = 0; i < code_nums_angles.size(); ++i) {
        const auto code_number = code_nums_angles.at(i).first;
        const auto code_angle = code_nums_angles.at(i).second;
        const auto& theta = theta_angles.at(i);

        std::array<LinComArrZ<XYEtaPhi>, 4> equations;
        if (code_number % 2 == 0) {
            equations = calculate_even_equations(code_number, code_angle, theta);
        } else {
            equations = calculate_odd_equations(code_number, code_angle, theta);
        }

        for (const auto& equation : equations) {

            const auto phi_coeff = equation.coeff<XYEtaPhi::Phi>();

            if (phi_coeff == 1) {
                positive_phi.insert(equation);
            } else if (phi_coeff == -1) {
                negative_phi.insert(equation);
            } else {
                std::ostringstream err{};
                err << "phi_coeff " << phi_coeff << " is not 1 or -1";
                throw std::runtime_error(err.str());
            }
        }
    }

    const auto no_phi_inequalities = eliminate_phi(positive_phi, negative_phi);

    return no_phi_inequalities;
}

// This doesn't really need to be sorted, but I like determinism.
std::set<LinComArrZ<XYEta>> calculate_bounding_inequalities(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles) {

    // nice little lambda to ensure const-correctness
    const auto code_nums_angles = [&]() {
        std::vector<std::pair<CodeNumber, XYZ>> tmp;
        for (const auto i : falgo::range(code_numbers.size())) {
            tmp.emplace_back(code_numbers.at(i), code_angles.at(i));
        }

        // Do it again
        if (tmp.size() % 2 != 0) {
            for (const auto i : falgo::range(code_numbers.size())) {
                tmp.emplace_back(code_numbers.at(i), code_angles.at(i));
            }
        }

        return tmp;
    }();

    const auto first_ineqs = first_inequalities(code_nums_angles);
    return first_ineqs;
    //const auto bad_angles = calculate_bad_angles(code_nums_angles);
    //std::cout << bad_angles << std::endl;

    //std::set<LinComArrZ<XYEta>> ineqs;
    //ineqs.insert(std::cbegin(first_ineqs), std::cend(first_ineqs));
    //std::cout << ineqs.size() << std::endl;
    //ineqs.insert(std::cbegin(bad_angles), std::cend(bad_angles));
    //std::cout << ineqs.size() << std::endl;

    //return ineqs;
}
