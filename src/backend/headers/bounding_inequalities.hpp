#pragma once

#include "code_sequence.hpp"
#include "general.hpp"

std::set<LinComArrZ<XYEta>> calculate_bounding_inequalities(const std::vector<CodeNumber>& code_numbers, const std::vector<XYZ>& code_angles);
