package billiards.viewer;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import billiards.codeseq.Storage;
import billiards.codeseq.CodeTypeCollection;
import billiards.codeseq.CodeTypeSet;
import billiards.database.Admin;
import billiards.vary.VaryLAction;
import billiards.vary.VaryLInstance;
import billiards.wrapper.ConnectionPool;
import billiards.geometry.Location;
import billiards.geometry.Vector2;
import javaslang.Tuple2;
import javaslang.collection.Array;
import javaslang.control.Either;
import static billiards.viewer.Utils.readFromFile;

public final class VaryLCmd {

	public static void main(String[] args) {
		// Give an explicit usage message
		if (args.length != 17) {
			System.err.println("// Error: Malformed command line arguments to LiMVL");
			System.err.println("// Received the following arguments:");
			System.err.printf("// ");
			for (String arg : args) {
				System.err.printf("%s ", arg);
			}
			System.err.println();
			System.err.println(
					"// Expected arguments of the form: pathToPoints pathToCoverStables min max shots oso cs cns ons osno"
							+ "maxOSOCodeLength maxCSCodeLength maxOSNOCodeLength maxOSOSideSum maxCSSideSum maxOSNOSideSum databaseName");
			return;
		}

		// Retrieve the points from the point path
		/*
		 * A valid file containing coordinates is a file containing any number of
		 * lines of two comma-separated integers
		 * Example:
		 * 30,30
		 * 40,50
		 */
		List<Vector2> coords = new ArrayList<Vector2>();
		try {
			String contents = readFromFile(args[0]);
			List<String> strings = Arrays.asList(contents.split(System.lineSeparator()));
			for (String s : strings) {
				coords.add(parseCoordinate(s));
			}
		} catch (InvalidPathException e) {
			System.err.println(e); // We choose to give explicit exception messages since this part of the program
									// is user-facing
			System.err.printf(
					"Something went wrong parsing the path \"%s\" to the points.\n",
					args[0]);
			return;
		} catch (NumberFormatException e) {
			System.err.println(e);
			System.err.printf(
					"Something went wrong when parsing coordinates. Seems like one of the values is not an integer.");
			return;
		} catch (IllegalArgumentException e) {
			System.err.println(e);
			System.err.printf(
					"Something went wrong when parsing a set of coordinates. Seems like one of the pairs is malformed");
			return;
		}

		// Retrieve the stables from the cover path
		List<String> codeList = new ArrayList<>();
		try {
			String contents = readFromFile(args[1]);
			codeList = Arrays.asList(contents.split(System.lineSeparator()));
			// NOTE: Strictly speaking we should also validate whether each line of the file
			// is a valid codesequence i.e we should build a List<CodeSequence> instead but
			// this implementation can be improved on later.
			codeList.replaceAll(Utils::tripleTrimmer);
		} catch (InvalidPathException e) {
			System.err.println(e);
			System.err.printf(
					"Something went wrong parsing the path \"%s\" to the cover's stables. Using empty code list.\n",
					args[1]);
		}
		// Attempt to parse each of the command line arguments
		Array<Vector2> points = Array.ofAll(coords);
		int min = Integer.parseInt(args[2]);
		int sum = Integer.parseInt(args[3]); // Use the name sum to denote the default maximum side sum
		int shots = Integer.parseInt(args[4]);
		CodeTypeSet types = CodeTypeSet.builder()
				.setOSO(args[5].equals("1"))
				.setCS(args[6].equals("1"))
				.setCNS(args[7].equals("1"))
				.setONS(args[8].equals("1"))
				.setOSNO(args[9].equals("1"))
				.build();
		Either<Integer, CodeTypeCollection<Integer>> max;
		int maxOSOCodeLength = Integer.parseInt(args[10]);
		int maxCSCodeLength = Integer.parseInt(args[11]);
		int maxOSNOCodeLength = Integer.parseInt(args[12]);
		int maxOSOSideSum = Integer.parseInt(args[13]);
		int maxCSSideSum = Integer.parseInt(args[14]);
		int maxOSNOSideSum = Integer.parseInt(args[15]);
		// Assumes the database given by the command line arg exists in the
		// billiards_database directory
		ConnectionPool pool = Admin.getConnectionPool(args[16], Utils.numThreads);

		max = Either.right(new CodeTypeCollection<Integer>(
				maxOSOSideSum,
				maxCSSideSum,
				sum,
				sum,
				maxOSNOSideSum));
		CodeTypeCollection<Integer> maxCodeLengths = new CodeTypeCollection<Integer>(
				maxOSOCodeLength,
				maxCSCodeLength,
				0,
				0,
				maxOSNOCodeLength);

		// Create the executors used for calculation
		final ExecutorService storageExecutor = new PriorityExecutor(Utils.numThreads);
		final ExecutorService shotExecutor = Executors.newFixedThreadPool(Utils.numThreads); // This can be a default

		// Perform the actual VaryL calculation
		System.out.println(
				"//~~~~~~~~~~~~~~~~~~~~~~~ middleVaryL with " + points.size() + " points ~~~~~~~~~~~~~~~~~~~~~~~"); // added
		System.out.println("// Looking for: " + types);
		System.out.printf("// Max code length: CS-%d OSO-%d OSNO-%d\n",
				maxCodeLengths.CS,
				maxCodeLengths.OSO,
				maxCodeLengths.OSNO);
		if (max.isRight()) {
			System.out.printf("// Override side sums: CS-%d OSO-%d OSNO-%d\n",
					max.get().CS,
					max.get().OSO,
					max.get().OSNO);
		} else {
			System.out.printf("// MVL: %d shots and %d to %d moves%n", shots, min, sum);
		}

		VaryLInstance instance = new VaryLInstance.Builder(points, codeList, pool, storageExecutor, shotExecutor)
				.setPrintMid(true)
				.setPrintFirstLast(true)
				.setMaxPrintNum(0) // Set to 0 to print all codes
				.build();

		int index = 0;
		int codesFound = recursiveCalculate(instance, index, min, max, shots, maxCodeLengths, types,
				new ArrayList<>());
		System.out.println(
				"//~~~~~~~~~~~~~~~~~~~~~~~~~~~ " + codesFound + " codes found total ~~~~~~~~~~~~~~~~~~~~~~~~~~~");

		System.out.println(
				"+-------------- " + (instance.printMid ? "MiddleVaryL" : "VaryL") + " Completed --------------+");
		System.out.println();

		storageExecutor.shutdown();
		shotExecutor.shutdown();
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 25, 2026</b>
	 * <p>
	 * <code>recursiveCalculate</code> recursively runs the VaryL algorithm mutating
	 * the output stream with the results of each calculation
	 * </p>
	 * 
	 * @param instance       A VaryLInstance
	 * @param idx            Current index of the worked on point (within the bounds
	 *                       of coords in instance)
	 * @param min            Minimum sidesum for found codes
	 * @param max            Maximum sidesums for found codes (Either integer for
	 *                       all or CodeTypeCollection specifying for each type)
	 * @param shots          Number of shots to perform
	 * @param maxCodeLengths Maximum code lengths for each code type
	 * @param types          Code types to search for
	 * @param previousCodes  Found codes from the last calculation that was
	 *                       non-empty
	 * @return Returns the number of codes found
	 */
	private static int recursiveCalculate(VaryLInstance instance, int idx, int min,
			Either<Integer, CodeTypeCollection<Integer>> max,
			int shots, CodeTypeCollection<Integer> maxCodeLengths, CodeTypeSet types,
			List<Storage> previousCodes) {
		Vector2 point = instance.coords.get(idx);
		int codesFound = 0;
		List<Storage> storages = new ArrayList<>();

		// Check if the point has been found by previous codes
		if (!checkStorage(previousCodes, point)) {
			// If not, build a varyL action and run to calculate new codes
			VaryLAction action = new VaryLAction(instance, idx, min, shots, max, maxCodeLengths, types);
			Tuple2<Integer, List<Storage>> result = action.run();
			codesFound = result._1();
			storages = result._2();
		}

		// Then determine if we need to recurse
		if (instance.isValidIdx(idx + instance.step))
			return codesFound
					+ recursiveCalculate(instance, idx + instance.step, min, max, shots, maxCodeLengths, types,
							storages.isEmpty() ? previousCodes : storages);
		return codesFound;
	}

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 25, 2026</b>
	 * <p>
	 * checkStorage checks if the given point is filled by a code in the given
	 * previousCodes.
	 * Returns true if so, false otherwise.
	 * </p>
	 */
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

	/**
	 * <b>Jeff Khuu</b><br>
	 * <b>May 25, 2026</b>
	 * <p>
	 * <code>parseCoordinate</code> attempts to parse a valid Vector2 from a given
	 * string s. A valid string is two whitespace-separated values integer values
	 * </p>
	 * 
	 * @param s A string
	 * @return Vector2
	 * @throw IllegalArgumentException
	 * @throw NumberFormatException
	 */
	private static Vector2 parseCoordinate(String s) throws IllegalArgumentException, NumberFormatException {
		String[] split = s.split("\\s");
		if (split.length != 2) {
			String msg = String
					.format("The given string \"%s\" has an incorrect amount of whitespace-separated values.", s);
			throw new IllegalArgumentException(msg);
		}

		double x = Double.parseDouble(split[0].trim());
		double y = Double.parseDouble(split[1].trim());
		return Vector2.create(x, y);
	}
}
