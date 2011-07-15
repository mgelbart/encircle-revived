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
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.MouseInputListener;


public class DrawUtils {

  public static int DEFAULTRENDEREDGAMEWIDTH = SimulationUtils.ARENASIZE;
  public static int DEFAULTRENDEREDGAMEHEIGHT = SimulationUtils.ARENASIZE;

  public static Paint DEFAULTPAINT = Color.BLACK;
  public static BasicStroke DEFAULTSTROKE = new BasicStroke((float) 1.);

  public static int IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;

  // The drawing functions cannot be called before this is set to true.
  private static boolean initialized = false;

  // Set to true if the canvas is resized[ Need to reallocate the buffer images when this happens.
  private static boolean canvasResized = false;
  
  // The Paint object currently in use.
  private static Paint paint;

  // Double buffered graphics
  // Offscreen stores the currently buffered image that
  // methods like shape() and image() draw on.
  // Calling show() switches this buffered image to the screen.
  private static BufferedImage offscreenImage, onscreenImage;
  private static Graphics2D offscreen, onscreen;

  // The JLabel that the objects are drawn on.
  private static JButton canvas;

  // the minimal area of game coordinates that will be rendered.
  private static Rectangle renderedGameCoords;

  // Initialize the module to draw objects on the specified canvas.
  public static void initialize(JButton canv) {
    assert(!initialized);

    assert(canv != null);
    canvas = canv;

    canvas.addComponentListener(new ResizeListener());
    
    // Transformation from overlay/game coordinates to Java graphics coordinates.
    setRenderedGameCoords(0, 0, DEFAULTRENDEREDGAMEWIDTH, DEFAULTRENDEREDGAMEHEIGHT);

    initBuffers();
    
    initialized = true;
  }
 
  private static void initBuffers() {
    // Initialize the buffer and the screen.
    offscreenImage = new BufferedImage(canvasWidth(), canvasHeight(), IMAGE_TYPE);
    onscreenImage  = new BufferedImage(canvasWidth(), canvasHeight(), IMAGE_TYPE);
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
  }
  
  public static void freeResources() {
    assert(false); // Not supported yet.

    assert(initialized);

    initialized = false;
  }
  
  // Set the minimal region of game coordinates that is rendered.
  public static void setRenderedGameCoords(int x, int y, int width, int height) {
    setRenderedGameCoords(new Rectangle(x-width/2, y-height/2, width, height));
  }

  public static void setRenderedGameCoords(Rectangle r) {
    renderedGameCoords = (Rectangle) r.clone();
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
    offscreen.fillRect(0, 0, canvasWidth(), canvasHeight());
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
      ss = fromGameCoords().createTransformedShape(s);
    else
      ss = fromOverlayCoords().createTransformedShape(s);

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
      b = fromGameCoords().createTransformedShape(box).getBounds2D();
    else
      b = fromOverlayCoords().createTransformedShape(box).getBounds2D();

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
    if (canvasResized) {
      // reallocate the buffer images and skip frame.
      canvasResized = false;
      initBuffers();
      return;
    }
    
    onscreen.drawImage(offscreenImage, 0, 0, null);
    canvas.repaint();
  }


  /**
   * Return the current canvas height (in pixels).
   */
  public static int canvasHeight() {
    return canvas.getSize().height;
  }


  /**
   * Return the current canvas width (in pixels).
   */
  public static int canvasWidth() {
    return canvas.getSize().width;
  }

  // Transform p from game coordinates to the Java GUI coordinates.
  public static Point2D fromGameToJavaUserSpaceCoordinates(Point2D p) {
    return fromGameCoords().transform(p, null);
  }
  
  public static Point2D fromJavaUserSpaceToGameCoordinates(Point2D p) {
    try {
      return fromGameCoords().inverseTransform(p, null);
    } catch (NoninvertibleTransformException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      assert(false);
      return null;
    }
  }
  
  private static AffineTransform fromGameCoords() {
    AffineTransform at = new AffineTransform();

    final double scaleFact = Math.min(canvasWidth() / (double) renderedGameCoords.width, canvasHeight() / (double) renderedGameCoords.height);
    
    at.translate(canvasWidth()/2, canvasHeight()/2);
    at.scale(scaleFact, scaleFact); // keep the aspect ratio
    at.scale(1, -1); // Switch to Java's left-handed coordinate system.
    return at;
  }
  
  private static AffineTransform fromOverlayCoords() {
    AffineTransform at = new AffineTransform();
    at.translate(canvasWidth()/2, canvasHeight()/2);
    // Java Graphics use a left handed coordinate
    // system with x || East and y || South.
    at.scale(1, -1);
    return at;
  }
  
  public static void addMouseListener(MouseListener l) {
    canvas.addMouseListener(l);
  }
  
  public static void addMouseMotionListener(MouseMotionListener l) {
    canvas.addMouseMotionListener(l);
  }
  
  public static void addKeyListener(KeyListener l) {
    canvas.addKeyListener(l);
  }
  
  public static class ResizeListener implements ComponentListener {
    
    public void componentResized(ComponentEvent e) {
      canvasResized = true;
    }

    public void componentHidden(ComponentEvent arg0) {}

    public void componentMoved(ComponentEvent arg0) {}

    public void componentShown(ComponentEvent arg0) {}
  }
}