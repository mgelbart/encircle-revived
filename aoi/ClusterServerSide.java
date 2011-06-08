package aoi;

import java.util.*;
import java.io.FileNotFoundException;

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

  public ClusterServerSide(NotStupidPolygon p, String imgPath, int imgID, Map<Integer, Double> nsLikelihoods) {
	super(p, imgPath, imgID);
	this.nsLikelihoods = nsLikelihoods;
  }
  
  public void randomizeLocation() {
  	int x = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
  	int y = (int) (Simulation.ARENASIZE * 0.85*(0.5 - Math.random()));
  	p.move(x, y);    
  }

  // Is nSegments an acceptable guess for this cluster?
  public boolean acceptable(int nSegments) {
	if (!nsLikelihoods.containsKey(nSegments)) return false;
	return nsLikelihoods.get(nSegments) >= MIN_ACCEPTED_LIKELIHOOD;
  }


}
