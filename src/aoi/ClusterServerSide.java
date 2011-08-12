package aoi;

import java.util.*;

/**
 *  Extends the cluster class in order to store
 *  information and methods about the clusters
 *  that the clients are not supposed to have.
 *
 *  Namely the number of segments and some
 *  generation functions.
 */
public class ClusterServerSide extends Cluster {
  // Likelihoods for each value of nSegments
  // Whether or not a given value of nSegments is accepted is now computed
  // on the fly
  Map<Integer, Double> nsLikelihoods;

  // Determines which clusters we accept given the likelihood function.
  private static final double MIN_ACCEPTED_LIKELIHOOD = 0.9;

  public ClusterServerSide(NSPolygon contour, ImageMetaData imgMetaData, Map<Integer, Double> nsLikelihoods) {
	super(contour, imgMetaData);
	this.nsLikelihoods = nsLikelihoods;
  }
  
  // Is nSegments an acceptable guess for this cluster?
  public boolean acceptable(int nSegments) {
	if (!nsLikelihoods.containsKey(nSegments)) return false;
	return nsLikelihoods.get(nSegments) >= MIN_ACCEPTED_LIKELIHOOD;
  }
  
  // move the cluster by (deltaX, deltaY)
  public ClusterServerSide move(int deltaX, int deltaY) {
    return new ClusterServerSide(contour().move(deltaX, deltaY), imgMetaData().move(deltaX, deltaY), nsLikelihoods);
  } 
  
  // center the *image* at this new location
  public ClusterServerSide moveTo(int newX, int newY) {
    return move(-imgCenterX() + newX, -imgCenterY() + newY);
  }
  
  // used for initializing clusters
  public ClusterServerSide randomizeLocation() {
    int x = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
    int y = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
    return (ClusterServerSide) moveTo(x, y);    
  }

  public ClusterServerSide randomizeLocationSpread(Vector<ClusterServerSide> alreadyPlaced) {
    int x, y;
    ClusterServerSide newCluster = null;
    do {
      x = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
      y = (int) (SimulationUtils.ARENASIZE * 0.85*(0.5 - Math.random()));
      final int ARBITRARY_FACTOR = 10; // the larger this is, the less the clusters will spread apart
      final int ARBITRARY_FACT0R = 5;  // the larger this is, the less the clusters will spread apart
      for (ClusterServerSide css : alreadyPlaced) {
        x += Math.signum(x-css.center().x) * SimulationUtils.ARENASIZE / ARBITRARY_FACTOR *
               Math.exp(-(Math.abs(x-css.center().x) / SimulationUtils.ARENASIZE) * 
               ARBITRARY_FACT0R);
        y += Math.signum(y-css.center().y) * SimulationUtils.ARENASIZE / ARBITRARY_FACTOR *
               Math.exp(-(Math.abs(y-css.center().y) / SimulationUtils.ARENASIZE) * 
               ARBITRARY_FACT0R);
      }
      newCluster = (ClusterServerSide) moveTo(x, y);
    } while (x >  SimulationUtils.ARENASIZE * 0.5 * 0.85 ||
           x < -SimulationUtils.ARENASIZE * 0.5 * 0.85 ||
           y >  SimulationUtils.ARENASIZE * 0.5 * 0.85 ||
           y < -SimulationUtils.ARENASIZE * 0.5 * 0.85);
    //x = (int) Math.min(x,  SimulationUtils.ARENASIZE * 0.5 * 0.85);
    //x = (int) Math.max(x, -SimulationUtils.ARENASIZE * 0.5 * 0.85);
    //y = (int) Math.min(y,  SimulationUtils.ARENASIZE * 0.5 * 0.85);
    //y = (int) Math.max(y, -SimulationUtils.ARENASIZE * 0.5 * 0.85);
    return newCluster;
  }

}
