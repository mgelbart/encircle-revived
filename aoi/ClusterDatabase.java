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
import javax.swing.ImageIcon;

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



  // Name of the data set
  public final String datasetName;

  // All the image filenames in the database
  public final String[] imageFilenames;

  // The current Cluster index
  private int i;

  @SuppressWarnings("unchecked")
  public ClusterDatabase(String datasetName) throws FileNotFoundException {
	i = 0;
	this.datasetName = datasetName;
    Vector<String> vImageFilenames = new Vector<String>();

    Scanner infoScanner = new Scanner(ClusterDatabase.class.getClassLoader().getResourceAsStream(
									SEGMENTATION_BASEDIR + OS_FILESEP + IMAGE_NAME_LIST + 
									OS_FILESEP + datasetName + TEXT_FORMAT));
    while (infoScanner.hasNext()) { 
      vImageFilenames.add(infoScanner.nextLine());

	  // The image filename.
      // String filename = infoScanner.nextLine();
	  //
      // // The likelihoods of different nSegments values.
      // Map<Integer, Double> nsLikelihoods = new HashMap<Integer, Double>();
      // String nsLikelihoodsLine = infoScanner.nextLine();
      // if (nsLikelihoodsLine.length() > 0) {
      //   String[] nsLikelihoodStrings = nsLikelihoodsLine.split(",");
      //   for (int i = 0; i < nsLikelihoodStrings.length; ++i) {
      //     String[] nsLikelihoodPair = nsLikelihoodStrings[i].split(":");
      //     nsLikelihoods.put(Integer.parseInt(nsLikelihoodPair[0]),
      //                       Double.parseDouble(nsLikelihoodPair[1]));
      //   }
      // }
      // 
      // // The details about votes from past data collection;
      // // currently unused.
      // String votes = infoScanner.nextLine();
      // 
      // vProtoClusters.add(new ProtoCluster(IMAGE_PATH + datasetName + "/" + filename, 
      //                                     nsLikelihoods, votes));
    }

    imageFilenames = new String[vImageFilenames.size()];
    vImageFilenames.toArray(imageFilenames);
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

  // Returns the next filename
  private String nextFilename() {
	if (i >= imageFilenames.length) return null;
    return imageFilenames[i++];
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
	
	// temporary crap, later we will get polygons
    ImageIcon img = new ImageIcon(imagePath);

    return new ClusterServerSide(img.getIconWidth() * (Simulation.ARENASIZE/800),
                                 img.getIconHeight() * (Simulation.ARENASIZE/800),
                                 imagePath, filename, nsLikelihoods);
  }

  // Return a random ProtoCluster that includes nSegments as an acceptable possibility
  public ClusterServerSide getRandom(int nSegments) throws FileNotFoundException {
 	ClusterServerSide css;
	do {
      css = parseCluster(nextFilename());
System.out.println(css);
System.out.println(nSegments);
System.out.println(css.acceptable(nSegments));
	} while (!css.acceptable(nSegments));
    return css;
  }
 

  // // Returns a random ProtoCluster than has at least minLikelihood likelihood
  // // of having nSegments segments. If none exist, return null.
  // public ProtoCluster getRandom(int nSegments) {
  //   // Shuffle allProtoClusters.
  //   // This might be SLOW. There's surely a smarter way of doing this...
  //   // Like sorting by number of segments, etc. Oh well, can change it later.
  //   Collections.shuffle(Arrays.asList(allProtoClusters));
  //   for (ProtoCluster pc : allProtoClusters)
  //     if (pc.acceptableNS().contains(nSegments))
  //       return pc;
  //   return null;
  // }
  // 
  // 
  // // Return the metadata for one particular image stored in 'path'.
  // public ProtoCluster getByPath(String path) {
  // 
  //   for (ProtoCluster p : allProtoClusters) {
  //     if (p.filepath.equals(path))
  //       return p;
  //   }
  // 
  //   assert(false);
  //   return null;
  // }

}
