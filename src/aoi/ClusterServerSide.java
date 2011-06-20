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

  public ClusterServerSide(NotStupidPolygon contour, ImageMetaData imgMetaData, Map<Integer, Double> nsLikelihoods) {
	super(contour, imgMetaData);
	this.nsLikelihoods = nsLikelihoods;
  }
  
  // Is nSegments an acceptable guess for this cluster?
  public boolean acceptable(int nSegments) {
	if (!nsLikelihoods.containsKey(nSegments)) return false;
	return nsLikelihoods.get(nSegments) >= MIN_ACCEPTED_LIKELIHOOD;
  }


}
