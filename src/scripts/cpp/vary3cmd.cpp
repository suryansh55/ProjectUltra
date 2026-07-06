#include <cstdint>
#include <iostream>
#include <set>
#include <cmath>
#include <sstream>
#include <set>

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
            << "// oso: " << checked(data.searchFor.oso) 
            << ", cs: " << checked(data.searchFor.cs) 
            << ", cns: " << checked(data.searchFor.cns) 
            << ", ons: " << checked(data.searchFor.ons) 
            << ", osno: " << checked(data.searchFor.osno) 
            << std::endl;
}


int main(int argc, char *argv[])
{
    if(argc != 12) {
        cerr << "Usage: " << argv[0] << " <db_x> <db_y> <int_minSideSum> <int_maxSideSum> <int_shots> <bool_oso> <bool_cs> <bool_cns> <bool_ons> <bool_osno> <REGULAR/MIDDLE/FIRSTMIDLAST_printMode>" << endl;
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
    PrintMode mode = strToPrintMode(argv[11]).value_or(PrintMode::REGULAR);
    
    Vary3Args args = {x, y, minSideSum, maxSideSum, shots, oso, cs, cns, ons, osno};

    auto start = std::chrono::steady_clock::now();
    printStartInfo(args);
	auto codeSequences = findCodesVary3(args);
    printCodes(codeSequences, mode);
    auto end = std::chrono::steady_clock::now();

    std::chrono::duration<double> elapsed = end - start;

    auto hrs = std::chrono::duration_cast<std::chrono::hours>(elapsed);
    auto mins = std::chrono::duration_cast<std::chrono::minutes>(elapsed - hrs);
    auto secs = std::chrono::duration_cast<std::chrono::seconds>(elapsed - hrs - mins);

    std::cout << hrs.count() << "h " 
              << mins.count() << "m " 
              << secs.count() << "s\n";
}
