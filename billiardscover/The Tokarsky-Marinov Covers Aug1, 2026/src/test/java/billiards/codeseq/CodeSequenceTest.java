package billiards.codeseq;

import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.collection.Array;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

public final class CodeSequenceTest {

    @Test
    public static void testEmptyCodeSequence() {
        Assertions.assertThrows(RuntimeException.class, () -> new CodeSequence(IntArrayList.newListWith()));
    }

    @Test
    public static void testNegativeCodeNumbers() {
        final Array<IntList> invalidCodeNumbers = Array.of(
            IntArrayList.newListWith(0),
            IntArrayList.newListWith(-1),
            IntArrayList.newListWith(0, 1),
            IntArrayList.newListWith(1, 2, 3, 0), IntArrayList.newListWith(-1, 2, -3, 4));

        for (final IntList codeNumbers : invalidCodeNumbers) {
            Assertions.assertThrows(RuntimeException.class, () -> new CodeSequence(codeNumbers));
        }
    }

    @Test
    public static void testIllegalCodeSequences() {
        final List<IntList> illegalCodeSequences = new ArrayList<>();
        illegalCodeSequences.add(IntArrayList.newListWith(1)); // O
        illegalCodeSequences.add(IntArrayList.newListWith(2)); // E

        illegalCodeSequences.add(IntArrayList.newListWith(3, 5)); // OO
        illegalCodeSequences.add(IntArrayList.newListWith(1, 2)); // OE
        illegalCodeSequences.add(IntArrayList.newListWith(4, 7)); // EO

        illegalCodeSequences.add(IntArrayList.newListWith(1, 3, 8));    // OOE
        illegalCodeSequences.add(IntArrayList.newListWith(15, 4, 7));   // OEO
        illegalCodeSequences.add(IntArrayList.newListWith(32, 17, 81)); // EOO
        illegalCodeSequences.add(IntArrayList.newListWith(3, 12, 18));  // OEE
        illegalCodeSequences.add(IntArrayList.newListWith(8, 21, 78));  // EOE
        illegalCodeSequences.add(IntArrayList.newListWith(38, 52, 25)); // EEO
        illegalCodeSequences.add(IntArrayList.newListWith(2, 4, 8));    // EEE

        illegalCodeSequences.add(IntArrayList.newListWith(15, 37, 55, 21)); // OOOO
        illegalCodeSequences.add(IntArrayList.newListWith(15, 37, 55, 20)); // OOOE
        illegalCodeSequences.add(IntArrayList.newListWith(15, 37, 54, 21)); // OOEO
        illegalCodeSequences.add(IntArrayList.newListWith(15, 38, 55, 21)); // OEOO
        illegalCodeSequences.add(IntArrayList.newListWith(16, 37, 55, 21)); // EOOO

        for (final IntList codeNumbers : illegalCodeSequences) {
            Assertions.assertThrows(RuntimeException.class, () -> new CodeSequence(codeNumbers));
        }
    }

    @Test
    public static void testRepeaters() {
        final Array<Tuple2<IntList, IntList>> repeaters =
            Array.of(Tuple.of(IntArrayList.newListWith(1, 1, 1, 1, 1, 1),
                              IntArrayList.newListWith(1, 1, 1)),
                     Tuple.of(IntArrayList.newListWith(1, 1, 1, 1, 1, 1, 1, 1, 1),
                              IntArrayList.newListWith(1, 1, 1)),
                     Tuple.of(IntArrayList.newListWith(1, 1, 4, 1, 1, 4),
                              IntArrayList.newListWith(1, 1, 4, 1, 1, 4)));

        for (final Tuple2<IntList, IntList> pair : repeaters) {
            final CodeSequence codeSeq = new CodeSequence(pair._1);
            Assertions.assertEquals(codeSeq.codeNumbers, pair._2);
        }
    }

    @Test
    public static void testOrder() {
        final Array<Tuple2<IntList, IntList>> codes = Array.of(
            Tuple.of(IntArrayList.newListWith(1, 1, 3), IntArrayList.newListWith(1, 1, 3)),
            Tuple.of(IntArrayList.newListWith(3, 1, 1), IntArrayList.newListWith(1, 1, 3)),
            Tuple.of(IntArrayList.newListWith(2, 4), IntArrayList.newListWith(2, 4)),
            Tuple.of(IntArrayList.newListWith(4, 2), IntArrayList.newListWith(2, 4)));

        for (final Tuple2<IntList, IntList> pair : codes) {
            final CodeSequence codeSeq = new CodeSequence(pair._1);
            Assertions.assertEquals(codeSeq.codeNumbers, pair._2);
        }
    }

    @Test
    public static void testCodeType() {
        final Array<Tuple2<IntList, CodeType>> classifications = Array.of(
            Tuple.of(IntArrayList.newListWith(1, 1, 1), CodeType.OSO),
            Tuple.of(IntArrayList.newListWith(2, 2), CodeType.CNS),
            Tuple.of(IntArrayList.newListWith(1, 1, 2, 1, 3, 2), CodeType.ONS),
            Tuple.of(IntArrayList.newListWith(1, 1, 1, 1, 2, 1, 1, 1, 1, 2), CodeType.CS),
            Tuple.of(IntArrayList.newListWith(1, 1, 2, 2, 1, 1, 3, 3), CodeType.OSNO));

        for (final Tuple2<IntList, CodeType> pair : classifications) {
            final CodeSequence codeSeq = new CodeSequence(pair._1);
            Assertions.assertEquals(codeSeq.type(), pair._2);
        }
    }
}
