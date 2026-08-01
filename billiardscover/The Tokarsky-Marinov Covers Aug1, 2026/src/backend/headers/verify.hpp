#pragma once

#include "cover.hpp"
#include "equations.hpp"

bool verify_cover(const ClosedRectangleQ& square, const OpenConvexPolygonQ& polygon,
                  const std::map<size_t, std::pair<CodePair, StableInfo>>& single_infos,
                  const std::map<size_t, std::pair<TriplePair, TripleInfo>>& triple_infos,
                  const cover::Cover& cover,
                  const uint32_t digits);
