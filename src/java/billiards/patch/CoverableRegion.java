package billiards.patch;

import java.util.ArrayList;

import billiards.geometry.ConvexPolygon;
import billiards.utils.Polygon;

/**
 * <b>Jeff Khuu</b><br>
 * <b>Aug 10, 2026</b>
 * <p>
 * <code>CoverableRegion</code> represents a polygon, either from a cover polygon or a patch polygon.
 * </p>
 */
public class CoverableRegion {
    public String name;
    public String polygon;

    /**
     * <b>Jeff Khuu</b><br>
     * <b>Aug 10, 2026</b>
     * Creates a new <code>CoverableRegion</code> with the given name and polygon.
     * @param name the name of the region should be unique within a list of regions
     * @param polygon string representing a cleaned polygon, with each vertex on a new line and coordinates separated by a space
     */
    public CoverableRegion(String name, String polygon) {
        this.name = name;
        this.polygon = polygon;
    }

    /**
    * <b>Jeff Khuu</b><br>
    * <b>Aug 10, 2026</b>
     * @param patchesString string representing multiple polygons, with each polygon separated by two newlines
     * @return an ArrayList of ConvexPolygon objects parsed from the input string
     */
    public static ArrayList<ConvexPolygon> parsePatchPolygons(String patchesString) {
        final ArrayList<ConvexPolygon> patches = new ArrayList<>();
        if (patchesString.trim().isEmpty()) {
            return patches;
        }

        final String[] patchStrings = patchesString.split("\\R\\R");
        for (int i = 0; i < patchStrings.length; i++) {
            final String patchString = patchStrings[i].trim();
            if (patchString.isEmpty()) {
                continue;
            }
            patches.add(Polygon.createConvexPolygon(patchString));
        }
        return patches;
    }

}
