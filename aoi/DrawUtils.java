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
      at a fixed location in the frame. The x and y
      coordinates are specified in pixels between
      plus and minus frameWidth/2 and frameHeight/2,
      respectively

    * The in-game-coordinates are used to draw
      objects relative to other objects in the game.
      Which part of the in-game-coordinate system
      is shown and how they are scaled at any given
      time is up to the implementation. The default
      visible area is between plus and minus
      Simulation.ARENASIZE/2.




  Note: Many of the methods in this class derive
  originally from a class called StdDraw used in
  computer science classes at Princeton University.
  For details see:

  http://introcs.cs.princeton.edu/java/home/
  
 */


import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import javax.imageio.ImageIO;


public class DrawUtils {

  public static int DEFAULTRENDEREDGAMEWIDTH = Simulation.ARENASIZE;
  public static int DEFAULTRENDEREDGAMEHEIGHT = Simulation.ARENASIZE;

  public static Paint DEFAULTPAINT = Color.BLACK;
  public static BasicStroke DEFAULTSTROKE = new BasicStroke((float) 1.);


  private static boolean initialized = false;

  private static AffineTransform fromOverlayCoords;
  private static AffineTransform fromGameCoords;

  private static int frameWidth;
  private static int frameHeight;


  // The Paint and Stroke objects currently in use.
  private static Paint paint;
  private static Stroke stroke;


  // Double buffered graphics
  // Offscreen stores the currently buffered image that
  // methods like shape() and image() draw on.
  // Calling show() switches this buffered image to the screen.
  private static BufferedImage offscreenImage, onscreenImage;
  private static Graphics2D offscreen, onscreen;

  // The frame that the objects are drawn in.
  private static JFrame frame;


  // Initialize the module to draw objects in the specified frame.
  public static void initialize(JFrame f, int width, int height) {
    assert(!initialized);

    assert(f != null);
    frame = f;

    assert(width > 0);
    assert(height > 0);
    frameWidth = width;
    frameHeight = height;

    // Transformation from overlay/game coordinates to Java graphics coordinates.
    setRenderedGameCoords(0, 0, DEFAULTRENDEREDGAMEWIDTH, DEFAULTRENDEREDGAMEHEIGHT);

    fromOverlayCoords = new AffineTransform();
    fromOverlayCoords.translate(frameHeight/2, frameWidth/2);
    // Java Graphics use a left handed coordinate
    // system with x || East and y || South.
    fromOverlayCoords.scale(1, -1);


    // Initialize the buffer and the screen.

    //if (frame != null) frame.setVisible(false);
    offscreenImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
    onscreenImage  = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
    offscreen = offscreenImage.createGraphics();
    onscreen  = onscreenImage.createGraphics();
    //offscreen.setColor(DEFAULT_CLEAR_COLOR);
    //offscreen.fillRect(0, 0, width, height);
    setPaint(DEFAULTPAINT);
    setStroke(DEFAULTSTROKE);
    clear(Color.BLACK);

    // add antialiasing
    RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                                              RenderingHints.VALUE_ANTIALIAS_ON);
    hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    offscreen.addRenderingHints(hints);


    // frame stuff
    ImageIcon icon = new ImageIcon(onscreenImage);
    JLabel draw = new JLabel(icon);

    //draw.addMouseListener(std);
    //draw.addMouseMotionListener(std);

    frame.setContentPane(draw);
    //frame.addKeyListener(std);    // JLabel cannot get keyboard focus
    frame.setResizable(false);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);            // closes all windows
    // frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);      // closes only current window
    frame.setTitle("AoI");
    JMenuBar menubar = new JMenuBar();
    menubar.add(new JMenu("Press here to focus!"));
    frame.setJMenuBar(menubar); // hackz... just need something focusable for keyboard events.
    frame.pack();
    frame.setVisible(true);


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

    fromGameCoords.scale(frameWidth / (double) width, frameHeight / (double) height);
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
   * In particular Paint can be just a Color object.
   */
  public static void setStroke(Stroke s) {
    stroke = s;
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
   * @param color the Color to make the background
   */
  public static void clear(Color c) {
    offscreen.setColor(c);
    offscreen.fillRect(0, 0, frameWidth, frameHeight);
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

    //System.err.println("Drawing at (" + ss.getBounds2D().getX() + ", " + ss.getBounds2D().getY() + "). gameCoordinates: " + gameCoordinates);
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
    picture(new Rectangle(x - w/2, y - h/2, w, h), image, gameCoordinates);
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

    //System.err.println("Picture at (" + xs + ", " + ys + ")");
    offscreen.drawImage(image,
                        (int) (b.getX()),
                        (int) (b.getY()),
                        (int) (b.getWidth()),
                        (int) (b.getHeight()),
                        null);
  }


  /**
   * Render the currently buffered "off-screen" image to the screen.
   */
  public static void show() {
    onscreen.drawImage(offscreenImage, 0, 0, null);
    frame.repaint();
  }


  /**
   * Return the current frame height (in pixels).
   */
  public static int height() {
    return frameHeight;
  }


  /**
   * Return the current frame width (in pixels).
   */
  public static int width() {
    return frameWidth;
  }

}