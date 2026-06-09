#include "vary3.hpp"

const float64_t OFFSET = 0.000005;

/*  TODO: 
	There is a lot of repetition between the sequentialSearch and the iterateFireAway, 
	we should unify these changes to make future modifications to vary3 easier	
*/

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

struct SearchTask {
    float64_t specMin;
    float64_t specMax;
    TriangleBilliard cbilliard;

    int32_t depth;
    SideSum sideSum;
    std::vector<int32_t> code;
};
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

void cancelSearch(boost::asio::thread_pool& pool) {
    std::cout << "C++ Vary3 Canceling" << std::endl;
    pool.stop();
    std::cout << "Canceled" << std::endl;
}

void sequentialSearch(int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
                      SideSum& sideSum, TriangleBilliard billiard, std::vector<int32_t>& code, std::mutex& codesFoundMutex,
                      std::vector<std::vector<int32_t>>& codesFound, std::vector<CodeType> allowed, int depth, boost::asio::thread_pool& pool) {
    std::vector<Frame> stack;
    stack.push_back(Frame{specMin, specMax, 0, billiard, false, false, false});

    // parallel code verify limit
    std::atomic<int> inflight{0};

    // setting limit for submition to the memory
    float usage = 0.5;
    if (max > 30000) {
        usage = 0.05;
    } else if (max > 25000) {
        usage = 0.1;
    } else if (max > 15000) {
        usage = 0.2;
    } else if (max > 10000) {
        usage = 0.3;
    } else if (max > 6000) {
        usage = 0.4;
    };
    const int MAX_INFLIGHT = compute_max_inflight(usage, 16384);

    while (!stack.empty()) {
        // Check if the algorithm has been cancelled
        if (cancel_flag().load(std::memory_order_relaxed)) {
            cancelSearch(pool);
            return;
        }

        Frame& frame = stack.back();

        if (depth >= max) {
            if (!code.empty()) { // only pop if there is something to pop
                code.pop_back();
            }
            depth--;
            frame.goLeft ? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
            stack.pop_back();
            continue;
        }

        float64_t specialAngle = frame.cbilliard.getSpecialAngle();

        if (!frame.leftTried && !frame.rightTried) {

            if (depth > min) {
                if (std::abs(sideSum.sum()) < OFFSET && frame.cbilliard.side == 2 &&
                    frame.cbilliard.orient == 1) {

                    float64_t perfectAngle = std::atan2(
                        frame.cbilliard.vertexA.y,
                        frame.cbilliard.vertexA.x + initPosition);

                    if (frame.specMax > perfectAngle && perfectAngle > frame.specMin) {

                        std::vector<int32_t> code2 = code;

                        while (inflight >= MAX_INFLIGHT) {
                            std::this_thread::sleep_for(std::chrono::microseconds(100));
                        }
                        // type check if its is the right candidate, add it in the code
                        inflight.fetch_add(1, std::memory_order_relaxed);
                        boost::asio::post(pool, [=, &codesFound, &inflight, &codesFoundMutex] {
                            std::vector<int32_t> intVec(code2.begin(), code2.end());
                            boost::optional<CodeType> codeType = getCodeType(intVec);
                            if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                                std::lock_guard<std::mutex> lock(codesFoundMutex);
                                codesFound.push_back(code2);
                            }
                            inflight.fetch_sub(1, std::memory_order_relaxed);
                        });
                    }
                }
            }

            frame.leftTried = true;

            if (frame.specMax > specialAngle) {
                TriangleBilliard newbilliard = frame.cbilliard.getNext(true);
                int32_t rightSwap = 3 - frame.cbilliard.side - newbilliard.side;

                sideSum.add(rightSwap);
                code.emplace_back(rightSwap);
                stack.push_back(Frame{
                    std::max(specialAngle, frame.specMin), frame.specMax,
                    rightSwap, newbilliard,
                    false, false, true});
                depth++;
                continue;
            }
        }

        if (!frame.rightTried) {
            frame.rightTried = true;

            if (frame.specMin < specialAngle) {
                TriangleBilliard newbilliard = frame.cbilliard.getNext(false);
                int32_t leftSwap = 3 - frame.cbilliard.side - newbilliard.side;
                ;

                sideSum.sub(leftSwap);
                code.emplace_back(leftSwap);
                stack.push_back(Frame{
                    frame.specMin, std::min(specialAngle, frame.specMax),
                    leftSwap, newbilliard,
                    false, false, false});
                depth++;
                continue;
            }
        }
        // Both directions done — backtrack
        if (!code.empty())
            code.pop_back(); // safeguard
        depth--;
        frame.goLeft ? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
        // billiard.getNextReverse(frame.goLeft);  // reverse the correct direction

        stack.pop_back();
    }
}

