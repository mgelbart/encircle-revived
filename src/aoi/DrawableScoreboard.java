package aoi;

import java.awt.*;
import java.awt.geom.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

/**
 *  A GUI element for drawing the score.
 */
public class DrawableScoreboard implements Drawable {

  private boolean removed = false;

  // The colors are:
  //  [0] The neutral color for the sequence
  //  [1] The color of the 1st player's score
  //  [2] The color of the 2nd player's score
  private Paint[] colors;
  private boolean[] fill;
  private Stroke[] strokes;
  private Shape[] shapes;
  private GlyphVector scoreboard;

  private AffineTransform at;

  public DrawableScoreboard(Color neutralColor, int[] sequence) {

    fill = new boolean[1+SimulationUtils.NUMPLAYERS];
    fill[0] = true;
    for (int j=1; j<fill.length; ++j)
      fill[j] = false;


    colors = new Color[1+SimulationUtils.NUMPLAYERS];
    colors[0] = neutralColor;
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      colors[j+1] = DefaultDrawable.getColor(j);


    strokes = new Stroke[1+SimulationUtils.NUMPLAYERS];
    strokes[0] = DrawUtils.DEFAULTSTROKE;
    Stroke highlightStroke = new BasicStroke(DrawUtils.DEFAULTSTROKE.getLineWidth() * 3);
    for (int j=1; j<strokes.length; ++j)
      strokes[j] = highlightStroke;

    // Make a string out of the sequence
    assert(sequence.length > 0);
    StringBuilder seq = new StringBuilder();
    seq.append(sequence[0]);
    for (int j=1; j< sequence.length; ++j)
      seq.append(" " + sequence[j]);
    seq.append(" *");

    // This is a bit hackz but we need to create an
    // actual graphics object in order to get the FontMetrics object.
    Graphics2D graphics = (new BufferedImage(1, 1,
                                             BufferedImage.TYPE_INT_ARGB))
      .createGraphics();
    Font f = new Font("Monospaced", Font.PLAIN, 10);
    scoreboard = f.createGlyphVector(graphics.getFontMetrics(f).getFontRenderContext(), seq.toString());

    double boardHeight = scoreboard.getOutline().getBounds2D().getHeight();
    double boardWidth  = scoreboard.getOutline().getBounds2D().getWidth();
    double scaleFact = 15. / boardHeight;  // make the letters 15 px high

    at = new AffineTransform();
    at.translate(0., 0.47 * DrawUtils.canvasHeight()); // Move to the top
    at.scale(scaleFact, scaleFact);
    at.translate(-boardWidth/2, 0.); // Center
    at.scale(1, -1); // Java Graphiscs use a left-handed coordinate system.

    shapes = new Shape[1+SimulationUtils.NUMPLAYERS];
    shapes[0] = at.createTransformedShape(scoreboard.getOutline());

    assert(colors.length == 1+SimulationUtils.NUMPLAYERS);
    assert(strokes.length == 1+SimulationUtils.NUMPLAYERS);
    assert(shapes.length == 1+SimulationUtils.NUMPLAYERS);
  }

  public Shape[] shapes(GameState game) {

    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
      Rectangle2D box = scoreboard.getGlyphOutline(2*game.states[j].score).getBounds2D();

      double margin = box.getWidth() * 0.35;
      
      double xL = box.getX()-margin;
      double yT = box.getY()-margin;
 
      if (game.states[0].score != game.states[1].score) {
        double xR = box.getWidth()+2*margin;
        double yB = box.getHeight()+2*margin;
        
        box = new Rectangle2D.Double(xL, yT, xR, yB);
        shapes[j+1] = at.createTransformedShape(box); 
      }
      else {  // make these lines instead of rectangles
        
        double xR = box.getX()+box.getWidth()+margin;
        double yB = box.getY()+box.getHeight()+margin;
        
        Path2D scorePath = new Path2D.Double();
        if (j == 0) {
          // top and left
          scorePath.moveTo(xR, yT);
          scorePath.lineTo(xL, yT);
          scorePath.lineTo(xL, yB);
//  	      scoreLine0 = new Line2D.Double(xL, yT, xR, yT);
//          scoreLine1 = new Line2D.Double(xL, yT, xL, yB);
        }
        else if (j == 1) { 
          // bottom and right
          scorePath.moveTo(xR, yT);
          scorePath.lineTo(xR, yB);
          scorePath.lineTo(xL, yB);
//          scoreLine0 = new Line2D.Double(xL, yB, xR, yB);
//          scoreLine1 = new Line2D.Double(xR, yT, xR, yB);
        }
        shapes[j+1] = at.createTransformedShape(scorePath);
//        shapes[shapeCount++] = at.createTransformedShape(scoreLine0);
//        shapes[shapeCount++] = at.createTransformedShape(scoreLine1);
      }
    }
	
    return shapes;
  }


  public boolean[] fill() { return fill; }


  public Paint[] paint() { return colors; }


  public Stroke[] stroke() { return strokes; }


  public Image img(GameState state) { return null; }
  public int[] imgLocation() { return null; }
  public boolean ingameCoordinates() { return false; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }

}
