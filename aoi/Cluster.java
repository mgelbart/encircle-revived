package aoi;

/**
 *  Clusters are image patches spread around the map. Each cluster
 *  contains a particular number of segments which defines the
 *  clusters numerical value. The players collect clusters in
 *  increasing order of numberical value.
 */
public class Cluster {
	public final NotStupidPolygon p;
	public final String imgPath;
  public final int imgID; // a unique image ID
								// eventually, the client should not have this information
								// but it's currently needed for making a 
								// ClusterServerSide out of a Cluster

    public Cluster(NotStupidPolygon p, String imgPath, int imgID) {
	this.p = p;
	this.imgPath = imgPath;
	this.imgID = imgID;

	assert(p.centerX()+p.width()/2  <=  Simulation.ARENASIZE/2);
	assert(p.centerX()+p.width()/2  >= -Simulation.ARENASIZE/2);
	assert(p.centerX()-p.width()/2  <=  Simulation.ARENASIZE/2);
	assert(p.centerX()-p.width()/2  >= -Simulation.ARENASIZE/2);

	assert(p.centerY()+p.height()/2	<=  Simulation.ARENASIZE/2);
	assert(p.centerY()+p.height()/2 >= -Simulation.ARENASIZE/2);
	assert(p.centerY()-p.height()/2 <=  Simulation.ARENASIZE/2);
	assert(p.centerY()-p.height()/2 >= -Simulation.ARENASIZE/2);    
	}

	// For serializing in XML file
    public String toString() {
		String s = "<cluster imgPath=\"" + imgPath
	    + "\" imgID=\"" + imgID
		+ "\" d=\"" + p.n() + ":";
		int[][] coords = p.coords();
		for (int i = 0; i < p.n(); i++)
			s += coords[0][i] + " " + coords[1][i] + " ";
		s += "\"/>";
		return s;
    }
	
	public boolean intersects(Cluster that) {
		return p.intersects(that.p);
	}	
	public boolean contains(int x, int y) {
		return p.contains(x, y);
	}
	public int x() {
		return p.centerX();
	}
	public int y() {
		return p.centerY();
	}
	public int height() {
		return p.height();
	}
	public int width() {
		return p.width();
	}

}
