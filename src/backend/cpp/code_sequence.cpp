#include "code_sequence.hpp"
#include "conversion.hpp"

static bool is_repeated(const std::vector<CodeNumber>& code_numbers, const size_t sub_length) {

    for (size_t i = sub_length; i < code_numbers.size(); ++i) {
        const auto sub_index = i % sub_length;
        if (code_numbers.at(i) != code_numbers.at(sub_index)) {
            return false;
        }
    }

    return true;
}

static XYZ next_angle(const XYZ prev, const XYZ curr, const CodeNumber number) {

    if (number % 2 == 0) {
        // even number
        return prev;
    } else {
        // odd number
        return other_angle(prev, curr);
    }
}

static bool is_legal(const std::vector<CodeNumber>& code_numbers, const size_t end) {

    // A code sequence is legal iff the code angles wrap around correctly at the beginning
    // and end.

    // Strictly speaking, code sequences of length one are allowed if they
    // consist of a single even number (odd ones are illegal).
    // However, all these sequences are trivially empty, so there is no point
    // in considering them.

    // We will have to update these as we go along
    auto prev = XYZ::X; // On the last number
    auto curr = XYZ::Y; // On the first number

    for (size_t i = 0; i < end; ++i) {

        const auto num = code_numbers.at(i);

        // The angle of the next number depends on the current number
        const auto next = next_angle(prev, curr, num);

        // Update prev and curr for the next loop iteration
        // Be very careful about the order you do this
        prev = curr;
        curr = next;
    }

    return prev == XYZ::X && curr == XYZ::Y;
}

// code_numbers must already be valid
// Return the end index of the smallest repeated legal subvector
static size_t smallest_index(const std::vector<CodeNumber>& code_numbers) {

    const auto size = code_numbers.size();

    // Find the shortest repeated legal sublist,
    // where we look at the sublists code_numbers[0:i]
    for (size_t i = 2; i < size; ++i) {
        // if i divides the length of the code numbers
        if (size % i == 0) {
            if (is_repeated(code_numbers, i) && is_legal(code_numbers, i)) {
                return i;
            }
        }
    }

    // There are no repeaters, so just return the entire vector
    return size;
}

// If we're going to mutate a parameter, it's better to just
// do so in a meaningful way instead of returning the actual result
// WARNING: this method involves quite a bit of mutability. Be careful.
// It turns out this is a well known problem in computer science:
// https://en.wikipedia.org/wiki/Lexicographically_minimal_string_rotation
// If this ever becomes a performance bottleneck (say if we want to generate
// lists of all valid code sequences), then this might be useful
// We could also just concatenate the vector with itself
// See also Lyndon words
static void minimal_rotation(std::vector<CodeNumber>& code_numbers) {

    // This must be a copy, not a reference, since we mutate code_numbers
    auto min = code_numbers;

    const auto size = code_numbers.size();

    for (size_t i = 0; i < size; ++i) {
        // rotate left
        std::rotate(std::begin(code_numbers), std::next(std::begin(code_numbers)), std::end(code_numbers));

        if (code_numbers < min) {
            min = code_numbers;
        }
    }

    // After size rotations, the vector is now back to where it was before.
    // Now we reverse it, and do it again

    std::reverse(std::begin(code_numbers), std::end(code_numbers));

    for (size_t i = 0; i < size; ++i) {
        // rotate left
        std::rotate(std::begin(code_numbers), std::next(std::begin(code_numbers)), std::end(code_numbers));

        if (code_numbers < min) {
            min = code_numbers;
        }
    }

    code_numbers = min;
}

static void validate(const std::vector<CodeNumber>& code_numbers) {

    if (code_numbers.empty()) {
        throw std::runtime_error("CodeSequence: empty code numbers");
    }

    const auto pos = [](const auto num) { return num > 0; };

    const auto all_pos = std::all_of(std::cbegin(code_numbers), std::cend(code_numbers), pos);

    if (!all_pos) {
        throw std::runtime_error("CodeSequence: non-positive numbers");
    }

    const auto legal = is_legal(code_numbers, code_numbers.size());

    if (!legal) {
        throw std::runtime_error("CodeSequence: illegal pattern");
    }
}

CodeSequence::CodeSequence(const std::vector<CodeNumber>& code_numbers_) {

    validate(code_numbers_);

    const auto index = smallest_index(code_numbers_);

    for (size_t i = 0; i < index; ++i) {
        code_numbers.push_back(code_numbers_.at(i));
    }

    minimal_rotation(code_numbers);
}

CodeSequence::const_iterator CodeSequence::begin() const {
    return std::begin(code_numbers);
}

CodeSequence::const_iterator CodeSequence::end() const {
    return std::end(code_numbers);
}

static bool is_palindrome(const std::vector<CodeNumber>& code_numbers, const size_t i) {

    const size_t length = code_numbers.size();
    const size_t half_length = length / 2;

    // i is the index of E1
    // we start at E2, because it wraps around correctly when using %
    // j starts at E2 - 1, and goes <- through l1
    // k starts at E2 + 1, and goes -> and wraps around through l2
    size_t j = i + half_length - 1;
    size_t k = (i + half_length + 1) % length;

    // j and k meet at E1
    while (j != k) {
        if (code_numbers.at(j) != code_numbers.at(k)) {
            return false;
        }

        j -= 1;
        k = (k + 1) % length;
    }

    return true;
}

