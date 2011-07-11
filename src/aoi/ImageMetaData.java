package aoi;

import org.xml.sax.Attributes;
import javax.xml.bind.DatatypeConverter;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/**
 *  Data wrapper class. Image data for a cluster.
 */
public class ImageMetaData {

  // The format of the on image (PNG).
  //public static final String IMAGEMIME = "image/png";

  // Every valid PNG file begins with these bytes.
  public final byte[] IMGFORMATHEADER = DatatypeConverter.parseHexBinary("89504e47");

  private final byte[] imgData; // The image data in binary format
  private int sizeX, sizeY;  // the dimensions of the image, for drawing only
  private int centerX, centerY; // the center of the image, for drawing only
  // (the above is generally (0,0) except for some small) perturbations
  private final String name;  // filename of the image file
  // eventually, the client should not have this information
  // but it's currently needed for making a 
  // ClusterServerSide out of a Cluster
			
  public ImageMetaData(String imgData, int sizeX, int sizeY, int centerX, int centerY, String name) {
    this(DatatypeConverter.parseBase64Binary(imgData), sizeX, sizeY, centerX, centerY, name);
  }
			
  public ImageMetaData(byte[] imgData, int sizeX, int sizeY, int centerX, int centerY, String name) {
    this.imgData = imgData; // Note: not cloned!
    this.sizeX = sizeX;
    this.sizeY = sizeY;
    this.centerX = centerX;
    this.centerY = centerY;
    this.name = name;

    for (int j=0; j < IMGFORMATHEADER.length; ++j) {
      assert(this.imgData[j] == IMGFORMATHEADER[j]);
    }
  }
	
  // Read serialized object
  public static ImageMetaData parse(Attributes serialized) {
    String imgData = serialized.getValue("imgData");

    String[] sizes = serialized.getValue("imgSize").split(",");
    int sizeX = Integer.parseInt(sizes[0]);
    int sizeY = Integer.parseInt(sizes[1]);
	  
    String[] centers = serialized.getValue("imgCenter").split(",");
    int centerX = Integer.parseInt(centers[0]);
    int centerY = Integer.parseInt(centers[1]);
	  	  
    String name = serialized.getValue("imgName");

    return new ImageMetaData(imgData, sizeX, sizeY, centerX, centerY, name);
  }

  // Encode object for serialization
  public String toString() {
    return " imgData=\"" + DatatypeConverter.printBase64Binary(imgData) + "\""
      +  " imgSize=\"" + sizeX + "," + sizeY + "\""
      +  " imgCenter=\"" + centerX + "," + centerY + "\""
      +  " imgName=\"" + name + "\"";
  }
	
  public void move(int deltaX, int deltaY) {
    centerX += deltaX;
    centerY += deltaY;
  }

  public Image img() {
    try {
      return ImageIO.read(new ByteArrayInputStream(imgData));
    } catch (Exception e) {
      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      assert(false);
      return null;
    }
  }
	
  public int width()   { return sizeX; }
  public int height()  { return sizeY; }
  public int centerX() { return centerX; }
  public int centerY() { return centerY; }
  //public byte[] imgData() { return imgData; }  // Note: doesn't clone the byte[]!
  public String name() { return name; }

}
