package patternfinder;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import billiards.codeseq.CodeSequence;

public final class PatternTests {

	@Test
	void testSinglePatternSimpleBase() {
		// Arrangement:
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, -1);
		ImmutableIntList code = IntLists.immutable.of(1, 2, 1, 4);

		// Act: (The constructor of Spattern creates the base)
		Spattern pattern = new Spattern(pat, code);

		// Assert:
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 4);
		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testTriplePatternSimpleBase() {
		// Arrangement:
		Tpattern pattern = new Tpattern();

		// Make the stable, unstable, stable patterns
		ImmutableIntList pat1 = IntLists.immutable.of(0, 1, 1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, 1, 0, 1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, 1, 1);
		pattern.setPat(pat1, 0); // set the patterns into the created triple pattern
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		// create an arbitrary but standardized code sequence and give it to setBase
		ImmutableIntList[] triple = { IntLists.immutable.of(1, 5, 5), IntLists.immutable.of(1, 2, 1, 4),
				IntLists.immutable.of(1, 5, 5) };

		// Act:
		pattern.setBase(triple);

		// Assert:
		Assertions.assertEquals("1 5 5, 1 2 1 4, 1 5 5", pattern.baseString());
	}

	@Test
	void testNegativeTriplePatternSimpleBase() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -1, -1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, -1, 0, -1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, -1, -1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 5, 5), IntLists.immutable.of(1, 6, 1, 8),
				IntLists.immutable.of(1, 5, 5) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 2 1 4, 1 1 1", pattern.baseString());
	}

	@Test
	void testNegativeAndPositiveTriplePatternBase() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -1, -1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, -1, 0, 1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, 1, 1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 5, 5), IntLists.immutable.of(1, 2, 1, 10),
				IntLists.immutable.of(1, 5, 5) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 5 5, 1 2 1 10, 1 5 5", pattern.baseString());
	}

	@Test
	void testNegativeAndPositiveTriplePatternBase2() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -1, -1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, -1, 0, 2);
		ImmutableIntList pat3 = IntLists.immutable.of(0, 1, 1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 5, 5), IntLists.immutable.of(1, 6, 1, 10),
				IntLists.immutable.of(1, 5, 5) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 2 1 18, 1 9 9", pattern.baseString());
	}

	@Test
	void testNegativeAndPositiveTriplePatternBase3() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, 1, 1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, 1, 0, -1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, 1, 1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 1, 1), IntLists.immutable.of(1, 2, 1, 4),
				IntLists.immutable.of(1, 1, 1) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 2 1 4, 1 1 1", pattern.baseString());
	}

	@Test
	void testNegativeAndPositiveTriplePatternBase4() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -1, -1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, -1, 0, 1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, -1, -1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 1, 1), IntLists.immutable.of(1, 2, 1, 4),
				IntLists.immutable.of(1, 1, 1) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 2 1 4, 1 1 1", pattern.baseString());
	}

	@Test
	void testNegativeAndPositiveTriplePatternBase5() {
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -1, -1);
		ImmutableIntList pat2 = IntLists.immutable.of(0, 1, 0, -1);
		ImmutableIntList pat3 = IntLists.immutable.of(0, -1, -1);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 3, 3), IntLists.immutable.of(1, 4, 1, 4),
				IntLists.immutable.of(1, 3, 3) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 6 1 2, 1 1 1", pattern.baseString());
	}

	@Test
	void testTripleBase() {
		/*
		 * 
		 * 1 5 5, 1 10 1 10, 1 5 5
		 * 1 1 1, 1 4 1 6, 1 1 1
		 * 
		 */
		Tpattern pattern = new Tpattern();

		ImmutableIntList pat1 = IntLists.immutable.of(0, -2, -2);
		ImmutableIntList pat2 = IntLists.immutable.of(0, -3, 0, -2);
		ImmutableIntList pat3 = IntLists.immutable.of(0, -2, -2);
		pattern.setPat(pat1, 0);
		pattern.setPat(pat2, 1);
		pattern.setPat(pat3, 2);

		ImmutableIntList[] triple = { IntLists.immutable.of(1, 5, 5), IntLists.immutable.of(1, 10, 1, 10),
				IntLists.immutable.of(1, 5, 5) };
		pattern.setBase(triple);

		Assertions.assertEquals("1 1 1, 1 4 1 6, 1 1 1", pattern.baseString());
	}

	@Test
	void testNegativeSinglePatternBase() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, -1);
		ImmutableIntList code = IntLists.immutable.of(1, 4, 1, 8);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 6);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testNegativeAndPositiveSinglePatternBase() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, 1);
		ImmutableIntList code = IntLists.immutable.of(1, 4, 1, 8);

		Spattern pattern = new Spattern(pat, code);
		CodeSequence expectedBase = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 10)).get();
		Assertions.assertEquals(expectedBase, pattern.getBaseCode());
	}

	@Test
	void testNegativeAndPositiveSinglePatternBase2() {
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, -1);
		ImmutableIntList code = IntLists.immutable.of(1, 4, 1, 8);

		Spattern pattern = new Spattern(pat, code);

		CodeSequence expectedBase = CodeSequence.create(IntLists.immutable.of(1, 2, 1, 10)).get();
		Assertions.assertEquals(expectedBase, pattern.getBaseCode());
	}

	@Test
	void testSinglePatternBase() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, 2);
		ImmutableIntList code = IntLists.immutable.of(1, 6, 1, 10);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 18);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	/*
	 * TODO: Change getBase to getBaseCode() and compare versus the CodeSequence
	 */
	@Test
	void testSinglePatternBase2() {
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, -2);
		ImmutableIntList code = IntLists.immutable.of(1, 6, 1, 10);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 18);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase3() {
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, 2);
		ImmutableIntList code = IntLists.immutable.of(1, 12, 1, 16);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 6, 1, 4);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase4() {
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, 2);
		ImmutableIntList code = IntLists.immutable.of(1, 8, 1, 8);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 6, 1, 4);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase5() {
		ImmutableIntList pat = IntLists.immutable.of(0, 1, 0, -2);
		ImmutableIntList code = IntLists.immutable.of(1, 10, 1, 16);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 32);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase6() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, 2);
		ImmutableIntList code = IntLists.immutable.of(1, 10, 1, 16);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 2, 1, 32);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase7() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, -2);
		ImmutableIntList code = IntLists.immutable.of(1, 6, 1, 4);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 6, 1, 4);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}

	@Test
	void testSinglePatternBase8() {
		ImmutableIntList pat = IntLists.immutable.of(0, -1, 0, -2);
		ImmutableIntList code = IntLists.immutable.of(1, 10, 1, 12);

		Spattern pattern = new Spattern(pat, code);
		ImmutableIntList expectedBase = IntLists.immutable.of(1, 6, 1, 4);

		Assertions.assertEquals(expectedBase, pattern.getBase());
	}
}
