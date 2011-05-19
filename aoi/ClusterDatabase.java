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

    // All the ProtoClusters in the database
    public final ProtoCluster[] allProtoClusters;


    public ClusterDatabase(String datasetName) throws FileNotFoundException {
	Vector<ProtoCluster> vProtoClusters = new Vector<ProtoCluster>();

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

            vProtoClusters.add(new ProtoCluster(IMAGE_PATH + datasetName + "/" + filename, 
						nsLikelihoods, votes));
        }

	// Initialize allProtoClusters and copy vProtoClusters into allProtoClusters.
	allProtoClusters = new ProtoCluster[vProtoClusters.size()];
	vProtoClusters.toArray(allProtoClusters);
    }

    // Returns a random ProtoCluster than has at least minLikelihood likelihood
    // of having nSegments segments. If none exist, return null.
    public ProtoCluster getRandom(int nSegments) {
	// Shuffle allProtoClusters.
	// This might be SLOW. There's surely a smarter way of doing this...
	// Like sorting by number of segments, etc. Oh well, can change it later.
	Collections.shuffle(Arrays.asList(allProtoClusters));
	for (ProtoCluster pc : allProtoClusters)
	    if (pc.acceptableNS().contains(nSegments))
		return pc;
	return null;
    }


    // Return the metadata for one particular image stored in 'path'.
    public ProtoCluster getByPath(String path) {

	for (ProtoCluster p : allProtoClusters) {
	    if (p.filepath.equals(path))
		return p;
	}

	assert(false);
	return null;
    }

}
