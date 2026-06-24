#include <cstdint>
#include <iostream>
#include <set>
#include <cmath>
#include <sstream>
#include <set>
#include <boost/asio.hpp>

#include "vary_cs.hpp"
#include "vary3.hpp"
#include "vary.hpp"
#include "utils.hpp"
#include "classified_code_sequence.hpp"
using namespace std;

/**
 * Print the starting information for the Vary3 calculation.
 */
void printStartInfo(Vary3Args data) {
    auto checked = [](bool flag) { return flag ? "y" : "n"; };

    std::cout << "// Vary3 at (" << data.x << ", " << data.y << "), min = " 
        << data.minSideSum << ", max = " << data.maxSideSum 
        << ", shots = " << data.shots << std::endl;
    std::cout 
            << "// oso: " << checked(data.oso) 
            << ", cs: " << checked(data.cs) 
            << ", cns: " << checked(data.cns) 
            << ", ons: " << checked(data.ons) 
            << ", osno: " << checked(data.osno) 
            << std::endl;
}

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
    std::cout << "Found " << numThreads << " threads" << std::endl;

    std::set<ClassifiedCodeSequence> codeSeqs;


    if(args.cs) {
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

    if(args.oso || args.cns || args.ons || args.osno) {

        unsigned int availableThreads = numThreads / args.shots;
        int32_t searchSpace = args.maxSideSum - args.minSideSum;
        for(int count = 1; count <= args.shots; ++count) {
            // Subdivide our side sum search space by the number of remaining threads (after we account for dividing by shots)

            int32_t subdividedMinSideSum = args.minSideSum;
            int32_t subdividedMaxSideSum = args.minSideSum;
            const float64_t pos = increment * count;
            for(unsigned int subdivision = 1; subdivision < availableThreads; ++subdivision){ 
                subdividedMaxSideSum += searchSpace / (std::pow(2, subdivision));

                // Start a parallelized vary3 job
                std::cerr << "Job started from " << subdividedMinSideSum << " to " << subdividedMaxSideSum << std::endl;
                boost::asio::post(pool, [xRad, yRad, pos, subdividedMinSideSum, subdividedMaxSideSum, &codeSeqs, &args]() {
                    std::vector<std::vector<int32_t>> foundCodes = fireAway3(subdividedMinSideSum, subdividedMaxSideSum, xRad, yRad, pos, getReqTypesStr(args.oso, false, args.cns, args.ons, args.osno));
                    for(const auto& code : foundCodes) {
                        if(boost::optional<ClassifiedCodeSequence> seq = convert(code)) {
                            codeSeqs.insert(seq.get());
                        };
                    }
                });
                subdividedMinSideSum = subdividedMaxSideSum;
            }
            // Implement the last subdivision manually to make sure we search the space fully in the case where the sideSumInterval is weird
            std::cerr << "Job started from " << subdividedMinSideSum << " to " << args.maxSideSum << std::endl;
            boost::asio::post(pool, [xRad, yRad, pos, subdividedMinSideSum, &codeSeqs, &args]() {
                std::vector<std::vector<int32_t>> foundCodes = fireAway3(subdividedMinSideSum, args.maxSideSum, xRad, yRad, pos, getReqTypesStr(args.oso, false, args.cns, args.ons, args.osno));
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

int main(int argc, char *argv[])
{
    if(argc != 11) {
        cerr << "Usage: " << argv[0] << " <db_x> <db_y> <int_minSideSum> <int_maxSideSum> <int_shots> <bool_oso> <bool_cs> <bool_cns> <bool_ons> <bool_osno>" << endl;
        return 1;
    }

    // Parse data from command line arguments
    double x = std::stod(argv[1]);
    double y = std::stod(argv[2]);

    int32_t minSideSum = std::stoi(argv[3]);
    int32_t maxSideSum = std::stoi(argv[4]);
    int32_t shots = std::stoi(argv[5]);

    bool oso = std::stoi(argv[6]) != 0;
    bool cs = std::stoi(argv[7]) != 0;
    bool cns = std::stoi(argv[8]) != 0;
    bool ons = std::stoi(argv[9]) != 0;
    bool osno = std::stoi(argv[10]) != 0;

    Vary3Args args = {x, y, minSideSum, maxSideSum, shots, oso, cs, cns, ons, osno};


    printStartInfo(args);
    ScopedTimer t{"Vary3"};
	auto codeSequences = findCodesVary3(args);
    int count = 0;
    
    for(auto& seq : codeSequences) {
        ++count;
        cout << standard(seq, count) << endl;
    }
    
}
