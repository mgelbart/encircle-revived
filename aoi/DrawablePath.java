package aoi;

/*
  A class used to pass a sequence of points to the GUI
  to be connected by lines. Can be used to render polygons
  or open paths.
*/

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Color;

public class DrawablePath implements Drawable {

  private boolean removed = false;

  protected Path2D path;
  protected Color color;
  protected double width;

  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  public DrawablePath(int[][] pts, boolean close, Color color, double width) {
    this.color = color;
    this.width = width;

    Path2D p = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.length);

    if (pts.length > 0) {
      p.moveTo(pts[0][0], pts[0][1]);
      for (int j=1; j<pts.length; ++j) {
        p.lineTo(pts[j][0], pts[j][1]);
      }
    }

    this.path = p;
  }

  public DrawablePath(Path2D path, Color color, double width) {
    this.path = path;
    this.color = color;
    this.width = width;
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

  // Return the pen color used to draw the shape.
  public Color[] color() {
    return new Color[]{color};
  }

  // Return the pen width used to draw the shape.
  public double[] penWidth() {
    return new double[]{width};
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
