#include "code_sequence.hpp"

static bool is_repeated(const std::vector<CodeNumber>& numbers, const size_t sub_length) {

    for (size_t i = sub_length; i < numbers.size(); ++i) {
        const auto sub_index = i % sub_length;
        if (numbers.at(i) != numbers.at(sub_index)) {
            return false;
        }
    }

    return true;
}

static XYZ next_angle(const XYZ prev, const XYZ curr, const CodeNumber number) {
    if (number % 2 == 0) {
        return prev;
    } else {
        return other_angle(prev, curr);
    }
}

static bool is_valid(const std::vector<CodeNumber>& numbers, const size_t end) {

    auto prev = XYZ::X; // On the last number
    auto curr = XYZ::Y; // On the first number

    for (size_t i = 0; i < end; ++i) {

        // The angle of the next number depends on the current number
        const auto next = next_angle(prev, curr, numbers.at(i));

        // Update prev and curr for the next loop iteration
        // Be careful about the order you do this
        prev = curr;
        curr = next;
    }

    return prev == XYZ::X && curr == XYZ::Y;
}

// Return the end index of the smallest repeated legal subvector
static size_t smallest_index(const std::vector<CodeNumber>& code_numbers) {

    const auto size = code_numbers.size();

    // Find the shortest repeated valid sublist,
    // where we look at the sublists code_numbers[0:i]
    for (size_t i = 2; i < size; ++i) {
        // if i divides the length of the code code_numbers
        if (size % i == 0) {
            if (is_repeated(code_numbers, i) && is_valid(code_numbers, i)) {
                return i;
            }
        }
    }

    // There are no repeaters, so just return the entire vector
    return size;
}

static void smallest_rotation_reversal(std::vector<CodeNumber>& code_numbers) {

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

static void validate(const std::vector<CodeNumber>& numbers) {

    if (numbers.empty()) {
        throw std::runtime_error("CodeSequence: empty code numbers");
    }

    const auto pos = [](const auto num) { return num > 0; };

    const auto all_pos = std::all_of(std::cbegin(numbers), std::cend(numbers), pos);

    if (!all_pos) {
        throw std::runtime_error("CodeSequence: non-positive numbers");
    }

    const auto valid = is_valid(numbers, numbers.size());

    if (!valid) {
        throw std::runtime_error("CodeSequence: invalid pattern");
    }
}

CodeSequence::CodeSequence(const std::vector<CodeNumber>& numbers) {

    validate(numbers);

    const auto index = smallest_index(numbers);

    for (size_t i = 0; i < index; ++i) {
        code_numbers.push_back(numbers.at(i));
    }

    smallest_rotation_reversal(code_numbers);
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

boost::optional<size_t> CodeSequence::perp_index() const {

    // Odd sequences are never perpendicular
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

            if (is_palindrome(code_numbers, i)) {
                return i;
            }
        }
    }

    // Not a closed sequence, so none
    return boost::none;
}

LinComArrZ<XYEta> CodeSequence::constraint(const InitialAngles& initial_angles) const {

    const auto first = initial_angles.first;
    const auto second = initial_angles.second;

    // All odd sequences are stable, so return zero
    if (is_odd()) {
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

std::vector<XYZ> CodeSequence::angles(const InitialAngles& initial_angles) const {

    // We start off with these two
    std::vector<XYZ> code_angles{initial_angles.first, initial_angles.second};

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

bool CodeSequence::is_stable() const {
    // The angles we choose here are arbitrary
    return constraint(InitialAngles{XYZ::X, XYZ::Y}).is_zero();
}

// The sum and length of the code sequence have the same parity
bool CodeSequence::is_odd() const {
    return code_numbers.size() % 2 != 0;
}

bool CodeSequence::is_perp() const {
    return static_cast<bool>(perp_index());
}

CodeType CodeSequence::type() const {

    const auto stable = is_stable();
    const auto odd = is_odd();
    const auto perp = is_perp();

    if (stable && odd && !perp) {
        return CodeType::OSO;
    } else if (stable && !odd && perp) {
        return CodeType::CS;
    } else if (stable && !odd && !perp) {
        return CodeType::OSNO;
    } else if (!stable && !odd && perp) {
        return CodeType::CNS;
    } else if (!stable && !odd && !perp) {
        return CodeType::ONS;
    } else {
        std::ostringstream err{};
        err << "CodeSequence::type: unable to determine type of " << code_numbers;
        throw std::runtime_error(err.str());
    }
}

CodeNumber CodeSequence::number(const size_t i) const {
    return code_numbers.at(i);
}

// We order code sequences using a length-lex ordering (first compare them by length,
// and then lexicographically).
static int compare(const CodeSequence& lhs, const CodeSequence& rhs) {

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
