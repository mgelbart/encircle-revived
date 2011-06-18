package aoi;

import java.io.*;
import java.util.*;

/**
 *  Loads all the cluster information for a 
 *  particular data set. This information is
 *  then accessed to make instances of
 *  ClusterServerSide through generateArena() 
 *  and copyFromCluster()
 */
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
  public static final String IMAGE_RAW_FORMAT = ".png";

  // The path where all image polygon bounding boxes are stored
  public static final String IMAGE_POLY_PATH = "polygons";

  // The path where all segment likelihoods are stored
  public static final String IMAGE_LIKELIHOOD_PATH = "segment_likelihoods";
  
  // The path where votes are written to files during the game
  public static final String IMAGE_OUTPUT = "game_output";

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
    while (infoScanner.hasNext())
      vImageFilenames.add(infoScanner.nextLine());

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
    // Get the file path from the stored paths in memory
    String imagePath = imagePath(filename);

    // Read the image data from the file to a vector of bytes.
    InputStream imgStream = Client.class.getClassLoader().getResourceAsStream(imagePath);
    Vector<Byte> imgDataVec = new Vector<Byte>();

    try {
      int b = imgStream.read();
      while(b >= 0) {
        imgDataVec.add((byte) b);
        b = imgStream.read(); // Returns -1 at end of stream.
      }
    } catch (Exception e) {
      System.err.println(e);
      assert(false);
    }

    // Convert to a byte[]
    byte[] imgData = new byte[imgDataVec.size()];
    for (int j=0; j < imgData.length; ++j)
      imgData[j] = imgDataVec.get(j);


    // Read the polygons [FOR NOW JUST THROW THIS INFORMATION AWAY]
    Scanner polyScanner = new Scanner(ClusterDatabase.class.getClassLoader().getResourceAsStream(
                                                                                                 SEGMENTATION_BASEDIR + OS_FILESEP + 
                                                                                                 IMAGE_POLY_PATH + OS_FILESEP + 
                                                                                                 datasetName + OS_FILESEP + filename + TEXT_FORMAT));
    
    String[] imageSizeString = polyScanner.nextLine().split(",");
    int imageSizeX = Integer.parseInt(imageSizeString[0]);
    int imageSizeY = Integer.parseInt(imageSizeString[1]);
    
    int numPointsInContour = Integer.parseInt(polyScanner.nextLine());
     
    String polyXCoordsString = polyScanner.nextLine();
    String polyYCoordsString = polyScanner.nextLine();
    String[] polyXCoordsStrings = polyXCoordsString.split(",");
    String[] polyYCoordsStrings = polyYCoordsString.split(",");
    assert(polyXCoordsStrings.length == numPointsInContour);
    assert(polyYCoordsStrings.length == numPointsInContour);
    int[] polyXCoords = new int[numPointsInContour];
    int[] polyYCoords = new int[numPointsInContour];
  	
    double scale = Simulation.ARENASIZE*DrawableCluster.CLUSTER_SCALING_FACTOR; // convert to Arena coordinates
    for (int i = 0; i < numPointsInContour; i++) {
      polyXCoords[i] = (int) (Double.parseDouble(polyXCoordsStrings[i])*scale);
      polyYCoords[i] = (int) (Double.parseDouble(polyYCoordsStrings[i])*scale);		
    }
    imageSizeX *= scale;
    imageSizeY *= scale;
    // scale the image size as well
    // this could also be done with the scale() and scaledVersion() commands of ImageMetaData and NotStupidPolygon
    // respectively, but it's simpler to do it this way because of double/int conversion + rounding errors
  	
    NotStupidPolygon imageContour = new NotStupidPolygon(polyXCoords, polyYCoords);
    // The data from the database comes from Matlab which gives the coordinates
    // from the center of the image. This does not mean that the center of these
    // coordinates is necessarily (0,0) because the contour might not be centered
    // in the image. But, this is approximately and if untrue, probably only
    // by one pixel. 
    ImageMetaData imageMetaData = new ImageMetaData(imgData, imageSizeX, imageSizeY, 0, 0, filename);
	
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

    return new ClusterServerSide(imageContour, imageMetaData, nsLikelihoods);
  }
  private ClusterServerSide parseCluster(int i) throws FileNotFoundException {
    assert(i >= 0);
    assert(i < imageFilenames.length);
  
    return parseCluster(imageFilenames[i]);
  }
  
  // Write to file that a player voted cluster name to have a particular number of segments
  public void writeData(String name, int vote, String playerID) throws IOException {
    BufferedWriter out = new BufferedWriter(new FileWriter(
                                                           SEGMENTATION_BASEDIR + OS_FILESEP + IMAGE_OUTPUT + 
                                                           OS_FILESEP + datasetName + OS_FILESEP + name + TEXT_FORMAT, true));
    // the "true" above causes the writer to append instead of overwrite
    out.write(vote + "::" + playerID);
    out.newLine();
    out.close();
  }
  
  // Construct a ClusterServerSide corresponding to a Cluster
  public ClusterServerSide copyFromCluster(Cluster c) throws FileNotFoundException {
    ClusterServerSide css = parseCluster(c.imgName());
    // System.err.println(c.imgName());
    // System.err.println(c.contour());
    // System.err.println(c.imgMetaData());
    // System.err.println(css.nsLikelihoods);
    // System.err.println("--");
    return new ClusterServerSide(c.contour(), c.imgMetaData(), css.nsLikelihoods);
  }
  

  /*
    Generates a collection of clusters that can be used to initialize an Arena.

    In the end the placement of the clusters will be quite important for game-
    play as well as information gain about the cluster size so we probably want
    to think about how to do this more carefully.
  */
  public Vector<ClusterServerSide> generateArena(int[] clustersPerValue) throws FileNotFoundException{

    Vector<ClusterServerSide> arenaClusters = new Vector<ClusterServerSide>();

    ClusterServerSide css;
    int count, x, y, i;
    boolean overlaps;

    for (int nSegments=1; nSegments <= clustersPerValue.length; nSegments++) {

      for (int k=0; k < clustersPerValue[nSegments-1]; ++k) {
        
        // Get a random cluster that includes nSegments as an acceptable possibility.
        // Guarantees that this cluster has not already been returned by this function.
        // If no Clusters are available, throw an error (for now, just an assert)
       	css = null;
      	for (i = 0; i < imageFilenames.length; i++) {
          if (alreadyUsed[i]) continue;
          css = parseCluster(i);
          if (css.acceptable(nSegments))
            break;
          else
            css = null;
      	}
        
        if (css == null) System.err.println("Error: database does not have enough clusters with " + nSegments + " segments.");
        assert(css != null);
        
        // keep searching for a random location until finding one that does not
        // overlap with any existing clusters already chosen for the arena
        do {
          // give it a random location
          css.randomizeLocation();

          overlaps = false;
          for (Cluster inArena : arenaClusters)
            if (inArena.intersects(css)) 
              overlaps = true;
        } while(overlaps);

        arenaClusters.add(css);
        // Only if this cluster is actually added to the arena does alreadyUsed get edited
        alreadyUsed[i] = true;
      }

    }

    return arenaClusters;

  }


}
