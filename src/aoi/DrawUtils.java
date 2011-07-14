package aoi;

/*
  A simple interface for drawing objects in the
  game arena and the control panels.

  The initialize() method must be called before
  trying to draw anything.

  The freeResources() method must be called before
  trying to re-initialize.

  Note: This class uses two distinct coordinate
  systems: overlay and in-game-coordinates
  coordinates. Both coordinate systems are right-
  handed and centered at (0,0).

    * Overlay coordinates can be used to draw objects
      at a fixed location on the canvas. The x and y
      coordinates are specified in pixels between
      plus and minus canvasWidth/2 and canvasHeight/2,
      respectively

    * The in-game-coordinates are used to draw
      objects relative to other objects in the game.
      Which part of the in-game-coordinate system
      is shown and how they are scaled at any given
      time is up to the implementation. The default
      visible area is between plus and minus
      SimulationUtils.ARENASIZE/2.




  Note: Many of the methods in this class derive
  originally from a class called StdDraw used in
  computer science classes at Princeton University.
  For details see:

  http://introcs.cs.princeton.edu/java/home/
  
 */

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;


public class DrawUtils {

  public static int DEFAULTRENDEREDGAMEWIDTH = SimulationUtils.ARENASIZE;
  public static int DEFAULTRENDEREDGAMEHEIGHT = SimulationUtils.ARENASIZE;

  public static Paint DEFAULTPAINT = Color.BLACK;
  public static BasicStroke DEFAULTSTROKE = new BasicStroke((float) 1.);

  public static int IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;


  private static boolean initialized = false;

  private static AffineTransform fromOverlayCoords;
  private static AffineTransform fromGameCoords;

  private static int canvasWidth;
  private static int canvasHeight;


  // The Paint object currently in use.
  private static Paint paint;

  // Double buffered graphics
  // Offscreen stores the currently buffered image that
  // methods like shape() and image() draw on.
  // Calling show() switches this buffered image to the screen.
  private static BufferedImage offscreenImage, onscreenImage;
  private static Graphics2D offscreen, onscreen;

  // The JLabel that the objects are drawn on.
  private static JLabel canvas;


  // Initialize the module to draw objects on the specified canvas.
  public static void initialize(JLabel canv) {
    assert(!initialized);

    assert(canv != null);
    canvas = canv;

    canvasWidth = canvas.getSize().width;
    canvasHeight = canvas.getSize().height;

    // Transformation from overlay/game coordinates to Java graphics coordinates.
    setRenderedGameCoords(0, 0, DEFAULTRENDEREDGAMEWIDTH, DEFAULTRENDEREDGAMEHEIGHT);

    fromOverlayCoords = new AffineTransform();
    fromOverlayCoords.translate(canvasHeight/2, canvasWidth/2);
    // Java Graphics use a left handed coordinate
    // system with x || East and y || South.
    fromOverlayCoords.scale(1, -1);


    // Initialize the buffer and the screen.
    offscreenImage = new BufferedImage(canvasWidth, canvasHeight, IMAGE_TYPE);
    onscreenImage  = new BufferedImage(canvasWidth, canvasHeight, IMAGE_TYPE);
    offscreen = offscreenImage.createGraphics();
    onscreen  = onscreenImage.createGraphics();
    setPaint(DEFAULTPAINT);
    setStroke(DEFAULTSTROKE);
    clear(Color.BLACK);

    // add antialiasing
    RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                                              RenderingHints.VALUE_ANTIALIAS_ON);
    hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    offscreen.addRenderingHints(hints);


    ImageIcon icon = new ImageIcon(onscreenImage);
    canvas.setIcon(icon);

