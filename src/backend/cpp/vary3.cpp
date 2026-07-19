#include "vary3.hpp"

const float64_t OFFSET = 0.000005;



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


// iterateFireAway3 — BFS until the frontier is big enough to feed every core,
// then one DFS task per surviving branch, scheduled on a TBB work-stealing
// task_group instead of a fixed boost::asio::thread_pool.
//
// Phase 1 (BFS): expand every branch level-by-level, in lockstep, until any of:
//   - the frontier reaches targetFrontierSize (= cores * tasksPerCore), or
//   - depth reaches breadthDepth (a safety cap; <=0 means "no extra cap", just max), or
//   - the frontier empties out (no branches left).
//   Branching is not a clean binary tree — the specMin/specMax pruning
//   conditions mean a node can produce 0, 1, or 2 children — so the frontier
//   is grown until it's actually wide enough, rather than assuming a fixed
//   depth will produce that many branches.
//   The "code found" check that the original DFS ran on entry to each frame is
//   also run here, once per visited node, synchronously (no task spawned).
//
// Phase 2 (parallel DFS): each surviving frontier node becomes one tbb::task_group
//   task, running the same DFS loop as the original function against its own
//   local stack / sideSum / code. TBB's work-stealing scheduler balances these
//   across worker threads itself — a core that finishes a short branch steals
//   another queued branch task rather than sitting idle, so tasksPerCore (a
//   few tasks queued per core rather than exactly one) is what gives the
//   scheduler something to steal, not a manual balancing scheme.
//
// Assumes SideSum is copyable (has copy ctor, .add(int32_t), .sub(int32_t), .sum()).
// Everything else (TriangleBilliard, CodeType, parse_code_types, getCodeType,
// is_code_type_in_list, stringToCodeType, cancel_flag, OFFSET, float64_t) is
// taken from the existing codebase, unchanged.
//
// Build/link note: replaces the boost::asio thread-pool dependency with oneTBB
// (link -ltbb). No other boost::asio usage remains in this function.

#include <tbb/task_group.h>
#include <tbb/task_arena.h>
#include <tbb/global_control.h>

