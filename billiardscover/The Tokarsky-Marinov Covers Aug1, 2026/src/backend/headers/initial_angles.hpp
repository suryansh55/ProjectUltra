#pragma once

#include <ostream>
#include <tuple>

#include "math/symbols.hpp"

struct InitialAngles final {

    XYZ first;
    XYZ second;

    explicit InitialAngles(const XYZ first_, const XYZ second_)
        : first{first_}, second{second_} {}

    friend std::ostream& operator<<(std::ostream& os, const InitialAngles& initial_angles) {
        return os << initial_angles.first << initial_angles.second;
    }

    friend bool operator==(const InitialAngles& lhs, const InitialAngles& rhs) {
        return std::tie(lhs.first, lhs.second) == std::tie(rhs.first, rhs.second);
    }

    friend bool operator<(const InitialAngles& lhs, const InitialAngles& rhs) {
        return std::tie(lhs.first, lhs.second) < std::tie(rhs.first, rhs.second);
    }
};
