#include "vary_cs.hpp"

size_t get_total_physical_memory() {
#if defined(__APPLE__) || defined(__MACH__)
    int64_t mem;
    size_t len = sizeof(mem);
    if (sysctlbyname("hw.memsize", &mem, &len, nullptr, 0) == 0) {
        return static_cast<size_t>(mem);
    } else {
        std::cerr << "sysctlbyname failed\n";
        return 0;
    }
#elif defined(__linux__)
    long pages = sysconf(_SC_PHYS_PAGES);
    long page_size = sysconf(_SC_PAGE_SIZE);
    return static_cast<size_t>(pages) * static_cast<size_t>(page_size);
#elif defined(_WIN64)
    MEMORYSTATUSEX status;
    status.dwLength = sizeof(status);

    if (GlobalMemoryStatusEx(&status)) {
        // ullTotalPhys gives the total physical memory in bytes
        return status.ullTotalPhys;
    }

    return 0;
#else
    return 4L * 1024 * 1024 * 1024;  // Fallback to 4GB
#endif
}

int compute_max_inflight(float usage_fraction = 0.75f, size_t per_task_bytes = 128) {
    const size_t total_memory = get_total_physical_memory();
    const size_t usable_memory = static_cast<size_t>(total_memory * usage_fraction);
    return static_cast<int>(usable_memory / per_task_bytes);
}


const float64_t OFFSET = 0.0005;
const float64_t SMALLOFFSET = 0.0000000000005;

struct SearchTask {
    float64_t specMin;
    float64_t specMax;
    TriangleBilliard cbilliard;

    int32_t depth;
    SideSum sideSum;
    std::vector<int32_t> code;
};


/* this is a modifed version of Vary3.java
 * all funcion and class from java is implemented in c++
   but some of them no longer reference due to performence improvement reasons.
   Such as CodeSequence, ClassifiedCodeSequence, Utils.convert
   
    1. the process of creating ClassidedCodeSequence has been remove from here,
      this step will be done when wrapper recive information in Java.
	2. There will be seprate mulithread processing the gerenate codeSequence, and verify its code type
	3. There is a detacte the max code size,and computer memory size,limit number of submition,
	 code type check. avoid large amount of memory swap, 
   */
void iterateFireAwayCS2(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::mutex& codesFoundMutex, 
    int32_t depth, int32_t swapValue, bool goLeft, 
    boost::asio::thread_pool& pool, std::atomic<int>& inflight) 
    {
    // store data in each level
    struct Frame {
        float64_t specMin;
        float64_t specMax;
        int32_t swapValue;
        TriangleBilliard cbilliard;
        bool leftTried = false;
        bool rightTried = false;
        bool goLeft = false;
    };

    std::vector<Frame> stack;
    stack.reserve(max*2);

    // NOTE: Make sure to pass swap value 
    stack.push_back(Frame{specMin, specMax, swapValue, billiard, false, false, goLeft});


    while (!stack.empty()) {
        Frame& frame = stack.back();

        if (cancel_flag().load(std::memory_order_relaxed)) {
            std::cout << "C++ VaryCS Canceling" << std::endl;
            pool.stop();
            std::cout << "Canceled" << std::endl;
            return ;
        }

        if (depth >= max) {
            code.pop_back();
            depth--;
            frame.goLeft? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
            stack.pop_back();
            continue;
        }

        // float64_t specialAngle = frame.cbilliard.vertexC.x;
        // setting limit for submition to the memory
        float usage = 0.4;
        if (max >30000){usage = 0.04;}
        else if (max >25000){usage=0.08;}
        else if (max >15000){usage=0.1;}
        else if (max >10000){usage=0.2;}
        else if (max >6000){usage = 0.3;};
        const int MAX_INFLIGHT = compute_max_inflight(usage, 16384);  // tune based on memory

        if (!frame.leftTried && !frame.rightTried ) {
            if (depth > min && std::abs(sideSum.sum()) < OFFSET) {
                if ((frame.specMax - frame.specMin) > SMALLOFFSET) {
                    std::vector<int32_t> code2;
                    code2.insert(code2.end(), code.begin(), code.end());
                    code2.insert(code2.end(), code.rbegin(), code.rend());

                    // detect if hitting limit, if yes wait
                    while (inflight >= MAX_INFLIGHT) {
                        std::this_thread::sleep_for(std::chrono::microseconds(100));  // or use condition_variable
                    }
                    inflight.fetch_add(1, std::memory_order_relaxed);
                    // type check
                    boost::asio::post(pool, [=, &codesFound, &inflight, &codesFoundMutex] {
                        // std::vector<int> intVec(code2.begin(), code2.end());
                        auto seq = convert(code2);
                        if (seq){
                            CodeType codeType = seq.get().codeType;
                            if (codeType == CodeType::CS) {
                                std::lock_guard<std::mutex> lock(codesFoundMutex);
                                codesFound.push_back(code2);
                            }
                        }
                        inflight.fetch_sub(1, std::memory_order_relaxed);
                    });
                }
            }

            frame.leftTried = true;

            if (frame.specMin < frame.cbilliard.vertexC.x){
                int32_t leftSwap = 3 - frame.cbilliard.side;
                
                TriangleBilliard newbilliard = frame.cbilliard.getNext(true);
                leftSwap = leftSwap - newbilliard.side;
                
                sideSum.add(leftSwap);
                code.emplace_back(leftSwap);
                stack.push_back(Frame{
                    frame.specMin,
                    std::min(frame.cbilliard.vertexC.x, frame.specMax),
                    leftSwap, newbilliard,
                    false, false, true
                });
                depth++;
                continue;
            }
        }

        if (!frame.rightTried) {
            frame.rightTried = true;

            if (frame.specMax > frame.cbilliard.vertexC.x){
                int32_t rightSwap = 3 - frame.cbilliard.side;

                TriangleBilliard newbilliard = frame.cbilliard.getNext(false);
                rightSwap = rightSwap - newbilliard.side;

                sideSum.sub(rightSwap);
                code.emplace_back(rightSwap);
                stack.push_back(Frame{
                    std::max(frame.cbilliard.vertexC.x, frame.specMin),
                    frame.specMax,
                    rightSwap, newbilliard,
                    false,false,false
                });
                depth++;
                continue;
            }
        }


        // Both directions done — backtrack
        if (!code.empty()) code.pop_back();  // safeguard
        depth--;
        frame.goLeft? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
        stack.pop_back();
    }
}

