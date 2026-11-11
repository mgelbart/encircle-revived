package aoi;

import java.awt.*;

/**
 *  A default Drawable implementation.
 */
public class DefaultDrawable implements Drawable {

  public static final Color thisPlayerColor = Color.RED;
  public static final String thisPlayerColorName = "Red";
  
  // Currently allow up to 8 players
  public static final Color[] otherPlayerColors = 
    new Color[]{Color.BLUE, Color.MAGENTA, Color.CYAN,
                Color.YELLOW, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};

  public static final String[] otherPlayerColorNames = 
    new String[]{"Blue", "Magenta", "Cyan",
                "Yellow", "Orange", "Pink", "Gray"};

  protected int playerNo;
  
  // default stroke, fill, and pain objects returned for all shapes
  protected Stroke stroke;
  protected Paint paint;
  protected boolean fill;
  
  private boolean removed;
  protected int nShapes;  // the number of shapes in this drawable
  
  // Connect the pts[npoints][2] with lines.
  // If close is true, the first and last points are connected.
  protected DefaultDrawable(int playerNo, Stroke stroke) {
    this.stroke = stroke;
    this.playerNo = playerNo;
    this.removed = false;
    this.paint = getColor(playerNo);
    this.fill = false;
    this.nShapes = 1;
  }

  // Returns the Color associated with the given player number.
  public static Color getColor(int playerNo) { 
    if (playerNo == ClientConfiguration.playerNo) return thisPlayerColor;
    else return otherPlayerColors[
      playerNo > ClientConfiguration.playerNo?playerNo-1:playerNo];
  }

  // Returns the name of the Color associated with the given player number.
  public static String getColorName(int playerNo) { 
    if (playerNo == ClientConfiguration.playerNo) return thisPlayerColorName;
    else return otherPlayerColorNames[
      playerNo > ClientConfiguration.playerNo?playerNo-1:playerNo];
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
      fillArray[i] = fill;
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
