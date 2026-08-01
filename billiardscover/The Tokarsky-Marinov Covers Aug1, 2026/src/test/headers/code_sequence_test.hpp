#pragma once

#include <code_sequence.hpp>

BOOST_AUTO_TEST_CASE(test_empty_code_sequence) {
    BOOST_CHECK_THROW(CodeSequence{{}}, std::runtime_error);
}

BOOST_AUTO_TEST_CASE(test_negative_code_numbers) {
    const std::vector<std::vector<CodeNumber>> invalid = {
        {0},
        {-1},
        {0, 1},
        {1, 2, 3, 0},
        {-1, -2, -3, 4},
    };

    for (const auto& code_numbers : invalid) {
        BOOST_CHECK_THROW(CodeSequence{code_numbers}, std::runtime_error);
    }
}

BOOST_AUTO_TEST_CASE(test_illegal_code_sequences) {

    const std::vector<std::vector<CodeNumber>> illegal = {
        {1}, // 1
        {2}, // 0

        {3, 5}, // 11
        {1, 2}, // 10
        {4, 7}, // 01

        {1, 3, 8},    // 110
        {15, 4, 7},   // 101
        {32, 17, 81}, // 011
        {3, 12, 18},  // 100
        {8, 21, 78},  // 010
        {38, 52, 25}, // 001
        {2, 4, 8},    // 000

        {15, 37, 55, 21}, // 1111
        {15, 37, 55, 20}, // 1110
        {15, 37, 54, 21}, // 1101
        {15, 38, 55, 21}, // 1011
        {16, 37, 55, 21}, // 0111
    };

    for (const auto& code_numbers : illegal) {
        BOOST_CHECK_THROW(CodeSequence{code_numbers}, std::runtime_error);
    }
}

BOOST_AUTO_TEST_CASE(test_repeaters) {

    // TODO add more cases
    const std::vector<std::pair<std::vector<CodeNumber>, std::vector<CodeNumber>>> repeaters = {
        {{1, 1, 1, 1, 1, 1}, {1, 1, 1}},
        {{1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1}},
        {{1, 1, 4, 1, 1, 4}, {1, 1, 4, 1, 1, 4}},
    };

    for (const auto& p : repeaters) {
        const auto code_seq1 = CodeSequence{p.first};
        const auto code_seq2 = CodeSequence{p.second};

        BOOST_TEST(code_seq1 == code_seq2);
    }
}

BOOST_AUTO_TEST_CASE(test_order) {
    const std::vector<std::pair<std::vector<CodeNumber>, std::vector<CodeNumber>>> codes = {

        {{1, 1, 3}, {1, 1, 3}},
        {{3, 1, 1}, {1, 1, 3}},
        {{1, 3, 1}, {1, 1, 3}},
        {{2, 4}, {2, 4}},
        {{4, 2}, {2, 4}},
    };

    for (const auto& p : codes) {
        const auto code_seq1 = CodeSequence{p.first};
        const auto code_seq2 = CodeSequence{p.second};

        BOOST_TEST(code_seq1 == code_seq2);
    }
}

BOOST_AUTO_TEST_CASE(test_type) {
    const std::vector<std::pair<std::vector<CodeNumber>, CodeType>> types = {
        {{1, 1, 1}, CodeType::OSO},
        {{2, 2}, CodeType::CNS},
        {{1, 1, 2, 1, 3, 2}, CodeType::ONS},
        {{1, 1, 1, 1, 2, 1, 1, 1, 1, 2}, CodeType::CS},
        {{1, 1, 2, 2, 1, 1, 3, 3}, CodeType::OSNO}};

    for (const auto& p : types) {
        const auto code_seq = CodeSequence{p.first};
        const auto code_type = code_seq.type();

        BOOST_TEST(code_type == p.second);
    }
}

BOOST_AUTO_TEST_CASE(test_angles) {

    const std::vector<std::pair<std::vector<CodeNumber>, std::vector<XYZ>>> input = {
        // OSO
        {{1, 1, 1}, {XYZ::X, XYZ::Y, XYZ::Z}},
        // CNS
        {{2, 2}, {XYZ::X, XYZ::Y}},
        // OSO
        {{1, 1, 3}, {XYZ::X, XYZ::Y, XYZ::Z}},
        // CNS
        {{1, 2, 1, 2}, {XYZ::X, XYZ::Y, XYZ::X, XYZ::Z}},
        // CNS
        {{1, 1, 2, 1, 1, 2}, {XYZ::X, XYZ::Y, XYZ::Z, XYZ::Y, XYZ::X, XYZ::Z}},
        // ONS
        {{1, 1, 2, 1, 3, 2}, {XYZ::X, XYZ::Y, XYZ::Z, XYZ::Y, XYZ::X, XYZ::Z}},
        // CS
        {{1, 1, 1, 1, 2, 1, 1, 1, 1, 2}, {XYZ::X, XYZ::Y, XYZ::Z, XYZ::X, XYZ::Y, XYZ::X, XYZ::Z, XYZ::Y, XYZ::X, XYZ::Z}},
        // OSNO
        {{1, 1, 2, 2, 1, 1, 3, 3}, {XYZ::X, XYZ::Y, XYZ::Z, XYZ::Y, XYZ::Z, XYZ::X, XYZ::Y, XYZ::Z}},
    };

    for (const auto& p : input) {

        const auto code_sequence = CodeSequence{p.first};
        const auto code_angles = code_sequence.angles(InitialAngles{XYZ::X, XYZ::Y});

        BOOST_TEST(code_angles == p.second);
    }
}

BOOST_AUTO_TEST_CASE(test_constraint) {

    const std::vector<std::pair<std::vector<CodeNumber>, std::string>> input = {
        {{1, 1, 1}, "0"},
        {{2, 2}, "x-y"},
        {{1, 1, 3}, "0"},
        {{1, 2, 1, 2}, "x-eta"},
        {{1, 1, 2, 1, 1, 2}, "x-y"},
        {{1, 1, 2, 1, 3, 2}, "2x-y"},
        {{1, 1, 1, 1, 2, 1, 1, 1, 1, 2}, "0"},
        {{1, 1, 2, 2, 1, 1, 3, 3}, "0"},
    };

    for (const auto& p : input) {

        const auto code_sequence = CodeSequence{p.first};

        const auto constraint = code_sequence.constraint(InitialAngles{XYZ::X, XYZ::Y});

        const auto actual_constraint = parse_lin_com_arr_xyeta(p.second);

        BOOST_TEST(constraint == actual_constraint);
    }
}
