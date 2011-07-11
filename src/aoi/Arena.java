package aoi;

import java.io.*;
import java.util.Vector;
import java.util.Arrays;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.*;

/**
 * A class for the static map information,
 *  namely about the clusters.
 */
public class Arena {

  public static final String ARENA_METADATA_FILENAME = "arena.xml";

  // The collectable objects in the map.
  public final Vector<Cluster> clusters; 

  // The sequence in which the objects are to be collected.
  private final int[] sequence;

  public Arena(Vector<Cluster> clusters, int[] sequence) {
    this.clusters = clusters;
    this.sequence = sequence;
  }

  public Arena(Cluster[] clusters, int[] sequence) {
    this.clusters = new Vector<Cluster>(Arrays.asList(clusters));
    this.sequence = sequence;
  }

  // Return the sequence to be collected.
  public int[] sequence() {

    int[] s = new int[sequence.length];
    System.arraycopy(sequence, 0, s, 0, sequence.length);

    return s;
  }

  public String toString() {
    StringBuilder s = new StringBuilder();

    assert(sequence.length > 0);

    s.append("<arena sequence=\"" + sequence[0]);
    for (int j=1; j<sequence.length; ++j)
      s.append("," + sequence[j]);
    s.append("\">\n");

    for (int j=0; j<clusters.size(); ++j) {
      s.append(clusters.get(j).toString());
      s.append("\n");
    }

    s.append("</arena>\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) {

    try {

      o.writeBytes(this.toString());

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  public static Arena parse(InputStream i) {

    try {

      SAXParser sax = SAXParserFactory.newInstance().newSAXParser();
      ArenaParser arena = new ArenaParser();
      sax.parse(i, arena);

      return new Arena(arena.clusters, arena.sequence);

    } catch (Exception e) {
      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      assert(false);
      return null;
    }

  }


  public static void main(String argv[]) throws Exception {
    FileInputStream file = new FileInputStream(ARENA_METADATA_FILENAME);
    Arena arena = Arena.parse(file);
    Logger.println(Logger.RepetitionRate.FEW, "Parsed arena:");
    Logger.println(Logger.RepetitionRate.FEW, arena.toString());
  }


  // SAX parser for extracting the clusters out of the arena XML file.
  private static class ArenaParser extends DefaultHandler {

    public int[] sequence;
    public Vector<Cluster> clusters;

    public ArenaParser() {
      super();
      clusters = new Vector<Cluster>();
    }

    public void startElement(String uri, String localName, String qName, Attributes attr)  {

      if (qName == "arena") {
        String[] nums = attr.getValue("sequence").split(",");

        sequence = new int[nums.length];
        for (int j=0; j < sequence.length; ++j)
          sequence[j] = Integer.parseInt(nums[j]);
      }

      if (qName == "cluster")
        clusters.add(new DrawableCluster(NotStupidPolygon.parse(attr), ImageMetaData.parse(attr)));

    }

  }

}
