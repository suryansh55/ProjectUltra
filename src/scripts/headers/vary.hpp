#pragma once

#include <set>
#include <string>
#include "classified_code_sequence.hpp"

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

size_t getNumThreads();
std::string getReqTypesStr(bool oso, bool cs, bool cns, bool ons, bool osno);
