package billiards.vary;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import billiards.geometry.Vector2;
import billiards.wrapper.ConnectionPool;
import javaslang.collection.Array;

/**
 * <b>Jeff Khuu</b><br>
 * <b>May 26, 2026</b>
 * <p>
 * <code>VaryLInstance</code> is a wrapper class around commonly used classes
 * and settings for VaryL calculations. VaryL is not responsible for shutdown of
 * its member variables
 * </p>
 */
public class VaryLInstance {
	public final Array<Vector2> coords;
	public final MutableSortedSet<String> coverCodes;
	public final ConnectionPool pool;
	public final ExecutorService shotExecutor;
	public final ExecutorService storageExecutor;

	// Index of the first valid coordinate in coords
	public final int start;
	public final int step;
	// One passed the index of the last valid coordinate in coords
	public final int end;

	// Print this number of codes, print all codes if equal to -1
	public final int maxPrintNum;
	public final boolean printMid;
	public final boolean printFirstLast;

	private VaryLInstance(Builder builder) {
		this.coords = builder.coords;
		this.coverCodes = builder.coverCodes;
		this.pool = builder.pool;
		this.shotExecutor = builder.shotExecutor;
		this.storageExecutor = builder.storageExecutor;

		this.start = builder.start;
		this.step = builder.step;
		this.end = builder.end;

		this.maxPrintNum = builder.maxPrintNum;
		this.printMid = builder.printMid;
		this.printFirstLast = builder.printFirstLast;
	}

	public void shutdown() {
		shotExecutor.shutdown();
		storageExecutor.shutdown();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 25, 2026</b>
	 * <p>
	 * True if start <= index < end, False otherwise.
	 * </p>
	 */
	public boolean isValidIdx(int index) {
		return index >= this.start && index < this.end;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 26, 2026</b>
	 * <p>
	 * Builder constructs a VaryL requiring only necessary
	 * fields with the ability to set different optional fields
	 * </p>
	 */
	public static class Builder {
		private final Array<Vector2> coords;
		private final MutableSortedSet<String> coverCodes = new TreeSortedSet<>();
		private final ConnectionPool pool;
		private final ExecutorService storageExecutor;
		private final ExecutorService shotExecutor;

		// Start is the first valid index, end is one passed the last valid index
		private int start = 0, step = 1, end;

		// Print this number of codes, print all codes if equal to -1
		private int maxPrintNum = 0;
		private boolean printMid = false;
		private boolean printFirstLast = false;

		// NOTE: We should add a field for OverrideSideSum that is an
		// Optional<CodeTypeCollection<Integer>>
		// CodeTypeCollection<T> should be a wrapper class around five T values
		// corresponding to each CodeType
		public Builder(final Array<Vector2> points, final List<String> codes,
				final ConnectionPool pool, final ExecutorService storageExecutor, final ExecutorService shotExecutor) {
			this.coords = points;
			this.end = points.size();
			coverCodes.addAll(codes);
			this.pool = pool;
			this.storageExecutor = storageExecutor;
			this.shotExecutor = shotExecutor;
		}

		public Builder setMaxPrintNum(int num) {
			this.maxPrintNum = num;
			return this;
		}

		public Builder setPrintMid(boolean val) {
			this.printMid = val;
			return this;
		}

		public Builder setPrintFirstLast(boolean val) {
			this.printFirstLast = val;
			return this;
		}

		public Builder setStart(int num) {
			this.start = num;
			return this;
		}

		public Builder setStep(int num) {
			this.step = num;
			return this;
		}

		public Builder setEnd(int num) {
			this.end = num;
			return this;
		}

		public VaryLInstance build() {
			return new VaryLInstance(this);
		}
	}
}
