package aoi;

import java.awt.*;

/**
 *  A class used to pass a sequence of points to the GUI
 *  to be connected by lines. Can be used to render polygons
 *  or open paths.
 */
public class DrawablePlayerComponent implements Drawable {

  private boolean removed = false;

  private Paint paint;
  protected Stroke stroke;
  protected int playerNo;
  
  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  public DrawablePlayerComponent(int playerNo, Stroke stroke) {
    this.stroke = stroke;
    this.playerNo = playerNo;

  }

  // TODO should depend on your color
  public static Color getColor(int playerNo) { 
    return (playerNo == 0) ? Color.RED : Color.BLUE;
  }
  
  // Returns the shape to be drawn, if any.
  // May depend on the current game state.
  public Shape[] shapes(GameState state) {
    return new Shape[0];
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
