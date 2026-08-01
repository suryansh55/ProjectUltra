#pragma once

#include <cstdint>

struct CCodeInfo {
    char* points;
    char* sin_equations;
    char* cos_equations;
};

extern "C" {
int32_t load_code_info(const char* const code_numbers_ptr,
                       const char* const initial_angles_ptr,
                       CCodeInfo* const c_code_info);

char* bounding_polygon(const char* const code_sequence_ptr, const char* const initial_angles_ptr);

void cleanup_code_info(const CCodeInfo* const c_code_info);

char* get_code_sequence(const char* const code_sequence_ptr, const char* const angles_ptr);

char* check_square(const int64_t numerx, const int64_t numery, const int64_t denom,
                   const char* const code_pair_str, const char* const initial_angles_ptr,
                   const char* const cover_dir);

void free_string(const char* const str);
}