/**
 * Wrapper function around iterateFireAwayCS2 to provide a simpler interface, beginning search from 0 depth (beginning)
 */
void iterateFireAwayCS2(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound)
    {
        unsigned int cores = std::thread::hardware_concurrency();
        std::mutex codesFoundMutex;

        // parallel code verify
        std::atomic<int> inflight{0};

        try{
            boost::asio::thread_pool pool(cores); 
            iterateFireAwayCS2(min, max, specMin, specMax, sideSum, billiard, code, codesFound, codesFoundMutex, 0, 0, false, pool, inflight);
            pool.join();
        } catch (const std::exception& ex) {
            std::cerr << "Exception caught: " << ex.what() << '\n';
        }

    }


void search(
    SearchTask task,
    int32_t min,
    int32_t max,
    std::mutex& codesFoundMutex,
    std::vector<std::vector<int32_t>>& codesFound,
    boost::asio::thread_pool& pool,
    std::atomic<int>& inflight) {
        // Check if the algorithm has been cancelled
        if (cancel_flag().load(std::memory_order_relaxed)) {
            std::cout << "C++ VaryCS Canceling" << std::endl;
            pool.stop();
            std::cout << "Canceled" << std::endl;
            return;
        }

        // Check if the current depth exceeds the maximum allowed depth
        if(task.depth >= max) return;

        // Check if the current state meets the criteria for a valid code sequence
        if (task.depth > min && std::abs(task.sideSum.sum()) < OFFSET && task.specMax - task.specMin > SMALLOFFSET) {
                std::vector<int32_t> code2;
                code2.insert(code2.end(), task.code.begin(), task.code.end());
                code2.insert(code2.end(), task.code.rbegin(), task.code.rend());

                auto seq = convert(code2);
                if (seq){
                    CodeType codeType = seq.get().codeType;
                    if (codeType == CodeType::CS) {
                        std::lock_guard<std::mutex> lock(codesFoundMutex);
                        codesFound.push_back(code2);
                    }
                }
        }

    	const int32_t PARALLEL_DEPTH = static_cast<int>(0.95 * max);

        // Submit a task to explore the left direction
        if (task.specMin < task.cbilliard.vertexC.x){
            int32_t leftSwap = 3 - task.cbilliard.side;
            
            TriangleBilliard newbilliard = task.cbilliard.getNext(true);
            leftSwap = leftSwap - newbilliard.side;

            SideSum leftSideSum = task.sideSum;
            std::vector<int32_t> leftCode = task.code;
            leftSideSum.add(leftSwap);
            leftCode.emplace_back(leftSwap);
            SearchTask leftTask{
                task.specMin,
                std::min(task.cbilliard.vertexC.x, task.specMax),
                newbilliard,
                task.depth + 1,
                leftSideSum,
                leftCode
            };

            if (leftTask.depth < PARALLEL_DEPTH) {
                boost::asio::post(pool, [=, &codesFoundMutex, &codesFound, &pool, &inflight] {
                    // Run search for the left direction
                    search(leftTask, min, max, codesFoundMutex, codesFound, pool, inflight);
                });
            } else {
                iterateFireAwayCS2(min, max, leftTask.specMin, leftTask.specMax, leftTask.sideSum, leftTask.cbilliard, leftTask.code, codesFound, codesFoundMutex, leftTask.depth, leftSwap, true, pool, inflight);
            }
        }

        // Submit a task to explore the right direction
        if(task.specMax > task.cbilliard.vertexC.x){
            int32_t rightSwap = 3 - task.cbilliard.side;
            TriangleBilliard newbilliard = task.cbilliard.getNext(false);
            rightSwap = rightSwap - newbilliard.side;

            SideSum rightSideSum = task.sideSum;
            std::vector<int32_t> rightCode = task.code;
            rightSideSum.sub(rightSwap);
            rightCode.emplace_back(rightSwap);
            SearchTask rightTask{
                std::max(task.cbilliard.vertexC.x, task.specMin),
                task.specMax,
                newbilliard,
                task.depth + 1,
                rightSideSum,
                rightCode
            };

            if (rightTask.depth < PARALLEL_DEPTH) {
                boost::asio::post(pool, [=, &codesFoundMutex, &codesFound, &pool, &inflight] {
                    // Run search for the right direction
                    search(rightTask, min, max, codesFoundMutex, codesFound, pool, inflight);
                });
            } else {
                iterateFireAwayCS2(min, max, rightTask.specMin, rightTask.specMax, rightTask.sideSum, rightTask.cbilliard, rightTask.code, codesFound, codesFoundMutex, rightTask.depth, rightSwap, false, pool, inflight);
            }
        }

    }

    void parallelFireAwayCS(int32_t min, int32_t max, float64_t specMin, float64_t specMax, SideSum& sideSum, TriangleBilliard billiard,
        std::vector<int32_t>& code, std::vector<std::vector<int32_t>>& codesFound ) 
    {

        std::mutex codesFoundMutex;
        const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
        unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env)) : std::thread::hardware_concurrency();
        std::atomic<int> inflight{0};
        try{
            boost::asio::thread_pool pool(cores); 
            SearchTask initialTask{specMin, specMax, billiard, 0, sideSum, code};
            search(
                initialTask,
                min,
                max,
                codesFoundMutex,
                codesFound,
                pool,
                inflight
            );
            pool.join();
        } catch (const std::exception& ex) {
            std::cerr << "Exception caught: " << ex.what() << '\n';
        }
        }

