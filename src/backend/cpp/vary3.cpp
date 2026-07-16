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
// then one DFS task per surviving branch.
//
// Phase 1 (BFS): expand every branch level-by-level, in lockstep, until any of:
//   - the frontier reaches targetFrontierSize (= cores * tasksPerCore), or
//   - depth reaches breadthDepth (a safety cap; <=0 means "no extra cap", just max), or
//   - the frontier empties out (no branches left).
//   Branching here is not a clean binary tree — the specMin/specMax pruning
//   conditions mean a node can produce 0, 1, or 2 children — so the frontier
//   is grown until it's actually wide enough, rather than assuming a depth
//   like ceil(log2(cores)) will produce that many branches.
//   The "code found" check that the original DFS ran on entry to each frame is
//   also run here, once per visited node, so no candidate codes are skipped.
//   tasksPerCore > 1 gives each core a few tasks instead of exactly one, so a
//   core that finishes an unusually shallow/short branch early can pick up
//   another queued task instead of sitting idle while a sibling core is still
//   working through a much deeper branch.
//
// Phase 2 (parallel DFS): each surviving frontier node is posted to the thread
//   pool as an independent task that runs the *same* DFS loop as the original
//   function, but scoped to its own local stack, sideSum, and code vector
//   (copied out of the Node so branches never touch each other's state).
//
// Assumes SideSum is copyable (has copy ctor, .add(int32_t), .sub(int32_t), .sum()).
// Everything else (TriangleBilliard, CodeType, parse_code_types, getCodeType,
// is_code_type_in_list, stringToCodeType, cancel_flag, OFFSET, float64_t) is
// taken from the existing codebase, unchanged.

void iterateFireAway3(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::string reqType) {
    std::vector<CodeType> allowed = parse_code_types(reqType, stringToCodeType);

    // parallel code verify limit
    std::atomic<int> inflight{0};

    // setting limit for submission to the memory
    const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
    unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env)) : std::thread::hardware_concurrency();
    // Suryansh Ankur, 2026
    // Each queued task captures a code vector copy (max * 4 bytes).
    // Cap at cores*8 to prevent OOM from thousands of queued lambda closures.
    const int MAX_INFLIGHT = std::max(4, (int)cores) * 8;
    std::mutex codesFoundMutex;

    // Never try to BFS deeper than the traversal itself goes.
    // breadthDepth <=0 means "no extra cap" — the frontier-size target below
    // (targetFrontierSize) is what actually decides when the BFS phase ends.
    int32_t breadthDepth = std::min(12, max);
    int32_t tasksPerCore = 4;

    // Stop expanding once the frontier has roughly enough independent branches
    // to keep every core busy with a few tasks each (tasksPerCore lets threads
    // that finish early steal / pick up more work rather than sitting idle —
    // pure "cores" branches would mean any imbalance in branch depth stalls
    // the whole pool on its slowest task).
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

    try {
        boost::asio::thread_pool pool(cores);

        // Same "found code" check the original DFS ran on entering a frame,
        // reused for both the BFS phase and the per-branch DFS phase.
        auto checkAndDispatch = [&](const TriangleBilliard& cbilliard, SideSum& localSideSum,
                                     const std::vector<int32_t>& localCode,
                                     float64_t frameSpecMin, float64_t frameSpecMax, int32_t depth) {
            if (depth > min) {
                if (std::abs(localSideSum.sum()) < OFFSET &&
                    cbilliard.side == 2 && cbilliard.orient == 1) {

                    float64_t perfectAngle = std::atan2(
                        cbilliard.vertexA.y,
                        cbilliard.vertexA.x + initPosition);

                    if (frameSpecMax > perfectAngle && perfectAngle > frameSpecMin) {
                        std::vector<int32_t> code2 = localCode;

                        while (inflight >= MAX_INFLIGHT) {
                            std::this_thread::sleep_for(std::chrono::microseconds(100));
                        }
                        inflight.fetch_add(1, std::memory_order_relaxed);
                        boost::asio::post(pool, [code2 = std::move(code2), &codesFound, &inflight,
                                                  &codesFoundMutex, &allowed] {
                            boost::optional<CodeType> codeType = getCodeType(code2);
                            if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
                                std::lock_guard<std::mutex> lock(codesFoundMutex);
                                codesFound.push_back(code2);
                            }
                            inflight.fetch_sub(1, std::memory_order_relaxed);
                        });
                    }
                }
            }
        };

        // ---------- Phase 1: BFS down to breadthDepth ----------
        std::vector<Node> frontier;
        frontier.push_back(Node{specMin, specMax, billiard, sideSum, code, 0});

        int32_t depth = 0;
        while (depth < breadthDepth && !frontier.empty() && frontier.size() < targetFrontierSize) {
            if (cancel_flag().load(std::memory_order_relaxed)) {
                std::cout << "C++ Vary3 Canceling" << std::endl;
                pool.stop();
                pool.join();
                std::cout << "Canceled" << std::endl;
                return;
            }

            std::vector<Node> nextFrontier;
            nextFrontier.reserve(frontier.size() * 2);

            for (auto& node : frontier) {
                checkAndDispatch(node.cbilliard, node.sideSum, node.code,
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

        std::cout << "// BFS phase complete: depth=" << depth << ", frontier size=" << frontier.size() << std::endl;

        // ---------- Phase 2: one DFS task per surviving frontier node ----------
        // Each task runs its candidate-code checks synchronously, in-line, on its
        // own pool thread — no sub-tasks posted back to the pool from here, so
        // there's no risk of long-running branch tasks starving small verify tasks
        // queued behind them. codesFoundMutex still serializes writes to codesFound
        // across branches.
        for (auto& node : frontier) {
            boost::asio::post(pool, [node, min, max, initPosition,
                                      &codesFound, &codesFoundMutex, &allowed]() mutable {

                // Identical algorithm to the original DFS, scoped to this
                // branch's own local stack / sideSum / code — no shared state
                // with sibling branches running in other pool threads.
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
                    if (cancel_flag().load(std::memory_order_relaxed)) {
                        return;
                    }

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
                                    // Checked synchronously, in-line, on this branch's own
                                    // pool thread — no sub-task posted, no inflight gate needed
                                    // here (that gate still applies to the BFS-phase dispatches
                                    // above, which do post to the pool).
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

        pool.join();

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
