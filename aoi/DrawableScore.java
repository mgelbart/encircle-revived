package aoi;

/*
  A GUI element for drawing the score.
*/


import java.awt.Shape;
import java.awt.Color;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.Font;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;


public class DrawableScore implements Drawable {

  private boolean removed = false;

  // The colors are:
  //  [0] The neutral color for the sequence
  //  [1] The color of the 1st player's score
  //  [2] The color of the 2nd player's score
  private Color[] colors;
  private boolean[] fill;
  private double[] widths;
  private Shape[] shapes;
  private GlyphVector scoreboard;

  private AffineTransform at;

  public DrawableScore(Color neutralColor, Color[] playerColors) {
    assert(playerColors.length == Simulation.NUMPLAYERS);


    fill = new boolean[1+Simulation.NUMPLAYERS];
    fill[0] = true;
    for (int j=1; j<fill.length; ++j)
      fill[j] = false;


    colors = new Color[1+Simulation.NUMPLAYERS];
    colors[0] = neutralColor;
    for (int j=1; j<colors.length; ++j)
      colors[j] = playerColors[j-1];


    widths = new double[1+Simulation.NUMPLAYERS];
    widths[0] = stdlib.StdDraw.DEFAULT_PEN_RADIUS;
    for (int j=1; j<widths.length; ++j)
      widths[j] = 3*stdlib.StdDraw.DEFAULT_PEN_RADIUS;


    // This is a bit hackz but we need to create an
    // actual graphics object in order to get the FontMetrics.
    Graphics2D graphics = (new BufferedImage(1, 1,
                                             BufferedImage.TYPE_INT_ARGB))
      .createGraphics();
    Font f = new Font("Monospaced", Font.PLAIN, 10);
    scoreboard = f.createGlyphVector(graphics.getFontMetrics(f).getFontRenderContext(), "1 2 3 4 5 6 7 *");

    double boardHeight = scoreboard.getOutline().getBounds2D().getHeight();
    double boardWidth  = scoreboard.getOutline().getBounds2D().getWidth();
    double scaleFact = 1. / boardHeight / 40.;

    at = new AffineTransform();
    at.translate(0., 0.47); // Move to the top
    at.scale(scaleFact, scaleFact);
    at.translate(-boardWidth/2, 0.); // Center
    at.scale(1, -1); // Java Graphiscs use a left-handed coordinate system.

    shapes = new Shape[1+Simulation.NUMPLAYERS];
    shapes[0] = at.createTransformedShape(scoreboard.getOutline());

    assert(colors.length == 1+Simulation.NUMPLAYERS);
    assert(widths.length == 1+Simulation.NUMPLAYERS);
    assert(shapes.length == 1+Simulation.NUMPLAYERS);
  }

  public Shape[] shapes(GameState game) {

    for (int j=0; j<Simulation.NUMPLAYERS; ++j) {
      Rectangle2D box = scoreboard.getGlyphOutline(2*game.states[j].score).getBounds2D();

      double margin = box.getWidth() * 0.35;
      box = new Rectangle2D.Double(box.getX()-margin,
                                   box.getY()-margin,
                                   box.getWidth()+2*margin,
                                   box.getHeight()+2*margin);
	    
      shapes[1+j] = at.createTransformedShape(box);
    }
	
    return shapes;
  }


  public boolean[] fill() { return fill; }


  public Color[] color() { return colors; }


  public double[] penWidth() { return widths; }


  public Image img(GameState state) { return null; }
  public int[] imgLocation() { return null; }
  public boolean ingameCoordinates() { return false; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }

}
