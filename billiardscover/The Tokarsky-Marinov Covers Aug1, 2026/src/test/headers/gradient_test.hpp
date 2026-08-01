#pragma once

#include <boost/algorithm/string.hpp>

#include <parse.hpp>

// Always do the simplest test case first, and then move up from there
BOOST_AUTO_TEST_CASE(test_gradient_bound) {

    const std::vector<std::pair<std::string, Coeff64>> input = {
        {"trig(0)", 0},
        {"trig(x)", 1},
        {"trig(y)", 1},
        {"-2trig(2x)", 4},
        {"2trig(3x-5y)-8trig(5x+6y)", 104},
    };

    for (const auto& p : input) {

        const auto string_sin = boost::replace_all_copy(p.first, "trig", "sin");
        const auto expr_sin = parse_lin_com_map_sin_xy(string_sin);

        const auto sin_bound = gradient_bounds(expr_sin);

        BOOST_TEST(sin_bound.first + sin_bound.second == p.second);

        const auto string_cos = boost::replace_all_copy(p.first, "trig", "cos");
        const auto expr_cos = parse_lin_com_map_cos_xy(string_cos);

        const auto cos_bound = gradient_bounds(expr_cos);

        BOOST_TEST(cos_bound.first + cos_bound.second == p.second);
    }
}
