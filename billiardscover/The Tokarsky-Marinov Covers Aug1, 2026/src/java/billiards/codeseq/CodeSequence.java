package billiards.codeseq;

import billiards.math.LinCom;
import billiards.math.XYEta;
import billiards.math.XYZ;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.Optional;

// In Java, the type system isn't expressive enough to describe immutable
// or constant objects, so you deal with this by making immutability
// a feature of the API of the class. Aka, private members, and no
// methods that return a mutable reference to or mutate those members.
// As such, you have the common pattern of a mutable "builder" object,
// which you construct, and then you create an immutable object from
// the builder. In Rust and C++, this is unnecessary.

// You can't modify an IntList from the binding, but it could
// be modified by performing some other operation.
// But you can't modify an ImmutableIntList from anywhere.
// That is the difference. So use the first for function parameters,
// and the second (or MutableIntList) for variables.
// Use the interface when specifying the type, the specific
// class when creating the type

// We us an ImmutableIntList. It gives us a fixed length, immutable list.
// It doesn't have nice indexing, but that's stupid 'ol java for you

public final class CodeSequence implements Comparable<CodeSequence> {

    public final ImmutableIntList codeNumbers;

    public CodeSequence(final IntList tmpCodeNumbers) {

        validate(tmpCodeNumbers);

        final int index = smallestIndex(tmpCodeNumbers);

        final MutableIntList sublist = subList(tmpCodeNumbers, 0, index);

        this.codeNumbers = minimalRotation(sublist);
    }

    private static MutableIntList subList(final IntList list, final int start, final int end) {

        final MutableIntList subList = new IntArrayList();
        for (int i = start; i < end; ++i) {
            final int elem = list.get(i);
            subList.add(elem);
        }

        return subList;
    }

    private static boolean isRepeated(final IntList codeNumbers, final int subLength) {

        for (int i = subLength; i < codeNumbers.size(); ++i) {
            final int subIndex = i % subLength;
            if (codeNumbers.get(i) != codeNumbers.get(subIndex)) {
                return false;
            }
        }

        return true;
    }

    private static void validate(final IntList codeNumbers) {

        // Must be nonempty
        if (codeNumbers.isEmpty()) {
            throw new RuntimeException("empty code numbers");
        }

        // All numbers must be strictly positive
        final boolean allPos = codeNumbers.allSatisfy(num -> num > 0);
        if (!allPos) {
            throw new RuntimeException("non-positive numbers");
        }

        // Check if it has a legal pattern
        final boolean legal = isLegal(codeNumbers, codeNumbers.size());

        if (!legal) {
            throw new RuntimeException("illegal pattern");
        }
    }

    // codeNumbers must already be valid
    private static int smallestIndex(final IntList codeNumbers) {

        final int size = codeNumbers.size();

        // Find the shortest repeated legal sublist.
        for (int i = 2; i < size; ++i) {
            if (size % i == 0) {
                if (isRepeated(codeNumbers, i) && isLegal(codeNumbers, i)) {
                    return i;
                }
            }
        }

        // Otherwise, there are no repeaters, so just return the code numbers
        return size;
    }

    private static XYZ nextAngle(final XYZ prev, final XYZ curr, final int number) {
        if (number % 2 == 0) {
            return prev;
        } else {
            return XYZ.otherAngle(prev, curr);
        }
    }

    private static boolean isLegal(final IntList codeNumbers, final int end) {

        XYZ prev = XYZ.X;
        XYZ curr = XYZ.Y;

        for (int i = 0; i < end; ++i) {
            final int num = codeNumbers.get(i);

            final XYZ next = nextAngle(prev, curr, num);

            prev = curr;
            curr = next;
        }

        return prev == XYZ.X && curr == XYZ.Y;
    }

    private static ImmutableIntList minimalRotation(final MutableIntList codeNumbers) {

        // We rotate and reverse `codeNumbers`, which would change `min` too if it were
        // just a reference. To prevent this, `min` is an immutable copy
        ImmutableIntList min = codeNumbers.toImmutable();

        final int length = codeNumbers.size();

        // Rotate length times
        for (int i = 0; i < length; ++i) {
            rotateLeft(codeNumbers);

            // if codeNumbers < min
            if (compareIntList(codeNumbers, min) < 0) {
                min = codeNumbers.toImmutable();
            }
        }

        // After length rotations, the list is now back
        // to where it was before. Rotate it, and do it again

        codeNumbers.reverseThis();

        for (int i = 0; i < length; ++i) {
            rotateLeft(codeNumbers);

            // if codeNumbers < min
            if (compareIntList(codeNumbers, min) < 0) {
                min = codeNumbers.toImmutable();
            }
        }

        return min;
    }

    private static int compareIntList(final IntList list1, final IntList list2) {
        final int lengthComp = Integer.compare(list1.size(), list2.size());

        if (lengthComp < 0) {
            // list1.size() < list2.size()
            return -1;
        } else if (lengthComp > 0) {
            // list1.size() > list2.size()
            return 1;
        } else {
            // sizes are the same

            for (int i = 0; i < list1.size(); ++i) {
                final int elem1 = list1.get(i);
                final int elem2 = list2.get(i);

                final int comp = Integer.compare(elem1, elem2);

                if (comp < 0) {
                    return -1;
                } else if (comp > 0) {
                    return 1;
                }

                // else comp == 0, so check the next element
            }

            return 0;
        }
    }

