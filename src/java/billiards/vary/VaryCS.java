package billiards.vary;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.DoubleArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.geometry.TriangleBilliard;
import billiards.viewer.SideSum;
import billiards.wrapper.Wrapper;

public final class VaryCS {
    public static int doneIteration(MutableIntList code, IntArrayList sideSumArray, DoubleArrayList specMinArray, DoubleArrayList specMaxArray, BooleanArrayList leftArray, BooleanArrayList rightArray, ArrayList<TriangleBilliard> billiards, int iterationDepth, SideSum sideSum) {
        boolean sideSumArrayIsEmpty = sideSumArray.isEmpty();
        int sideSumDelta = sideSumArrayIsEmpty ? 0 : sideSumArray.getLast();

        removeLast(code, sideSumArray, specMinArray, specMaxArray, leftArray, rightArray, billiards);

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

    public static void removeLast(MutableIntList code, IntArrayList sideSumArray, DoubleArrayList specMinArray, DoubleArrayList specMaxArray, BooleanArrayList leftArray, BooleanArrayList rightArray, ArrayList<TriangleBilliard> billiards) {
        if (!code.isEmpty()) code.removeAtIndex(code.size() - 1);
        if (!sideSumArray.isEmpty()) sideSumArray.removeAtIndex(sideSumArray.size() - 1);
        if (!specMinArray.isEmpty()) specMinArray.removeAtIndex(specMinArray.size() - 1);
        if (!specMaxArray.isEmpty()) specMaxArray.removeAtIndex(specMaxArray.size() - 1);
        if (!leftArray.isEmpty()) leftArray.removeAtIndex(leftArray.size() - 1);
        if (!rightArray.isEmpty()) rightArray.removeAtIndex(rightArray.size() - 1);
        if (!billiards.isEmpty()) billiards.remove(billiards.size() - 1);
    }

            /* jul,31,2025 Marco Mai
     * move varyCS to backend, rest of the code here being disable
     */
    public static MutableList<ClassifiedCodeSequence> fireAway(final int movesMin, final int movesMax,
            final double xAngle, final double yAngle,final String reqTypes) {
		
		Optional<MutableList<ClassifiedCodeSequence>> values = Wrapper.varyCSCpp( movesMin, movesMax,xAngle,yAngle,reqTypes);
		if(!values.isPresent()){
			final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			return codes;
		} else {
			MutableList<ClassifiedCodeSequence> codes = values.get();
			return codes;
		}
    }
	
	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>June 09, 2026</b>
	 * <p>
	 * <code>fireAwayParallel</code> calls a parallelized-version of VaryCS, this
	 * version parallelizes the search AND verification of code sequences.
	 * @see src/backend/cpp/vary_cs.cpp
	 * </p>
	 */
    public static MutableList<ClassifiedCodeSequence> fireAwayParallel(final int movesMin, final int movesMax,
            final double xAngle, final double yAngle,final String reqTypes) {
		
		Optional<MutableList<ClassifiedCodeSequence>> values = Wrapper.varyCSCppParallel( movesMin, movesMax,xAngle,yAngle,reqTypes);
		if(!values.isPresent()){
			final MutableList<ClassifiedCodeSequence> codes = new FastList<>();
			return codes;
		} else {
			MutableList<ClassifiedCodeSequence> codes = values.get();
			return codes;
		}
    }
}
