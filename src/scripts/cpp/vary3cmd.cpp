#include <cstdint>
#include <iostream>
#include <set>
#include <cmath>
#include <sstream>
#include <set>
#include <ranges>

#include "vary.hpp"
#include "utils.hpp"
#include "classified_code_sequence.hpp"
using namespace std;

/**
 * Print the starting information for the Vary3 calculation.
 */
void printStartInfo(Vary3Args data) {
    auto checked = [](bool flag) { return flag ? "y" : "n"; };

    if(std::holds_alternative<int32_t>(data.maxSideSum)) {
        std::cout << "// Vary3 at (" << data.x << ", " << data.y << "), min = " 
            << data.minSideSum << ", max = " << std::get<int32_t>(data.maxSideSum) 
            << ", shots = " << data.shots << std::endl;
    } else {
        const auto& maxSideSumOverrides = std::get<CodeTypeCollection<int32_t>>(data.maxSideSum);
        std::cout << "// Vary3 at (" << data.x << ", " << data.y << "), min = " 
            << data.minSideSum 
            << ", max (overrides) = CS: " << maxSideSumOverrides.cs
            << ", OSO: " << maxSideSumOverrides.oso
            << ", OSNO: " << maxSideSumOverrides.osno
            << ", shots = " << data.shots
            << std::endl;
    }
    std::cout 
            << "// oso: " << checked(data.searchFor.oso) 
            << ", cs: " << checked(data.searchFor.cs) 
            << ", cns: " << checked(data.searchFor.cns) 
            << ", ons: " << checked(data.searchFor.ons) 
            << ", osno: " << checked(data.searchFor.osno) 
            << std::endl;
}


int main(int argc, char *argv[]) {
    if(argc != 12 && argc != 15 && argc != 18) {
        cerr << "Usage: " << argv[0] << " <db_x> <db_y> <int_minSideSum> <int_maxSideSum> <int_shots> <bool_oso> <bool_cs> <bool_cns> <bool_ons> <bool_osno> <REGULAR/MIDDLE/FIRSTMIDLAST_printMode> "; 
        cerr << "<int_maxOSOCodeLength?> <int_maxCSCodeLength?> <int_maxOSNOCodeLength?> <int_overrideCS?> <int_overrideOSO?> <int_overrideOSNO?>" << endl;
        return 1;
    }

    // Parse data from command line arguments
    double x = std::stod(argv[1]);
    double y = std::stod(argv[2]);

    int32_t minSideSum = std::stoi(argv[3]);
    int32_t maxSideSum = std::stoi(argv[4]);
    std::variant<int32_t, CodeTypeCollection<int32_t>> maxSideSumVariant = maxSideSum;
    int32_t shots = std::stoi(argv[5]);

    bool oso = std::stoi(argv[6]) != 0;
    bool cs = std::stoi(argv[7]) != 0;
    bool cns = std::stoi(argv[8]) != 0;
    bool ons = std::stoi(argv[9]) != 0;
    bool osno = std::stoi(argv[10]) != 0;
    boost::optional<PrintMode> opt = strToPrintMode(argv[11]);
    if(!opt){
        cerr << "Failed to parse print mode " << argv[11];
        return 1;
    }
    PrintMode mode = opt.get();

    boost::optional<int32_t> maxOSOCodeLength = argc == 15 ? boost::optional<int32_t>(stoi(argv[12])) : boost::none;
    boost::optional<int32_t> maxCSCodeLength = argc == 15 ? boost::optional<int32_t>(stoi(argv[13])) : boost::none;
    boost::optional<int32_t> maxOSNOCodeLength = argc == 15 ? boost::optional<int32_t>(stoi(argv[14])) : boost::none;
    boost::optional<int32_t> overrideOSO = argc == 18 ? boost::optional<int32_t>(stoi(argv[15])) : boost::none;
    boost::optional<int32_t> overrideCS = argc == 18 ? boost::optional<int32_t>(stoi(argv[16])) : boost::none;
    boost::optional<int32_t> overrideOSNO = argc == 18 ? boost::optional<int32_t>(stoi(argv[17])) : boost::none;

    if(argc == 18) {
        if(overrideOSO && overrideCS && overrideOSNO) {
            CodeTypeCollection<int32_t> maxSideSumOverrides = {overrideOSO.get(), overrideCS.get(), maxSideSum, maxSideSum, overrideOSNO.get()};
            maxSideSumVariant = maxSideSumOverrides;
        } else {
            cerr << "All three overrides must be provided if any are provided." << endl;
            return 1;
        }
    }

    Vary3Args args = {x, y, minSideSum, maxSideSumVariant, shots, CodeTypeSet{oso, cs, cns, ons, osno}};

    size_t numThreads = getNumThreads();
    std::cout << "// Found " << numThreads << " threads" << std::endl;

    auto start = std::chrono::steady_clock::now();
    printStartInfo(args);
	auto codeSequences = findCodesVary3(args);

    auto filteredSequences = codeSequences
        | std::views::filter([&argc, &maxOSOCodeLength, &maxCSCodeLength, &maxOSNOCodeLength](const ClassifiedCodeSequence& code) {
            if(argc != 15) return true;
            if(code.codeType == CodeType::CS) return code.codeLength <= maxCSCodeLength.get();
            else if(code.codeType == CodeType::OSO) return code.codeLength <= maxOSOCodeLength.get();
            else if(code.codeType == CodeType::OSNO) return code.codeLength <= maxOSNOCodeLength.get();
            else return true;
        })
        | std::ranges::to<std::set<ClassifiedCodeSequence>>();
    printCodes(filteredSequences, mode);

    auto end = std::chrono::steady_clock::now();

    std::chrono::duration<double> elapsed = end - start;

    auto hrs = std::chrono::duration_cast<std::chrono::hours>(elapsed);
    auto mins = std::chrono::duration_cast<std::chrono::minutes>(elapsed - hrs);
    auto secs = std::chrono::duration_cast<std::chrono::seconds>(elapsed - hrs - mins);

    std::cout << "// Elapsed time: "
        << hrs.count() << "h " 
        << mins.count() << "m " 
        << secs.count() << "s" 
        << std::endl;
}
