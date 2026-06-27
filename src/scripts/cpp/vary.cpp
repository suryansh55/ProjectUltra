#include "vary.hpp"
#include "classified_code_sequence.hpp"
#include "code_type.hpp"
#include "utils.hpp"
#include <boost/optional.hpp>
#include <bits/std_thread.h>
#include <map>
#include <iostream>
#include <string>

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
    int32_t codeLength;
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

void printCodes(std::set<ClassifiedCodeSequence> codeSequences, PrintMode mode) {
    int count = 0;
    // Handle the simplest case and return early
    if(mode == PrintMode::REGULAR) {
        for(auto& seq : codeSequences) std::cout << standard(seq, ++count) << std::endl;
        return;
    }

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
