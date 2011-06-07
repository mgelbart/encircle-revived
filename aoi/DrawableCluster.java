package aoi;

/*
  A GUI element for rendering a cluster.
*/

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Color;

public class DrawableCluster extends Cluster implements Drawable {

  private boolean removed = false;

  public final Image img;

  public DrawableCluster(NotStupidPolygon p, String imgPath, String imgName) {
    super(p, imgPath, imgName);

    // System.err.print(imgPath + " ");

    // Prepending the "/" w/o checking if it's
    // already included is a bit hax0r but eventually
    // we won't be loading the data here from a file
    // anyway...
    URL url = getClass().getResource("/" + imgPath);
    assert(url != null);
    this.img = (new ImageIcon(url)).getImage();

    // System.err.println("loaded.");
  }

  public DrawableCluster(Cluster c) {
    this(c.p, c.imgPath, c.imgName);
  }


  // Returns the image to be drawn.
  public Image img(GameState state) { return img; }


  // Return {centerx, centery, width, height} of the image
  public int[] imgLocation() { return new int[] {p.centerX(), p.centerY(), p.width(), p.height()}; }


  public Shape[] shapes(GameState state) { return null; }
  public boolean[] fill() { return null; }
  public Color[] color() { return null; }
  public double[] penWidth() { return null; }
  public boolean ingameCoordinates() { return true; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }
}
