package aoi;

import java.awt.Point;

/**
 *  Clusters are image patches spread around the map. Each cluster
 *  contains a particular number of segments which defines the
 *  clusters numerical value. The players collect clusters in
 *  increasing order of numerical value.
 */
public class Cluster {
	private final NSPolygon contour; // the outline polygon, relevant for gameplay
	private final ImageMetaData imgMetaData;



  public Cluster(NSPolygon contour, ImageMetaData imgMetaData) {
  	this.contour = contour;
    this.imgMetaData = imgMetaData;
    
    // assert(p.centerX()+p.width()/2  <=  SimulationUtils.ARENASIZE/2);
    // assert(p.centerX()+p.width()/2  >= -SimulationUtils.ARENASIZE/2);
    // assert(p.centerX()-p.width()/2  <=  SimulationUtils.ARENASIZE/2);
    // assert(p.centerX()-p.width()/2  >= -SimulationUtils.ARENASIZE/2);
    // 
    // assert(p.centerY()+p.height()/2  <=  SimulationUtils.ARENASIZE/2);
    // assert(p.centerY()+p.height()/2 >= -SimulationUtils.ARENASIZE/2);
    // assert(p.centerY()-p.height()/2 <=  SimulationUtils.ARENASIZE/2);
    // assert(p.centerY()-p.height()/2 >= -SimulationUtils.ARENASIZE/2);    
	}

	// For serializing in XML file
  public String toString() {
  	return "<cluster" + contour + imgMetaData + "/>";
  }
	
	public boolean intersects(Cluster that) {
		return contour.intersects(that.contour);
	}	
	public boolean contains(int x, int y) {
    return contour.contains(x, y);
	}
	
	public boolean intersects(Ship s) {
	  return contour.intersects(s.getNSPath());
	}
	
	public boolean intersects(Bullet b) {
	  return contour.intersects(b.getLine()) || contains(b.x, b.y);
	}
	
	// move the cluster by (deltaX, deltaY)
	public void move(int deltaX, int deltaY) {
	  contour.move(deltaX, deltaY);
	  imgMetaData.move(deltaX, deltaY);
	} 
	
	// center the *image* at this new location
	public void moveTo(int newX, int newY) {
	  move(-imgCenterX() + newX, -imgCenterY() + newY);
	}
	
	// used for initializing clusters
  public void randomizeLocation() {
  	int x = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
  	int y = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
  	moveTo(x, y);    
  }
  
  
	
	// In most cases, these functions can be used interchangeably for
	// image and contour values. However, it should be noted that
	// the countour has its own width, height, and center
	// that may not be exactly the same as for the image.
  public int imgWidth()   { return imgMetaData.width();   }
  public int imgHeight()  { return imgMetaData.height();  }
  public int imgCenterX() { return imgMetaData.centerX(); }
  public int imgCenterY() { return imgMetaData.centerY(); }
  public String imgName() { return imgMetaData.name();    }
  
  public int width()   { return contour.width();   }
  public int height()  { return contour.height();  }
  public Point center(){ return contour.center();  }
  
  public NSPolygon contour()  { return contour; }
  public ImageMetaData imgMetaData() { return imgMetaData; }


}
