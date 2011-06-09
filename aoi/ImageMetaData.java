package aoi;

import org.xml.sax.Attributes;

/**
 *  Data wrapper class. Image data for a cluster.
 */
public class ImageMetaData {
	private final String path;
	private int sizeX, sizeY;  // the dimensions of the image, for drawing only
	private int centerX, centerY; // the center of the image, for drawing only
	           // (the above is generally (0,0) except for some small) perturbations
	private final String name;  // filename of the image file
	// eventually, the client should not have this information
	// but it's currently needed for making a 
	// ClusterServerSide out of a Cluster
			
	public ImageMetaData(String path, int sizeX, int sizeY, int centerX, int centerY, String name) {
	  this.path = path;
	  this.sizeX = sizeX;
	  this.sizeY = sizeY;
	  this.centerX = centerX;
	  this.centerY = centerY;
	  this.name = name;
	}
	
	// Read serialized object
	public ImageMetaData(Attributes serialized) {
	  path = serialized.getValue("imgPath");

	  String[] sizes = serialized.getValue("imgSize").split(",");
	  sizeX = Integer.parseInt(sizes[0]);
	  sizeY = Integer.parseInt(sizes[1]);
	  
	  String[] centers = serialized.getValue("imgCenter").split(",");
	  centerX = Integer.parseInt(centers[0]);
	  centerY = Integer.parseInt(centers[1]);
	  	  
	  name = serialized.getValue("imgName");
	}
	// Encode object for serialization
	public String toString() {
	  return " imgPath=\"" + path + "\""
        +  " imgSize=\"" + sizeX + "," + sizeY + "\""
        +  " imgCenter=\"" + centerX + "," + centerY + "\""
        +  " imgName=\"" + name + "\"";
	}
	
	public void move(int deltaX, int deltaY) {
	  centerX += deltaX;
	  centerY += deltaY;
	}
	
	public int width()   { return sizeX; }
	public int height()  { return sizeY; }
	public int centerX() { return centerX; }
	public int centerY() { return centerY; }
	public String path() { return path; }
	public String name() { return name; }

	
}
