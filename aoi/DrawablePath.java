package aoi;

import java.awt.geom.Path2D;
import java.awt.*;

/**
 *  A class used to pass a sequence of points to the GUI
 *  to be connected by lines. Can be used to render polygons
 *  or open paths.
 */
public class DrawablePath implements Drawable {

  private boolean removed = false;

  protected Path2D path;
  protected Paint paint;
  protected Stroke stroke;

  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  public DrawablePath(int[][] pts, boolean close, Paint paint, Stroke stroke) {
    this.paint = paint;
    this.stroke = stroke;

    Path2D p = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.length);

    if (pts.length > 0) {
      p.moveTo(pts[0][0], pts[0][1]);
      for (int j=1; j<pts.length; ++j) {
        p.lineTo(pts[j][0], pts[j][1]);
      }
      if (close)
        p.closePath();
    }

    this.path = p;
  }

  public DrawablePath(Path2D path, Paint paint, Stroke stroke) {
    this.path = path;
    this.paint = paint;
    this.stroke = stroke;
  }

  // Returns the shape to be drawn, if any.
  // May depend on the current game state.
  public Shape[] shapes(GameState state) {
    return new Shape[]{path};
  }

  // Returns the image to be drawn, if any.
  // May depend on the current game state.
  public Image img(GameState state) {
    return null;
  }

  // Return {centerx, centery, width, height} of
  // the image, if any.
  public int[] imgLocation() {
    return null;
  }

  public boolean[] fill() { return new boolean[]{false}; }

  // Return the Paint object used to draw the shape.
  public Paint[] paint() {
    return new Paint[]{paint};
  }

  // Return the pen width used to draw the shape.
  public Stroke[] stroke() {
    return new Stroke[]{stroke};
  }

  public boolean ingameCoordinates() { return true; }

  // Remove the object from the collection of
  // objects to be drawn.
  public void remove() {
    removed = true;
  }

  // Remove the object from the collection of
  // objects to be drawn.
  public boolean isRemoved() {
    return removed;
  }

}
