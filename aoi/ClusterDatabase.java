package aoi;

/*
  Loads all the cluster information for a 
  particular data set. This information is
  then accessed to make instances of
  ClusterServerSide

  We don't create the Cluster objects at 
  this point because that would take extra
  memory. Instead, we just create them
  (and the associated ClusterServerSide) 
  as needed.

  Also, this may be more reasonable from
  an organizational standpoint... this is
  still unclear.
 */

import java.io.*;
import java.util.*;

public class ClusterDatabase {

    // The path where all images and info files are stored
    public static final String IMAGE_PATH = "./images/";
    
    // The file extension used for our metadata files
    public static final String INFO_SUFFIX = ".info";


    // image filenames
    public final Vector<ProtoCluster> allProtoClusters;


    public ClusterDatabase(String datasetName) throws FileNotFoundException {
	allProtoClusters = new Vector<ProtoCluster>();

        Scanner infoScanner = new Scanner(new File(IMAGE_PATH + datasetName + INFO_SUFFIX));
        while (infoScanner.hasNext()) { 
            // The image filename.
            String filename = infoScanner.nextLine();

            // The likelihoods of different nSegments values.
            Map<Integer, Double> nsLikelihoods = new HashMap<Integer, Double>();
            String nsLikelihoodsLine = infoScanner.nextLine();
            if (nsLikelihoodsLine.length() > 0) {
                String[] nsLikelihoodStrings = nsLikelihoodsLine.split(",");
                for (int i = 0; i < nsLikelihoodStrings.length; ++i) {
                    String[] nsLikelihoodPair = nsLikelihoodStrings[i].split(":");
                    nsLikelihoods.put(Integer.parseInt(nsLikelihoodPair[0]),
                                      Double.parseDouble(nsLikelihoodPair[1]));
                }
            }

            // The details about votes from past data collection;
            // currently unused.
            String votes = infoScanner.nextLine();

            allProtoClusters.add(new ProtoCluster(filename, nsLikelihoods, votes));
        }


}





    private class ProtoCluster {
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
        
    	// The filename associated with this cluster.
   	public final String filename;
    
   	// The likelihoods of different nSegments values.
   	public final Map<Integer, Double> nsLikelihoods;

   	// Currently unused; String type is just temporary
   	public final String votes;  

    	public ProtoCluster(String filename, Map<Integer, Double> nsLikelihoods, String votes) {
	    this.filename = filename;
	    this.nsLikelihoods = nsLikelihoods;
	    this.votes = votes;	    
    	}

    }
}