void search(
    SearchTask task,
    int32_t min,
    int32_t max,
    float64_t initPosition,
    std::vector<CodeType> allowed,
    std::mutex& codesFoundMutex,
    std::vector<std::vector<int32_t>>& codesFound,
    boost::asio::thread_pool& pool) {
    // Check if the algorithm has been cancelled
    if (cancel_flag().load(std::memory_order_relaxed)) {
        cancelSearch(pool);
        return;
    }

    // Check if we've reached the maximum depth
    if (task.depth >= max)
        return;

    float64_t specialAngle = task.cbilliard.getSpecialAngle();
    const int32_t PARALLEL_DEPTH = static_cast<int>(0.95 * max);

    // Check if we have a potential candidate for a code sequence
    if (task.depth > min && std::abs(task.sideSum.sum()) < OFFSET && task.cbilliard.side == 2 && task.cbilliard.orient == 1) {

        float64_t perfectAngle = std::atan2(
            task.cbilliard.vertexA.y,
            task.cbilliard.vertexA.x + initPosition);

        if (task.specMax > perfectAngle && perfectAngle > task.specMin) {

            std::vector<int32_t> code = task.code;

            // type check if its is the right candidate, add it in the code
            std::vector<int32_t> intVec(code.begin(), code.end());
            boost::optional<CodeType> codeType = getCodeType(intVec);
            if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                std::lock_guard<std::mutex> lock(codesFoundMutex);
                codesFound.push_back(code);
            }
        }
    }

    // submit a task to go left
    if (task.specMax > specialAngle) {
        TriangleBilliard newbilliard = task.cbilliard.getNext(true);
        int32_t rightSwap = 3 - task.cbilliard.side - newbilliard.side;
        SideSum leftSideSum = task.sideSum;
        std::vector<int32_t> leftCode = task.code;

        leftSideSum.add(rightSwap);
        leftCode.emplace_back(rightSwap);

        SearchTask leftTask{
            std::max(specialAngle, task.specMin),
            task.specMax,
            newbilliard,
            task.depth + 1,
            leftSideSum,
            leftCode};
        if (task.depth < PARALLEL_DEPTH) {
            boost::asio::post(pool, [=, &codesFoundMutex, &codesFound, &pool] {
                // Run search for the left direction
                search(leftTask, min, max, initPosition, allowed, codesFoundMutex, codesFound, pool);
            });
        } else {
            sequentialSearch(min, max, leftTask.specMin, leftTask.specMax, initPosition, leftTask.sideSum, leftTask.cbilliard, leftTask.code, codesFoundMutex, codesFound, allowed, leftTask.depth, pool);
        }
    }
    // And then submit a task to go right
    if (task.specMin < specialAngle) {
        TriangleBilliard newbilliard = task.cbilliard.getNext(false);
        int32_t leftSwap = 3 - task.cbilliard.side - newbilliard.side;
        ;
        SideSum rightSideSum = task.sideSum;
        std::vector<int32_t> rightCode = task.code;

        rightSideSum.sub(leftSwap);
        rightCode.emplace_back(leftSwap);

        SearchTask rightTask{
            task.specMin,
            std::min(specialAngle, task.specMax),
            newbilliard,
            task.depth + 1,
            rightSideSum,
            rightCode};
        if (task.depth < PARALLEL_DEPTH) {
            boost::asio::post(pool, [=, &codesFoundMutex, &codesFound, &pool] {
                // Run search for the right direction
                search(rightTask, min, max, initPosition, allowed, codesFoundMutex, codesFound, pool);
            });
        } else {
            sequentialSearch(min, max, rightTask.specMin, rightTask.specMax, initPosition, rightTask.sideSum, rightTask.cbilliard, rightTask.code, codesFoundMutex, codesFound, allowed, rightTask.depth, pool);
        }
    }
}

void parallelFireAway3(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::string reqType) {
    std::vector<CodeType> allowed = parse_code_types(reqType, stringToCodeType);
    std::mutex codesFoundMutex;
    const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
    unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env)) : std::thread::hardware_concurrency();

    try {
        boost::asio::thread_pool pool(cores);
        SearchTask initialTask{specMin, specMax, billiard, 0, sideSum, code};
        boost::asio::post(pool, [=, &codesFoundMutex, &codesFound, &pool] {
            search(initialTask, min, max, initPosition, allowed, codesFoundMutex, codesFound, pool);
        });
        pool.join();
    } catch (const std::exception& ex) {
        std::cerr << "Exception caught: " << ex.what() << '\n';
    }
}

