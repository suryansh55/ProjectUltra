#include "vary.hpp"
#include <bits/std_thread.h>


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
