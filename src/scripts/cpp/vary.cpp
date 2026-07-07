#include "vary.hpp"
#include "geometry/convex_polygon.hpp"
#include "classified_code_sequence.hpp"
#include "code_type.hpp"
#include "utils.hpp"
#include "vary_cs.hpp"
#include "vary3.hpp"
#include "vary4.hpp"
#include "equations.hpp"
#include "bounding_region.hpp"
#include "wrapper.hpp"
#include <algorithm>
#include <iterator>
#include <boost/optional.hpp>
#include <boost/asio.hpp>
#include <bits/std_thread.h>
#include <map>
#include <iostream>
#include <string>
#include <ranges>

/**
 * getNumThreads queries the system for the number of threads available. 
 * Able to be used normally and on HPC clusters using SLURM workload manager
 * @return size_t >= 1
 */
size_t getNumThreads() {
    const bool IS_SLURM_ENV = (std::getenv("SLURM_CPUS_PER_TASK") != nullptr);

    size_t numThreads = IS_SLURM_ENV ? std::stoi(std::getenv("SLURM_CPUS_PER_TASK")) : std::thread::hardware_concurrency();
    if(numThreads == 0) {
        numThreads = 1; // Fallback to 1 if hardware_concurrency cannot determine the number of threads
    }
    return numThreads;
}

/**
 * getReqTypesStr builds and returns a space-delimited string of request type codes based on the five boolean arguments, 
 * adding each enabled code in the order oso, cs, cns, ons, and osno. 
 * It is intended to produce the type string expected by Vary fire away functions.
 * @example getReqTypesStr(true, true, true, true, true) -> "oso cs cns ons osno"
 * @example getReqTypesStr(true, false, true, true, true) -> "oso cns ons osno"
 */
std::string getReqTypesStr(bool oso, bool cs, bool cns, bool ons, bool osno){
    std::ostringstream stream;
    if(oso) stream << "oso ";
    if(cs) stream << "cs ";
    if(cns) stream << "cns ";
    if(ons) stream << "ons ";
    if(osno) stream << "osno";
    return stream.str();
}

/**
 * `strToPrintMode` takes a given string and if it represents a valid print mode returns the corresponding PrintMode.
 * Otherwise, returns none. The method should be case agnostic.
 */
boost::optional<PrintMode> strToPrintMode(std::string s) {
    // Transform the given string to uppercase in place
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return std::toupper(c); });

    if(s == "REGULAR") return PrintMode::REGULAR;
    else if(s == "MIDDLE") return PrintMode::MIDDLE;
    else if(s == "FIRSTMIDLAST") return PrintMode::FIRSTMIDLAST;
    else return boost::none;
}

/**
 * `CodeLenAndPattern` is a wrapper class around a code sequence's codeLength and oddEvenPattern
 * used to group together data we want to classify code sequences by
 */
struct CodeLenAndPattern {
    long codeLength;
    std::string oddEvenPattern;
};

bool operator<(const CodeLenAndPattern a, const CodeLenAndPattern b) {
    return std::tie(a.codeLength, a.oddEvenPattern) < std::tie(b.codeLength, b.oddEvenPattern);
}

/**
 * `groupByCodeLengthAndPattern` creates a map with each code type from a given set of code sequences 
 * that groups code sequences with the same code length and odd even pattern into the same vector.
 * This is used a processing step before being able to print the first/last/middle of a certain
 * sequence group.
 */
std::map<CodeType, std::map<CodeLenAndPattern, std::vector<ClassifiedCodeSequence>>> groupByCodeLengthAndPattern(const std::set<ClassifiedCodeSequence> &codeSequences) {
    std::map<CodeType, std::map<CodeLenAndPattern, std::vector<ClassifiedCodeSequence>>> res;
    for(const ClassifiedCodeSequence& codeSeq : codeSequences) {
        // If the code type exists in the current CodeType map
        if(auto it = res.find(codeSeq.codeType); it != res.end() ){
            std::map<CodeLenAndPattern, std::vector<ClassifiedCodeSequence>> &group = it->second;
            // Check if the CodeLenAndPattern already exists in the group
            CodeLenAndPattern pat{codeSeq.codeLength, codeSeq.oddEvenPattern}; 
            if(auto it = group.find(pat); it != group.end()) {
                // Get the corresponding list of codes and add the current
                std::vector<ClassifiedCodeSequence> &codes = it->second;
                codes.emplace_back(codeSeq);
            } else { // Otherwise, create a new group entry
                group.emplace(pat, std::vector{codeSeq});
            }
        } else { // Otherwise, get the map for the relevant CodeType
            std::map<CodeLenAndPattern, std::vector<ClassifiedCodeSequence>> group = {
                {
                    CodeLenAndPattern{codeSeq.codeLength, codeSeq.oddEvenPattern},
                    std::vector{codeSeq}
                } 
            };
            res.emplace(codeSeq.codeType, group);
        }
    }
    return res;
}

