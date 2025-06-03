package billiards.utils;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.viewer.Utils;

import java.util.*;

public class PrintMid {
    public static ArrayList<ClassifiedCodeSequence> printMid(Collection<ClassifiedCodeSequence> codes, final int numToPrint) {
        final CodeType[] codeTypes = {CodeType.CS, CodeType.OSO, CodeType.OSNO, CodeType.CNS, CodeType.ONS};

        long currentLength = -1;
        Map<CodeType, Map<String, ArrayList<ClassifiedCodeSequence>>> processedCodes = new HashMap<>();
        Map<CodeType, Map<String, Integer>> processedCodesLength = new HashMap<>();

        for (CodeType codeType : codeTypes) {
            processedCodes.put(codeType, new HashMap<>());
            processedCodesLength.put(codeType, new HashMap<>());
        }

        int i = numToPrint;
        int codeNum = 1;
        ArrayList<ClassifiedCodeSequence> codesPrinted = new ArrayList<>();

        for(ClassifiedCodeSequence code: codes) {
            if (i <= 0) break;

            if (currentLength == -1) {
                currentLength = code.codeLength;
            }

            if (code.codeLength == currentLength) {
                processedCodesLength.get(code.codeType).compute(code.oddEvenPattern,
                        (k, lengthCount) -> (lengthCount == null) ? 1 : lengthCount + 1);

                if (!processedCodes.get(code.codeType).containsKey(code.oddEvenPattern)) {
                    processedCodes.get(code.codeType).put(code.oddEvenPattern, new ArrayList<>());
                }
                processedCodes.get(code.codeType).get(code.oddEvenPattern).add(code);
            } else {
                for (CodeType codeType : codeTypes) {
                    if (i <= 0) break;

                    for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
                        if (i <= 0) break;

                        // Only print the middle one
                        final ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
                                .get(oddEvenPattern)
                                .get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);

                        --i;
                        System.out.println(Utils.standard(codeToPrint, codeNum++));

                        codesPrinted.add(codeToPrint);
                    }

                    // Clear and re-initialize for the next iteration
                    processedCodes.get(codeType).clear();
                    processedCodesLength.get(codeType).clear();
                }

                currentLength = code.codeLength;
                processedCodes.get(code.codeType).put(code.oddEvenPattern, new ArrayList<>());
                processedCodes.get(code.codeType).get(code.oddEvenPattern).add(code);
                processedCodesLength.get(code.codeType).put(code.oddEvenPattern, 1);
            }
        }

        for (CodeType codeType : codeTypes) {
            if (i <= 0) break;

            // We reached the end of the iteration, add the middle of last (code type, code length, odd-even) group
            for (String oddEvenPattern : processedCodesLength.get(codeType).keySet()) {
                if (i <= 0) break;

                if (!processedCodes.get(codeType).get(oddEvenPattern).isEmpty()) {
                    ClassifiedCodeSequence codeToPrint = processedCodes.get(codeType)
                            .get(oddEvenPattern)
                            .get(processedCodesLength.get(codeType).get(oddEvenPattern) / 2);
                    --i;
                    System.out.println(Utils.standard(codeToPrint, codeNum++));

                    codesPrinted.add(codeToPrint);
                }
            }
        }

        return codesPrinted;
    }
}
