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

    public DrawableCluster(int x, int y, int width, int height, String imgPath) {
	super(x, y, width, height, imgPath);

	// System.err.print(imgPath + " ");

	// Prepending the "/" w/o checking if it's
	// already included is a bit hax0r but eventually
	// we won't be loading the data here from a file
	// anyway...
	URL p = getClass().getResource("/" + imgPath);
	assert(p != null);
	this.img = (new ImageIcon(p)).getImage();

	// System.err.println("loaded.");
    }

    public DrawableCluster(Cluster c) {
	this(c.x, c.y, c.width, c.height, c.imgPath);
    }


    // Returns the image to be drawn.
    public Image img(GameState state) { return img; }


    // Return {centerx, centery, width, height} of the image
    public int[] imgLocation() { return new int[] {x, y, width, height}; }


    public Shape[] shapes(GameState state) { return null; }
    public Color[] color() { return null; }
    public double[] width() { return new double[]{0}; }
    public boolean ingameCoordinates() { return true; }
    public void remove() { removed = true; }
    public boolean isRemoved() { return removed; }
}