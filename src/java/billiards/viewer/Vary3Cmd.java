package billiards.viewer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import billiards.codeseq.ClassifiedCodeSequence;

public final class Vary3Cmd {

	public static void main(String[] args) {
		if (args.length != 5) {
			System.err.println("// Error: Malformed command line arguments to vary3");
			System.err.println("// Received the following arguments:");
			System.err.printf("// ");
			for (String arg : args) {
				System.err.printf("%s ", arg);
			}
			System.err.println();
			return;
		}
		final double x = Double.parseDouble(args[0].trim());
		final double y = Double.parseDouble(args[1].trim());
		final int min = Integer.parseInt(args[2].trim());
		final int max = Integer.parseInt(args[3].trim());
		final int shots = Integer.parseInt(args[4].trim());

		System.out.println(String.format("// Vary3 at (%s, %s), min = %d, max = %d, shots = %d", args[0], args[1], min,
				max, shots));
		Vary3Cmd.callV3(x, y, min, max, shots);
	}

	private static void callV3(final double x, final double y, final int min, final int max, final int shots) {
		//					OSO	  CS    CNS    ONS    OSNO  
		boolean[] types = { true, true, false, false, true };
		final long startTime = System.currentTimeMillis();// george june 12,2019 added && !CS2cb.isSelected() &&
															// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
															// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
		int count = 0;
		final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
		for (ClassifiedCodeSequence code : BoyanMenu.findCodes3(x, y, min, max, shots, types, executor)) {
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

