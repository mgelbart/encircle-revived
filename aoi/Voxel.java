package aoi;

/*
  A supervoxel.
 */

import javax.swing.ImageIcon;
import java.awt.Image;

public class Voxel {
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final String imgPath;
    public final Image img;

    public Voxel(int x, int y, int width, int height, String imgPath) {
	this.x = x;
	this.y = y;
	this.width = width;
	this.height = height;
	this.imgPath = imgPath;
	
	this.img = (new ImageIcon(imgPath)).getImage();

	assert(x-width/2 <=  Simulation.MAPSIZE/2);
	assert(x-width/2 >= -Simulation.MAPSIZE/2);
	assert(x+width/2 <=  Simulation.MAPSIZE/2);
	assert(x+width/2 >= -Simulation.MAPSIZE/2);

	assert(y-height/2 <=  Simulation.MAPSIZE/2);
	assert(y-height/2 >= -Simulation.MAPSIZE/2);
	assert(y+height/2 <=  Simulation.MAPSIZE/2);
	assert(y+height/2 >= -Simulation.MAPSIZE/2);
    }

    public String toString() {
	return "<voxel x=\"" + x
	    + "\" y=\"" + y
	    + "\" width=\"" + width
	    + "\" height=\"" + height
	    + "\" img=\"" + imgPath + "\"/>";
    }

}
