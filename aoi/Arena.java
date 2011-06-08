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

    public final Vector<Cluster> clusters; 

    public Arena(Vector<Cluster> clusters) {
	this.clusters = clusters;
    }

    public Arena(Cluster[] clusters) {
	this.clusters = new Vector<Cluster>(Arrays.asList(clusters));
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("<arena>\n");

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
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static Arena parse(InputStream i) {

	try {

	    SAXParser sax = SAXParserFactory.newInstance().newSAXParser();
	    ArenaParser arena = new ArenaParser();
	    sax.parse(i, arena);

	    return new Arena(arena.clusters);

	} catch (Exception e) {
	    System.out.println(e.toString());
	    assert(false);
	    return null;
	}

    }


    public static void main(String argv[]) throws Exception {
	FileInputStream file = new FileInputStream(ARENA_METADATA_FILENAME);
	Arena arena = Arena.parse(file);
	System.out.println("Parsed arena:");
	System.out.println(arena.toString());
    }


    // SAX parser for extracting the clusters out of the arena XML file.
    private static class ArenaParser extends DefaultHandler {

	public Vector<Cluster> clusters;

	public ArenaParser() {
	    super();
	    clusters = new Vector<Cluster>();
	}

	public void startElement(String uri, String localName, String qName, Attributes attr)  {

	    if (qName == "cluster") {
		String imgPath = attr.getValue("imgPath");
		int imgID = Integer.parseInt(attr.getValue("imgID"));
		String polygon = attr.getValue("d");
		String[] parts = polygon.split(":");
		int n = Integer.parseInt(parts[0]);
		String[] coords = parts[1].split(" ");
		int[] xCoords = new int[n];
		int[] yCoords = new int[n];
		int j = 0;
		for (int i = 0; i < n; i++) {
			xCoords[i] = Integer.parseInt(coords[j++]);
			yCoords[i] = Integer.parseInt(coords[j++]);
		}
		

		clusters.add(new DrawableCluster(new NotStupidPolygon(xCoords, yCoords), imgPath, imgID));
	    }

	}

    }

}
