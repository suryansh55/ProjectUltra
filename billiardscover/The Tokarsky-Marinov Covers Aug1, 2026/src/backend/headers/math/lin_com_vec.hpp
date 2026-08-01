#pragma once

#include <sstream> // std::ostringstream
#include <vector>  // std::vector

#include "lin_com_map.hpp"

namespace math {

// This class is an immutable version of a LinComMap. Once an equation has been constructed,
// it will often be kept around to do calculations with. Because of the sheer number of equations
// we have to deal with, it is important to use a memory-efficient and cache-friendly representation,
// which is what this class provides.

// To ensure the class invariants are maintained, this can only be constructed by converting
// from a LinComMap.
template <typename T, typename N>
class LinComVec final {
  private:
    //std::vector<std::pair<T, N>> coeffs;

  public:
    std::vector<std::pair<T, N>> coeffs;

    using value_type = typename decltype(coeffs)::value_type;
    using const_iterator = typename decltype(coeffs)::const_iterator;

    explicit LinComVec()
        : coeffs{} {}

    explicit LinComVec(const LinComMap<T, N>& lin_com_map) {

        for (const auto& kv : lin_com_map) {
            coeffs.emplace_back(kv.first, kv.second);
        }
    }

    const_iterator begin() const {
        return std::begin(coeffs);
    }

    const_iterator end() const {
        return std::end(coeffs);
    }

    size_t size() const {
        return coeffs.size();
    }

    bool is_zero() const {
        // The sum of no elements is zero
        return coeffs.empty();
    }

    friend bool operator==(const LinComVec<T, N>& lhs, const LinComVec<T, N>& rhs) {
        return lhs.coeffs == rhs.coeffs;
    }

    friend bool operator<(const LinComVec<T, N>& lhs, const LinComVec<T, N>& rhs) {
        return lhs.coeffs < rhs.coeffs;
    }

    friend std::ostream& operator<<(std::ostream& os, const LinComVec<T, N>& lin_com) {

        if (lin_com.is_zero()) {
            return os << '0';
        }

        // set to false once we add the first coefficient
        bool front = true;

        for (const auto& kv : lin_com) {

            const auto& expr = kv.first;
            const auto& coeff = kv.second;

            // add a positive sign if it is positive and not at the front
            if ((coeff > 0) && !front) {
                os << '+';
            }

            if (coeff == 1) {
                // do nothing
            } else if (coeff == -1) {
                os << '-';
            } else if (coeff != 0) {
                os << coeff;
            } else {
                std::ostringstream err{};
                err << expr << " has zero coefficient in LinComVec";
                throw std::runtime_error(err.str());
            }

            os << expr;
            front = false;
        }

        return os;
    }
};
}
