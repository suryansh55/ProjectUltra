#pragma once

#include "code_sequence.hpp"
#include "general.hpp"

struct CodePair final {
    CodeSequence sequence;
    InitialAngles angles;

    explicit CodePair(CodeSequence code_sequence_, InitialAngles initial_angles_)
        : sequence{std::move(code_sequence_)}, angles{std::move(initial_angles_)} {}

    friend std::ostream& operator<<(std::ostream& os, const CodePair& code_pair) {
        return os << code_pair.sequence << ", " << code_pair.angles;
    }

    friend bool operator==(const CodePair& lhs, const CodePair& rhs) {
        return std::tie(lhs.sequence, lhs.angles) == std::tie(rhs.sequence, rhs.angles);
    }

    friend bool operator<(const CodePair& lhs, const CodePair& rhs) {
        return std::tie(lhs.sequence, lhs.angles) < std::tie(rhs.sequence, rhs.angles);
    }
};

struct TriplePair final {
    CodePair stable_neg;
    CodePair unstable;
    CodePair stable_pos;

    explicit TriplePair(CodePair stable_neg_, CodePair unstable_, CodePair stable_pos_)
        : stable_neg{std::move(stable_neg_)},
          unstable{std::move(unstable_)},
          stable_pos{std::move(stable_pos_)} {}

    friend std::ostream& operator<<(std::ostream& os, const TriplePair& triple_pair) {
        return os << triple_pair.stable_neg << "; "
                  << triple_pair.unstable << "; "
                  << triple_pair.stable_pos;
    }

    friend bool operator==(const TriplePair& lhs, const TriplePair& rhs) {
        return std::tie(lhs.stable_neg, lhs.unstable, lhs.stable_pos) == std::tie(rhs.stable_neg, rhs.unstable, rhs.stable_pos);
    }

    friend bool operator<(const TriplePair& lhs, const TriplePair& rhs) {
        return std::tie(lhs.stable_neg, lhs.unstable, lhs.stable_pos) < std::tie(rhs.stable_neg, rhs.unstable, rhs.stable_pos);
    }
};

template <typename T>
class SharedPtr final {

  private:
    std::shared_ptr<T> ptr;

  public:
    template <typename... Args>
    explicit SharedPtr(Args&&... args) {
        ptr = std::make_shared<T>(std::forward<Args>(args)...);
    }

    T& get() {
        return *ptr;
    }

    const T& get() const {
        return *ptr;
    }

    friend bool operator==(const SharedPtr<T>& lhs, const SharedPtr<T>& rhs) {
        return lhs.get() == rhs.get();
    }

    friend bool operator<(const SharedPtr<T>& lhs, const SharedPtr<T>& rhs) {
        return lhs.get() < rhs.get();
    }

    friend std::ostream& operator<<(std::ostream& os, const SharedPtr<T>& p) {
        return os << p.get();
    }
};

template <typename T>
class Quarters final {

  private:
    std::array<T, 4> arr;

  public:
    using value_type = typename decltype(arr)::value_type;
    using iterator = typename decltype(arr)::iterator;
    using const_iterator = typename decltype(arr)::const_iterator;

    explicit Quarters(T ul, T ur, T ll, T lr)
        : arr{{std::move(ul), std::move(ur), std::move(ll), std::move(lr)}} {}

    iterator begin() {
        return std::begin(arr);
    }

    iterator end() {
        return std::end(arr);
    }

    const_iterator begin() const {
        return std::begin(arr);
    }

    const_iterator end() const {
        return std::end(arr);
    }

    template <size_t index>
    T& get() {
        return std::get<index>(arr);
    }

    template <size_t index>
    const T& get() const {
        return std::get<index>(arr);
    }

    T& at(const size_t i) {
        return arr.at(i);
    }

    const T& at(const size_t i) const {
        return arr.at(i);
    }
};

namespace cover {

struct Empty final {};

struct Single final {
    size_t index;

    explicit Single(const size_t index_)
        : index{index_} {}
};

struct Triple final {
    size_t index;

    explicit Triple(const size_t index_)
        : index{index_} {}
};

// We need to have a forward declaration here, since this is the recursive case
struct Divide;

using Cover = boost::variant<Empty, Single, Triple, Divide>;

struct Divide final {
    // TODO once we have support for std::variant, we can likely change this to a unique_ptr
    SharedPtr<Quarters<Cover>> quarters;

    explicit Divide(Cover cover0, Cover cover1, Cover cover2, Cover cover3)
        : quarters{std::move(cover0), std::move(cover1), std::move(cover2), std::move(cover3)} {}
};
}

ClosedRectangleQ load_square(const std::string& dir);

OpenConvexPolygonQ load_polygon(const std::string& dir);

std::vector<CodePair> load_singles(const std::string& dir);

std::vector<TriplePair> load_triples(const std::string& dir);

cover::Cover load_cover(const std::string& dir);

uint32_t load_digits(const std::string& dir);