void iterateFireAway3(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::string reqType
    ) 
{
    std::vector<CodeType> allowed = parse_code_types(reqType, stringToCodeType);

    // setting limit for submission to the memory
    const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
    unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env)) : std::thread::hardware_concurrency();
    if (cores == 0) cores = 1;
    
    // More aggressive CPU usage for HPC clusters
    if (cores > 16) {
        cores = std::min(cores, static_cast<unsigned int>(std::thread::hardware_concurrency()));
    }

    std::mutex codesFoundMutex;

    // Never try to BFS deeper than the traversal itself goes.
    // breadthDepth = (breadthDepth <= 0) ? max : std::min(breadthDepth, max);
    int32_t breadthDepth = 16;
    int32_t tasksPerCore = 8;  // Increased from 4 to better utilize multiple cores

    // Stop expanding once the frontier has enough independent branches to give
    // every core a few tasks to steal from — not just one each, since branch
    // depth/duration is uneven and a 1:1 mapping would stall the pool on
    // whichever branch happens to be slowest.
    const std::size_t targetFrontierSize = static_cast<std::size_t>(cores) * std::max(1, tasksPerCore);

    // ---- Per-branch state carried across the BFS frontier ----
    struct Node {
        float64_t specMin;
        float64_t specMax;
        TriangleBilliard cbilliard;
        SideSum sideSum;              // per-branch copy of accumulated side sum
        std::vector<int32_t> code;    // per-branch copy of the code path so far
        int32_t depth;
    };

    // Same "found code" check the original DFS ran on entering a frame,
    // reused for both the BFS phase and the per-branch DFS phase. Runs
    // synchronously — no task is spawned to do this, since it's cheap
    // relative to a whole branch's DFS.
    auto checkAndRecord = [&](const TriangleBilliard& cbilliard, SideSum& localSideSum,
                               const std::vector<int32_t>& localCode,
                               float64_t frameSpecMin, float64_t frameSpecMax, int32_t depth) {
        if (depth > min) {
            if (std::abs(localSideSum.sum()) < OFFSET &&
                cbilliard.side == 2 && cbilliard.orient == 1) {

                float64_t perfectAngle = std::atan2(
                    cbilliard.vertexA.y,
                    cbilliard.vertexA.x + initPosition);

                if (frameSpecMax > perfectAngle && perfectAngle > frameSpecMin) {
                    boost::optional<CodeType> codeType = getCodeType(localCode);
                    if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                        std::lock_guard<std::mutex> lock(codesFoundMutex);
                        codesFound.push_back(localCode);
                    }
                }
            }
        }
    };

    try {
        // Pin TBB's worker pool to `cores` threads for this call, matching the
        // SLURM/hardware_concurrency sizing the boost::asio version passed
        // explicitly to thread_pool(cores).
        tbb::global_control gc(tbb::global_control::max_allowed_parallelism, cores);
        tbb::task_arena arena(cores);
        tbb::task_group tg;

        arena.execute([&] {
            // ---------- Phase 1: BFS until wide enough ----------
            std::vector<Node> frontier;
            frontier.push_back(Node{specMin, specMax, billiard, sideSum, code, 0});

            int32_t depth = 0;
            while (depth < breadthDepth && !frontier.empty() && frontier.size() < targetFrontierSize) {
                if (cancel_flag().load(std::memory_order_relaxed)) {
                    std::cout << "C++ Vary3 Canceling" << std::endl;
                    tg.cancel();
                    tg.wait();
                    std::cout << "Canceled" << std::endl;
                    return;
                }

                std::vector<Node> nextFrontier;
                nextFrontier.reserve(frontier.size() * 2);

                for (auto& node : frontier) {
                    checkAndRecord(node.cbilliard, node.sideSum, node.code,
                                    node.specMin, node.specMax, node.depth);

                    float64_t specialAngle = node.cbilliard.getSpecialAngle();

                    // "right" child — mirrors the original leftTried branch (getNext(true))
                    if (node.specMax > specialAngle) {
                        TriangleBilliard newbilliard = node.cbilliard.getNext(true);
                        int32_t rightSwap = 3 - node.cbilliard.side - newbilliard.side;

                        Node child = node;              // copies sideSum + code
                        child.sideSum.add(rightSwap);
                        child.code.emplace_back(rightSwap);
                        child.specMin = std::max(specialAngle, node.specMin);
                        child.specMax = node.specMax;
                        child.cbilliard = newbilliard;
                        child.depth = node.depth + 1;

                        nextFrontier.push_back(std::move(child));
                    }

                    // "left" child — mirrors the original rightTried branch (getNext(false))
                    if (node.specMin < specialAngle) {
                        TriangleBilliard newbilliard = node.cbilliard.getNext(false);
                        int32_t leftSwap = 3 - node.cbilliard.side - newbilliard.side;

                        Node child = node;
                        child.sideSum.sub(leftSwap);
                        child.code.emplace_back(leftSwap);
                        child.specMin = node.specMin;
                        child.specMax = std::min(specialAngle, node.specMax);
                        child.cbilliard = newbilliard;
                        child.depth = node.depth + 1;

                        nextFrontier.push_back(std::move(child));
                    }
                }

                frontier = std::move(nextFrontier);
                depth++;
            }

            // ---------- Phase 2: one work-stealing task per surviving branch ----------
            // Optimize for HPC clusters by reducing task overhead and using more threads
            const size_t branchCount = frontier.size();
            if (branchCount > 0) {
                // Use all available cores for processing the frontier branches
                std::size_t actualCores = std::min(cores, static_cast<unsigned int>(branchCount));
                tbb::task_arena largeArena(actualCores);
                largeArena.execute([&] {
                    for (auto& node : frontier) {
                        tg.run([node, min, max, initPosition, &codesFound, &codesFoundMutex, &allowed]() {
                            if (cancel_flag().load(std::memory_order_relaxed)) return;

                            // Identical algorithm to the original DFS, scoped to this
                            // branch's own local stack / sideSum / code — no shared
                            // state with sibling branches on other worker threads.
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
                            stack.reserve(std::max(0, (max - node.depth)) * 2 + 1);
                            int32_t depth = node.depth;

                            SideSum localSideSum = node.sideSum;
                            std::vector<int32_t> localCode = node.code;

                            stack.push_back(Frame{node.specMin, node.specMax, 0, node.cbilliard, false, false, false});

                            while (!stack.empty()) {
                                if (cancel_flag().load(std::memory_order_relaxed)) return;

                                Frame& frame = stack.back();

                                if (depth >= max) {
                                    if (!localCode.empty()) localCode.pop_back();
                                    depth--;
                                    frame.goLeft ? localSideSum.sub(frame.swapValue) : localSideSum.add(frame.swapValue);
                                    stack.pop_back();
                                    continue;
                                }

                                float64_t specialAngle = frame.cbilliard.getSpecialAngle();

                                if (!frame.leftTried && !frame.rightTried) {
                                    if (depth > min) {
                                        if (std::abs(localSideSum.sum()) < OFFSET && frame.cbilliard.side == 2 &&
                                            frame.cbilliard.orient == 1) {

                                            float64_t perfectAngle = std::atan2(
                                                frame.cbilliard.vertexA.y,
                                                frame.cbilliard.vertexA.x + initPosition);

                                            if (frame.specMax > perfectAngle && perfectAngle > frame.specMin) {
                                                boost::optional<CodeType> codeType = getCodeType(localCode);
                                                if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                                                    std::lock_guard<std::mutex> lock(codesFoundMutex);
                                                    codesFound.push_back(localCode);
                                                }
                                            }
                                        }
                                    }

                                    frame.leftTried = true;

                                    if (frame.specMax > specialAngle) {
                                        TriangleBilliard newbilliard = frame.cbilliard.getNext(true);
                                        int32_t rightSwap = 3 - frame.cbilliard.side - newbilliard.side;

                                        localSideSum.add(rightSwap);
                                        localCode.emplace_back(rightSwap);
                                        stack.push_back(Frame{
                                            std::max(specialAngle, frame.specMin), frame.specMax,
                                            rightSwap, newbilliard,
                                            false, false, true
                                        });
                                        depth++;
                                        continue;
                                    }
                                }

                                if (!frame.rightTried) {
                                    frame.rightTried = true;

                                    if (frame.specMin < specialAngle) {
                                        TriangleBilliard newbilliard = frame.cbilliard.getNext(false);
                                        int32_t leftSwap = 3 - frame.cbilliard.side - newbilliard.side;

                                        localSideSum.sub(leftSwap);
                                        localCode.emplace_back(leftSwap);
                                        stack.push_back(Frame{
                                            frame.specMin, std::min(specialAngle, frame.specMax),
                                            leftSwap, newbilliard,
                                            false, false, false
                                        });
                                        depth++;
                                        continue;
                                    }
                                }

                                // Both directions done — backtrack
                                if (!localCode.empty()) localCode.pop_back();
                                depth--;
                                frame.goLeft ? localSideSum.sub(frame.swapValue) : localSideSum.add(frame.swapValue);
                                stack.pop_back();
                            }
                        });
                    }
                });
            }

            tg.wait();
        });

    } catch (const std::exception& ex) {
        std::cerr << "Exception caught: " << ex.what() << '\n';
    }
}


std::vector<std::vector<int32_t>> fireAway3(const int32_t movesMin, const int32_t movesMax,
		const float64_t xAngle, const float64_t yAngle,const float64_t pos,const std::string reqType) {

	std::vector<std::vector<int32_t>> foundCodes;
	TriangleBilliard billiard = TriangleBilliard::create(xAngle, yAngle, pos);
	SideSum sideSum = SideSum::create(xAngle, yAngle);
	std::vector<int32_t> code ;

	// high_prec_t pi_hp = boost::math::constants::pi<high_prec_t>();
	// float64_t pi_f64 = static_cast<float64_t>(pi_hp);
    float64_t pi = boost::math::constants::pi<double>();

	// float64_t pi_f64 = 3.14159265358979323846;

	
	iterateFireAway3(movesMin, movesMax, 0, pi, pos, sideSum, billiard, code, foundCodes, reqType);

	return foundCodes;


}