    // Rotate a list left by one spot
    private static void rotateLeft(final MutableIntList list) {

        if (list.notEmpty()) {
            final int first = list.get(0);
            for (int i = 1; i < list.size(); ++i) {
                final int elem = list.get(i);
                list.set(i - 1, elem);
            }

            list.set(list.size() - 1, first);
        }
    }

    public int sum() {

        int sum = 0;
        for (int i = 0; i < this.codeNumbers.size(); ++i) {
            final int codeNumber = this.codeNumbers.get(i);
            sum = Math.addExact(sum, codeNumber);
        }

        return sum;
    }

    public int length() {
        return this.codeNumbers.size();
    }

    public CodeType type() {
        final boolean odd = isOdd();
        final boolean closed = isClosed();
        final boolean stable = isStable();

        if (!closed && stable && odd) {
            return CodeType.OSO;
        } else if (!closed && stable && !odd) {
            return CodeType.OSNO;
        } else if (!closed && !stable) {
            return CodeType.ONS;
        } else if (closed && stable) {
            return CodeType.CS;
        } else if (closed && !stable) {
            return CodeType.CNS;
        } else {
            throw new RuntimeException(codeNumbers + " cannot be classified");
        }
    }

    public Optional<Integer> closedIndex() {

        // Odd code sequences are never closed
        if (isOdd()) {
            return Optional.empty();
        }

        final int length = this.codeNumbers.size();
        final int halfLength = length / 2;

        // Iterate over codeNumbers in intervals of halfLength, checking
        // to see if we find two even numbers.
        for (int i = 0; i < halfLength; i += 1) {
            final int first = this.codeNumbers.get(i);
            final int second = this.codeNumbers.get(i + halfLength);

            final boolean firstEven = first % 2 == 0;
            final boolean secondEven = second % 2 == 0;

            if (firstEven && secondEven) {
                // Now check if the two lists between the first and second integers are
                // reverses of each other.
                // We could have __ E ~~~~~ E ---, for example, and then
                // a = ~~~~~
                // b = ---__ (and then we reverse it)
                if (isPalindrome(this.codeNumbers, i)) {
                    return Optional.of(i);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isPalindrome(final IntList codeNumbers, final int i) {

        final int length = codeNumbers.size();
        final int halfLength = length / 2;

        // i is the index of E1
        // we start at E2, because it wraps around correctly when using %
        // j starts at E2 - 1, and goes <- through l1
        // k starts at E2 + 1, and goes -> and wraps around through l2
        int j = i + halfLength - 1;
        int k = (i + halfLength + 1) % length;

        // j and k meet at E1
        while (j != k) {
            if (codeNumbers.get(j) != codeNumbers.get(k)) {
                return false;
            }

            j -= 1;
            k = (k + 1) % length;
        }

        return true;
    }

    public boolean isClosed() {
        return closedIndex().isPresent();
    }

    public boolean isOdd() {
        return this.codeNumbers.size() % 2 != 0;
    }

    public boolean isStable() {
        // The angles we use here are arbitrary
        return constraint(XYZ.X, XYZ.Y).isZero();
    }

    public LinCom<XYEta> constraint(final XYZ first, final XYZ second) {

        if (isOdd()) {
            // odd codes are stable, so 0
            return new LinCom<>(0, 0, 0);
        }

        // even indices are add
        // odd indices are sub

        final LinCom<XYZ> constraint = new LinCom<>(0, 0, 0);
        constraint.add(codeNumbers.get(0), first);
        constraint.sub(codeNumbers.get(1), second);

        XYZ prevPrev = first;
        XYZ prev = second;

        for (int i = 2; i < codeNumbers.size(); ++i) {
            final int prevNumber = codeNumbers.get(i - 1);

            final XYZ current = nextAngle(prevPrev, prev, prevNumber);

            final int currentNumber = codeNumbers.get(i);

            if (i % 2 == 0) {
                // even index, so add
                constraint.add(currentNumber, current);
            } else {
                // odd index, so sub
                constraint.sub(currentNumber, current);
            }

            // Update prev_prev and prev for the next loop iteration
            prevPrev = prev;
            prev = current;
        }

        // z = 2*eta - x - y

        final int xCoeff = constraint.coeff(XYZ.X) - constraint.coeff(XYZ.Z);
        final int yCoeff = constraint.coeff(XYZ.Y) - constraint.coeff(XYZ.Z);
        final int etaCoeff = 2 * constraint.coeff(XYZ.Z);

        final LinCom<XYEta> etaConstraint = new LinCom<>(xCoeff, yCoeff, etaCoeff);
        etaConstraint.divideContent();
        etaConstraint.normalizeUnit();

        return etaConstraint;
    }

    @Override
    public String toString() {
        return this.codeNumbers.makeString(" ");
    }

    @Override
    public int hashCode() {
        return this.codeNumbers.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        final CodeSequence other = (CodeSequence) obj;
        return this.codeNumbers.equals(other.codeNumbers);
    }

    @Override
    public int compareTo(final CodeSequence other) {
        return compareIntList(this.codeNumbers, other.codeNumbers);
    }
}
