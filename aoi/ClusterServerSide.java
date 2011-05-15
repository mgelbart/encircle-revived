package aoi;

/*
  Extends the cluster class in order to store
  information and methods about the clusters
  that the clients are not supposed to have.

  Namely the number of segments and some
  generation functions.
 */

import java.util.Vector;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.ImageIcon;

public class ClusterServerSide extends Cluster {

    // Number of segments in the cluster.
    // int[] rather than int because we may not know the exact number.
    // These are the values that will be ACCEPTED by the game.
    public final Set<Integer> nSegments;

    public ClusterServerSide(int x, int y, int width, int height,
			     String imgPath, Set<Integer> nSegments) {
	super(x, y, width, height, imgPath);

	this.nSegments = nSegments;

	
    }





    /* Returns a random cluster which has nSegments as a possible
       number of segments reported by the preparser.
    
       If exact is set to true, a cluster with well known number
       of segments is returned.

       The cluster is centered at (x,y)
    */
    @SuppressWarnings("unchecked")
    public static ClusterServerSide getRandom(int x, int y,
					      int nSegments, boolean exact) {

	final int MAXSEGMENTS = 7;

	Vector<String>[] paths = new Vector[MAXSEGMENTS];

	for (int j=0; j<MAXSEGMENTS; ++j)
	    paths[j] = new Vector<String>();

	paths[0].add("./images/fly/labeled/1a.jpg");
	paths[0].add("./images/fly/labeled/1b.jpg");
	paths[0].add("./images/fly/1c.jpg");
	paths[0].add("./images/fly/1d.jpg");
	paths[0].add("./images/fly/1e.jpg");

	paths[1].add("./images/fly/2a.jpg");
	paths[1].add("./images/fly/2b.jpg");
	paths[1].add("./images/fly/2c.jpg");
	paths[1].add("./images/fly//2d.jpg");
	paths[1].add("./images/fly/labeled/2e.jpg");
	paths[1].add("./images/fly/labeled/2f.jpg");

	paths[2].add("./images/fly/labeled/3a.jpg");
	paths[2].add("./images/fly/labeled/3b.jpg");
	paths[2].add("./images/fly/labeled/3c.jpg");
	paths[2].add("./images/fly/labeled/3d.jpg");
	paths[2].add("./images/fly/labeled/3e.jpg");
	paths[2].add("./images/fly/labeled/3f.jpg");
	paths[2].add("./images/fly/labeled/3g.jpg");
	paths[2].add("./images/fly/labeled/3h.jpg");

	paths[3].add("./images/fly/labeled/4a.jpg");
	paths[3].add("./images/fly/labeled/4b.jpg");
	paths[3].add("./images/fly/labeled/4c.jpg");
	paths[3].add("./images/fly/labeled/4d.jpg");
	paths[3].add("./images/fly/labeled/4e.jpg");
	paths[3].add("./images/fly/labeled/4f.jpg");
	paths[3].add("./images/fly/labeled/4g.jpg");
	paths[3].add("./images/fly/labeled/4h.jpg");

	paths[4].add("./images/fly/labeled/5a.jpg");
	paths[4].add("./images/fly/labeled/5b.jpg");
	paths[4].add("./images/fly/labeled/5c.jpg");
	paths[4].add("./images/fly/labeled/5d.jpg");

	paths[5].add("./images/fly/labeled/6a.jpg");
	paths[5].add("./images/fly/labeled/6b.jpg");

	paths[6].add("./images/fly/labeled/7a.jpg");
	paths[6].add("./images/fly/labeled/7b.jpg");

	String path = paths[nSegments-1].get((int)
					    (Math.random() * paths[nSegments-1].size()));
	ImageIcon img = new ImageIcon(path);

	Set<Integer> segNos = new TreeSet<Integer>();
	segNos.add(nSegments);

	return new ClusterServerSide(x, y,
				     img.getIconWidth() * (Simulation.MAPSIZE/800),
				     img.getIconHeight() * (Simulation.MAPSIZE/800),
				     path, segNos);

    }

    /*
      Generates a collection of clusters that can be used to initialize a Map.

      In the end the placement of the clusters will be quite important for game-
      play as well as information gain about the cluster size so we probably want
      to think about how to do this more carefully.
     */
    public static Vector<ClusterServerSide> generateArena(int[] clustersPerValue) {

	Vector<ClusterServerSide> clusters = new Vector<ClusterServerSide>();

	int count, x, y;
	ClusterServerSide clust = null;
	boolean overlaps;

	for (int j=0; j < clustersPerValue.length; ++j) {

	    for (int k=0; k < clustersPerValue[j]; ++k) {

		overlaps = true;
		while (overlaps) {

		    x = (int) (Simulation.MAPSIZE * 0.85*(0.5 - Math.random()));
		    y = (int) (Simulation.MAPSIZE * 0.85*(0.5 - Math.random()));
		    clust = getRandom(x, y, 1+j, false);

		    //System.out.println("Generated " + (1+j) + "-cluster at (" + x + ", " + y + "). k = " + k);

		    overlaps = false;
		    for (Cluster prevClust : clusters) {
			if (prevClust.boundingBox().intersects(clust.boundingBox())) 
			    overlaps = true;

		    }

		}

		clusters.add(clust);

	    }

	}

	return clusters;

    }

}
