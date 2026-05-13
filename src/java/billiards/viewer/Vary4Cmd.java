package billiards.viewer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import billiards.codeseq.ClassifiedCodeSequence;

/**
Command line Program mimicking the behaviour of Vary4. Useful for parallel computation.

*/
class Vary4Cmd {
	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("// Error: Malformed command line arguments to vary4");
			System.out.println("// Received the following arguments:");
			System.out.printf("// ");
			for (String arg : args) {
				System.out.printf("%s ", arg);
			}
			System.out.println();
			return;
		}
		final double x = Double.parseDouble(args[0].trim());
		final double y = Double.parseDouble(args[1].trim());
		final int min = Integer.parseInt(args[2].trim());
		final int max = Integer.parseInt(args[3].trim());

		System.out.println(String.format("// Vary4 at (%s, %s), min = %d, max = %d", args[0], args[1], min, max));
		callV4(x, y, min, max);
	}
	// boolean[] types should be in the order OSO, CS, CNS, ONS, OSNO, CS2
	private static void callV4(final double x, final double y, final int min, final int max) {
		//					OSO	  CS	CNS	   ONS    OSNO	CS2
		boolean[] types = { true, true, false, false, true, false };
		final long startTime = System.currentTimeMillis();// george june 12,2019 added && !CS2cb.isSelected() &&
															// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
															// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
		final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
		int count = 0;
		for (ClassifiedCodeSequence code : BoyanMenu.findCodes4(x, y, min, max, 0, types)) {
			++count;
			final String codeString = Utils.standard(code, count);
			System.out.println(codeString);
		}
		executor.shutdown();
		final long endTime = System.currentTimeMillis();
		System.out.println("// Time: " + (endTime - startTime));
		System.out.println("// Time: " + Utils.timeConvert(endTime - startTime) + "\n");

	}
}
