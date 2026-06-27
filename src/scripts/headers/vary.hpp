#pragma once

#include <set>
#include <string>
#include <boost/optional.hpp>
#include "classified_code_sequence.hpp"

/**
 * Vary3Args is a wrapper around the data required to run Vary3
 */
struct Vary3Args {
    double x;
    double y;
    int32_t minSideSum;
    int32_t maxSideSum;
    int32_t shots;
    bool oso;
    bool cs;
    bool cns;
    bool ons;
    bool osno;
};

/**
 * PrintMode represents the possible print modes for a set of code sequences.
 */
enum PrintMode {
    REGULAR,
    MIDDLE,
    FIRSTMIDLAST
};


boost::optional<PrintMode> strToPrintMode(std::string s);
size_t getNumThreads();
std::string getReqTypesStr(bool oso, bool cs, bool cns, bool ons, bool osno);
void printCodes(std::set<ClassifiedCodeSequence> codeSequences, PrintMode mode);
