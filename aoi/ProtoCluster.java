

package aoi;

/*
  The information about a single cluster as stored in the 
  .info files. These are not yet game-play clusters as they
  have not been assigned locations on the map. This seems
  more memory-efficient that instantiating a large number
  of clusters at this database-reading stage and just setting
  the locations to NaN.

  Furthermore, here we have more information that is actually
  needed by the Simulation. The Simulation needs to
 */ 

import java.util.*;

public class ProtoCluster {
    
    // The filename associated with this cluster.
   public final String filepath;
   
   // The likelihoods of different nSegments values.
   public final Map<Integer, Double> nsLikelihoods;

   // Currently unused; String type is just temporary
   public final String votes;  

   public ProtoCluster(String filepath, Map<Integer, Double> nsLikelihoods, String votes) {
	this.filepath = filepath;
	this.nsLikelihoods = nsLikelihoods;
	this.votes = votes;	    
    }

    // Decide on the number of segments that will be accepted
    // based on the likelihood scores. This will require a lot 
    // of thought at some point... right now I am requiring
    // a likelihood score of at least 1.0
    public Set<Integer> acceptableNS() {
	Set<Integer> acceptableNS = new TreeSet<Integer>();
	if (nsLikelihoods.size() == 0) return acceptableNS;  // need to think about handling this...

	for (int i : nsLikelihoods.keySet())
	    if (nsLikelihoods.get(i) >= 1.0)
		acceptableNS.add(i);
	return acceptableNS;
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	s.append("ProtoCluster: image at ");
	s.append(filepath);
	s.append(" with likelihoods ");
	for (int i : nsLikelihoods.keySet()) {
	    s.append("(");
	    s.append(i);
	    s.append(":");
	    s.append(nsLikelihoods.get(i));
	    s.append("), ");
	}	
	s.append("and votes: ");
	s.append(votes);
	return s.toString();
    }

}
