#pragma once

#include "cover.hpp"
#include <boost/numeric/conversion/cast.hpp> //marco mai aug 28 2025 add this part1

std::string get_bounding_vertices(const CodePair& code_pair);

template <template <typename> class Trig>
EqVec<Trig> map_to_vec(const EqMap<Trig>& eq) {

    EqVec<Trig> vec{};
    for (const auto& kv : eq) {

        const auto x_coeff = kv.first.arg.coeff(XY::X);
        const auto y_coeff = kv.first.arg.coeff(XY::Y);

        const math::LinComArr<XY, Coeff16> arg{boost::numeric_cast<Coeff16>(x_coeff),
                                               boost::numeric_cast<Coeff16>(y_coeff)};
        const Trig<math::LinComArr<XY, Coeff16>> trig{arg};

        vec.coeffs.emplace_back(trig, boost::numeric_cast<Coeff16>(kv.second));
    }

    return vec;
}

struct CodeInfo final {

    std::vector<PointQ> points;
    std::set<EqMap<Sin>> sin_equations;
    std::set<EqMap<Cos>> cos_equations;

    explicit CodeInfo(std::vector<PointQ> points_,
                      std::set<EqMap<Sin>> sin_equations_,
                      std::set<EqMap<Cos>> cos_equations_)
        : points{std::move(points_)},
          sin_equations{std::move(sin_equations_)},
          cos_equations{std::move(cos_equations_)} {}
};

struct StableInfo {

    OpenConvexPolygonQ polygon;
    std::vector<std::tuple<EqVec<Sin>, Coeff64, Coeff64>> sines;
    std::vector<std::tuple<EqVec<Cos>, Coeff64, Coeff64>> cosines;

    explicit StableInfo(const CodeInfo& code_info)
        : polygon{code_info.points} {

        for (const auto& equation : code_info.sin_equations) {
            const auto gbounds = gradient_bounds(equation);
            sines.emplace_back(map_to_vec(equation), gbounds.first, gbounds.second);
        }

        for (const auto& equation : code_info.cos_equations) {
            const auto gbounds = gradient_bounds(equation);
            cosines.emplace_back(map_to_vec(equation), gbounds.first, gbounds.second);
        }
    }
};

struct UnstableInfo {

    OpenSegmentQ segment;
    std::vector<std::tuple<EqVec<Sin>, Coeff64, Coeff64>> sines;
    std::vector<std::tuple<EqVec<Cos>, Coeff64, Coeff64>> cosines;

    explicit UnstableInfo(const CodeInfo& code_info)
        : segment{code_info.points.at(0), code_info.points.at(1)} {

        for (const auto& equation : code_info.sin_equations) {
            const auto gbounds = gradient_bounds(equation);
            sines.emplace_back(map_to_vec(equation), gbounds.first, gbounds.second);
        }

        for (const auto& equation : code_info.cos_equations) {
            const auto gbounds = gradient_bounds(equation);
            cosines.emplace_back(map_to_vec(equation), gbounds.first, gbounds.second);
        }
    }
};

struct TripleInfo {

    StableInfo stable_neg_info;
    UnstableInfo unstable_info;
    StableInfo stable_pos_info;

    explicit TripleInfo(StableInfo stable_neg_info_,
                        UnstableInfo unstable_info_,
                        StableInfo stable_pos_info_)
        : stable_neg_info{std::move(stable_neg_info_)},
          unstable_info{std::move(unstable_info_)},
          stable_pos_info{std::move(stable_pos_info_)} {}
};

CodeInfo calculate_code_info(const CodePair& code_pair);

std::map<size_t, std::pair<CodePair, StableInfo>> load_single_infos(const std::vector<CodePair>& singles);
std::map<size_t, std::pair<TriplePair, TripleInfo>> load_triple_infos(const std::vector<TriplePair>& triples);
