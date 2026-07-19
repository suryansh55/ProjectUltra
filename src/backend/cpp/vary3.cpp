#include "vary3.hpp"

#include <atomic>
#include <chrono>
#include <deque>
#include <memory>
#include <mutex>

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


void iterateFireAway3(
    int32_t min, int32_t max, float64_t specMin, float64_t specMax, float64_t initPosition,
    SideSum& sideSum, TriangleBilliard billiard,
    std::vector<int32_t>& code,
    std::vector<std::vector<int32_t>>& codesFound, std::string reqType)
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
    int32_t depth = 0;

    stack.push_back(Frame{specMin, specMax, 0, billiard, false, false, false});

	    std::vector<CodeType> allowed = parse_code_types(reqType,stringToCodeType);

	// parallel code verify limit
	std::atomic<int> inflight{0};

	// setting limit for submition to the memory
	const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
    unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env)) : std::thread::hardware_concurrency();
	// Suryansh Ankur, 2026
	// Each queued task captures a code vector copy (max * 4 bytes).
	// Cap at cores*8 to prevent OOM from thousands of queued lambda closures.
	const int MAX_INFLIGHT = std::max(4, (int)cores) * 8;
    std::mutex codesFoundMutex;

	try{
			boost::asio::thread_pool pool(cores); 
			while (!stack.empty()) {
                if (cancel_flag().load(std::memory_order_relaxed)) {
                    std::cout << "C++ Vary3 Canceling" << std::endl;
					pool.stop();
                    pool.join();
					std::cout << "Canceled" << std::endl;
                    return ;}


				Frame& frame = stack.back();

				if (depth >= max) {
					if (!code.empty()) {   // only pop if there is something to pop
						code.pop_back();
					}
					depth--;
					frame.goLeft? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
					stack.pop_back();
					continue;
				}

				float64_t specialAngle = frame.cbilliard.getSpecialAngle();

				if (!frame.leftTried && !frame.rightTried ) {

					if (depth > min) {
						if (std::abs(sideSum.sum()) < OFFSET && frame.cbilliard.side == 2 &&
								frame.cbilliard.orient == 1) {
							
								float64_t perfectAngle = std::atan2(
									frame.cbilliard.vertexA.y,
									frame.cbilliard.vertexA.x + initPosition);

								if (frame.specMax > perfectAngle && perfectAngle > frame.specMin) {

									std::vector<int32_t> code2 = code;

									while (inflight >= MAX_INFLIGHT) {
										// Suryansh Ankur, 2026
										if (cancel_flag().load(std::memory_order_relaxed)) break;
										std::this_thread::sleep_for(std::chrono::milliseconds(1));
									}
									// type check if its is the right candidate, add it in the code
									inflight.fetch_add(1, std::memory_order_relaxed);
									boost::asio::post(pool, [=, &codesFound, &inflight, &codesFoundMutex] {
										std::vector<int32_t> intVec(code2.begin(),code2.end());
										boost::optional<CodeType> codeType = getCodeType(intVec);
										if (codeType && is_code_type_in_list(codeType.get(),allowed)) {
										std::lock_guard<std::mutex> lock(codesFoundMutex);
										codesFound.push_back(code2);
									}




										inflight.fetch_sub(1, std::memory_order_relaxed);
									});
							}
						}
					}


					frame.leftTried = true;

					if (frame.specMax > specialAngle){
						TriangleBilliard newbilliard = frame.cbilliard.getNext(true);
						int32_t rightSwap = 3 - frame.cbilliard.side - newbilliard.side;
											
						sideSum.add(rightSwap);
						code.emplace_back(rightSwap);
						stack.push_back(Frame{
							std::max(specialAngle, frame.specMin), frame.specMax,
							rightSwap, newbilliard,
							false, false, true
						});
						depth++;
						continue;
					}
				}

				if (!frame.rightTried ) {
					frame.rightTried = true;

					if (frame.specMin < specialAngle){
						TriangleBilliard newbilliard = frame.cbilliard.getNext(false);
						int32_t leftSwap = 3 - frame.cbilliard.side - newbilliard.side;;

						sideSum.sub(leftSwap);
						code.emplace_back(leftSwap);
						stack.push_back(Frame{
							frame.specMin, std::min(specialAngle, frame.specMax),
							leftSwap, newbilliard,
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
				// billiard.getNextReverse(frame.goLeft);  // reverse the correct direction
				stack.pop_back();
				
		}
			pool.join();

     
	}catch (const std::exception& ex){
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


// ---------------------------------------------------------------------------
// BFS-frontier / subtree-split parallelization — Suryansh Ankur, 2026.
//
// iterateFireAway3 above walks one shot's whole tree on one thread (its pool
// only verifies emitted candidates), so a multi-shot run can never use more
// than ~shots cores. The functions below split every shot's tree into many
// independent subtrees and run them all on ONE shared pool:
//
//   phase A (sequential, cheap): expand each shot's tree breadth-first until
//     ~frontierTarget pending nodes exist. Nodes expanded here are
//     emission-checked here; nodes left in the frontier are not.
//   phase B (parallel): each frontier node becomes a DFS task; the task
//     emission-checks its root node and everything below it.
//
// Every tree node is emission-checked exactly once (the check only reads
// node-local state — depth, spec window, side sum, billiard, path — so the
// partition cannot change the result set). Candidate verification
// (getCodeType) runs inline in the task instead of being posted to a pool:
// the traversal itself is parallel here, so offloading buys nothing, and it
// removes the MAX_INFLIGHT queue entirely.

namespace {

// A self-contained node of the Vary3 search tree: everything the DFS needs to
// continue from this point, so a node can be handed to any thread.
struct SearchNode {
	int32_t depth;
	float64_t specMin;
	float64_t specMax;
	SideSum sideSum;
	TriangleBilliard billiard;
	std::vector<int32_t> code;
};

// Same candidate check as in iterateFireAway3, for one node.
void emitIfCandidate(const SearchNode& node, const int32_t min, const float64_t initPosition,
		const std::vector<CodeType>& allowed,
		std::vector<std::vector<int32_t>>& found)
{
	if (node.depth <= min) return;
	SideSum sideSum = node.sideSum;             // sum() is non-const
	TriangleBilliard billiard = node.billiard;  // vertex access below is const-safe, copy for symmetry
	if (std::abs(sideSum.sum()) < OFFSET && billiard.side == 2 && billiard.orient == 1) {
		const float64_t perfectAngle = std::atan2(
			billiard.vertexA.y, billiard.vertexA.x + initPosition);
		if (node.specMax > perfectAngle && perfectAngle > node.specMin) {
			std::vector<int32_t> code2 = node.code;
			boost::optional<CodeType> codeType = getCodeType(code2);
			if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
				found.push_back(node.code);
			}
		}
	}
}

// Children of a node, same branch conditions and spec windows as
// iterateFireAway3.
void appendChildren(const SearchNode& node, std::deque<SearchNode>& out)
{
	TriangleBilliard billiard = node.billiard;  // getSpecialAngle()/getNext() are non-const
	const float64_t specialAngle = billiard.getSpecialAngle();

	if (node.specMax > specialAngle) {
		TriangleBilliard next = billiard.getNext(true);
		const int32_t swap = 3 - billiard.side - next.side;
		SideSum sideSum = node.sideSum;
		sideSum.add(swap);
		std::vector<int32_t> code = node.code;
		code.emplace_back(swap);
		out.push_back(SearchNode{node.depth + 1,
			std::max(specialAngle, node.specMin), node.specMax,
			sideSum, next, std::move(code)});
	}
	if (node.specMin < specialAngle) {
		TriangleBilliard next = billiard.getNext(false);
		const int32_t swap = 3 - billiard.side - next.side;
		SideSum sideSum = node.sideSum;
		sideSum.sub(swap);
		std::vector<int32_t> code = node.code;
		code.emplace_back(swap);
		out.push_back(SearchNode{node.depth + 1,
			node.specMin, std::min(specialAngle, node.specMax),
			sideSum, next, std::move(code)});
	}
}

// Depth-first traversal of the subtree rooted at `start`, structured exactly
// like iterateFireAway3 (one shared code vector + side sum, one small Frame
// per level) so memory stays O(depth), not O(depth^2). Emission-checks the
// root node itself and every node below it.
void dfsSubtree(SearchNode start, const int32_t min, const int32_t max,
		const float64_t initPosition, const std::vector<CodeType>& allowed,
		std::vector<std::vector<int32_t>>& found)
{
	struct Frame {
		float64_t specMin;
		float64_t specMax;
		int32_t swapValue;
		TriangleBilliard cbilliard;
		bool leftTried = false;
		bool rightTried = false;
		bool goLeft = false;
	};

	if (start.depth >= max) return;

	SideSum sideSum = start.sideSum;
	std::vector<int32_t>& code = start.code;  // full path from the tree root; grows/shrinks in place
	const size_t prefixLen = code.size();
	int32_t depth = start.depth;

	std::vector<Frame> stack;
	stack.push_back(Frame{start.specMin, start.specMax, 0, start.billiard, false, false, false});

	while (!stack.empty()) {
		if (cancel_flag().load(std::memory_order_relaxed)) return;

		Frame& frame = stack.back();

		if (depth >= max) {
			if (code.size() > prefixLen) code.pop_back();
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
						boost::optional<CodeType> codeType = getCodeType(code2);
						if (codeType && is_code_type_in_list(codeType.get(), allowed)) {
							found.push_back(code);
						}
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
				sideSum.sub(leftSwap);
				code.emplace_back(leftSwap);
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
		if (code.size() > prefixLen) code.pop_back();
		depth--;
		frame.goLeft ? sideSum.sub(frame.swapValue) : sideSum.add(frame.swapValue);
		stack.pop_back();
	}
}

// Breadth-first expansion of one shot's tree until ~`target` pending subtrees
// exist (or the whole tree has been walked). `depthCap` bounds how deep the
// BFS may go down a narrow line: each pending node carries its full code
// prefix, so unbounded depth would mean unbounded frontier memory.
void expandFrontier(SearchNode root, const int32_t min, const int32_t max,
		const float64_t initPosition, const std::vector<CodeType>& allowed,
		const size_t target, const int32_t depthCap,
		std::vector<SearchNode>& frontier,
		std::vector<std::vector<int32_t>>& found)
{
	std::deque<SearchNode> queue;
	queue.push_back(std::move(root));

	while (!queue.empty() && queue.size() + frontier.size() < target) {
		if (cancel_flag().load(std::memory_order_relaxed)) return;
		SearchNode node = std::move(queue.front());
		queue.pop_front();
		if (node.depth >= max) continue;  // never emitted, matches the DFS
		if (node.depth >= depthCap) {
			// Narrow line that has not branched out: stop growing it here and
			// let a DFS task take it. Not emission-checked here — the task
			// checks its own root.
			frontier.push_back(std::move(node));
			continue;
		}
		emitIfCandidate(node, min, initPosition, allowed, found);
		appendChildren(node, queue);
	}

	for (auto& node : queue) frontier.push_back(std::move(node));
}

}  // namespace


std::vector<std::vector<std::vector<int32_t>>> fireAway3Parallel(
		const int32_t movesMin, const int32_t movesMax,
		const float64_t xAngle, const float64_t yAngle,
		const std::vector<float64_t>& positions,
		const std::string& reqType)
{
	const float64_t pi = boost::math::constants::pi<double>();
	const std::vector<CodeType> allowed = parse_code_types(reqType, stringToCodeType);

	const char* cpu_env = std::getenv("SLURM_CPUS_PER_TASK");
	const unsigned int cores = cpu_env ? static_cast<unsigned int>(std::stoi(cpu_env))
	                                   : std::thread::hardware_concurrency();
	const unsigned int numThreads = std::max(1u, cores);
	// Many subtrees per core so the pool can load-balance the heavily skewed
	// subtree sizes; the depth cap bounds phase-A time and frontier memory.
	// Both are overridable for cluster tuning.
	const char* target_env = std::getenv("BILLIARDS_FRONTIER_TARGET");
	const size_t frontierTarget = target_env
		? std::max<size_t>(1, static_cast<size_t>(std::stoi(target_env)))
		: std::max<size_t>(64, static_cast<size_t>(numThreads) * 64);
	const char* cap_env = std::getenv("BILLIARDS_FRONTIER_DEPTH_CAP");
	const int32_t frontierDepthCap = std::min(movesMax,
		cap_env ? std::max(1, std::stoi(cap_env)) : 4096);

	const size_t shots = positions.size();
	std::vector<std::vector<std::vector<int32_t>>> perShot(shots);

	struct SubtreeTask {
		size_t shot;
		SearchNode node;
	};
	std::vector<SubtreeTask> tasks;

	// Phase A: expand every shot's frontier (sequential; tiny next to phase B).
	for (size_t s = 0; s < shots; ++s) {
		if (cancel_flag().load(std::memory_order_relaxed)) return perShot;
		SearchNode root{0, 0.0, pi,
			SideSum::create(xAngle, yAngle),
			TriangleBilliard::create(xAngle, yAngle, positions[s]),
			std::vector<int32_t>{}};
		std::vector<SearchNode> frontier;
		expandFrontier(std::move(root), movesMin, movesMax, positions[s], allowed,
			frontierTarget, frontierDepthCap, frontier, perShot[s]);
		tasks.reserve(tasks.size() + frontier.size());
		for (auto& node : frontier) {
			tasks.push_back(SubtreeTask{s, std::move(node)});
		}
	}

	std::cout << "// vary3 parallel dfs: " << tasks.size() << " subtrees across "
	          << shots << " shots on " << numThreads << " threads" << std::endl;

	// Phase B: one shared pool over every subtree of every shot.
	std::mutex resultsMutex;
	std::unique_ptr<std::atomic<size_t>[]> remaining(new std::atomic<size_t>[shots]);
	std::unique_ptr<std::atomic<long long>[]> busyMs(new std::atomic<long long>[shots]);
	for (size_t s = 0; s < shots; ++s) {
		remaining[s].store(0);
		busyMs[s].store(0);
	}
	for (const auto& task : tasks) {
		remaining[task.shot].fetch_add(1, std::memory_order_relaxed);
	}

	try {
		boost::asio::thread_pool pool(numThreads);
		for (SubtreeTask& task : tasks) {  // tasks is not resized past this point
			boost::asio::post(pool, [&task, &positions, &allowed, &perShot,
					&resultsMutex, &remaining, &busyMs,
					movesMin, movesMax, shots] {
				const auto taskStart = std::chrono::steady_clock::now();
				const size_t shot = task.shot;

				std::vector<std::vector<int32_t>> local;
				if (!cancel_flag().load(std::memory_order_relaxed)) {
					dfsSubtree(std::move(task.node), movesMin, movesMax,
						positions[shot], allowed, local);
				}

				const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
					std::chrono::steady_clock::now() - taskStart).count();
				busyMs[shot].fetch_add(ms, std::memory_order_relaxed);

				size_t shotCodes = 0;
				{
					std::lock_guard<std::mutex> lock(resultsMutex);
					std::vector<std::vector<int32_t>>& out = perShot[shot];
					out.insert(out.end(),
						std::make_move_iterator(local.begin()),
						std::make_move_iterator(local.end()));
					shotCodes = out.size();
				}

				if (remaining[shot].fetch_sub(1, std::memory_order_acq_rel) == 1) {
					std::ostringstream msg;
					msg << "// shot " << (shot + 1) << "/" << shots << " subtrees done, "
					    << shotCodes << " raw codes, "
					    << (busyMs[shot].load(std::memory_order_relaxed) / 1000.0)
					    << "s cpu\n";
					std::cout << msg.str() << std::flush;
				}
			});
		}
		pool.join();
	} catch (const std::exception& ex) {
		std::cerr << "Exception caught: " << ex.what() << '\n';
	}

	return perShot;
}
