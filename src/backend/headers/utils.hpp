#pragma once

#include <array>
#include <string>
#include <sstream>
#include <list>
#include <vector>
#include <cmath> 
#include <cstdint>
#include <boost/cstdfloat.hpp>
#include <stdexcept>
#include <atomic>

#include <unordered_map>

#include <boost/optional.hpp>
#include "classified_code_sequence.hpp"
#include "code_sequence.hpp"

inline std::atomic<bool>& cancel_flag() {
    static std::atomic<bool> f{false};
    return f;
}

// Defined once in utils.cpp. This used to be a `static` map in the header, which
// gave every translation unit its own dynamically-initialized copy.
extern std::unordered_map<std::string, CodeType> stringToCodeType;

// std::unordered_set<CodeType> parse_code_type_set(const std::string& input);
std::string to_lower(const std::string& str);

std::vector<CodeType> parse_code_types(const std::string& input,
                                       const std::unordered_map<std::string, CodeType>& lookup) ;

bool is_code_type_in_list(CodeType code, const std::vector<CodeType>& allowed);

boost::optional<ClassifiedCodeSequence> convert(const std::vector<int>& codeList);

// Spaces that bring a CodeType's name up to the width of the widest one, so that the "(length, sum)"
// column lines up from row to row. Exposed for testing: CS and OSNO codes cannot be built from short
// code numbers, so this is the only way to exercise those two branches.
std::string code_type_padding(CodeType type);

/*
Jun 23 2026 Jeff Khuu
Gives a neat string of the code with information about it. Ported from Utils.java.
*/
std::string standard(const ClassifiedCodeSequence& code, int count);

// Takes a const ref: the body copies codeList into newCode before touching anything.
boost::optional<CodeType> getCodeType(const std::vector<int32_t>& codeList);

int32_t modN(int32_t x, int32_t n);