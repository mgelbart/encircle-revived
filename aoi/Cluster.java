package aoi;

/*
  Clusters are image patches spread around the map. Each cluster
  contains a particular number of segments which defines the
  clusters numerical value. The players collect clusters in
  increasing order of numberical value.
 */

import java.awt.Rectangle;
import java.awt.Polygon;

public class Cluster {
    public int x;
    public int y;
    public final int width;
    public final int height;
	public final Polygon p;
	public final String imgPath;
    public final String imgName; // a unique image ID


    public Cluster(int x, int y, int width, int height, String imgPath, String imgName) {
	this.x = x;
	this.y = y;
	this.width = width;
	this.height = height;
	this.imgPath = imgPath;
	this.imgName = imgName;
	this.p = null;

	assert(x-width/2 <=  Simulation.ARENASIZE/2);
	assert(x-width/2 >= -Simulation.ARENASIZE/2);
	assert(x+width/2 <=  Simulation.ARENASIZE/2);
	assert(x+width/2 >= -Simulation.ARENASIZE/2);

	assert(y-height/2 <=  Simulation.ARENASIZE/2);
	assert(y-height/2 >= -Simulation.ARENASIZE/2);
	assert(y+height/2 <=  Simulation.ARENASIZE/2);
	assert(y+height/2 >= -Simulation.ARENASIZE/2);
    }

	// For serializing in XML file
    public String toString() {
	return "<cluster x=\"" + x
	    + "\" y=\"" + y
	    + "\" width=\"" + width
	    + "\" height=\"" + height
	    + "\" imgPath=\"" + imgPath
		+ "\" imgID=\"" + imgName
		+ "\" d=\"" + "M250 150 L150 350 L350 350 Z" + "\"/>";
    }

	// soon to be use polygon's boundingbox fuction...
    public Rectangle boundingBox() {
	return new Rectangle(x - width/2, y - height/2, width, height);
    }

}
