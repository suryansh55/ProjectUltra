package billiards.vary;

import java.util.List;
import java.util.concurrent.ExecutorService;

import billiards.geometry.ConvexPolygon;
import billiards.geometry.Vector2;
import billiards.wrapper.ConnectionPool;

/**
 * AutoPolyVaryInstance
 */
public class AutoPolyVaryInstance {
	public final ExecutorService storageExecutor;
	public final ExecutorService shotExecutor;
	public final ConnectionPool pool;
	// Index of the first point to consider
	public final int start;
	public final int step;
	// One passed the index of the last point to consider
	public final int end;

	public final List<Vector2> obo;
	public final List<List<Vector2>> points;
	public final ConvexPolygon polygon;

	public final int maxNumPrint;
	public final boolean isSuper;
	public final AutoVaryMode printMode;
	// for a complete implementation of AutoPolyVary we should also store whether to
	// add to cover (boolean)

	private AutoPolyVaryInstance(ConnectionPool pool, ConvexPolygon polygon, List<List<Vector2>> points, int start, int step, int end,
                                 ExecutorService storageExecutor, ExecutorService shotExecutor, List<Vector2> obo, int maxNumPrint, boolean isSuper, AutoVaryMode printMode) {
        this.pool = pool;
        this.polygon = polygon;
		this.storageExecutor = storageExecutor;
		this.shotExecutor = shotExecutor;
		this.start = start;
		this.step = step;
		this.end = end;
		this.points = points;
        this.obo = obo;
        this.maxNumPrint = maxNumPrint;
        this.isSuper = isSuper;
        this.printMode = printMode;
    }

	public boolean isValidIdx(int idx){
		return idx >= this.start && idx < this.end;
	}

	public static class Builder {
		private final ExecutorService storageExecutor;
		private final ExecutorService shotExecutor;
		private int start = 0;
		private int step = 1;
		private int end;
		private final List<List<Vector2>> points;
		private final List<Vector2> obo;
		private boolean isSuper = false;
		private final ConvexPolygon polygon;
		private int numMaxPrint = 0;
		private final AutoVaryMode printMode;
		private final ConnectionPool pool;

		public Builder(ConnectionPool pool, ConvexPolygon polygon, List<Vector2> obo, List<List<Vector2>> points,
					   AutoVaryMode mode, ExecutorService storageExecutor, ExecutorService shotExecutor) {
			this.polygon = polygon;
			this.points = points;
			this.end = points.size();
			this.storageExecutor = storageExecutor;
			this.shotExecutor = shotExecutor;
			this.printMode = mode;
			this.obo = obo;
			this.pool = pool;
		}

		public Builder setIsSuper(boolean val) {
			this.isSuper = val;
			return this;
		}

		public Builder setNumMaxPrint(int val){
			this.numMaxPrint = val;
			return this;
		}

		public Builder setStart(int val){
			this.start = val;
			return this;
		}

		public Builder setStep(int val){
			this.step = val;
			return this;
		}

		public Builder setEnd(int val){
			this.end = val;
			return this;
		}

		public AutoPolyVaryInstance build() {
			return new AutoPolyVaryInstance(pool, polygon, points, start, step, end, storageExecutor,
					shotExecutor, obo, numMaxPrint, isSuper, printMode);
		}

	}

}
