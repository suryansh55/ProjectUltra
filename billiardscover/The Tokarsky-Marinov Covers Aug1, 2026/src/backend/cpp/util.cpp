#include <fstream>

#include <boost/algorithm/string.hpp>

#include "util.hpp"

// Splitting an empty string should return an empty list
std::vector<std::string> split(const std::string& str, const std::string& delims) {

    std::vector<std::string> vec{};
    if (!str.empty()) {
        boost::split(vec, str, boost::is_any_of(delims));
    }

    return vec;
}

std::string read_file(const std::string& path) {

    std::ifstream file{};
    file.exceptions(std::ifstream::badbit | std::ifstream::failbit | std::ifstream::eofbit);

    file.open(path);

    std::stringstream buff{};
    buff << file.rdbuf();

    return buff.str();
}