void iterateFireAway3(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::string reqType) {
    cancel_flag().store(false, std::memory_order_relaxed);

    std::vector<Frame> stack;
    int32_t depth = 0;

    stack.push_back(Frame{specMin, specMax, 0, billiard, false, false, false});

    std::vector<CodeType> allowed = parse_code_types(reqType, stringToCodeType);

    // parallel code verify limit
    std::atomic<int> inflight{0};

    // setting limit for submition to the memory
    float usage = 0.5;
    if (max > 30000) {
        usage = 0.05;
    } else if (max > 25000) {
        usage = 0.1;
    } else if (max > 15000) {
        usage = 0.2;
    } else if (max > 10000) {
        usage = 0.3;
    } else if (max > 6000) {
        usage = 0.4;
    };
    const int MAX_INFLIGHT = compute_max_inflight(usage, 16384);
    unsigned int cores = std::thread::hardware_concurrency();
    std::mutex codesFoundMutex;

    try {
        boost::asio::thread_pool pool(cores);

        while (!stack.empty()) {
            // Check if the algorithm has been cancelled
            if (cancel_flag().load(std::memory_order_relaxed)) {
                std::cout << "C++ Vary3 Canceling" << std::endl;
                pool.stop();
                pool.join();
                std::cout << "Canceled" << std::endl;
                return;
            }

            Frame& frame = stack.back();

            if (depth >= max) {
                if (!code.empty()) { // only pop if there is something to pop
                    code.pop_back();
                }
                depth--;
                frame.goLeft ? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
                stack.pop_back();
                continue;
            }

            float64_t specialAngle = frame.cbilliard.getSpecialAngle();

            if (!frame.leftTried && !frame.rightTried) {

                if (depth > min) {
                    if (std::abs(sideSum.sum()) < OFFSET && frame.cbilliard.side == 2 &&
                        frame.cbilliard.orient == 1) {

                        float64_t perfectAngle = std::atan2(
                            frame.cbilliard.vertexA.y,
                            frame.cbilliard.vertexA.x + initPosition);

                        if (frame.specMax > perfectAngle && perfectAngle > frame.specMin) {

                            std::vector<int32_t> code2 = code;

                            while (inflight >= MAX_INFLIGHT) {
                                std::this_thread::sleep_for(std::chrono::microseconds(100));
                            }
                            // type check if its is the right candidate, add it in the code
                            inflight.fetch_add(1, std::memory_order_relaxed);
                            boost::asio::post(pool, [=, &codesFound, &inflight, &codesFoundMutex] {
                                std::vector<int32_t> intVec(code2.begin(), code2.end());
                                boost::optional<CodeType> codeType = getCodeType(intVec);
                                if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                                    std::lock_guard<std::mutex> lock(codesFoundMutex);
                                    codesFound.push_back(code2);
                                }
                                inflight.fetch_sub(1, std::memory_order_relaxed);
                            });
                        }
                    }
                }

                frame.leftTried = true;

                if (frame.specMax > specialAngle) {
                    TriangleBilliard newbilliard = frame.cbilliard.getNext(true);
                    int32_t rightSwap = 3 - frame.cbilliard.side - newbilliard.side;

                    sideSum.add(rightSwap);
                    code.emplace_back(rightSwap);
                    stack.push_back(Frame{
                        std::max(specialAngle, frame.specMin), frame.specMax,
                        rightSwap, newbilliard,
                        false, false, true});
                    depth++;
                    continue;
                }
            }

            if (!frame.rightTried) {
                frame.rightTried = true;

                if (frame.specMin < specialAngle) {
                    TriangleBilliard newbilliard = frame.cbilliard.getNext(false);
                    int32_t leftSwap = 3 - frame.cbilliard.side - newbilliard.side;
                    ;

                    sideSum.sub(leftSwap);
                    code.emplace_back(leftSwap);
                    stack.push_back(Frame{
                        frame.specMin, std::min(specialAngle, frame.specMax),
                        leftSwap, newbilliard,
                        false, false, false});
                    depth++;
                    continue;
                }
            }
            // Both directions done — backtrack
            if (!code.empty())
                code.pop_back(); // safeguard
            depth--;
            frame.goLeft ? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
            // billiard.getNextReverse(frame.goLeft);  // reverse the correct direction
            stack.pop_back();
        }
    
        pool.join();
        
    } catch (const std::exception& ex) {
        std::cerr << "Exception caught: " << ex.what() << '\n';
    }
}


std::vector<std::vector<int32_t>> fireAway3(const int32_t movesMin, const int32_t movesMax,
                                            const float64_t xAngle, const float64_t yAngle, const float64_t pos, const std::string reqType) {

    std::vector<std::vector<int32_t>> foundCodes;
    TriangleBilliard billiard = TriangleBilliard::create(xAngle, yAngle, pos);
    SideSum sideSum = SideSum::create(xAngle, yAngle);
    std::vector<int32_t> code;

    // high_prec_t pi_hp = boost::math::constants::pi<high_prec_t>();
    // float64_t pi_f64 = static_cast<float64_t>(pi_hp);
    float64_t pi = boost::math::constants::pi<double>();

    // float64_t pi_f64 = 3.14159265358979323846;

    // WARNING: Temporary Change- find out how to expose more wrapper functions
    iterateFireAway3(movesMin, movesMax, 0, pi, pos, sideSum, billiard, code, foundCodes, reqType);

    return foundCodes;
}

std::vector<std::vector<int32_t>> fireAway3Parallel(const int32_t movesMin, const int32_t movesMax,
                                                    const float64_t xAngle, const float64_t yAngle, const float64_t pos, const std::string reqType) {

    std::vector<std::vector<int32_t>> foundCodes;
    TriangleBilliard billiard = TriangleBilliard::create(xAngle, yAngle, pos);
    SideSum sideSum = SideSum::create(xAngle, yAngle);
    std::vector<int32_t> code;

    float64_t pi = boost::math::constants::pi<double>();

    parallelFireAway3(movesMin, movesMax, 0, pi, pos, sideSum, billiard, code, foundCodes, reqType);

    return foundCodes;
}

