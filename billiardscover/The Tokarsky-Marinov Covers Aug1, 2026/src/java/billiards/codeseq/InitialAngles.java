package billiards.codeseq;

import billiards.math.XYZ;
import java.util.Objects;

public final class InitialAngles {

    public final XYZ first;
    public final XYZ second;

    public InitialAngles(final XYZ first, final XYZ second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.first, this.second);
    }

    @Override
    public boolean equals(final Object obj) {
        final InitialAngles other = (InitialAngles) obj;
        return this.first == other.first && this.second == other.second;
    }

    @Override
    public String toString() {
        // TODO change this around too
        return this.first.toString() + this.second.toString();
    }
}
