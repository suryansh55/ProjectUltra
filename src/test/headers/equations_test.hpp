#pragma once

#include <boost/optional/optional_io.hpp>

#include <equations.hpp>
#include <parse.hpp>
#include <utils.hpp>

#include <cstdlib>
#include <cstdint>
#include <iomanip>
#include <sstream>

BOOST_AUTO_TEST_CASE(test_calculate_empty) {

    std::vector<CodeSequence> code_seqs{
        CodeSequence{std::vector<CodeNumber>{1, 2, 1, 4, 1, 2, 1, 4, 1, 2, 1, 6}},
    };
    BOOST_REQUIRE(!code_seqs.empty());

    for (const auto& code_seq : code_seqs) {
        const auto code_type = code_seq.type();
        if (is_stable(code_type)) {
            BOOST_TEST(!calculate_stable(code_seq, code_type));
        } else {
            BOOST_TEST(!calculate_unstable(code_seq, code_type));
        }
    }
}

BOOST_AUTO_TEST_CASE(test_calculate_nonempty) {

    std::vector<CodeSequence> code_seqs{
        CodeSequence{std::vector<CodeNumber>{1, 3, 3}},
        CodeSequence{std::vector<CodeNumber>{1, 2, 1, 4}}
    };
    BOOST_REQUIRE(!code_seqs.empty());


    for (const auto& code_seq : code_seqs) {
        const auto code_type = code_seq.type();
        if (is_stable(code_type)) {
            BOOST_TEST(calculate_stable(code_seq, code_type));
        } else {
            BOOST_TEST(calculate_unstable(code_seq, code_type));
        }
    }
}
