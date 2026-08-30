package billiards.pattern;

import billiards.codeseq.*;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class SingleTests {
	@Test
	void TestCalculateSimple() {
		CodeSequence c1 = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get();
		CodeSequence c2 = CodeSequence.create(IntLists.immutable.of(1, 6, 1, 8)).get();
		SinglePattern pattern = SinglePattern.create(c1, c2).get();
		Assertions.assertEquals(pattern.toString(), "2 4");
		Assertions.assertEquals(pattern.toStringFull(), "2 2 4 4");
	}

	@Test
	void TestSimpleBase() {
		CodeSequence c1 = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get();
		CodeSequence c2 = CodeSequence.create(IntLists.immutable.of(1, 6, 1, 8)).get();
		SinglePattern pattern = SinglePattern.create(c1, c2).get();
		Assertions.assertEquals(pattern.getBase(), new ClassifiedCodeSequence(c1));
	}

	@Test
	void TestNegativeBase() {
		CodeSequence c1 = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 4)).get();
		CodeSequence c2 = CodeSequence.create(IntLists.immutable.of(1, 6, 1, 8)).get();
		SinglePattern pattern = SinglePattern.create(c2, c1).get();
		Assertions.assertEquals(pattern.getBase(), new ClassifiedCodeSequence(c1));
	}
	@Test
	void TestPosAndNegBase() {
		CodeSequence c2 = CodeSequence.create(IntLists.immutable.of(1, 6, 1, 4)).get();
		CodeSequence c1 = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 8)).get();
		SinglePattern pattern = SinglePattern.create(c2, c1).get();
		Assertions.assertEquals(pattern.toString(), "-2 4");
		Assertions.assertEquals(pattern.getBase(), new ClassifiedCodeSequence(c1));
	}

	@Test
	void TestSingleDiff(){
		CodeSequence codes = CodeSequence.create(IntLists.immutable.of(1, 12, 1, 22)).get();
		ImmutableIntList diff = IntLists.immutable.with(0, 4, 0, -2);
		SinglePattern pattern = SinglePattern.create(codes, diff).get();
		Assertions.assertEquals("1 4 1 26", pattern.getBase().toString());
	}
}
