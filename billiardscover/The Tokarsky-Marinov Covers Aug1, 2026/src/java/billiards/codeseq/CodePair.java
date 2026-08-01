package billiards.codeseq;

import java.util.Objects;

public final class CodePair {

    public final CodeSequence sequence;
    public final InitialAngles angles;

    public CodePair(final CodeSequence sequence, final InitialAngles angles) {
        this.sequence = sequence;
        this.angles = angles;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sequence, this.angles);
    }

    @Override
    public boolean equals(final Object obj) {
        final CodePair other = (CodePair) obj;
        return this.sequence.equals(other.sequence) && this.angles.equals(other.angles);
    }

    @Override
    public String toString() {
        return this.sequence + ", " + this.angles;
    }
}
