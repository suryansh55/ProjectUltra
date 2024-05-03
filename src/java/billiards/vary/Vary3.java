package billiards.vary;

import java.util.Optional;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.geometry.TriangleBilliard;
import billiards.viewer.SideSum;

public final class Vary3 {
    private static final double OFFSET = 0.05;

	private static void recurseFireAway(
	    final int min, final int max, final double specMin, final double specMax, final double initPosition,
	    final int depth, final SideSum sideSum, final TriangleBilliard billiard, final MutableIntList code,
	    final MutableList<ClassifiedCodeSequence> codesFound) {

		if (depth >= max) return;
		if (Thread.currentThread().isInterrupted()) return;

		if (depth > min) {
			// here we check if we have reached a periodic path

		    if (Math.abs(sideSum.sum()) < OFFSET && billiard.side == 2 && billiard.orient == 1) {

		        final double perfectAngle = FastMath.atan2(billiard.vertexA.y, billiard.vertexA.x + initPosition);

		        if (specMax > perfectAngle && perfectAngle > specMin) {
		            final Optional<ClassifiedCodeSequence> codeSeq = Convert.convert(code);
		            if (codeSeq.isPresent()) {
		            	codesFound.add(codeSeq.get());
		            }
		        }
		    }
		}

		final double specialAngle = billiard.getSpecialAngle();

		if (specMax > specialAngle) {
		    // go left
		    final TriangleBilliard leftBilliard = billiard.getNext(true);
		    final int leftSwap = 3 - billiard.side - leftBilliard.side;

		    sideSum.add(leftSwap);
		    code.add(leftSwap);

		    recurseFireAway(min, max, Math.max(specialAngle, specMin), specMax, initPosition,
		                    depth + 1, sideSum, leftBilliard, code, codesFound);

		    code.removeAtIndex(code.size() - 1);
		    sideSum.sub(leftSwap);
		}

		if (specMin < specialAngle) {
		    // go right
		    final TriangleBilliard rightBilliard = billiard.getNext(false);
		    final int rightSwap = 3 - billiard.side - rightBilliard.side;

		    sideSum.sub(rightSwap);
		    code.add(rightSwap);

		    recurseFireAway(min, max, specMin, Math.min(specialAngle, specMax), initPosition,
		                    depth + 1, sideSum, rightBilliard, code, codesFound);

		    code.removeAtIndex(code.size() - 1);
		    sideSum.add(rightSwap);
		}
	}

	public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, final int movesMax,
            final double xAngle, final double yAngle, final double pos) {

        final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
        final TriangleBilliard billiard = TriangleBilliard.create(xAngle, yAngle, pos);
        final SideSum sideSum = SideSum.create(xAngle, yAngle);
        final MutableIntList foundCode = new IntArrayList();

        recurseFireAway(movesMin, movesMax, 0, Math.PI, pos, 0, sideSum, billiard, foundCode, codes);
		return codes;
	}

}
