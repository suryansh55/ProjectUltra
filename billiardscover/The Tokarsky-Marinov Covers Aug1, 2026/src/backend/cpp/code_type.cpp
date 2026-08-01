#include "code_type.hpp"
#include "util.hpp"



std::ostream& operator<<(std::ostream& os, const CodeType code_type) {

    switch (code_type) {
    case CodeType::OSO:
        return os << "OSO";
    case CodeType::CS:
        return os << "CS";
    case CodeType::OSNO:
        return os << "OSNO";
    case CodeType::CNS:
        return os << "CNS";
    case CodeType::ONS:
        return os << "ONS";
    }

    throw std::runtime_error(invalid_enum_value("CodeType", code_type));
}


std::string code_to_string(const CodeType code_type){
    switch (code_type) {
        case CodeType::OSO:
            return "OSO";
        case CodeType::CS:
            return "CS";
        case CodeType::OSNO:
            return "OSNO";
        case CodeType::CNS:
            return "CNS";
        case CodeType::ONS:
            return "ONS";
        }

        throw std::runtime_error(invalid_enum_value("CodeType", code_type));
}

bool is_stable(const CodeType code_type) {

    switch (code_type) {
    case CodeType::OSO:
    case CodeType::CS:
    case CodeType::OSNO:
        return true;
    case CodeType::CNS:
    case CodeType::ONS:
        return false;
    }

    throw std::runtime_error(invalid_enum_value("CodeType", code_type));
}
