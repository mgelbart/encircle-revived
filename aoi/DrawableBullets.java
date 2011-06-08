package aoi;

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Image;

/**
 *  A GUI element for drawing bullets.
 */
public class DrawableBullets implements Drawable {

  private boolean removed = false;
  private Color[] colors = null;
  private boolean[] fill = null;
  private double[] widths = null;

  private final static int BULLETSIZE = Simulation.ARENASIZE / 200;
  private final static Color BULLETCOLOR = stdlib.StdDraw.GREEN;


  // Return the polygon to be drawn.
  public Shape[] shapes(GameState game) {

    Shape[] shapes = new Shape[game.bullets.size()];
    colors = new Color[shapes.length];
    fill = new boolean[shapes.length];
    widths = new double[shapes.length];

    for (int j=0; j < game.bullets.size(); ++j) {

      Bullet b = game.bullets.get(j);
      shapes[j] = new Rectangle(b.x - BULLETSIZE/2,
                                b.y - BULLETSIZE/2,
                                BULLETSIZE,
                                BULLETSIZE);

      colors[j] = BULLETCOLOR;
      fill[j] = true;
      widths[j] = 0.;

    }	

    return shapes;
  }

  public Color[] color() { return colors; }

  public boolean[] fill() { return fill; }

  public double[] penWidth() { return widths; }

  public boolean ingameCoordinates() { return true; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }
  public Image img(GameState game) { return null; }
  public int[] imgLocation() { return null; }

}
