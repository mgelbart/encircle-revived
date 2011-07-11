package aoi;

import java.awt.Color;
import java.awt.Image;
import java.awt.Shape;
import java.awt.Paint;
import java.awt.Stroke;

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
  public static final double CLUSTER_SCALING_FACTOR = 1./700.;

  public DrawableCluster(NotStupidPolygon contour, ImageMetaData imgMetaData) {
    super(contour, imgMetaData);

    this.img = imgMetaData.img();

    Logger.println(Logger.RepetitionRate.INFREQUENT, "loaded.");
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


  public Shape[] shapes(GameState state) { 
  //drawables.add(new DrawablePath(c.contour().coordsTranspose(), true, Color.GREEN, DrawUtils.DEFAULTSTROKE));
    return new Shape[]{contour()}; 
  }
  public boolean[] fill() { 
    return new boolean[]{false}; 
  }
  public Paint[] paint() { 
    return new Paint[]{Color.GREEN}; 
  }
  public Stroke[] stroke() {
    return new Stroke[]{DrawUtils.DEFAULTSTROKE}; 
  }
  
  
  public boolean ingameCoordinates() { return true; }
  public void remove() { removed = true; }
  public boolean isRemoved() { return removed; }
}
