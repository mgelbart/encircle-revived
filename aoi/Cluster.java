package aoi;

/**
 *  Clusters are image patches spread around the map. Each cluster
 *  contains a particular number of segments which defines the
 *  clusters numerical value. The players collect clusters in
 *  increasing order of numberical value.
 */
public class Cluster {
	private final NotStupidPolygon contour; // the outline polygon, relevant for gameplay
	private final ImageMetaData imgMetaData;



  public Cluster(NotStupidPolygon contour, ImageMetaData imgMetaData) {
  	this.contour = contour;
    this.imgMetaData = imgMetaData;
    
    // assert(p.centerX()+p.width()/2  <=  Simulation.ARENASIZE/2);
    // assert(p.centerX()+p.width()/2  >= -Simulation.ARENASIZE/2);
    // assert(p.centerX()-p.width()/2  <=  Simulation.ARENASIZE/2);
    // assert(p.centerX()-p.width()/2  >= -Simulation.ARENASIZE/2);
    // 
    // assert(p.centerY()+p.height()/2  <=  Simulation.ARENASIZE/2);
    // assert(p.centerY()+p.height()/2 >= -Simulation.ARENASIZE/2);
    // assert(p.centerY()-p.height()/2 <=  Simulation.ARENASIZE/2);
    // assert(p.centerY()-p.height()/2 >= -Simulation.ARENASIZE/2);    
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
  	int x = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
  	int y = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
  	moveTo(x, y);    
  }
  
  
	
	// In most cases, these functions can be used interchangeably for
	// image and contour values. However, it should be noted that
	// the countour has its own width, height, and center
	// that may not be exactly the same as for the image.
  public int imgWidth()   { return imgMetaData.width(); }
  public int imgHeight()  { return imgMetaData.height(); }
  public int imgCenterX() { return imgMetaData.centerX(); }
  public int imgCenterY() { return imgMetaData.centerY(); }
  public int imgID()      { return imgMetaData.id(); }
  
  public int width()   { return contour.width(); }
  public int height()  { return contour.height(); }
  public int centerX() { return contour.centerX(); }
  public int centerY() { return contour.centerY(); }
  
  public NotStupidPolygon contour()  { return contour; }
  public ImageMetaData imgMetaData() { return imgMetaData; }


}
