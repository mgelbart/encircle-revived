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
  int[] sequence;

  // Translates and scales the coordinates from the glyph vector to 'overlay coordinates' used in DrawUtils.
  private AffineTransform at;

  // The unscaled/translated numbers shown in the scoreboard.
  private Shape numbers;
  
  public DrawableScoreboard(Color neutralColor, int[] sequence) {
    this.sequence = sequence;

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

    final double boardHeight = scoreboard.getOutline().getBounds2D().getHeight();
    final double boardWidth  = scoreboard.getOutline().getBounds2D().getWidth();
    final double scaleFact = 15. / boardHeight;  // make the letters 15 px high
    
    // Precalculate everything except the translation for the coordinate transformation.
    // The amount of translation depends on the canvas size which may change.
    at = new AffineTransform();
    at.scale(scaleFact, scaleFact);
    at.translate(-boardWidth/2, 0.); // Center
    at.scale(1, -1); // Java Graphics use a left-handed coordinate system.

    shapes = new Shape[1+SimulationUtils.NUMPLAYERS];
    numbers = scoreboard.getOutline();

    assert(colors.length == 1+SimulationUtils.NUMPLAYERS);
    assert(strokes.length == 1+SimulationUtils.NUMPLAYERS);
    assert(shapes.length == 1+SimulationUtils.NUMPLAYERS);
  }
  
  private AffineTransform toOverlayCoords() {
    AffineTransform t =  AffineTransform.getTranslateInstance(0., DrawUtils.canvasHeight()/2 - 20); // Move to the top
    t.concatenate(at);
    return t;
  }

  public Shape[] shapes(GameState game) {
    shapes[0] = toOverlayCoords().createTransformedShape(numbers);

    // the number of players that already have the i'th score drawn.
    int[] playersAlreadyAtScore = new int[sequence.length+1];
    
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
      final int score = game.pStates[j].score;
      Rectangle2D box = scoreboard.getGlyphVisualBounds(2*score).getBounds2D();
      
      final double margin = box.getWidth() * 0.35;
      
      final double xL = box.getX()-margin;
      final double yT = box.getY()-margin;
      final double xR = box.getX()+box.getWidth()+margin;
      final double yB = box.getY()+box.getHeight()+margin;
      final Path2D scorePath = new Path2D.Double();
      
//      if (SimulationUtils.NUMPLAYERS == 2) {
//        switch (j) {
//        case 0:        
//          scorePath.moveTo(xR, yT);
//          scorePath.lineTo(xL, yT);
//          scorePath.moveTo(xR, yB);
//          scorePath.lineTo(xL, yB);
//          break;
//        case 1:
//          scorePath.moveTo(xR, yT);
//          scorePath.lineTo(xR, yB);
//          scorePath.moveTo(xL, yT);
//          scorePath.lineTo(xL, yB);
//          break;
//        case 2:
//          scorePath.moveTo(xL, yB);
//          scorePath.lineTo(xR, yB);
//          break;
//        case 3:
//          scorePath.moveTo(xL, yT);
//          scorePath.lineTo(xR, yT);
//          break;
//        }
//      } else {
        final double vOffset = playersAlreadyAtScore[score]*2.;
        scorePath.moveTo(xL, yB+vOffset);
        scorePath.lineTo(xR, yB+vOffset);
//      }

      shapes[j+1] = toOverlayCoords().createTransformedShape(scorePath);

      ++playersAlreadyAtScore[score];
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

  public void prepare(GameState gState) { }
  public void postpare(GameState gState) { }
  
  
}