std::vector<std::vector<int32_t>> fireAwayCS(const int32_t movesMin, const int32_t movesMax,
		const float64_t xAngle, const float64_t yAngle, const std::string reqType) {

    cancel_flag().store(false,  std::memory_order_relaxed); 
	std::vector<std::vector<int32_t>> foundCodes;
	TriangleBilliard billiard = TriangleBilliard::create(xAngle, yAngle, 0);
	SideSum sideSum = SideSum::create(xAngle, yAngle);
	std::vector<int32_t> code;

    // WARNING: This is parallel for testing purposes
    parallelFireAwayCS(movesMin/2, movesMax/2, 0, billiard.vertexB.x, sideSum, billiard, code, foundCodes);

	return foundCodes;
}

std::vector<std::vector<int32_t>> fireAwayParallelCS(const int32_t movesMin, const int32_t movesMax,
        const float64_t xAngle, const float64_t yAngle) {

    cancel_flag().store(false,  std::memory_order_relaxed); 
    std::vector<std::vector<int32_t>> foundCodes;
    TriangleBilliard billiard = TriangleBilliard::create(xAngle, yAngle, 0);
    SideSum sideSum = SideSum::create(xAngle, yAngle);
    std::vector<int32_t> code;

    parallelFireAwayCS(movesMin/2, movesMax/2, 0, billiard.vertexB.x, sideSum, billiard, code, foundCodes);

    return foundCodes;
}
