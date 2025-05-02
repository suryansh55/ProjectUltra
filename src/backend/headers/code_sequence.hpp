#pragma once

#include "code_type.hpp"
#include "general.hpp"

// A fully canonicalized code sequence. No classification done here
// This is essentially a newtype around a std::vector<CodeNumber>
// We do this because we want the newtype to distinguish
// - this code sequence has been validated and is in standard form
// - to prevent mutability. This class must remain constant to prevent modification
class CodeSequence final {
  private:
    std::vector<CodeNumber> code_numbers;

  public:
    using value_type = typename decltype(code_numbers)::value_type;
    using const_iterator = typename decltype(code_numbers)::const_iterator;

    // Don't call any non-static functions within the constructor,
    // because they might access fields that haven't been initialzed yet.
    explicit CodeSequence(const std::vector<CodeNumber>& code_numbers_);

    const_iterator begin() const;

    const_iterator end() const;

    boost::optional<size_t> closed_index() const;

    LinComArrZ<XYEta> constraint(const XYZ first, const XYZ second) const;

    // The angles you choose for first and second determine which sextant the region ends up in.
    // If a region is symmetrical across a sextant line, say x = y, then that means the equations are
    // unchanged when you swap x <-> y
    // The standard sextant is the one where x <= y <= z.
    std::vector<XYZ> angles(const XYZ first, const XYZ second) const;

    size_t length() const;

    CodeNumber sum() const;

    bool is_odd() const;

    bool is_closed() const;

    bool is_stable() const;

    CodeType type() const;

    CodeNumber number(const size_t i) const;

    // For compatability right now, mostly
    // There are some rules we should observe when it comes to references
    // and safety. We don't want whatever the reference points to to go
    // out of scope or moved around, because then the reference is messed up
    const std::vector<CodeNumber>& numbers() const {
        return code_numbers;
    }
};

// Impose a total order on the code sequences
int compare(const CodeSequence& lhs, const CodeSequence& rhs);

bool operator==(const CodeSequence& lhs, const CodeSequence& rhs);

bool operator<(const CodeSequence& lhs, const CodeSequence& rhs);

std::ostream& operator<<(std::ostream& os, const CodeSequence& code_seq);
