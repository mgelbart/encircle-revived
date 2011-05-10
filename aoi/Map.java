package aoi;

/*
  A class for the static map information,
  namely about the supervoxels.
 */

import java.io.*;
import java.util.Vector;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.*;

public class Map {
    public final Vector<Voxel> voxels;

    public Map(Vector<Voxel> voxels) {
	this.voxels = voxels;
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("<map>\n");

	for (int j=0; j<voxels.size(); ++j) {
	    s.append(voxels.get(j).toString());
	    s.append("\n");
	}

	s.append("</map>\n");

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

    public static Map parse(InputStream i) {

	try {

	    SAXParser sax = SAXParserFactory.newInstance().newSAXParser();
	    MapParser map = new MapParser();
	    sax.parse(i, map);

	    return new Map(map.voxels);

	} catch (Exception e) {
	    System.out.println(e.toString());
	    assert(false);
	    return null;
	}

    }

    public static void main(String argv[]) throws Exception {
	FileInputStream file = new FileInputStream("map.xml");
	Map map = Map.parse(file);
	System.out.println("Parsed map:");
	System.out.println(map.toString());
    }


    // SAX parser for extracting the voxels out of the map XML file.
    private static class MapParser extends DefaultHandler {

	public Vector<Voxel> voxels;

	public MapParser() {
	    super();
	    voxels = new Vector<Voxel>();
	}

	public void startElement(String uri, String localName, String qName, Attributes attr)  {

	    if (qName == "voxel") {
		int x = Integer.parseInt(attr.getValue("x"));
		int y = Integer.parseInt(attr.getValue("y"));
		int width = Integer.parseInt(attr.getValue("width"));
		int height = Integer.parseInt(attr.getValue("height"));
		String img = attr.getValue("img");

		voxels.add(new Voxel(x, y, width, height, img));
	    }

	}

    }

}
