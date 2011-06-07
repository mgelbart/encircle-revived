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

  // Either '/' or '\' depending on OS. Eventually we can find the real way of doing this.
  public static final String OS_FILESEP = "/";

  // The base directory where all segmentation stuff is stored
  public static final String SEGMENTATION_BASEDIR = "segmentation";

  // The path where the lists of image filenames are stored
  public static final String IMAGE_NAME_LIST = "meta";

  // The path where all images and info files are stored
  public static final String IMAGE_RAW_PATH = "images";

  // The format of these raw images
  public static final String IMAGE_RAW_FORMAT = ".jpg";

  // The path where all image polygon bounding boxes are stored
  public static final String IMAGE_POLY_PATH = "polygons";

  // The path where all segment likelihoods are stored
  public static final String IMAGE_LIKELIHOOD_PATH = "segment_likelihoods";

  // The format for all the text info
  public static final String TEXT_FORMAT = ".txt";



  // Name of the data set.
  public final String datasetName;

  // All the image filenames in the database.
  public final String[] imageFilenames;

  // The Clusters that have already been returned, and thus should not be used again.
  public final boolean[] alreadyUsed;

  @SuppressWarnings("unchecked")
  public ClusterDatabase(String datasetName) throws FileNotFoundException {
	this.datasetName = datasetName;
    Vector<String> vImageFilenames = new Vector<String>();

    Scanner infoScanner = new Scanner(ClusterDatabase.class.getClassLoader().getResourceAsStream(
									SEGMENTATION_BASEDIR + OS_FILESEP + IMAGE_NAME_LIST + 
									OS_FILESEP + datasetName + TEXT_FORMAT));
    while (infoScanner.hasNext()) { 
      vImageFilenames.add(infoScanner.nextLine());
    }

    imageFilenames = new String[vImageFilenames.size()];
    vImageFilenames.toArray(imageFilenames);
	alreadyUsed = new boolean[imageFilenames.length];
	// Shuffle the array of filenames, and then use in order from now on. 
	// This fascilitates random sampling without replacement.
    Collections.shuffle(Arrays.asList(imageFilenames));
  }

  // Get image path from image filename
  public String imagePath(String filename) {
	return SEGMENTATION_BASEDIR + OS_FILESEP + 
		   IMAGE_RAW_PATH + OS_FILESEP + datasetName + 
		   OS_FILESEP + filename + IMAGE_RAW_FORMAT;
  }
  
  // Parse all the data about a particular ProtoCluster given the filename
  public ClusterServerSide parseCluster(String filename) throws FileNotFoundException {
	assert(filename != null);
	
	String imagePath =  imagePath(filename);

	// Read the polygons [FOR NOW JUST THROW THIS INFORMATION AWAY]
	Scanner polyScanner = new Scanner(ClusterDatabase.class.getClassLoader().getResourceAsStream(
									SEGMENTATION_BASEDIR + OS_FILESEP + 
									IMAGE_POLY_PATH + OS_FILESEP + 
									datasetName + OS_FILESEP + filename + TEXT_FORMAT));

    String polyXCoordsString = polyScanner.nextLine();
    String polyYCoordsString = polyScanner.nextLine();
    String[] polyXCoordsStrings = polyXCoordsString.split(",");
    String[] polyYCoordsStrings = polyYCoordsString.split(",");
	assert(polyXCoordsStrings.length == polyYCoordsStrings.length);
	int nPoints = polyXCoordsStrings.length;
	int[] polyXCoords = new int[nPoints];
	int[] polyYCoords = new int[nPoints];
	for (int i = 0; i < nPoints; i++) {
	  	polyXCoords[i] = (int) (Double.parseDouble(polyXCoordsStrings[i])*(Simulation.ARENASIZE/800));
	  	polyYCoords[i] = (int) (Double.parseDouble(polyYCoordsStrings[i])*(Simulation.ARENASIZE/800));		
	}
	NotStupidPolygon imageCoords = new NotStupidPolygon(polyXCoords, polyYCoords);
	
	// Read the segment likelihoods
 	Scanner nsScanner = new Scanner(ClusterDatabase.class.getClassLoader().getResourceAsStream(
									SEGMENTATION_BASEDIR + OS_FILESEP + 
									IMAGE_LIKELIHOOD_PATH + OS_FILESEP + 
									datasetName + OS_FILESEP + filename + TEXT_FORMAT));
    // The likelihoods of different nSegments values.
	Map<Integer, Double> nsLikelihoods = new HashMap<Integer, Double>();
    String nsLikelihoodsLine = nsScanner.nextLine();
    if (nsLikelihoodsLine.length() > 0) {
 	  String[] nsLikelihoodStrings = nsLikelihoodsLine.split(",");
  	  for (int i = 0; i < nsLikelihoodStrings.length; ++i) {
  	    String[] nsLikelihoodPair = nsLikelihoodStrings[i].split(":");
		nsLikelihoods.put(Integer.parseInt(nsLikelihoodPair[0]), Double.parseDouble(nsLikelihoodPair[1]));
   	  }
	}

  return new ClusterServerSide(imageCoords, imagePath, filename, nsLikelihoods);
  }

  // Return a random cluster that includes nSegments as an acceptable possibility.
  // Guarantees that this cluster has not already been returned by this function.
  // If no Clusters are available, returns null;
  public ClusterServerSide getRandom(int nSegments) throws FileNotFoundException {
 	ClusterServerSide css = null;
	for (int i = 0; i < imageFilenames.length; i++) {
		if (alreadyUsed[i]) continue;
		css = parseCluster(imageFilenames[i]);
		if (css.acceptable(nSegments)) {
		  alreadyUsed[i] = true;
		  break;
	  }
		  
	}
    return css;
  }


}
