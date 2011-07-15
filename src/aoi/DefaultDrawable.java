package aoi;

import java.awt.*;

/**
 *  A default Drawable implementation.
 */
public class DefaultDrawable implements Drawable {

  protected Stroke stroke;
  protected int playerNo;
  private Paint paint;
  private boolean removed;
  
  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  protected DefaultDrawable(int playerNo, Stroke stroke) {
    this.stroke = stroke;
    this.playerNo = playerNo;
    this.removed = false;
    this.paint = getColor(playerNo);
  }

  // TODO should depend on your color
  public static Color getColor(int playerNo) { 
    return (playerNo == ClientConfiguration.playerNo) ? Color.RED : Color.BLUE;
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

  public boolean[] fill() { 
    return new boolean[]{false}; 
  }

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

  
  // All the stuff you want to do before returning shapes, etc.
  public void prepare(GameState state) { }
  
//All the stuff you want to do after returning shapes, etc.
  public void postpare(GameState state) { }
  
}