/**
 * `filterFirstMidLast` creates a vector of code sequences of at most three elements, 
 * containing the first, mid and last code sequence in the given group.
 * The given vector of code sequence should be a valid grouping (same code type and code length)
 */
std::vector<ClassifiedCodeSequence> filterFirstMidLast(const std::vector<ClassifiedCodeSequence> &codeSequences) {
    std::vector<ClassifiedCodeSequence> filtered;
    size_t size = codeSequences.size();
    size_t target_size = (size >= 3) ? 3 : size;
    filtered.reserve(target_size);
    if(size >= 2) filtered.emplace_back(codeSequences[0]);
    filtered.emplace_back(codeSequences[size / 2]);
    if(size >= 3) filtered.emplace_back(codeSequences.back());
    return filtered;
}

/**
 * `printCodes` prints the given set of code sequences according to the given print mode
 */
void printCodes(std::set<ClassifiedCodeSequence> codeSequences, PrintMode mode) {
    int count = 0;
    if(mode == PrintMode::REGULAR) {
        for(auto& seq : codeSequences) std::cout << standard(seq, ++count) << std::endl;
    } else {
        auto classifiedCodes = groupByCodeLengthAndPattern(codeSequences);
        for(const auto& [_, groups] : classifiedCodes) {
            for(const auto& [pat, codes] : groups) {
                auto filtered = filterFirstMidLast(codes);
                if(mode == PrintMode::FIRSTMIDLAST) {
                    for(const auto &code : filtered) std::cout << standard(code, ++count) << std::endl;
                } else if(mode == PrintMode::MIDDLE) {
                    std::cout << standard(filtered[filtered.size() / 2], ++count) << std::endl;
                } 
            }
        }
    }
}

enum Location {
    INSIDE,
    OUTSIDE,
    BOUNDARY
};

// algorithm taken from http://paulbourke.net/geometry/polygonmesh
Location location(const std::vector<Vector2<Interval>>& polygon, const double x, const double y) {
    const auto sign = [](const Vector2<Interval>& p0, const Vector2<Interval>& p1, const double x, const double y) {
        const auto crossProduct = (p1.x() - p0.x()) * (y - p0.y()) - (p1.y() - p0.y()) * (x - p0.x());
        if(crossProduct > 0) return 1;
        else if(crossProduct < 0) return -1;
        else return 0;
    };

    const int firstSideSign = sign(polygon[0], polygon[1], x, y);

    for(size_t i = 1; i < polygon.size(); ++i) {
        const Vector2<Interval>& p0 = polygon[i];
        const Vector2<Interval>& p1 = polygon[(i + 1) % polygon.size()];

        int sideSign = sign(p0, p1, x, y);
        if(sideSign == 0) {
            return Location::BOUNDARY;
        } else if(sideSign != firstSideSign) {
            return Location::OUTSIDE;
        }
    }
    return Location::INSIDE;
};


/**
 * Find the classified code sequences for the given Vary3 arguments using Vary3 and VaryCS.
 */
