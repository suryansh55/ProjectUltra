package billiards.pattern;

import billiards.codeseq.*;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class TripleTests {
    @Test
    void TestSimpleTriple() {
        ClassifiedCodeSequence[] c1 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
        };
        ClassifiedCodeSequence[] c2 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 4, 1, 6)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
        };

        Triple t1 = Triple.create(c1).get();
        Triple t2 = Triple.create(c2).get();
        TriplePattern pattern = TriplePattern.create(t1, t2).get();
        Assertions.assertEquals(t1, pattern.getBase());
    }

    @Test
    void TestReversedSimpleTriple() {
        ClassifiedCodeSequence[] c1 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
        };
        ClassifiedCodeSequence[] c2 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 4, 1, 6)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
        };

        Triple t1 = Triple.create(c1).get();
        Triple t2 = Triple.create(c2).get();
        TriplePattern pattern = TriplePattern.create(t2, t1).get();
        Assertions.assertEquals(t1, pattern.getBase());
    }

    @Test
    void TestSimpleDoubleTriple() {
        ClassifiedCodeSequence[] c1 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 3, 3)).get(),
        };
        ClassifiedCodeSequence[] c2 = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 7, 7)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 6, 1, 8)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 7, 7)).get(),
        };

        Triple t1 = Triple.create(c1).get();
        Triple t2 = Triple.create(c2).get();
        TriplePattern pattern = TriplePattern.create(t2, t1).get();
        Assertions.assertEquals(t1, pattern.getBase());
    }

    @Test
    void TestSimpleTripleString() {
        // Arrange:
        ClassifiedCodeSequence[] codes = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
        };

        ImmutableIntList[] diffs = new ImmutableIntList[] {
                IntLists.immutable.of(0, 2, 2),
                IntLists.immutable.of(0, 2, 0, 2),
                IntLists.immutable.of(0, 2, 2),
        };
        Triple triple = Triple.create(codes).get();
        TriplePattern pattern = TriplePattern.create(triple, diffs).get();

        // Assert:
        Assertions.assertEquals("1 5 5, 1 2 1 4, 1 5 5", pattern.getBase().toString());
    }

    @Test
    void TestPosAndNegTriple() {
        ClassifiedCodeSequence[] codes = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 6, 1, 10)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
        };

        ImmutableIntList[] diffs = new ImmutableIntList[] {
                IntLists.immutable.of(0, -2, -2),
                IntLists.immutable.of(0, -2, 0, 4),
                IntLists.immutable.of(0, 2, 2)
        };

        Triple triple = Triple.create(codes).get();
        TriplePattern pattern = TriplePattern.create(triple, diffs).get();

        Assertions.assertEquals("1 1 1, 1 2 1 18, 1 9 9", pattern.getBase().toString());
    }

    @Test
    void TestCalculatePattern() {
        ClassifiedCodeSequence[] codes = new ClassifiedCodeSequence[] {
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 6, 1, 10)).get(),
                ClassifiedCodeSequence.create(IntLists.immutable.of(1, 5, 5)).get(),
        };
        ImmutableIntList[] diffs = new ImmutableIntList[] {
                IntLists.immutable.of(0, -4, -4),
                IntLists.immutable.of(0, -4, 0, 8),
                IntLists.immutable.of(0, 4, 4)
        };

        Triple triple = Triple.create(codes).get();
        TriplePattern pattern = TriplePattern.create(triple, diffs).get();

        Assertions.assertEquals("-2 -3, -2 4 4, 2 3", pattern.toString());
        Assertions.assertEquals("-2 -2 -3 -3, -2 -2 4 4 4 4, 2 2 3 3", pattern.toStringFull());

    }
}
