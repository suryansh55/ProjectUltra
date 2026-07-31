package billiards.patch;

import billiards.geometry.ConvexPolygon;

public class Patch {
    public ConvexPolygon patchArea;
    public int magnification, digits, empties;

    public Patch(ConvexPolygon patchArea, int magnification, int digits, int empties) {
        this.patchArea = patchArea;
        this.magnification = magnification;
        this.digits = digits;
        this.empties = empties;
    }
}
