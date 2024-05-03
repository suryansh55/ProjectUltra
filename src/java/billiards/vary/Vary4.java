package billiards.vary;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import com.google.common.collect.Lists;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.geometry.TriangleBilliard4;
import billiards.viewer.SideSum;
import billiards.viewer.Utils;
import javaslang.Tuple;
import javaslang.Tuple3;
import javaslang.collection.Array;

public class Vary4 {
private static final double OFFSET = 0.05;
    
	private static void recurseFireAway(
			final int min, final int max, final int depth, final SideSum sideSum, final TriangleBilliard4 billiard, 
			final MutableIntList code, final MutableList<ClassifiedCodeSequence> codesFound) {

		if (depth >= max) {
		    return;
		}
	
		if (depth > min) {
			// here we check if we have reached a periodic path
		    if (Math.abs(sideSum.sum()) < OFFSET && billiard.side == 2 && billiard.orient == 1) {

		        final double perfectAngle = FastMath.atan2(billiard.vertexA.y, billiard.vertexA.x);

		        if (billiard.between(perfectAngle)) {
					final Optional<ClassifiedCodeSequence> codeSeq = Utils.convert(code);
					
		            if (codeSeq.isPresent()) {

		            	codesFound.add(codeSeq.get());
		            }
		        }
		    }
		}
		
	    final Optional<TriangleBilliard4> optLeftBilliard = billiard.getNext(true);
		if (optLeftBilliard.isPresent()) {
		    // we are able to go left
			final TriangleBilliard4 leftBilliard = optLeftBilliard.get();
		    final int leftSwap = 3 - billiard.side - leftBilliard.side;
	
		    sideSum.add(leftSwap);
		    code.add(leftSwap);
	
		    recurseFireAway(min, max, depth + 1, sideSum, leftBilliard, code, codesFound);
	
		    code.removeAtIndex(code.size() - 1);
		    sideSum.sub(leftSwap);
		}
	
	    final Optional<TriangleBilliard4> optRightBilliard = billiard.getNext(false);
		if (optRightBilliard.isPresent()) {
		    // we are able to go right
		    final TriangleBilliard4 rightBilliard = optRightBilliard.get();
		    final int rightSwap = 3 - billiard.side - rightBilliard.side;
	
		    sideSum.sub(rightSwap);
		    code.add(rightSwap);
	
		    recurseFireAway(min, max, depth + 1, sideSum, rightBilliard, code, codesFound);
	
		    code.removeAtIndex(code.size() - 1);
		    sideSum.add(rightSwap);
		}
	}
	
	// this is a version of recurseFireAway meant to be done with a small maxDepth. It will return
	// all triangles which are exactly maxDepth moves away from the base triangle.
	private static MutableList<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> makeStarts(
			final TriangleBilliard4 billiard, final int depth, final int maxDepth,
			final MutableIntList code, final SideSum sideSum) {
		
		if (depth >= maxDepth) {
			final MutableList<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> list = new FastList<>();
	        final MutableIntList codeClone = IntArrayList.newList(code);
			final SideSum sumClone = sideSum.copy();
			list.add(Tuple.of(billiard, codeClone, sumClone));
		    return list;
		}
		
		MutableList<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> starts = new FastList<>();

        final Optional<TriangleBilliard4> optLeftBilliard = billiard.getNext(true);
		if (optLeftBilliard.isPresent()) {
			// we are able to go left
			final TriangleBilliard4 leftBilliard = optLeftBilliard.get();
		    final int leftSwap = 3 - billiard.side - leftBilliard.side;
	
		    sideSum.add(leftSwap);
		    code.add(leftSwap);
	
		    starts.addAll(makeStarts(leftBilliard, depth + 1, maxDepth, code, sideSum));
	
		    code.removeAtIndex(code.size() - 1);
		    sideSum.sub(leftSwap);

		}
        final Optional<TriangleBilliard4> optRightBilliard = billiard.getNext(false);
		if (optRightBilliard.isPresent()) {
			// we are able to go right
		    final TriangleBilliard4 rightBilliard = optRightBilliard.get();
		    final int rightSwap = 3 - billiard.side - rightBilliard.side;
	
		    sideSum.sub(rightSwap);
		    code.add(rightSwap);
	
		    starts.addAll(makeStarts(rightBilliard, depth + 1, maxDepth, code, sideSum));
	
		    code.removeAtIndex(code.size() - 1);
		    sideSum.add(rightSwap);
		}
        return starts;
	}

	public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, 
			final int movesMax, final double xAngle, final double yAngle) {
		final int numThreads = Utils.numThreads;
		
		final TriangleBilliard4 startBilliard = TriangleBilliard4.create(xAngle, yAngle);
		final SideSum sideSum = SideSum.create(xAngle, yAngle);
		final MutableIntList startCode = new IntArrayList();
		
        final Array<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> starts = 
        		Array.ofAll(makeStarts(startBilliard, 0, numThreads, startCode, sideSum));
        
        final Array<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> sortStarts = lazySort(starts);

        final Array<Callable<MutableList<ClassifiedCodeSequence>>> tasks = sortStarts.map(T -> () -> {
        	final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			
			recurseFireAway(movesMin, movesMax, numThreads, T._3, T._1, T._2, codes);
			return codes;
        });
        final MutableList<ClassifiedCodeSequence> allCodes = new FastList<ClassifiedCodeSequence>();
        
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final Array<Future<MutableList<ClassifiedCodeSequence>>> futures =
    			tasks.map(task -> executor.submit(task));
        
        for (final Future<MutableList<ClassifiedCodeSequence>> future : futures) {
        	try {
				allCodes.addAll(future.get());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
        }
        
        executor.shutdown();

		return allCodes;
	}
	
	// there must be a way to just sort an Array, but I can't find it, so here we are.
	final static Array<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> lazySort(
			final Array<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> array) {
		
		final List<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> list = Lists.newArrayList(array);
		
		Collections.sort(list, new Comparator<Tuple3<TriangleBilliard4, MutableIntList, SideSum>>() {
		
			@Override
			public int compare(Tuple3<TriangleBilliard4, MutableIntList, SideSum> o1,
					Tuple3<TriangleBilliard4, MutableIntList, SideSum> o2) {
				return Double.compare(o1._1.interval(), o2._1.interval());
			}
			
		});
		
		final Array<Tuple3<TriangleBilliard4, MutableIntList, SideSum>> sortStarts = Array.ofAll(list);
	    
		return sortStarts;
	}
	
}