std::set<ClassifiedCodeSequence> findCodesVary3(const Vary3Args& args) {
    const float64_t pi = boost::math::constants::pi<double>();
    const float64_t xRad = args.x * pi / 180.0;
    const float64_t yRad = args.y * pi / 180.0;

    // Create a boost thread pool
    size_t numThreads = getNumThreads();
    boost::asio::thread_pool pool(numThreads);

    std::set<ClassifiedCodeSequence> codeSeqs;

    if(args.searchFor.cs) {
        float64_t xAngle = xRad;
        float64_t yAngle = yRad;

        for(int32_t i = 0; i < 3; ++i) {
            // Start a parallelized vary_cs job for the current angles
            boost::asio::post(pool, [xAngle, yAngle, &codeSeqs, &args]() {
                std::vector<std::vector<int32_t>> foundCodes = fireAwayCS(args.minSideSum, args.maxSideSum, xAngle, yAngle, "cs");
                for(const auto& code : foundCodes) {
                    if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                        codeSeqs.insert(seq.get());
                    };
                }
            });

            float64_t zAngle = pi - (xAngle + yAngle);
            xAngle = yAngle;
            yAngle = zAngle;
        }

        // Wait for all vary_cs jobs to finish and collect results
        pool.join();
    }

    const float64_t base = sin(xRad + yRad);
    const float64_t increment = base / (args.shots + 1);

    if(args.searchFor.oso || args.searchFor.cns || args.searchFor.ons || args.searchFor.osno) {
        unsigned int availableThreads = numThreads / args.shots;
        int32_t searchSpace = args.maxSideSum - args.minSideSum;
        for(int count = 1; count <= args.shots; ++count) {
            // Subdivide our search space in half per availableThread
            int32_t subdividedMinSideSum = args.minSideSum;
            int32_t subdividedMaxSideSum = args.minSideSum;
            const float64_t pos = increment * count;
            for(unsigned int subdivision = 1; subdivision < availableThreads; ++subdivision){ 
                subdividedMaxSideSum += searchSpace / (std::pow(2, subdivision));

                // Start a parallelized vary3 job
                boost::asio::post(pool, [xRad, yRad, pos, subdividedMinSideSum, subdividedMaxSideSum, &codeSeqs, &args]() {
                    std::vector<std::vector<int32_t>> foundCodes = fireAway3(subdividedMinSideSum, subdividedMaxSideSum, 
                        xRad, yRad, pos, getReqTypesStr(args.searchFor.oso, false, args.searchFor.cns, args.searchFor.ons, args.searchFor.osno));
                    for(const auto& code : foundCodes) {
                        if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                            codeSeqs.insert(seq.get());
                        };
                    }
                });
                subdividedMinSideSum = subdividedMaxSideSum;
            }
            // Implement the last subdivision manually to make sure we search the space fully in the case where the sideSumInterval is weird
            boost::asio::post(pool, [xRad, yRad, pos, subdividedMinSideSum, &codeSeqs, &args]() {
                std::vector<std::vector<int32_t>> foundCodes = fireAway3(subdividedMinSideSum, args.maxSideSum, 
                    xRad, yRad, pos, getReqTypesStr(args.searchFor.oso, false, args.searchFor.cns, args.searchFor.ons, args.searchFor.osno));
                for(const auto& code : foundCodes) {
                    if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                        codeSeqs.insert(seq.get());
                    };
                }
            });
        }

        // Wait for all vary3 jobs to finish and collect results
        pool.join();
    }

    return codeSeqs;
}

std::set<ClassifiedCodeSequence> findCodesVary4(const Vary4Args& args) {
    const float64_t pi = boost::math::constants::pi<double>();
    const float64_t xRad = args.x * pi / 180.0;
    const float64_t yRad = args.y * pi / 180.0;

    // Create a boost thread pool
    size_t numThreads = getNumThreads();
    boost::asio::thread_pool pool(numThreads);

    std::set<ClassifiedCodeSequence> codeSeqs;

    if(args.searchFor.cs) {
        float64_t xAngle = xRad;
        float64_t yAngle = yRad;

        for(int32_t i = 0; i < 3; ++i) {
            // Start a parallelized vary_cs job for the current angles
            boost::asio::post(pool, [xAngle, yAngle, &codeSeqs, &args]() {
                std::vector<std::vector<int32_t>> foundCodes = fireAwayCS(args.minSideSum, args.maxSideSum, xAngle, yAngle, "cs");
                for(const auto& code : foundCodes) {
                    if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                        codeSeqs.insert(seq.get());
                    };
                }
            });

            float64_t zAngle = pi - (xAngle + yAngle);
            xAngle = yAngle;
            yAngle = zAngle;
        }

        // Wait for all vary_cs jobs to finish and collect results
        pool.join();
    }

    if(args.searchFor.oso || args.searchFor.cns || args.searchFor.ons || args.searchFor.osno) {
        // Start a parallelized vary4 job
        boost::asio::post(pool, [xRad, yRad, &codeSeqs, &args]() {
            std::vector<std::vector<int32_t>> foundCodes = fireAway4(args.minSideSum, args.maxSideSum, 
                xRad, yRad, getReqTypesStr(args.searchFor.oso, false, args.searchFor.cns, args.searchFor.ons, args.searchFor.osno));
            for(const auto& code : foundCodes) {
                if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                    codeSeqs.insert(seq.get());
                };
            }
        });

        // Wait for all vary4 jobs to finish and collect results
        pool.join();
    }
    return codeSeqs;
}

