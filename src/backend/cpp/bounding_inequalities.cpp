//#include "bad_angles.hpp"
#include "conversion.hpp"

#include "bounding_inequalities.hpp"

// Suryansh Ankur, 2026
#include <tbb/parallel_for.h>
#include <tbb/blocked_range.h>
#include <tbb/enumerable_thread_specific.h>

static std::vector<LinComArrZ<XYEtaPhi>> calculate_angles(const std::vector<std::pair<CodeNumber, XYZ>>& code_nums_angles) {
    // Suryansh Ankur, 2026
    // ScopedTimer timer("calculate_angles");  // profiler disabled for release builds
    std::vector<LinComArrZ<XYEtaPhi>> angles{};

    // phi = shooting_angle
    LinComArrZ<XYEtaPhi> shooting_angle{0, 0, 0, 1};

    angles.push_back(shooting_angle);

    LinComArrZ<XYEtaPhi> running_sum{};

    for (size_t i = 0; i < code_nums_angles.size() - 1; ++i) {
        running_sum.scale(-1);

        auto number = code_nums_angles.at(i).first;
        auto angle = code_nums_angles.at(i).second;

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
    CodeNumber n = code_number / 2;

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
    CodeNumber n = (code_number - 1) / 2;

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

    auto phi = equation.coeff<XYEtaPhi::Phi>();

    if (phi != 0) {
        throw std::runtime_error("remove_phi: non-zero phi coeff");
    }

    auto x = equation.coeff<XYEtaPhi::X>();
    auto y = equation.coeff<XYEtaPhi::Y>();
    auto eta = equation.coeff<XYEtaPhi::Eta>();

    return LinComArrZ<XYEta>{x, y, eta};
}

  /* 2025,jul,31
   * This function is updated to calcualte new code parallel at the same time
   */
static std::set<LinComArrZ<XYEta>> eliminate_phi(const std::set<LinComArrZ<XYEtaPhi>>& positive_phi, const std::set<LinComArrZ<XYEtaPhi>>& negative_phi) {
    // Suryansh Ankur, 2026
    // ScopedTimer timer("eliminate_phi");  // profiler disabled for release builds
    // 0 < equation for each equation in the inequalities, so add the ones with
    // negative theta to the ones with positive theta.
    //
    // Parallelize over the positive equations with TBB (a shared, bounded,
    // work-stealing pool) instead of the old per-call boost::asio::thread_pool,
    // which oversubscribed the CPU when nested under the storageExecutor and
    // corrupted the native heap (SIGSEGV). enumerable_thread_specific gives one
    // accumulator set per worker thread, so peak memory scales with the thread
    // count rather than the chunk count. The result is a std::set, so the order
    // in which threads contribute does not affect it.
    std::vector<LinComArrZ<XYEtaPhi>> pos_vec(positive_phi.begin(), positive_phi.end());
    tbb::enumerable_thread_specific<std::set<LinComArrZ<XYEta>>> tls_zero_phi;

    tbb::parallel_for(tbb::blocked_range<std::size_t>(0, pos_vec.size()),
        [&](const tbb::blocked_range<std::size_t>& range) {
            std::set<LinComArrZ<XYEta>>& local = tls_zero_phi.local();
            for (std::size_t i = range.begin(); i < range.end(); ++i) {
                const auto& positive_equation = pos_vec[i];
                for (const auto& negative_equation : negative_phi) {
                    auto zero_equation = LinComArrZ<XYEtaPhi>::add(positive_equation, negative_equation);
                    auto no_phi = remove_phi(zero_equation);
                    no_phi.divide_content();
                    local.insert(no_phi);
                }
            }
        });

    std::set<LinComArrZ<XYEta>> zero_phi;
    for (auto& local : tls_zero_phi) {
        zero_phi.insert(local.begin(), local.end());
    }

    return zero_phi;
}

  /* 2025,jul,31
   * This function is updated to calcualte new code parallel at the same time
   */
static std::set<LinComArrZ<XYEta>> first_inequalities(const std::vector<std::pair<CodeNumber, XYZ>>& code_nums_angles) {
    // Suryansh Ankur, 2026
    // ScopedTimer timer("first_inequalities");  // profiler disabled for release builds
    auto theta_angles = calculate_angles(code_nums_angles);

    // assign max compuatation thread according to computer performence
    // if large set, small blocksize to allow time for memory swap
    std::size_t n = code_nums_angles.size();

    // Suryansh Ankur, 2026
    // Parallelize over the code numbers/angles with TBB (shared, bounded,
    // work-stealing pool) instead of the old per-call boost::asio::thread_pool
    // that oversubscribed the CPU under the storageExecutor and destabilized the
    // native heap. Each worker thread accumulates into its own pair of sets
    // (enumerable_thread_specific), so peak memory scales with the thread count
    // rather than the chunk count. The results are std::sets, so the order in
    // which threads contribute does not affect them.
    struct PhiSets {
        std::set<LinComArrZ<XYEtaPhi>> positive;
        std::set<LinComArrZ<XYEtaPhi>> negative;
    };
    tbb::enumerable_thread_specific<PhiSets> tls;

    tbb::parallel_for(tbb::blocked_range<std::size_t>(0, n),
        [&](const tbb::blocked_range<std::size_t>& range) {
            PhiSets& local = tls.local();
            for (std::size_t i = range.begin(); i < range.end(); ++i) {
                auto code_number = code_nums_angles.at(i).first;
                auto code_angle = code_nums_angles.at(i).second;
                auto& theta = theta_angles.at(i);

                std::array<LinComArrZ<XYEtaPhi>, 4> equations;
                if (code_number % 2 == 0) {
                    equations = calculate_even_equations(code_number, code_angle, theta);
                } else {
                    equations = calculate_odd_equations(code_number, code_angle, theta);
                }

                for (auto& equation : equations) {
                    auto phi_coeff = equation.coeff<XYEtaPhi::Phi>();
                    if (phi_coeff == 1) {
                        local.positive.insert(equation);
                    } else if (phi_coeff == -1) {
                        local.negative.insert(equation);
                    } else {
                        std::ostringstream err{};
                        err << "phi_coeff " << phi_coeff << " is not 1 or -1";
                        throw std::runtime_error(err.str());
                    }
                }
            }
        });

    std::set<LinComArrZ<XYEtaPhi>> positive_phi, negative_phi;
    for (auto& local : tls) {
        positive_phi.insert(local.positive.begin(), local.positive.end());
        negative_phi.insert(local.negative.begin(), local.negative.end());
    }

    auto no_phi_inequalities = eliminate_phi(positive_phi, negative_phi);

    return no_phi_inequalities;
}

// This doesn't really need to be sorted, but I like determinism.
std::set<LinComArrZ<XYEta>> calculate_bounding_inequalities(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles) {

    // nice little lambda to ensure const-correctness
    auto code_nums_angles = [&]() {
        std::vector<std::pair<CodeNumber, XYZ>> tmp;
        for (auto i : falgo::range(code_numbers.size())) {
            tmp.emplace_back(code_numbers.at(i), code_angles.at(i));
        }

        // Do it again
        if (tmp.size() % 2 != 0) {
            for (auto i : falgo::range(code_numbers.size())) {
                tmp.emplace_back(code_numbers.at(i), code_angles.at(i));
            }
        }

        return tmp;
    }();

    auto first_ineqs = first_inequalities(code_nums_angles);
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
