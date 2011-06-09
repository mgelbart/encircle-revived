package aoi;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Color;

/**
 *  A GUI element for rendering a cluster.
 */
public class DrawableCluster extends Cluster implements Drawable {

  private boolean removed = false;

  public final Image img;
  
  // The amount to scale clusters by when rendering them.
  // Increasing this will make the clusters larger on the map,
  // and decreasing this will make them smaller.
  // Currently, this needs to be modified if the inherent units of
  // the cluster images are changed (e.g., if we downsample all
  // cluster images we would need to increase this by a factor of two
  // to keep them the same apparent size in the game). Perhaps
  // later we might want to scale all the clusters beforehand so that
  // we are insensitive to this, and then we can phrase this factor
  // as fraction of the total arena size or something like that.
  public static final double CLUSTER_SCALING_FACTOR = 1./800.;

  public DrawableCluster(NotStupidPolygon contour, ImageMetaData imgMetaData) {
    super(contour, imgMetaData);

    // System.err.print(imgPath + " ");

    // Prepending the "/" w/o checking if it's
    // already included is a bit hax0r but eventually
    // we won't be loading the data here from a file
    // anyway...
    URL url = getClass().getResource("/" + imgMetaData.path());
    assert(url != null);
    this.img = (new ImageIcon(url)).getImage();

    // System.err.println("loaded.");
  }

  public DrawableCluster(Cluster c) {
    this(c.contour(), c.imgMetaData());
  }


  // Returns the image to be drawn.
  public Image img(GameState state) { return img; }


  // Return {centerX, centerY, width, height} of the image
  public int[] imgLocation() { 
    return new int[] {imgCenterX(), imgCenterY(), imgWidth(), imgHeight()}; 
  }


  public Shape[] shapes(GameState state) { return null; }
  public boolean[] fill() { return null; }
  public Color[] color() { return null; }
  public double[] penWidth() { return null; }
  public boolean ingameCoordinates() { return true; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }
}
