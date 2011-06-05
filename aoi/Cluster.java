package aoi;

/*
  Clusters are image patches spread around the map. Each cluster
  contains a particular number of segments which defines the
  clusters numerical value. The players collect clusters in
  increasing order of numberical value.
*/

import java.awt.Rectangle;

public class Cluster {
  public final int x;
  public final int y;
  public final int width;
  public final int height;
  public final String imgPath;

  public Cluster(int x, int y, int width, int height, String imgPath) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.imgPath = imgPath;

    assert(x-width/2 <=  Simulation.ARENASIZE/2);
    assert(x-width/2 >= -Simulation.ARENASIZE/2);
    assert(x+width/2 <=  Simulation.ARENASIZE/2);
    assert(x+width/2 >= -Simulation.ARENASIZE/2);

    assert(y-height/2 <=  Simulation.ARENASIZE/2);
    assert(y-height/2 >= -Simulation.ARENASIZE/2);
    assert(y+height/2 <=  Simulation.ARENASIZE/2);
    assert(y+height/2 >= -Simulation.ARENASIZE/2);
  }

  public String toString() {
    return "<cluster x=\"" + x
      + "\" y=\"" + y
      + "\" width=\"" + width
      + "\" height=\"" + height
      + "\" img=\"" + imgPath + "\"/>";
  }

  public Rectangle boundingBox() {
    return new Rectangle(x - width/2, y - height/2, width, height);
  }

}