    initialized = true;
  }

  public static void freeResources() {
    assert(false); // Not supported yet.

    assert(initialized);

    fromOverlayCoords = null;
    fromGameCoords = null;

    initialized = false;
  }


  // Set the region of game coordinates that is rendered.
  public static void setRenderedGameCoords(int x, int y, int width, int height) {
    fromGameCoords = new AffineTransform();

    fromGameCoords.scale(canvasWidth / (double) width, canvasHeight / (double) height);
    fromGameCoords.translate(-x + width/2, -y + height/2);
    fromGameCoords.scale(1, -1); // Switch to Java's left-handed coordinate system.
  }


  /**
   * Set the Paint object used to draw Shapes.
   * In particular Paint can be just a Color object.
   */
  public static void setPaint(Paint p) {
    paint = p;
    offscreen.setPaint(p);
  }


  /**
   * Set the Stroke object used to draw the outlines of Shapes.
   */
  public static void setStroke(Stroke s) {
    offscreen.setStroke(s);
  }


  /**
   * Convenience method that sets the current stroke to a
   * BasicStroke of width w. w is specified in pixels.
   */
  public static void useBasicStroke(float w) {
    setStroke(new BasicStroke(w));
  }


  /**
   * Clear the buffered image with the given color.
   * @param c the Color to make the background
   */
  public static void clear(Color c) {
    offscreen.setColor(c);
    offscreen.fillRect(0, 0, canvasWidth, canvasHeight);
    offscreen.setPaint(paint);
  }


  /**
   * Buffer a Shape object.
   * @param s the shape to be drawn.
   * @param gameCoordinates determines whether to draw the object in game or overlay coordinates.
   * @param fill determines whether the shape is filled or only the outline drawn.
   */
  public static void shape(Shape s, boolean gameCoordinates, boolean fill) {

    Shape ss;
    if (gameCoordinates)
      ss = fromGameCoords.createTransformedShape(s);
    else
      ss = fromOverlayCoords.createTransformedShape(s);

    //Logger.println(Logger.RepetitionRate.FREQUENT, "Drawing at (" + ss.getBounds2D().getX() + ", " + ss.getBounds2D().getY() + "). gameCoordinates: " + gameCoordinates);
    if (fill)
      offscreen.fill(ss);
    else
      offscreen.draw(ss);
  }


  /**
   * Buffer an Image object centered at (x, y) and scaled to size w-by-h.
   * @param x the center x co-ord of the image
   * @param y the center y co-ord of the image
   * @param w the width of the image
   * @param h the height of the image
   * @param image the Image object to draw
   * @param gameCoordinates determines whether to draw the object in game or overlay coordinates.
   */
  public static void picture(int x, int y, int w, int h, Image image, boolean gameCoordinates) {
    picture(new Rectangle((int) Math.round(x - w/2.), (int) Math.round(y - h/2.), w, h), image, gameCoordinates);
  }

  /**
   * Buffer an Image object centered at (x, y) and scaled to size w-by-h.
   * @param box the bounding box of the image
   * @param image the Image object to draw
   * @param gameCoordinates determines whether to draw the object in game or overlay coordinates.
   */
  public static void picture(Rectangle box, Image image, boolean gameCoordinates) {

    Rectangle2D b;
    if (gameCoordinates)
      b = fromGameCoords.createTransformedShape(box).getBounds2D();
    else
      b = fromOverlayCoords.createTransformedShape(box).getBounds2D();

    //Logger.println(Logger.RepetitionRate.FREQUENT, "Picture at (" + xs + ", " + ys + ")");
    offscreen.drawImage(image,
                        (int) Math.round((b.getX())),
                        (int) Math.round((b.getY())),
                        (int) Math.round((b.getWidth())),
                        (int) Math.round((b.getHeight())),
                        null);
  }


  /**
   * Render the currently buffered "off-screen" image to the screen.
   */
  public static void show() {
    onscreen.drawImage(offscreenImage, 0, 0, null);
    canvas.repaint();
  }


  /**
   * Return the current canvas height (in pixels).
   */
  public static int height() {
    return canvasHeight;
  }


  /**
   * Return the current canvas width (in pixels).
   */
  public static int width() {
    return canvasWidth;
  }

  // Transform p from game coordinates to the Java GUI coordinates.
  public static Point2D fromGameToJavaUserSpaceCoordinates(Point2D p) {
    return fromGameCoords.transform(p, null);
  }
  
}