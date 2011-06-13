package aoi;

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.Rectangle;
import java.awt.Image;

/**
 *  A GUI element for drawing bullets.
 */
public class DrawableBullets implements Drawable {

  private boolean removed = false;
  private Paint[] colors;
  private boolean[] fill;
  private Stroke[] strokes;

  private final static int BULLETSIZE = Simulation.ARENASIZE / 200;
  private final static Color BULLETCOLOR = Color.GREEN;


  // Return the polygon to be drawn.
  public Shape[] shapes(GameState game) {

    Shape[] shapes = new Shape[game.bullets.size()];
    colors = new Paint[shapes.length];
    fill = new boolean[shapes.length];
    strokes = new Stroke[shapes.length];

    for (int j=0; j < game.bullets.size(); ++j) {

      Bullet b = game.bullets.get(j);
      shapes[j] = new Rectangle(b.x - BULLETSIZE/2,
                                b.y - BULLETSIZE/2,
                                BULLETSIZE,
                                BULLETSIZE);

      colors[j] = BULLETCOLOR;
      fill[j] = true;
      strokes[j] = DrawUtils.DEFAULTSTROKE;

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

}
