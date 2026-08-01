#pragma once

#include "code_sequence.hpp"
#include "general.hpp"

std::pair<EqMap<Cos>, EqMap<Sin>> shooting_vector_odd(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYPi>>& code_angles);

std::pair<EqMap<Sin>, EqMap<Cos>> shooting_vector_perp(const CodeSequence& code_sequence, const std::vector<LinComArrZ<XYEta>>& code_angles);
