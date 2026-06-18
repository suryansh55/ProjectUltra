package billiards.viewer;

import billiards.codeseq.CodeTypeCollection;
import billiards.codeseq.CodeTypeSet;
import billiards.codeseq.Storage;
import billiards.database.Admin;
import billiards.geometry.ConvexPolygon;
import billiards.geometry.Location;
import billiards.geometry.Vector2;
import billiards.vary.AutoPolyVaryAction;
import billiards.vary.AutoPolyVaryInstance;
import billiards.vary.AutoVaryMode;
import billiards.wrapper.ConnectionPool;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static billiards.utils.Polygon.cleanPolygon;
import static billiards.utils.Polygon.createConvexPolygon;
import static billiards.viewer.Utils.readFromFile;

/**
 * VaryAutoPolyCmd
 */
public class VaryAutoPolyCmd {
	public static void main(String[] args) {
		// Give an explicit usage message
		if (args.length != 19 && args.length != 16) {
			System.err.println("// Error: Malformed command line arguments to LiLuMaxVary");
			System.err.println("// Received the following arguments:");
			System.err.print("// ");
			for (String arg : args) {
				System.err.printf("%s ", arg);
			}
			System.err.println();
			System.err.println(
					"// Expected arguments of the form: pathToPoints pathToPolygon min max shots subdivisions oso cs cns ons osno printMode "
							+ "maxOSOCodeLength maxCSCodeLength maxOSNOCodeLength databaseName maxOSOSideSum maxCSSideSum maxOSNOSideSum");
			return;
		}

		// Make list of list of points
		Tuple2<List<Vector2>, List<List<Vector2>>> l = parseOBOAndPoints(args[0]);
		List<Vector2> obo = l._1;
		List<List<Vector2>> pointsList = l._2;

		// Make Polygon
		ConvexPolygon polygon;
		try {
			String polyString = cleanPolygon(readFromFile(args[1]));
			polygon = createConvexPolygon(polyString);
		} catch (RuntimeException e) {
			System.err.printf(
					"Something went wrong when parsing the polygon. Check to make sure '%s' contains a valid polygon",
					args[2]);
			return;
		}

		int min = Integer.parseInt(args[2]);
		int sum = Integer.parseInt(args[3]); // Use the name sum to denote the default maximum side sum
		int shots = Integer.parseInt(args[4]);
		int subdivisions = Integer.parseInt(args[5]);
		CodeTypeSet types = CodeTypeSet.builder()
				.setOSO(args[6].equals("1"))
				.setCS(args[7].equals("1"))
				.setCNS(args[8].equals("1"))
				.setONS(args[9].equals("1"))
				.setOSNO(args[10].equals("1"))
				.build();

		AutoVaryMode printMode;
		try {
			printMode = AutoVaryMode.valueOf(args[11].trim().toUpperCase());
		} catch(IllegalArgumentException e) {
			System.err.printf(
					"Something went wrong when parsing the print mode. Check to make sure '%s' is one of REGULAR, MIDDLE, FIRSTMIDLAST",
					args[11]);
			return;

		}

		int maxOSOCodeLength = Integer.parseInt(args[12]);
		int maxCSCodeLength = Integer.parseInt(args[13]);
		int maxOSNOCodeLength = Integer.parseInt(args[14]);

		// Assumes the database given by the command line arg exists in the
		// billiards_database directory
		ConnectionPool pool = Admin.getConnectionPool(args[15], Utils.numThreads);

		Either<Integer, CodeTypeCollection<Integer>> max = Either.left(sum);
		if (args.length == 19) {
			int maxOSOSideSum = Integer.parseInt(args[16]);
			int maxCSSideSum = Integer.parseInt(args[17]);
			int maxOSNOSideSum = Integer.parseInt(args[18]);
			max = Either.right(new CodeTypeCollection<>(maxOSOSideSum, maxCSSideSum, 0, 0, maxOSNOSideSum));
		}

		CodeTypeCollection<Integer> maxCodeLength = new CodeTypeCollection<>(
				maxOSOCodeLength, maxCSCodeLength, 0, 0, maxOSNOCodeLength);

		final ExecutorService storageExecutor = new PriorityExecutor(Utils.numThreads);
		final ExecutorService shotExecutor = Executors.newFixedThreadPool(Utils.numThreads);
		AutoPolyVaryInstance instance = new AutoPolyVaryInstance.Builder(pool, polygon, obo, pointsList,
				printMode, storageExecutor, shotExecutor)
				.build();
		int index = 0;
		System.out.println("// Looking for " + types.toString());
		System.out.printf(
				"// +---------- AutoPolyVary running on %d hole(s): %d shots, %d subdivisions, and %d to %d moves----------+%n",
				instance.end - instance.start + 1,
				shots,
				subdivisions,
				min,
				sum);
		System.out.printf("// Max code length: CS-%d OSO-%d OSNO-%d\n", maxCodeLength.CS, maxCodeLength.OSO, maxCodeLength.OSNO);
		recurseCalculate(instance, index, min, max, shots, types, maxCodeLength, new ArrayList<>());

		System.out.println(
				"+------------------------ AutoPolyVary finished successfully ------------------------+");
		storageExecutor.shutdown();
		shotExecutor.shutdown();
	}

