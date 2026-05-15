package billiards.viewer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.util.FastMath;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.vary.VaryCS;
import billiards.vary.Vary3;

public final class Vary3Cmd {

	public static void main(String[] args) {
		if (args.length != 5 && args.length != 10) {
			System.err.println("// Error: Malformed command line arguments to vary3");
			System.err.println("// Received the following arguments:");
			System.err.printf("// ");
			for (String arg : args) {
				System.err.printf("%s ", arg);
			}
			System.err.println(
					"// Expected arguments of the form: \"x y min max shots\" or \"x y min max shots oso cs cns ons osno\" ");
			return;
		}
		final double x = Double.parseDouble(args[0].trim());
		final double y = Double.parseDouble(args[1].trim());
		final int min = Integer.parseInt(args[2].trim());
		final int max = Integer.parseInt(args[3].trim());
		final int shots = Integer.parseInt(args[4].trim());

		// types must be in the order: OSO CS CNS ONS OSNO
		boolean[] types = { true, true, false, false, true };
		if (args.length == 10) {
			final boolean oso = args[5].trim().equals("1");
			final boolean cs = args[6].trim().equals("1");
			final boolean cns = args[7].trim().equals("1");
			final boolean ons = args[8].trim().equals("1");
			final boolean osno = args[9].trim().equals("1");
			types = new boolean[] { oso, cs, cns, ons, osno };
		}

		System.out.println(String.format("// Vary3 at (%s, %s), min = %d, max = %d, shots = %d", args[0], args[1], min,
				max, shots));
		Vary3Cmd.callV3(x, y, min, max, shots, types);
	}

	private static void callV3(final double x, final double y, final int min, final int max, final int shots,
			boolean[] types) {
		final long startTime = System.currentTimeMillis();// george june 12,2019 added && !CS2cb.isSelected() &&
															// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
															// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
		System.out.printf("// oso: %s, cs: %s, cns: %s, ons: %s, osno: %s \n",
			checked(types[0]),
			checked(types[1]),
			checked(types[2]),
			checked(types[3]),
			checked(types[4])
		);
		int count = 0;
		final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
		for (ClassifiedCodeSequence code : findCodes3(x, y, min, max, shots, types, executor)) {
			++count;
			final String codeString = Utils.standard(code, count);
			System.out.println(codeString);
		}
		executor.shutdown();
		final long endTime = System.currentTimeMillis();
		System.out.println("// Time: " + (endTime - startTime));
		System.out.println("// Time: " + Utils.timeConvert(endTime - startTime) + "\n");

	}

	private static String checked(boolean b){
		return b ? "y" : "n";
	}

    public static MutableSet<ClassifiedCodeSequence> findCodes3(
        final double xCoord, final double yCoord, final int min, final int max, final double shots,
        final boolean[] types, final ExecutorService executor) {

        final double xRad = FastMath.toRadians(xCoord);
        final double yRad = FastMath.toRadians(yCoord);

        final double base = Math.sin(xRad + yRad);

        final MutableSet<ClassifiedCodeSequence> codeSeqs = new UnifiedSet<>();

        final MutableList<ClassifiedCodeSequence> futures = new FastList<>();
        final MutableList<Future<MutableList<ClassifiedCodeSequence>>> futures2 = new FastList<>();

        final double increment = base / (shots + 1);

        StringBuilder selectedTypes = new StringBuilder();

        //transfer this to backend checking if right type
        if (types[0] ) selectedTypes.append("OSO ");
        if (types[1] ) selectedTypes.append("CS ");
        if (types[2] ) selectedTypes.append("CNS ");
        if (types[3] ) selectedTypes.append("ONS ");
        if (types[4] ) selectedTypes.append("OSNO ");

        String reqTypes = selectedTypes.toString().trim();
        //run the CS-specific code
        if (types[1]) {
        	double xAngle = Double.valueOf(xRad);
        	double yAngle = Double.valueOf(yRad);

        	for (int i = 0; i < 3; i++) {

        		final Double finX = xAngle;
        		final Double finY = yAngle;

                final Future<MutableList<ClassifiedCodeSequence>> future =
	                executor.submit(() -> VaryCS.fireAway(min, max, finX, finY,reqTypes));
	            // final Future<MutableList<ClassifiedCodeSequence>> future =
	            //     executor.submit(() -> VaryCS.fireAway(min, max, finX, finY));


                try {
                    MutableList<ClassifiedCodeSequence> result = future.get();
                    futures.addAll(result);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);  // or handle it however you need
                }

	            double zAngle = Double.valueOf(Math.PI - xAngle - yAngle);
	            xAngle = Double.valueOf(yAngle);
	        	yAngle = Double.valueOf(zAngle);
	        }
        }
        //run the non-CS-specific code
        if(types[0] || types[2] || types[3] || types[4]) {

	        for (int count = 1; count <= shots; ++count) {

	            final double pos = count * increment;

	            final Future<MutableList<ClassifiedCodeSequence>> future =
	            		executor.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos,reqTypes));
                        //executor.submit(() -> Vary3.fireAway(min, max, xRad, yRad, pos));

                futures2.add(future);
	        }
            for (Future<MutableList<ClassifiedCodeSequence>> future : futures2) {
                try {
                    MutableList<ClassifiedCodeSequence> partial = future.get(); // get the actual list
                    futures.addAll(partial); // now addAll on MutableList, not Future
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace(); // handle exceptions as needed
                }
            }
        }

        codeSeqs.addAll(futures);

        return codeSeqs;
    }
}
