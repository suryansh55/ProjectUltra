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

extern std::unordered_map<std::string, CodeType> stringToCodeType;

// std::unordered_set<CodeType> parse_code_type_set(const std::string& input);
std::string to_lower(const std::string& str);

std::vector<CodeType> parse_code_types(const std::string& input,
                                       const std::unordered_map<std::string, CodeType>& lookup) ;

bool is_code_type_in_list(CodeType code, const std::vector<CodeType>& allowed);

boost::optional<ClassifiedCodeSequence> convert(const std::vector<int>& codeList);

std::string standard(ClassifiedCodeSequence code, const int count);

boost::optional<CodeType> getCodeType(const std::vector<int32_t>& codeList);

int32_t modN(int32_t x, int32_t n);