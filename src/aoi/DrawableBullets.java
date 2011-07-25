package aoi;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 *  A GUI element for drawing bullets.
 */
public class DrawableBullets implements Drawable {

  private boolean removed = false;
  private Paint[] colors;
  private boolean[] fill;
  private Stroke[] strokes;

  private final static int BULLETSIZE = (int) (SimulationUtils.ARENASIZE * 0.005);
  private final static Color BULLETCOLOR = Color.GREEN;
  private final static Color BULLETTAILCOLOR = Color.BLUE;

  private final Point endOfTail;
  private final Path2D bulletTail;
  
  public DrawableBullets() {
    endOfTail = new Point(-SimulationUtils.ARENASIZE/15, 0);
    
    bulletTail = new Path2D.Double();
    bulletTail.moveTo(0., 0.);
    bulletTail.lineTo(endOfTail.getX(), 0. - BULLETSIZE);
    bulletTail.lineTo(endOfTail.getX(), 0. + BULLETSIZE);
    bulletTail.closePath();
  }
  
  // Return the polygon to be drawn.
  public Shape[] shapes(GameState game) {

    Shape[] shapes = new Shape[2*game.bullets.size()];
    colors = new Paint[2*shapes.length];
    fill = new boolean[2*shapes.length];
    strokes = new Stroke[2*shapes.length];

    for (int j=0; j < game.bullets.size(); ++j) {

      // add a small rectangle for each bullet.
      Bullet b = game.bullets.get(j);
      shapes[2*j] = new Rectangle(b.x - BULLETSIZE/2,
                                b.y - BULLETSIZE/2,
                                BULLETSIZE,
                                BULLETSIZE);
      
      colors[2*j] = BULLETCOLOR;
      fill[2*j] = true;
      strokes[2*j] = DrawUtils.DEFAULTSTROKE;
      
      
      // add a tail to each bullet.
      AffineTransform at = AffineTransform.getTranslateInstance(b.x, b.y);
      at.rotate(b.direction.toDouble());
      shapes[2*j+1] = at.createTransformedShape(bulletTail);
      
      Point start = DrawUtils.fromGameToJavaUserSpaceCoordinates(new Point(b.x, b.y));
      Point end = DrawUtils.fromGameToJavaUserSpaceCoordinates(at.transform(endOfTail, null));
      colors[2*j+1] = new GradientPaint((float) start.getX(), (float) start.getY(), BULLETTAILCOLOR,
                                        (float) end.getX(), (float) end.getY(), Color.BLACK);
      fill[2*j+1] = true;
      strokes[2*j+1] = DrawUtils.DEFAULTSTROKE;

    }	

    return shapes;
  }

  public Paint[] paint() { return colors; }

  public boolean[] fill() { return fill; }

  public Stroke[] stroke() { return strokes; }

  public boolean ingameCoordinates() { return true; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }
  public Image img(GameState game) { return null; }
  public int[] imgLocation() { return null; }

  public void prepare(GameState gState) { }
  public void postpare(GameState gState) { }
  
}
