#pragma once

#include <utils.hpp>  // pulls in classified_code_sequence.hpp

#include <sstream>

/*
standard() is a port of Java's Utils.standard, and both write into the same cover files and logs, so
its format is pinned here. The expected strings below were produced by running the Java
implementation (harness ~/billiards-port-harnesses/billiards/viewer/StandardParity.java).
*/

BOOST_AUTO_TEST_CASE(test_code_type_padding_aligns_every_type) {
    // Every type name plus its padding must come out the same width, which is what keeps the
    // "(length, sum)" column aligned. CS and OSNO cannot be reached through a short code sequence,
    // so this is the only test that covers those two branches -- and CS is exactly the one the
    // upstream port gets wrong.
    const std::vector<CodeType> types = {
        CodeType::OSO, CodeType::OSNO, CodeType::ONS, CodeType::CS, CodeType::CNS,
    };

    for (const CodeType type : types) {
        std::ostringstream name;
        name << type;
        BOOST_CHECK_EQUAL(name.str().size() + code_type_padding(type).size(), 4u);
    }

    // Spelled out, so a change to the rule has to be deliberate
    BOOST_CHECK_EQUAL(code_type_padding(CodeType::CS), "  ");
    BOOST_CHECK_EQUAL(code_type_padding(CodeType::OSNO), "");
    BOOST_CHECK_EQUAL(code_type_padding(CodeType::OSO), " ");
    BOOST_CHECK_EQUAL(code_type_padding(CodeType::ONS), " ");
    BOOST_CHECK_EQUAL(code_type_padding(CodeType::CNS), " ");
}

BOOST_AUTO_TEST_CASE(test_standard_matches_java_output) {
    // Golden strings taken from the Java implementation
    const ClassifiedCodeSequence cns{CodeSequence{{1, 2, 1, 4}}};
    BOOST_CHECK_EQUAL(cns.codeType, CodeType::CNS);
    BOOST_CHECK_EQUAL(standard(cns, 1), "1  - CNS  (4, 8) 1 2 1 4");

    const ClassifiedCodeSequence oso{CodeSequence{{1, 3, 3}}};
    BOOST_CHECK_EQUAL(oso.codeType, CodeType::OSO);
    BOOST_CHECK_EQUAL(standard(oso, 1), "1  - OSO  (3, 7) 1 3 3");

    const ClassifiedCodeSequence ons{CodeSequence{{2, 3, 2, 1}}};
    BOOST_CHECK_EQUAL(ons.codeType, CodeType::ONS);
    BOOST_CHECK_EQUAL(standard(ons, 1), "1  - ONS  (4, 8) 1 2 3 2");
}

BOOST_AUTO_TEST_CASE(test_standard_count_padding) {
    // A count below ten is padded with a trailing space so the " - TYPE" column lines up
    const ClassifiedCodeSequence code{CodeSequence{{1, 2, 1, 4}}};

    BOOST_CHECK_EQUAL(standard(code, 1), "1  - CNS  (4, 8) 1 2 1 4");
    BOOST_CHECK_EQUAL(standard(code, 9), "9  - CNS  (4, 8) 1 2 1 4");
    BOOST_CHECK_EQUAL(standard(code, 10), "10 - CNS  (4, 8) 1 2 1 4");
    BOOST_CHECK_EQUAL(standard(code, 123), "123 - CNS  (4, 8) 1 2 1 4");
}

BOOST_AUTO_TEST_CASE(test_standard_reports_canonical_form) {
    // The input is rotated into standard form, and standard() must report that, not what was passed
    const ClassifiedCodeSequence code{CodeSequence{{2, 3, 2, 1}}};
    BOOST_CHECK_EQUAL(code.toString(), "1 2 3 2");
    BOOST_CHECK(standard(code, 1).find("1 2 3 2") != std::string::npos);
}
