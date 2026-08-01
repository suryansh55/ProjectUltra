#pragma once

#include <boost/optional.hpp>

#include "code_type.hpp"
#include "general.hpp"
#include "initial_angles.hpp"

class CodeSequence final {
  private:
    std::vector<CodeNumber> code_numbers;

  public:
    using value_type = typename decltype(code_numbers)::value_type;
    using const_iterator = typename decltype(code_numbers)::const_iterator;

    explicit CodeSequence(const std::vector<CodeNumber>& numbers);

    const_iterator begin() const;

    const_iterator end() const;

    boost::optional<size_t> perp_index() const;

    LinComArrZ<XYEta> constraint(const InitialAngles& initial_angles) const;

    std::vector<XYZ> angles(const InitialAngles& initial_angles) const;

    size_t length() const;

    CodeNumber sum() const;

    bool is_stable() const;

    bool is_odd() const;

    bool is_perp() const;

    CodeType type() const;

    CodeNumber number(const size_t i) const;

    const std::vector<CodeNumber>& numbers() const {
        return code_numbers;
    }
};

bool operator==(const CodeSequence& lhs, const CodeSequence& rhs);

bool operator<(const CodeSequence& lhs, const CodeSequence& rhs);

std::ostream& operator<<(std::ostream& os, const CodeSequence& code_seq);
