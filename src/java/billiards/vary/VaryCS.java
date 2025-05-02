package billiards.vary;

import java.util.Optional;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.geometry.TriangleBilliard;
import billiards.viewer.SideSum;
import billiards.viewer.Utils;

public final class VaryCS {
    private static final double OFFSET = 0.0005;
    private static final double SMALLOFFSET = 0.0000000000005;

    private static void recurseFireAway(
            final int min, final int max, final double specMin, final double specMax,
            final int depth, final SideSum sideSum, final TriangleBilliard billiard, final MutableIntList code,
            final MutableList<ClassifiedCodeSequence> codesFound) {
    	
		if (depth >= max) return;
		if (Thread.currentThread().isInterrupted()) return;
		
		if (depth > min) {
			// here we check if we have reached a periodic path

		    if (Math.abs(sideSum.sum()) < OFFSET) {
		    	// we do an additional check meant to reduce the number of empty sets due to 
		    	// rounding error. It is not very effective at large numbers of moves.
		    	if (specMax - specMin > SMALLOFFSET) {
			    	final MutableIntList code2 = new IntArrayList();
			    	code2.addAll(code);
			    	code2.addAll(code.toReversed());
			        final Optional<ClassifiedCodeSequence> codeSeq = Utils.convert(code2);
			        if (codeSeq.isPresent()) {
			        	codesFound.add(codeSeq.get());
			        }
		    	}
		    }
		}
		
		final double specialPos = billiard.vertexC.x;
		
		if (specMin < specialPos) {
		    // go left
		    final TriangleBilliard leftBilliard = billiard.getNext(true);
		    final int leftSwap = 3 - billiard.side - leftBilliard.side;

		    sideSum.add(leftSwap);
		    code.add(leftSwap);

		    recurseFireAway(min, max, specMin, Math.min(specialPos, specMax),
		                    depth + 1, sideSum, leftBilliard, code, codesFound);

		    code.removeAtIndex(code.size() - 1);
		    sideSum.sub(leftSwap);
		}

		if (specMax > specialPos) {
		    // go right
		    final TriangleBilliard rightBilliard = billiard.getNext(false);
		    final int rightSwap = 3 - billiard.side - rightBilliard.side;

		    sideSum.sub(rightSwap);
		    code.add(rightSwap);

		    recurseFireAway(min, max, Math.max(specialPos, specMin), specMax,
		                    depth + 1, sideSum, rightBilliard, code, codesFound);

		    code.removeAtIndex(code.size() - 1);
		    sideSum.add(rightSwap);
		}
    }
	
    public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, final int movesMax,
            final double xAngle, final double yAngle) {

		final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
		final TriangleBilliard billiard = TriangleBilliard.create(xAngle, yAngle, 0);
		final SideSum sideSum = SideSum.create(xAngle, yAngle);
		final MutableIntList foundCode = new IntArrayList();
		
		recurseFireAway(movesMin/2, movesMax/2, 0, billiard.vertexB.x, 0, sideSum, billiard, foundCode, codes);

		return codes;
    }
	
}
