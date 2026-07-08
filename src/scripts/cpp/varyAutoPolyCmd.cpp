#include <boost/algorithm/string.hpp>
#include <boost/optional.hpp>
#include <boost/cstdfloat.hpp>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <optional>
#include "general.hpp"
#include "vary.hpp"
using namespace std;

void printStartInfo(VaryAutoPolyArgs data) {
    auto checked = [](bool flag) { return flag ? "y" : "n"; };

    cout << "// +---------- AutoPolyVary running on " << data.holes.size() << " hole(s): " << data.shots << " shots, "
        << data.minSideSum << " to " << data.maxSideSum << " moves----------+ " << endl;
    cout << "// oso: " << checked(data.searchFor.oso)
        << " cs: " << checked(data.searchFor.cs)
        << " cns: " << checked(data.searchFor.cns)
        << " ons: " << checked(data.searchFor.ons)
        << " osno: " << checked(data.searchFor.osno)
        << endl; 
    cout << "// Max code length: CS - " << data.maxCSCodeLength 
        << " OSO - " << data.maxOSOCodeLength
        << " OSNO - " << data.maxOSNOCodeLength
        << endl;
    if(data.maxCSSideSum){ // if one of these values is present, all of them should be
        cout << "// Overrided side sum: CS - " << *data.maxCSSideSum
            << " OSO - " << *data.maxOSOSideSum
            << " OSNO - " << *data.maxOSNOSideSum
            << endl;
    }
}

int main(int argc, const char* argv[]) {
    if(argc != 14 && argc != 17) {
        cerr << "Usage: " << argv[0] << " <path/to/holes> <int_minSideSum> <int_maxSideSum> <int_shots> ";
        cerr << "<bool_oso> <bool_cs> <bool_cns> <bool_ons> <bool_osno> ";
        cerr << "<int_maxOSOCodeLength> <int_maxCSCodeLength> <int_maxOSNOCodeLength> <str_printMode>";
        cerr << "<int_maxOSOSideSum?> <int_maxCSSideSum?> <int_maxOSNOSideSum?>" << endl;
        return 1;
    }

    std::vector<std::vector<Vec2>> holes{};
    std::vector<Vec2> curHoles{};
    ifstream holeFile{argv[1]};
    if(!holeFile.is_open()) {
        cerr << "Failed to open " << argv[1] << endl;
        return 1;
    }
    string line{""};
    while(getline(holeFile, line)){
        boost::trim(line);
        // Empty line represents another sets of pixels for a different hole
        if(line.empty()){
            holes.emplace_back(curHoles);
            curHoles = {};
            continue;
        };

        // # represents a commented line
        if(line[0] == '#') continue; 

        // Split up the line by whitespaces        
        istringstream lineStream(line);
        string xCoord, yCoord;
        lineStream >> xCoord >> yCoord;
        Vec2 v{stod(xCoord), stod(yCoord)};
        curHoles.emplace_back(v);
    }
    holes.emplace_back(curHoles);

    int32_t minSideSum = stoi(argv[2]);
    int32_t maxSideSum = stoi(argv[3]);
    int32_t shots = stoi(argv[4]);
    bool oso = stoi(argv[5]) != 0;
    bool cs = stoi(argv[6]) != 0;
    bool cns = stoi(argv[7]) != 0;
    bool ons = stoi(argv[8]) != 0;
    bool osno = stoi(argv[9]) != 0;

    int32_t maxOSOCodeLength = stoi(argv[10]);
    int32_t maxCSCodeLength = stoi(argv[11]);
    int32_t maxOSNOCodeLength = stoi(argv[12]);

    boost::optional<PrintMode> opt = strToPrintMode(argv[13]);
    if(!opt){
        cerr << "Failed to parse print mode " << argv[13];
        return 1;
    }
    PrintMode printMode = opt.get();

    boost::optional<int32_t> maxOSOSideSum = argc == 17 ? boost::optional<int32_t>(stoi(argv[14])) : boost::none;
    boost::optional<int32_t> maxCSSideSum = argc == 17 ? boost::optional<int32_t>(stoi(argv[15])) : boost::none;
    boost::optional<int32_t> maxOSNOSideSum = argc == 17 ? boost::optional<int32_t>(stoi(argv[16])) : boost::none;

    VaryAutoPolyArgs args{
        holes, minSideSum, maxSideSum, shots, oso, cs, cns, ons, osno, 
        maxOSOCodeLength, maxCSCodeLength, maxOSNOCodeLength, printMode,
        maxOSOSideSum, maxCSSideSum, maxOSNOSideSum, 
    };

    auto start = std::chrono::steady_clock::now();
    printStartInfo(args);
    auto codes = findCodesPolyVary(args);
    std::cout << "// +------------------------ AutoPolyVary finished successfully ------------------------+" << std::endl;
    std::cout << "// Printing summary of codes found..." << std::endl;
    for (const auto& codeSet : codes) {
        if(codeSet.empty()) continue;
        printCodes(codeSet, printMode);
        std::cout << std::endl;
    }
    auto end = std::chrono::steady_clock::now();

    auto elapsed = end - start;
    auto hrs = std::chrono::duration_cast<std::chrono::hours>(elapsed);
    auto mins = std::chrono::duration_cast<std::chrono::minutes>(elapsed - hrs);
    auto secs = std::chrono::duration_cast<std::chrono::seconds>(elapsed - hrs - mins);

    std::cout << "// Elapsed time: "
        << hrs.count() << "h " 
        << mins.count() << "m " 
        << secs.count() << "s\n";
}