	private static void recurseCalculate(AutoPolyVaryInstance instance, int idx, int min,
                                         Either<Integer, CodeTypeCollection<Integer>> max, int shots,
										 CodeTypeSet types, CodeTypeCollection<Integer> maxCodeLength,
										 List<Storage> previousCodes) {

		System.out.println("\n//------------- working on point " + (idx + 1) + "-------------");
		List<Vector2> coords = instance.points.get(idx);
		Vector2 point = instance.obo.get(idx); // First point is the original
		List<Storage> storages = new ArrayList<>();

		if (!checkStorage(previousCodes, point)) {
			AutoPolyVaryAction action = new AutoPolyVaryAction(
					coords,
					min, max, maxCodeLength, types, shots,
					instance);
			storages = action.run();
		}
		if (instance.isValidIdx(idx + instance.step))
			recurseCalculate(instance, idx + instance.step, min, max,
					shots, types, maxCodeLength, storages.isEmpty() ? previousCodes : storages);
	}

	/**
	 * parseOBOandPoints processes a pointsFile to return the original OBO file as a
	 * list of Vector2
	 * and empty pixels for each OBO coordinate as a two-dimensional list of
	 * Vector2.
	 * xn represent an OBO coordinate, cn represent an empty pixel coordinate (both
	 * can be any real number)
	 * where n is an integer.
	 * The two lists correspond via their indices
	 * (index i corresponds the ith OBO coordinate to its list of empty pixels)
	 * 
	 * @param pointsFile A file of the following form
	 *                   <p>
	 *                   x1 y1
	 *                   (c1, c2)
	 *                   (c3, c4)
	 *                   ...
	 *
	 *                   x2 y2
	 *                   (c5, c6)
	 *                   ...
	 *                   </p>
	 * @return Tuple of (OBOcoordinates, pixels)
	 */
	private static Tuple2<List<Vector2>, List<List<Vector2>>> parseOBOAndPoints(String pointsFile) {
		String[] pointsStr = Utils.readFromFile(pointsFile).split("\\R");
		ArrayList<List<Vector2>> pointsLists = new ArrayList<>();
		ArrayList<Vector2> OBOcoords = new ArrayList<>();

		int i = 0;
		while (i < pointsStr.length) {
			ArrayList<Vector2> points = new ArrayList<>();
			OBOcoords.add(parseCoordinate(pointsStr[i++]));
			for (; i < pointsStr.length; ++i) {
				if (pointsStr[i].isEmpty())
					break;
				points.add(parseVector(pointsStr[i]));
			}
			pointsLists.add(points);
			++i;
		}

		return Tuple.of(OBOcoords, pointsLists);
	}

	/**
	 * <code>parseCoordinate</code> parses a Vector2 from a given string
	 * 
	 * @param s String of the form "x y" where x and y are real numbers
	 * @return A Vector2
	 */
	private static Vector2 parseCoordinate(String s) {
		String[] components = s.split("\\s+");
		double x = Double.parseDouble(components[0].trim());
		double y = Double.parseDouble(components[1].trim());
		return Vector2.create(x, y);
	}

	/**
	 * <code>parseVector</code> parses a Vector2 from a given string
	 * 
	 * @param s String of the form "(x,y)" where x and y are real numbers
	 * @return A Vector2
	 */
	private static Vector2 parseVector(String s) {
		String[] components = s.substring(1, s.length() - 1).split(",");
		double x = Double.parseDouble(components[0].trim());
		double y = Double.parseDouble(components[1].trim());
		return Vector2.create(x, y);
	}

	private static boolean checkStorage(List<Storage> previousCodes, Vector2 point) {
		for (Storage storage : previousCodes) {
			if (storage.classCodeSeq.stable) {
				final Storage.Stable stable = (Storage.Stable) storage;
				double rx = Math.toRadians(point.x);
				double ry = Math.toRadians(point.y);
				final Location location = stable.polygon.location(rx, ry);

				if (location == Location.INSIDE) {
					System.out.println("\n//------------- working on point " + point.x + ", " + point.y
							+ " -------------\nThis coordinate was filled by a code from the previous coordinate.");
					System.out.println(Utils.standard(storage.classCodeSeq, 1));
					return true;
				}
			}
		}
		return false;
	}
}
