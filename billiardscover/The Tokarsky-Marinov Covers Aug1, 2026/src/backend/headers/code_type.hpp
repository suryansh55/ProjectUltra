#pragma once

#include <ostream>

enum class CodeType {
    OSO, // stable odd nonperp
    CS, // stable even perp
    OSNO, // stable even nonperp
    CNS, // unstable even perp
    ONS, // unstable even nonperp
};

std::ostream& operator<<(std::ostream& os, const CodeType code_type);

bool is_stable(const CodeType code_type);

std::string code_to_string(const CodeType code_type);
