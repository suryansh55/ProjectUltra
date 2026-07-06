#pragma once

#include <set>
#include <string>
#include <boost/optional.hpp>
#include "general.hpp"
#include "classified_code_sequence.hpp"

/**
 * CodeTypeCollection is a template struct for grouping data by code types.
 */
template<typename T> 
struct CodeTypeCollection {
    T oso;
    T cs;
    T cns;
    T ons;
    T osno;
};

/**
 * CodeTypeSet is a type alias for a CodeTypeCollection of bools, representing the presence or absence of each code type.
 */
using CodeTypeSet = CodeTypeCollection<bool>;
using Vec2 = std::array<float64_t, 2>;

/**
 * PrintMode represents the possible print modes for a set of code sequences.
 */
enum PrintMode {
    REGULAR,
    MIDDLE,
    FIRSTMIDLAST
};

/**
 * Vary3Args is a wrapper around the data required to run Vary3
 */
struct Vary3Args {
    double x;
    double y;
    int32_t minSideSum;
    int32_t maxSideSum;
    int32_t shots;
    CodeTypeSet searchFor;
};


/**
 * VaryAutoPolyArgs is a wrapper around the data required to run VaryAutoPoly
 */
struct VaryAutoPolyArgs {
    std::vector<std::vector<Vec2>> holes;
    int32_t minSideSum;
    int32_t maxSideSum;
    int32_t shots;
    CodeTypeSet searchFor;
    int32_t maxOSOCodeLength;
    int32_t maxCSCodeLength;
    int32_t maxOSNOCodeLength;
    PrintMode mode;
    boost::optional<int32_t> maxOSOSideSum;
    boost::optional<int32_t> maxCSSideSum;
    boost::optional<int32_t> maxOSNOSideSum;
};

boost::optional<PrintMode> strToPrintMode(std::string s);
size_t getNumThreads();
std::string getReqTypesStr(bool oso, bool cs, bool cns, bool ons, bool osno);
void printCodes(std::set<ClassifiedCodeSequence> codeSequences, PrintMode mode);
std::set<ClassifiedCodeSequence> findCodesVary3(const Vary3Args& args);
std::vector<std::set<ClassifiedCodeSequence>> findCodesPolyVary(const VaryAutoPolyArgs& args);

