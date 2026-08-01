package billiards.codeseq;

import java.util.Objects;

public final class TriplePair {

    public final CodePair stableNeg;
    public final CodePair unstable;
    public final CodePair stablePos;

    public TriplePair(final CodePair stableNeg, final CodePair unstable, final CodePair stablePos) {
        this.stableNeg = stableNeg;
        this.unstable = unstable;
        this.stablePos = stablePos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stableNeg, this.unstable, this.stablePos);
    }

    @Override
    public boolean equals(final Object obj) {
        final TriplePair other = (TriplePair) obj;
        return this.stableNeg.equals(other.stableNeg) &&
               this.unstable.equals(other.unstable) &&
               this.stablePos.equals(other.stablePos);
    }

    @Override
    public String toString() {
        return this.stableNeg + "; " + this.unstable + "; " + this.stablePos;
    }
}
