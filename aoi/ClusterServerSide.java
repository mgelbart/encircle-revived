package aoi;

/*
  Extends the cluster class in order to store
  information and methods about the clusters
  that the clients are not supposed to have.

  Namely the number of segments and some
  generation functions.
*/

import java.util.*;
import javax.swing.ImageIcon;
import java.io.FileNotFoundException;

public class ClusterServerSide extends Cluster {

  // Number of segments in the cluster.
  // int[] rather than int because we may not know the exact number.
  // These are the values that will be ACCEPTED by the game.
  // public final Set<Integer> nSegments;

  // Likelihoods for each value of nSegments
  // Whether or not a given value of nSegments is accepted is now computed
  // on the fly
  Map<Integer, Double> nsLikelihoods;


  private static final String CLUSTER_DATASET = "fly";  // should not be hardcoded...
  private static final double MIN_ACCEPTED_LIKELIHOOD = 0.9;
  private static ClusterDatabase clusterDbSayWithPET;


  public ClusterServerSide(int x, int y, int width, int height, String imgPath,
                           String imgName, Map<Integer, Double> nsLikelihoods) {
    super(x, y, width, height, imgPath, imgName);
    this.nsLikelihoods = nsLikelihoods;
  }
  // If no (x, y) specified, use random coordinates
  public ClusterServerSide(int width, int height, String imgPath,
                           String imgName, Map<Integer, Double> nsLikelihoods) {
	this(0, 0, width, height, imgPath, imgName, nsLikelihoods);
	x = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
	y = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
  }


  // 
  // // Construct a ClusterServerSide corresponding to a cluster
  // public ClusterServerSide(Cluster c) {
  //   super(c.x, c.y, c.width, c.height, c.imgPath);
  // 
  //   if (clusterDbSayWithPET == null) {
  //     try {
  //       clusterDbSayWithPET = new ClusterDatabase(CLUSTER_DATASET);
  //     } catch (Exception e) {
  //       System.out.println(e.toString());
  //       assert(false);
  //     }
  //   }
  // 
  //   this.nSegments = clusterDbSayWithPET.getByPath(c.imgPath).acceptableNS();
  // }


  // Construct a ClusterServerSide corresponding to a cluster
  public static ClusterServerSide copyFromCluster(Cluster c) throws FileNotFoundException{
	return clusterDbSayWithPET.parseCluster(c.imgName);
  }


  // Copy a ClusterServerSide
  public ClusterServerSide(ClusterServerSide that) {
	this(that.x, that.y, that.width, that.height, that.imgPath, that.imgName, that.nsLikelihoods);
	// ok, they share the same nSegments... not very legit. maybe ok for now????
  }


  // Is nSegments an acceptable guess for this cluster?
  public boolean acceptable(int nSegments) {
	if (!nsLikelihoods.containsKey(nSegments)) return false;
	return nsLikelihoods.get(nSegments) >= MIN_ACCEPTED_LIKELIHOOD;
  }



  /* Returns a random cluster which has nSegments as a possible
     number of segments reported by the preparser.

	 This cluster should have at least likelihood minLikelihood
	 of having nSegments segments.
  */
  // @SuppressWarnings("unchecked")
  //   public static ClusterServerSide getRandom(int nSegments, double minLikelihood, 
  // 						  ClusterDatabase db) throws FileNotFoundException {
  // 
  //   // Get a random ProtoCluster from the database
  //   return db.getRandom(nSegments, minLikelihood);
  // 
  //   System.out.println(pc);
  // 
  //   ImageIcon img = new ImageIcon(pc.filepath);
  // 
  //   Set<Integer> segNos = new TreeSet<Integer>();
  //   segNos.add(nSegments);
  // 
  //   return new ClusterServerSide(x, y,
  //                                img.getIconWidth() * (Simulation.ARENASIZE/800),
  //                                img.getIconHeight() * (Simulation.ARENASIZE/800),
  //                                pc.filepath, segNos);
  // 
  // }

  /*
    Generates a collection of clusters that can be used to initialize a Map.

    In the end the placement of the clusters will be quite important for game-
    play as well as information gain about the cluster size so we probably want
    to think about how to do this more carefully.
  */
  public static Vector<ClusterServerSide> generateArena(int[] clustersPerValue) throws FileNotFoundException{

    // Read in all the Cluster info into a ClusterDatabase.
    // I might prefer this to be done in Simulation and maybe even stored,
    // but this is ok for now.
    if (clusterDbSayWithPET == null)
      clusterDbSayWithPET = new ClusterDatabase(CLUSTER_DATASET);

    Vector<ClusterServerSide> clusters = new Vector<ClusterServerSide>();

    int count, x, y;
    ClusterServerSide clust = null;
    boolean overlaps;

    for (int j=0; j < clustersPerValue.length; ++j) {

      for (int k=0; k < clustersPerValue[j]; ++k) {

        overlaps = true;
        while (overlaps) {

          clust = clusterDbSayWithPET.getRandom(1+j);

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
