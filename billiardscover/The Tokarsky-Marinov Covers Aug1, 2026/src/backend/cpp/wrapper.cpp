#include <iostream>

#include "check.hpp"
#include "equations.hpp"
#include "parse.hpp"
#include "wrapper.hpp"
#include "code_type.hpp"

static std::string serialize(const std::vector<PointQ>& points) {

    std::ostringstream oss{};

    bool first = true;
    for (const auto& point : points) {

        if (!first) {
            oss << '\n';
        }

        oss << point.x << ' ' << point.y;

        first = false;
    }

    return oss.str();
}

template <template <typename> class Trig>
void serialize(std::ostringstream& oss, const LinComMapZ<Trig<LinComArrZ<XY>>>& equation) {

    bool first = true;
    for (const auto& kv : equation) {
        // order is trig_coeff, x_coeff, y_coeff
        const auto trig_coeff = kv.second;
        const auto x_coeff = kv.first.arg.coeff(XY::X);
        const auto y_coeff = kv.first.arg.coeff(XY::Y);

        if (!first) {
            oss << ' ';
        }

        oss << trig_coeff << ' ' << x_coeff << ' ' << y_coeff;

        first = false;
    }
}

template <template <typename> class Trig>
std::string serialize(const std::set<LinComMapZ<Trig<LinComArrZ<XY>>>>& eqs) {

    std::ostringstream oss{};

    bool first = true;
    for (const auto& eq : eqs) {

        if (!first) {
            oss << '\n';
        }

        serialize(oss, eq);

        first = false;
    }

    return oss.str();
}

static char* to_cstr(const std::string& str) {

    // + 1 for the nul character
    char* const c_str = new char[str.size() + 1];
    strcpy(c_str, str.c_str());
    return c_str;
}

char* bounding_polygon(const char* const code_sequence_ptr, const char* const initial_angles_ptr) {

    const auto code_sequence = parse_code_sequence(code_sequence_ptr);
    const auto initial_angles = parse_initial_angles(initial_angles_ptr);

    CodePair pair = CodePair{code_sequence, initial_angles};

    const auto string = get_bounding_vertices(pair);

    return to_cstr(string);
}

static void copy_to_c_code_info(const CodeInfo& code_info, CCodeInfo* const c_code_info) {

    c_code_info->points = to_cstr(serialize(code_info.points));
    c_code_info->sin_equations = to_cstr(serialize(code_info.sin_equations));
    c_code_info->cos_equations = to_cstr(serialize(code_info.cos_equations));
}

// 0 means failure
// 1 means success
int32_t load_code_info(const char* const code_sequence_ptr,
                       const char* const initial_angles_ptr,
                       CCodeInfo* const c_code_info) {

    try {
        const auto code_sequence = parse_code_sequence(code_sequence_ptr);
        const auto initial_angles = parse_initial_angles(initial_angles_ptr);

        const auto code_info = calculate_code_info(CodePair{code_sequence, initial_angles});
        
        //  Dec 17, 2021
        // I used the following line to print the initial angles.
        //std::cout << "Using " << initial_angles << " to compute the bounding polygon and equations" << std::endl;

        copy_to_c_code_info(code_info, c_code_info);

        return 1;

    } catch (const std::exception& except) {
        std::cerr << except.what() << std::endl;

        return 0;
    }
}

char* get_code_sequence(const char* const code_sequence_ptr, const char* const angles_ptr){
    const auto code_sequence = parse_code_sequence(code_sequence_ptr);
    const auto initial_angles = parse_initial_angles(angles_ptr);

    CodePair codePair = CodePair{code_sequence, initial_angles};
    CodeType codeType = codePair.sequence.type();
    const auto str = code_to_string(codeType);
    return to_cstr(str);
}

void cleanup_code_info(const CCodeInfo* const c_code_info) {
    delete[] c_code_info->points;
    delete[] c_code_info->sin_equations;
    delete[] c_code_info->cos_equations;
}

char* check_square(const int64_t numerx, const int64_t numery, const int64_t denom,
                   const char* const code_sequence_ptr, const char* const initial_angles_ptr,
                   const char* const cover_dir) {

    try {
        const auto code_sequence = parse_code_sequence(code_sequence_ptr);
        const auto initial_angles = parse_initial_angles(initial_angles_ptr);

        const auto str = check_square(numerx, numery, denom, code_sequence, initial_angles, cover_dir);

        return to_cstr(str);

    } catch (const std::exception& except) {
        std::cerr << except.what() << std::endl;

        return nullptr;
    }
}

void free_string(const char* const str) {
    delete[] str;
}
