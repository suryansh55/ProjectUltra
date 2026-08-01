#pragma once

#include <array>
#include <map>
#include <ostream>
#include <set>
#include <sstream>
#include <vector>

// Unfortunately, we need this, since it is possible to cast an
// integer to an enum value outside the class
// Switches are ok, if you are returning from each branch. If
// you are not, use an if/else.
template <typename T>
std::string invalid_enum_value(const std::string& enum_name, const T value) {
    std::ostringstream err{};
    err << "unknown " << enum_name << " value " << static_cast<size_t>(value);
    return err.str();
}

// Return -1 if lhs < rhs
// Return 0 if lhs == rhs
// Return 1 if lhs > rhs
template <typename T>
int compare(const T lhs, const T rhs) {
    return (lhs > rhs) - (lhs < rhs);
}

// Intended for used with signed integers
template <typename Int>
Int signum(const Int val) {
    return compare(val, 0);
}

template <typename T, typename S>
std::ostream& operator<<(std::ostream& os, const std::pair<T, S>& p) {
    return os << '(' << p.first << ", " << p.second << ')';
}

// Why does this not already exist?
template <typename Cont>
std::ostream& print_container(std::ostream& os, const Cont& cont) {

    os << '[';

    bool first = true;
    for (const auto& elem : cont) {
        if (!first) {
            os << ", ";
        }

        first = false;

        os << elem;
    }

    os << ']';
    return os;
}

template <typename T, size_t N>
std::ostream& operator<<(std::ostream& os, const std::array<T, N>& arr) {
    return print_container(os, arr);
}

template <typename T>
std::ostream& operator<<(std::ostream& os, const std::vector<T>& vec) {
    return print_container(os, vec);
}

template <typename T>
std::ostream& operator<<(std::ostream& os, const std::set<T>& set) {
    return print_container(os, set);
}

template <typename T, typename S>
std::ostream& operator<<(std::ostream& os, const std::map<T, S>& map) {
    return print_container(os, map);
}

std::vector<std::string> split(const std::string& str, const std::string& delims);

std::string read_file(const std::string& path);
