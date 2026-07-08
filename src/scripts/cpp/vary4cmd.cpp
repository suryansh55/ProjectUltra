#include "vary.hpp"
#include <boost/optional.hpp>
#include <iostream>

using namespace std;

/**
 * Print the starting information for the Vary4 calculation.
 */
void printStartInfo(Vary4Args data) {
    auto checked = [](bool flag) { return flag ? "y" : "n"; };

    std::cout << "// Vary4 at (" << data.x << ", " << data.y << "), min = " 
        << data.minSideSum << ", max = " << data.maxSideSum << std::endl;
    std::cout 
            << "// oso: " << checked(data.searchFor.oso) 
            << ", cs: " << checked(data.searchFor.cs) 
            << ", cns: " << checked(data.searchFor.cns) 
            << ", ons: " << checked(data.searchFor.ons) 
            << ", osno: " << checked(data.searchFor.osno) 
            << std::endl;
}

int main(int argc, char* argv[]) {
    if(argc != 11) {
        cerr << "Usage: " << argv[0] << " <db_x> <db_y> <int_minSideSum> <int_maxSideSum> <bool_oso> <bool_cs> <bool_cns> <bool_ons> <bool_osno> <REGULAR/MIDDLE/FIRSTMIDLAST_printMode>" << endl;
        return 1;
    }
    double x = std::stod(argv[1]);
    double y = std::stod(argv[2]);
    int32_t minSideSum = std::stoi(argv[3]);
    int32_t maxSideSum = std::stoi(argv[4]);
    bool oso = std::stoi(argv[5]) != 0;
    bool cs = std::stoi(argv[6]) != 0;
    bool cns = std::stoi(argv[7]) != 0;
    bool ons = std::stoi(argv[8]) != 0;
    bool osno = std::stoi(argv[9]) != 0;
    boost::optional<PrintMode> printMode = strToPrintMode(std::string(argv[10]));

    if(!printMode){
        cerr << "Failed to parse print mode " << argv[10];
        return 1;
    }
    PrintMode mode = printMode.get();

    Vary4Args args = {x, y, minSideSum, maxSideSum, oso, cs, cns, ons, osno};

    auto start = std::chrono::steady_clock::now();
    printStartInfo(args);
    auto codes = findCodesVary4(args);
    printCodes(codes, mode);
    auto end = std::chrono::steady_clock::now();

    auto elapsed = end - start;
    auto hrs = std::chrono::duration_cast<std::chrono::hours>(elapsed);
    auto mins = std::chrono::duration_cast<std::chrono::minutes>(elapsed - hrs);
    auto secs = std::chrono::duration_cast<std::chrono::seconds>(elapsed - hrs - mins);

    std::cout << hrs.count() << "h " 
              << mins.count() << "m " 
              << secs.count() << "s\n";
}