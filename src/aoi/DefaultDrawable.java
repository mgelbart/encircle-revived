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
  protected int nShapes;  // the number of shapes in this drawable
  
  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  protected DefaultDrawable(int playerNo, Stroke stroke) {
    this.stroke = stroke;
    this.playerNo = playerNo;
    this.removed = false;
    this.paint = getColor(playerNo);
    this.nShapes = 1;
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
    boolean[] fillArray = new boolean[nShapes];
    for (int i = 0; i < nShapes; i++)
      fillArray[i] = false;
    return fillArray; 
  }

  // Return the Paint object used to draw the shape.
  public Paint[] paint() {
    Paint[] paintArray = new Paint[nShapes];
    for (int i = 0; i < nShapes; i++)
      paintArray[i] = paint;
    return paintArray;
  }

  // Return the pen width used to draw the shape.
  public Stroke[] stroke() {
    Stroke[] strokeArray = new Stroke[nShapes];
    for (int i = 0; i < nShapes; i++)
      strokeArray[i] = stroke;
    return strokeArray;
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

  
  public void prepare(GameState gState) { }
  public void postpare(GameState gState) { }
  
}
