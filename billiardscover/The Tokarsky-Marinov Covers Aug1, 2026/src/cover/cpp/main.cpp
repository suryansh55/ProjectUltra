#include <iostream>

#include "cover.hpp"
#include "equations.hpp"
#include "verify.hpp"

int main(const int argc, const char* const argv[]) {

    if (argc != 2) {
        std::cerr << "usage: " << argv[0] << " cover-directory" << std::endl;
        return EXIT_FAILURE;
    }

    const std::string cover_dir{argv[1]};

    const auto square = load_square(cover_dir);
    const auto polygon = load_polygon(cover_dir);
    const auto singles = load_singles(cover_dir);
    const auto triples = load_triples(cover_dir);
    const auto cover = load_cover(cover_dir);
    const auto digits = load_digits(cover_dir);

    const auto single_infos = load_single_infos(singles);
    const auto triple_infos = load_triple_infos(triples);

    const auto covered = verify_cover(square, polygon, single_infos, triple_infos, cover, digits);

    if (covered) {
        std::cout << "Success: polygon was covered and proved with all equations" << std::endl;
    } else {
        std::cout << "Failure: the polygon was not covered" << std::endl;
    }
}
