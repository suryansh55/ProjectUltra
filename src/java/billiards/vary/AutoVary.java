package billiards.vary;


import billiards.codeseq.CodeTypeCollection;
import billiards.geometry.Vector2;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeTypeSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;

public class AutoVary {

    /**
     * <b>Jeff Khuu</b><br>
     * <b>Jun 12, 2026</b>
     * <p>
     * Runs the <code>autoVary</code> algorithm for max code lengths for each Code Type in types
     * </p>
     * @param point Point to run algorithm on
     * @param min Minimum side sum
     * @param max Maximum side sum
     * @param shots Number of shots to perform (same as Vary3)
     * @param types Code Types to run algorithm on
     * @param instance Current instance of an AutoPolyVary
     * @return Set of found code sequences (classified)
     */
    public static MutableSortedSet<ClassifiedCodeSequence> autoVary(final Vector2 point, int min, int max, int shots,
                                                                    CodeTypeSet types, AutoPolyVaryInstance instance){
        int iterate = 0;
        int step = 50;

        int CSmin = min;
        int CSstep = 0;
        int OSmin = min;
        int OSstep = 0;

        CodeTypeSet noCS = CodeTypeSet.builder()
                .setOSO(types.OSO)
                .setCS(false)
                .setCNS(types.CNS)
                .setONS(types.ONS)
                .setOSNO(types.OSNO)
                .build();
        CodeTypeSet csOnly = CodeTypeSet.builder().setCS(types.CS).build();

        for (int i = 0; i < iterate + 1; i++) {
            final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
            if(types.CS) {
                codesFound.addAll(Vary.findCodes3(point.x, point.y, CSmin, max+ CSstep, shots, csOnly, instance.shotExecutor));
            }

            codesFound.addAll(Vary.findCodes3(point.x, point.y, OSmin, max + OSstep, shots, noCS, instance.shotExecutor));

            if (codesFound.isEmpty()) {
                CSmin = max;
                CSstep += step;
                OSmin = max;
                OSstep += OSstep;

            } else {
                return codesFound;
            }
        }
        return new TreeSortedSet<>();
    }

    /**
     * <b>Jeff Khuu</b><br>
     * <b>Jun 12, 2026</b>
     * <p>
     * Runs the <code>autoVary</code> algorithm for a given point with different maximum side sums per code type.
     * </p>
     * @param point Point to run algorithm on
     * @param min Minimum side sum
     * @param max Maximum side sums differentiated by code type
     * @param shots Number of shots to perform (same as Vary3)
     * @param instance Current instance of an AutoPolyVary
     * @return Set of found code sequences (classified)
     */
    public static MutableSortedSet<ClassifiedCodeSequence> autoVary(final Vector2 point, int min, CodeTypeCollection<Integer> max,
                                                                    int shots, CodeTypeSet types, AutoPolyVaryInstance instance){
        final int iterate = 0;
        final int step = 50;
        int CSmin = min;
        int CSstep = 0;
        int OSmin = min;
        int OSstep = 0;

        final CodeTypeSet noCS = CodeTypeSet.builder()
                .setOSO(types.OSO && max.OSO > 0)
                .setCNS(types.CNS && max.CNS > 0)
                .setONS(types.ONS && max.ONS > 0)
                .setOSNO(types.OSNO && max.OSNO > 0)
                .build();
        final CodeTypeSet onlyCS = CodeTypeSet.builder().setCS(types.CS && max.CS > 0).build();

        for (int i = 0; i < iterate + 1; i++) {
            final MutableSortedSet<ClassifiedCodeSequence> codesFound = new TreeSortedSet<>();
            codesFound.addAll(Vary.findCodes3(point.x, point.y, CSmin, max.CS + CSstep, shots, onlyCS, instance.shotExecutor));
            codesFound.addAll(Vary.findCodes3(point.x, point.y, OSmin, Math.max(max.OSO, max.OSNO) + OSstep, shots, noCS, instance.shotExecutor));

            codesFound.removeIf(code -> {
                int maxVal = max.get(code.codeType);
                return maxVal != 0 && code.codeSum >= maxVal;
            });

            if (codesFound.isEmpty()) {
                CSmin = max.CS;
                CSstep += step;
                OSmin = Math.max(max.OSO, max.OSNO);
                OSstep += OSstep;
            } else {
                return codesFound;
            }
        }

        return new TreeSortedSet<>();
    }
}