boost::optional<size_t> CodeSequence::closed_index() const {

    // The sum of an odd sequence is odd, and then doubling it makes it even
    // (with a single factor of 2). Perpendicular sequences always have a sum
    // that is a multiple of 4, so odd sequences are never perpendicular
    // (but what if you double it again?)
    if (is_odd()) {
        return boost::none;
    }

    const auto length = code_numbers.size();
    const auto half_length = length / 2;

    // return the index of E1
    for (size_t i = 0; i < half_length; ++i) {
        const auto first = code_numbers.at(i);
        const auto second = code_numbers.at(i + half_length);

        const auto first_even = (first % 2 == 0);
        const auto second_even = (second % 2 == 0);

        if (first_even && second_even) {
            // Now check if the two lists between the first and second integers are
            // reverses of each other
            // so we could have __ E1 ~~~~~ E2 ---, for example, and then
            // l1 = ~~~~~
            // l2 = ---__
            // need l1 == l2.reverse()

            // check if l1 == l2.reverse()
            if (is_palindrome(code_numbers, i)) {
                return i;
            }
        }
    }

    // Not a closed sequence, so none
    return boost::none;
}

LinComArrZ<XYEta> CodeSequence::constraint(const XYZ first, const XYZ second) const {

    // all odd sequences are stable
    if (is_odd()) {
        // Return zero
        return LinComArrZ<XYEta>{};
    }

    // even means add
    // odd means sub
    LinComArrZ<XYEta> constraint{};
    constraint.add(code_numbers.at(0), xyz_to_xyeta(first));
    constraint.sub(code_numbers.at(1), xyz_to_xyeta(second));

    // We will have to update these as we go along
    auto prev_prev = first;
    auto prev = second;

    for (size_t i = 2; i < code_numbers.size(); ++i) {

        // Which angle this is depends on the previous number
        const auto prev_number = code_numbers.at(i - 1);

        const auto current = next_angle(prev_prev, prev, prev_number);

        const auto current_number = code_numbers.at(i);

        if (i % 2 == 0) {
            // i is even, so add
            constraint.add(current_number, xyz_to_xyeta(current));
        } else {
            // i is odd, so subtract
            constraint.sub(current_number, xyz_to_xyeta(current));
        }

        // Update prev_prev and prev for the next loop iteration
        // Be very careful about the order you do this
        prev_prev = prev;
        prev = current;
    }

    constraint.divide_content();
    constraint.divide_unit();

    return constraint;
}

std::vector<XYZ> CodeSequence::angles(const XYZ first, const XYZ second) const {

    // We start off with these two
    std::vector<XYZ> code_angles{first, second};

    for (size_t i = 2; i < code_numbers.size(); ++i) {

        const auto prev_number = code_numbers.at(i - 1);

        if (prev_number % 2 == 0) {
            // even number
            // second last one
            const auto current = code_angles.at(i - 2);
            code_angles.push_back(current);
        } else {
            // odd number
            const auto prev_prev = code_angles.at(i - 2);
            const auto prev = code_angles.at(i - 1);
            const auto current = other_angle(prev_prev, prev);
            code_angles.push_back(current);
        }
    }

    return code_angles;
}

size_t CodeSequence::length() const {
    return code_numbers.size();
}

CodeNumber CodeSequence::sum() const {

    CodeNumber s = 0;

    for (const auto num : code_numbers) {
        s += num;
    }

    return s;
}

// The sum and length of the code sequence have the same parity
bool CodeSequence::is_odd() const {
    return code_numbers.size() % 2 != 0;
}

bool CodeSequence::is_closed() const {
    return static_cast<bool>(closed_index());
}

bool CodeSequence::is_stable() const {
    // The angles we choose here are arbitrary
    return constraint(XYZ::X, XYZ::Y).is_zero();
}

CodeType CodeSequence::type() const {

    const auto odd = is_odd();
    const auto closed = is_closed();
    const auto stable = is_stable();

    if (!closed && stable && odd) {
        return CodeType::OSO;
    } else if (!closed && stable && !odd) {
        return CodeType::OSNO;
    } else if (!closed && !stable) {
        return CodeType::ONS;
    } else if (closed && stable) {
        return CodeType::CS;
    } else if (closed && !stable) {
        return CodeType::CNS;
    } else {
        std::ostringstream err{};
        err << code_numbers << " cannot be given a code type";
        throw std::runtime_error(err.str());
    }
}

CodeNumber CodeSequence::number(const size_t i) const {
    return code_numbers.at(i);
}

int compare(const CodeSequence& lhs, const CodeSequence& rhs) {

    const auto lhs_size = lhs.length();
    const auto rhs_size = rhs.length();

    const auto size_compare = compare(lhs_size, rhs_size);

    if (size_compare != 0) {
        return size_compare;
    } else {
        // Same size, so compare elements one by one
        for (size_t i = 0; i < lhs_size; ++i) {
            const auto lhs_elem = lhs.number(i);
            const auto rhs_elem = rhs.number(i);

            const auto elem_compare = compare(lhs_elem, rhs_elem);

            if (elem_compare != 0) {
                return elem_compare;
            }

            // Else they are equal, so continue to the next element
        }

        // All elements are identical, so they are equal
        return 0;
    }
}

bool operator==(const CodeSequence& lhs, const CodeSequence& rhs) {
    return compare(lhs, rhs) == 0;
}

bool operator<(const CodeSequence& lhs, const CodeSequence& rhs) {
    return compare(lhs, rhs) < 0;
}

std::ostream& operator<<(std::ostream& os, const CodeSequence& code_seq) {

    bool first = true;

    for (const auto num : code_seq) {

        if (!first) {
            os << ' ';
        }

        os << num;

        first = false;
    }

    return os;
}
