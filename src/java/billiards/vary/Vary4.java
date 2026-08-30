package billiards.vary;

import java.util.*;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;


import billiards.codeseq.ClassifiedCodeSequence;
import billiards.geometry.TriangleBilliard4;
import billiards.viewer.SideSum;
import billiards.wrapper.Wrapper;

public class Vary4 {
	// Zhao Yu Li, Jul 11, 2025.
	// Replaced recursion with a while loop.
	public static int doneIteration(MutableIntList code, IntArrayList sideSumArray, BooleanArrayList leftArray, BooleanArrayList rightArray, ArrayList<TriangleBilliard4> billiards, int iterationDepth, SideSum sideSum) {
		boolean sideSumArrayIsEmpty = sideSumArray.isEmpty();
		int sideSumDelta = sideSumArrayIsEmpty ? 0 : sideSumArray.getLast();

		removeLast(code, sideSumArray, leftArray, rightArray, billiards);

		if (!leftArray.isEmpty() && !rightArray.isEmpty()) {
			if (!sideSumArrayIsEmpty) {
				if (!leftArray.getLast() && !rightArray.getLast()) sideSum.sub(sideSumDelta);
				else if (leftArray.getLast() && !rightArray.getLast()) sideSum.add(sideSumDelta);
			}

			if (!leftArray.getLast() && !rightArray.getLast()) leftArray.set(leftArray.size() - 1, true);
			else if (leftArray.getLast() && !rightArray.getLast()) rightArray.set(rightArray.size() - 1, true);
		}

		return iterationDepth - 1;
	}

	public static void removeLast(MutableIntList code, IntArrayList sideSumArray, BooleanArrayList leftArray, BooleanArrayList rightArray, ArrayList<TriangleBilliard4> billiards) {
		if (!code.isEmpty()) code.removeAtIndex(code.size() - 1);
		if (!sideSumArray.isEmpty()) sideSumArray.removeAtIndex(sideSumArray.size() - 1);
		if (!leftArray.isEmpty()) leftArray.removeAtIndex(leftArray.size() - 1);
		if (!rightArray.isEmpty()) rightArray.removeAtIndex(rightArray.size() - 1);
		if (!billiards.isEmpty()) billiards.remove(billiards.size() - 1);
	}

		/* jul,31,2025 Marco Mai
     * move vary3 to backend, rest of the code here being disable
	 * passing codetype to the backend
     */

	public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, final int movesMax,
            final double xAngle, final double yAngle,final String reqTypes) {
		
		Optional<MutableList<ClassifiedCodeSequence>> values = Wrapper.vary4Cpp( movesMin, movesMax, xAngle,yAngle,reqTypes);
		if(!values.isPresent()){
			final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			return codes;
		} else {
			MutableList<ClassifiedCodeSequence> codes = values.get();
			return codes;
		}


        // final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
        // final TriangleBilliard billiard = TriangleBilliard.create(xAngle, yAngle, pos);
        // final SideSum sideSum = SideSum.create(xAngle, yAngle);
        // final MutableIntList foundCode = new IntArrayList();
		// System.out.println("vray3");
        // recurseFireAway(movesMin, movesMax, 0, Math.PI, pos, 0, sideSum, billiard, foundCode, codes);
		// //fireAwayIterative(movesMin, movesMax, 0, Math.PI, pos, sideSum, billiard, codes);
		// return codes;
	}
}
