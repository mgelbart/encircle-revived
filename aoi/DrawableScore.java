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
    private double[] widths;
    private Shape[] shapes;
    private GlyphVector scoreboard;

    private AffineTransform at;

    public DrawableScore(Color neutralColor, Color[] playerColors) {
	assert(playerColors.length == Simulation.NUMPLAYERS);

	colors = new Color[1+Simulation.NUMPLAYERS];
	colors[0] = neutralColor;
	for (int j=1; j<colors.length; ++j)
	    colors[j] = playerColors[j-1];


	widths = new double[1+Simulation.NUMPLAYERS];
	for (int j=0; j<widths.length; ++j)
	    widths[j] = stdlib.StdDraw.DEFAULT_PEN_RADIUS;


	// This is a hackz but we need to create an
	// actual graphics object in order to get the FontMetrics.
	Graphics2D graphics = (new BufferedImage(10000, 10000,
						 BufferedImage.TYPE_INT_ARGB))
	                      .createGraphics();
	Font f = new Font("Monospaced", Font.PLAIN, 10);
        scoreboard = f.createGlyphVector(graphics.getFontMetrics(f).getFontRenderContext(), "1 2 3 4 5 6 7 *");

	double fontHeight = scoreboard.getOutline().getBounds2D().getHeight();
	double scaleFact = 1. / fontHeight / 40.;
	// System.err.println("font scale fact.: " + scaleFact);

	at = new AffineTransform();
	at.translate(-0.15, 0.45);
	at.scale(scaleFact, scaleFact);
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
	    
	    // TODO: shift the boxes slightly for different players so that they
	    //       don't overlap if the score is the same.
	    shapes[1+j] = at.createTransformedShape(box);
	}
	
        return shapes;
    }


    public Color[] color() { return colors; }


    public double[] width() { return widths; }


    public Image img(GameState state) { return null; }
    public int[] imgLocation() { return null; }
    public boolean ingameCoordinates() { return false; }
    public void remove() { removed = true; }
    public boolean isRemoved() { return removed; }

}