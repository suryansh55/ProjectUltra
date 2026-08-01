package billiards.viewer;

import billiards.geometry.Rectangle;

public final class PixelRadianMap {

    // Width and height of the viewing screen in pixels
    private final int size;

    private double scale;

    // Translation in radians
    private double translateX;
    private double translateY;

    public PixelRadianMap(final int size) {
        this.size = size;
        this.scale = 0.9;
        this.translateX = -0.2;
        this.translateY = -0.2;
    }

    public double radianX(final double pixelX) {
        return pixelX * Math.PI / this.size / this.scale + this.translateX;
    }

    public double radianY(final double pixelY) {
        return pixelY * Math.PI / this.size / this.scale + this.translateY;
    }

    public double pixelX(final double radianX) {
        return (radianX - this.translateX) * this.size * this.scale / Math.PI;
    }

    public double pixelY(final double radianY) {
        return (radianY - this.translateY) * this.size * this.scale / Math.PI;
    }

    public void setScale(final double newScale) {
        this.scale = newScale;
    }

    public void scaleBy(final double scaleFactor) {
        this.scale *= scaleFactor;
    }

    public void setTranslateX(final double translateX) {
        this.translateX = translateX;
    }

    public void setTranslateY(final double translateY) {
        this.translateY = translateY;
    }

    public void translateXBy(final double value) {
        this.translateX += value;
    }

    public void translateYBy(final double value) {
        this.translateY += value;
    }

    public double pixelSize() {
        return Math.PI / (this.scale * this.size);
    }

    // Rectangle that describes the current viewing screen
    public Rectangle getViewRectangle() {
        final double minViewX = this.radianX(0);
        final double maxViewX = this.radianX(size);

        final double minViewY = this.radianY(0);
        final double maxViewY = this.radianY(size);

        final Rectangle viewRectangle = Rectangle.create(minViewX, maxViewX, minViewY, maxViewY);

        return viewRectangle;
    }

    public void setViewRectangle(final Rectangle rect) {
        final double xMin = rect.intervalX.min;
        final double yMin = rect.intervalY.min;

        this.setTranslateX(xMin);
        this.setTranslateY(yMin);

        final double width = rect.intervalX.length();
        final double height = rect.intervalY.length();

        final double largest = Math.max(width, height);

        // a scale of 1 gives us a width of pi
        final double scale = Math.PI / largest;

        this.setScale(scale);
    }

    // reset to default configuration
    public void reset() {
        this.scale = 0.9;
        this.translateX = -0.2;
        this.translateY = -0.2;
    }
}
