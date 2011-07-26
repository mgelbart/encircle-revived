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


}
