package billiards.viewer;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

import billiards.vary.Vary;
import billiards.vary.CodeTypeSet;
import billiards.codeseq.ClassifiedCodeSequence;

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
		CodeTypeSet types = CodeTypeSet.getDefault();
		if (args.length == 10) {
			final boolean oso = args[5].trim().equals("1");
			final boolean cs = args[6].trim().equals("1");
			final boolean cns = args[7].trim().equals("1");
			final boolean ons = args[8].trim().equals("1");
			final boolean osno = args[9].trim().equals("1");
			types = CodeTypeSet.builder().setOSO(oso).setCS(cs).setCNS(cns).setONS(ons).setOSNO(osno).build();
		}

		System.out.println(String.format("// Vary3 at (%s, %s), min = %d, max = %d, shots = %d", args[0], args[1], min,
				max, shots));
		Vary3Cmd.callV3(x, y, min, max, shots, types);
	}

	private static void callV3(final double x, final double y, final int min, final int max, final int shots,
			CodeTypeSet types) {
		final long startTime = System.currentTimeMillis();// george june 12,2019 added && !CS2cb.isSelected() &&
															// !CNS2cb.isSelected() && !ONS2cb.isSelected() &&
															// !OSNO2cb.isSelected() && !OSO2cb.isSelected()
		System.out.printf("// oso: %s, cs: %s, cns: %s, ons: %s, osno: %s \n",
			checked(types.OSO),
			checked(types.CS),
			checked(types.CNS),
			checked(types.ONS),
			checked(types.OSNO)
		);
		int count = 0;
		System.out.printf("// Found %d threads\n", Utils.numThreads);
		final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
		MutableSet<ClassifiedCodeSequence> allCodes = Vary.findCodes3(x, y, min, max, shots, types, executor);
		MutableSortedSet<ClassifiedCodeSequence> sortedCodes = new TreeSortedSet<>();
		sortedCodes.addAll(allCodes);
		ArrayList<ClassifiedCodeSequence> filteredCodes = Vary.filterCodes(new ArrayList<>(sortedCodes));

		for (ClassifiedCodeSequence code : filteredCodes) {
			++count;
			final String codeString = Utils.standard(code, count);
			System.out.println(codeString);
		}

		executor.shutdown();
		final long endTime = System.currentTimeMillis();
		System.out.printf("// Found %d codes, %d codes after filtering\n", allCodes.size(), count);
		System.out.println("// Time: " + (endTime - startTime));
		System.out.println("// Time: " + Utils.timeConvert(endTime - startTime) + "\n");

	}

	private static String checked(boolean b){
		return b ? "y" : "n";
	}
}
