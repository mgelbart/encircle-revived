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

import aoi.Logger.Rep;

public class DrawUtils {

  public static int DEFAULTRENDEREDGAMEWIDTH = SimulationUtils.ARENASIZE;
  public static int DEFAULTRENDEREDGAMEHEIGHT = SimulationUtils.ARENASIZE;

  public static Paint DEFAULTPAINT = Color.BLACK;
  public static BasicStroke DEFAULTSTROKE = new BasicStroke((float) 1.);

  // The drawing functions cannot be called before this is set to true.
  private static boolean initialized = false;

  // The Paint object currently in use.
  private static Paint paint = DEFAULTPAINT;

  // The canvas the objects are drawn on.
  private static GameCanvas canvas;

  // the minimal area of game coordinates that will be rendered.
  private static Rectangle renderedGameCoords;
  
  // Initialize the module to draw objects on the specified canvas.
  public static void initialize(GameCanvas canv) {
    canvas = canv;
    
    // Transformation from overlay/game coordinates to Java graphics coordinates.
    setRenderedGameCoords(0, 0, DEFAULTRENDEREDGAMEWIDTH, DEFAULTRENDEREDGAMEHEIGHT);
    
    initialized = true;
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
    GameCanvas.offscreen().setPaint(p);
  }


  /**
   * Set the Stroke object used to draw the outlines of Shapes.
   */
  public static void setStroke(Stroke s) {
    GameCanvas.offscreen().setStroke(s);
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
    GameCanvas.offscreen().setColor(c);
    GameCanvas.offscreen().fillRect(0, 0, canvasWidth(), canvasHeight());
    GameCanvas.offscreen().setPaint(paint);
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
      GameCanvas.offscreen().fill(ss);
    else
      GameCanvas.offscreen().draw(ss);
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
    GameCanvas.offscreen().drawImage(image,
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
    if (GameCanvas.mode != GameCanvas.Mode.INGAME) return;
    GameCanvas.showBuffered();
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
  public static Point fromGameToJavaUserSpaceCoordinates(Point2D p) {
    Point2D p2d = fromGameCoords().transform(p, null);
    return new Point((int) Math.round(p2d.getX()), (int) Math.round(p2d.getY()));
  }
  
  public static Point fromJavaUserSpaceToGameCoordinates(Point2D p) {
    try {
      Point2D p2d = fromGameCoords().inverseTransform(p, null);
      return new Point((int) Math.round(p2d.getX()), (int) Math.round(p2d.getY()));
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
  
  public static GameCanvas createDefaultCanvas() {
    // The main application window
    JFrame frame = new JFrame();

    // The JLabel on which the whole GUI is drawn.
    GameCanvas canvas = new GameCanvas();
    //canvas.setBorder(null);
    canvas.setPreferredSize(new Dimension(700, 700));

    //canvas.setText("Loading...");
    
    frame.add(canvas);

    frame.setResizable(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    frame.setTitle("Encircle");
    frame.pack();
    frame.setVisible(true);
    
    return canvas;
  }

}