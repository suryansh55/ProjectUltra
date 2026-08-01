#pragma once

#include <trig_identities.hpp>

BOOST_AUTO_TEST_CASE(test_simplify_sin_xypi) {

    const std::vector<std::pair<std::string, std::pair<Coeff32, std::string>>> in_out = {
        // no transformations
        {"x", {1, "x"}},
        // even-odd identities
        {"-x", {-1, "x"}},
        {"-x+y", {-1, "x-y"}},
        // periodicity identitios
        {"x+pi", {-1, "x"}},
        {"x+4pi", {1, "x"}},
        // all together
        {"-8x+3y+5pi", {1, "8x-3y"}},
        {"-4x-2y+2pi", {-1, "4x+2y"}},
    };

    for (const auto& p : in_out) {

        const auto in_expr = parse_lin_com_arr_xypi(p.first);
        const auto simplified_expr_pair = simplify_sin_xypi(in_expr);

        const auto out_expr = parse_lin_com_arr_xy(p.second.second);
        const auto expected_out = std::make_pair(p.second.first, Sin<LinComArrZ<XY>>{out_expr});

        BOOST_TEST(simplified_expr_pair == expected_out);
    }
}

BOOST_AUTO_TEST_CASE(test_simplify_cos_xypi) {

    const std::vector<std::pair<std::string, std::pair<Coeff32, std::string>>> in_out = {
        // no transformxtions
        {"x", {1, "x"}},
        // even-odd identities
        {"-x", {1, "x"}},
        {"-x+y", {1, "x-y"}},
        // periodicity identitios
        {"x+pi", {-1, "x"}},
        {"x+4pi", {1, "x"}},
        // all together
        {"-8x+3y+5pi", {-1, "8x-3y"}},
        {"-4x-2y+2pi", {1, "4x+2y"}},
    };

    for (const auto& p : in_out) {

        const auto in_expr = parse_lin_com_arr_xypi(p.first);
        const auto simplified_expr_pair = simplify_cos_xypi(in_expr);

        const auto out_expr = parse_lin_com_arr_xy(p.second.second);
        const auto expected_out = std::make_pair(p.second.first, Cos<LinComArrZ<XY>>{out_expr});

        BOOST_TEST(simplified_expr_pair == expected_out);
    }
}

BOOST_AUTO_TEST_CASE(test_simplify_sin_xyeta) {

    const std::vector<std::pair<std::string, std::pair<Coeff32, std::string>>> in_out = {
        {"x+eta", {1, "x"}},
        {"x-eta", {-1, "x"}},
        {"x+15eta", {-1, "x"}},
        {"x-15eta", {1, "x"}},
        {"-4x-2y+3eta", {-1, "4x+2y"}},
    };

    for (const auto& p : in_out) {

        const auto in_expr = parse_lin_com_arr_xyeta(p.first);
        const auto simplified_expr_pair = simplify_sin_xyeta(in_expr);

        const auto out_expr = parse_lin_com_arr_xy(p.second.second);
        const auto expected_out = std::make_pair(p.second.first, Cos<LinComArrZ<XY>>{out_expr});

        BOOST_TEST(simplified_expr_pair == expected_out);
    }
}

BOOST_AUTO_TEST_CASE(test_simplify_cos_xyeta) {

    const std::vector<std::pair<std::string, std::pair<Coeff32, std::string>>> in_out = {
        {"x+eta", {-1, "x"}},
        {"x-eta", {1, "x"}},
        {"x+15eta", {1, "x"}},
        {"x-15eta", {-1, "x"}},
        {"-4x-2y+3eta", {-1, "4x+2y"}},
    };

    for (const auto& p : in_out) {

        const auto in_expr = parse_lin_com_arr_xyeta(p.first);
        const auto simplified_expr_pair = simplify_cos_xyeta(in_expr);

        const auto out_expr = parse_lin_com_arr_xy(p.second.second);
        const auto expected_out = std::make_pair(p.second.first, Sin<LinComArrZ<XY>>{out_expr});

        BOOST_TEST(simplified_expr_pair == expected_out);
    }
}