std::vector<std::set<ClassifiedCodeSequence>> findCodesPolyVary(const VaryAutoPolyArgs& args) {
    std::vector<std::vector<Vector2<Interval>>> filledRegion{};
    std::vector<std::set<ClassifiedCodeSequence>> codes{};

    int i = 1;
    for(const auto& coordinates : args.holes){
        const size_t numPoints = coordinates.size();
        std::cout << "// Processing Group " << i << " of " << args.holes.size() << std::endl;
        for(size_t index = 0; index < numPoints; ++index) {
            float64_t x = coordinates[index][0];
            float64_t y = coordinates[index][1];

            // Check to see if the point is covered by any of the previous points' stables
            bool skip = false;
            for(const auto& poly : filledRegion) {
                const float64_t pi = boost::math::constants::pi<double>();
                const float64_t xRad = x * pi / 180.0;
                const float64_t yRad = y * pi / 180.0;

                Location loc = location(poly, xRad, yRad);
                if(loc == Location::INSIDE) {
                    std::cout << "// Point (" << x << ", " << y << ") is covered by a previous stable" << std::endl;
                    skip = true;
                    break;
                }
            }

            if(skip) continue;

            // Find codes for the current point using Vary3
            int32_t csMaxSideSum = args.maxCSSideSum.value_or(args.maxSideSum);
            int32_t osoMaxSideSum = args.maxOSOSideSum.value_or(args.maxSideSum);
            int32_t osnoMaxSideSum = args.maxOSNOSideSum.value_or(args.maxSideSum);
            std ::cout << "// Inspecting Point (" << x << ", " << y << ")" << std::endl;
            Vary3Args CS = Vary3Args{x, y, args.minSideSum, csMaxSideSum, args.shots,
                false, args.searchFor.cs, false, false, false};
            Vary3Args nonCS = Vary3Args{x, y, args.minSideSum, std::max(osoMaxSideSum, osnoMaxSideSum), args.shots,
                args.searchFor.oso, false, args.searchFor.cns, args.searchFor.ons, args.searchFor.osno};

            std::set<ClassifiedCodeSequence> csCodes = findCodesVary3(CS);
            std::set<ClassifiedCodeSequence> nonCSCodes = findCodesVary3(nonCS);

            // Union the two sets of codes into a single set for further processing
            std::set<ClassifiedCodeSequence> allCodes = csCodes;
            allCodes.insert(nonCSCodes.begin(), nonCSCodes.end());

            // Filter codes by print mode
            auto classifiedCodes = groupByCodeLengthAndPattern(allCodes);
            std::vector<ClassifiedCodeSequence> filteredCodes{};
            for(const auto& [_, groups] : classifiedCodes) {
                for(const auto& [pat, codes] : groups) {
                    auto filtered = filterFirstMidLast(codes);
                    if(args.mode == PrintMode::FIRSTMIDLAST) {
                        for(const auto &code : filtered) filteredCodes.emplace_back(code);
                    } else if(args.mode == PrintMode::MIDDLE) {
                        filteredCodes.emplace_back(filtered[filtered.size() / 2]);
                    } 
                }
            }

            // Filter codes by max code length
            auto finalCodes = filteredCodes
                | std::views::filter([](const ClassifiedCodeSequence& code) { return code.stable; })
                | std::views::filter([&args](const ClassifiedCodeSequence& code) {
                    if(code.codeType == CodeType::CS) return code.codeLength <= args.maxCSCodeLength;
                    else if(code.codeType == CodeType::OSO) return code.codeLength <= args.maxOSOCodeLength;
                    else if(code.codeType == CodeType::OSNO) return code.codeLength <= args.maxOSNOCodeLength;
                    else return false;
                })
                | std::ranges::to<std::set<ClassifiedCodeSequence>>();

            printCodes(finalCodes, args.mode);

            auto polygons = finalCodes
                | std::views::transform([](const ClassifiedCodeSequence& code) { 
                    return calculate_stable(*code.codeSequence, code.codeType);
                })
                | std::views::filter([](const auto& poly) { return poly.has_value(); })
                | std::views::transform([](const auto& poly) { return poly.value(); })
                | std::views::transform([](const Stable& stable){ return stable.points; })
                | std::ranges::to<std::vector<std::vector<Vector2<Interval>>>>();

            
            codes.emplace_back(finalCodes);
            filledRegion.insert(filledRegion.end(), polygons.begin(), polygons.end());
        }
        std::cout << std::endl;
        ++i;
    }

    return codes;
}

