package aoi;
import java.awt.Point;
import java.io.*;
// Feeds scripted cursor positions (in game coordinates) through the 2011 mouse
// steering (Tracker.paramsToTarget, plus HigherBeing's "stop" key) and physics.
public class MouseTrace {
  public static void main(String[] a) throws Exception {
    BufferedReader in = new BufferedReader(new FileReader(a[0]));
    Arena arena = new Arena(new Cluster[0], new int[]{1});
    Ship ship = new Ship(new Point(0, 0), 0, 0, new Angle(0.0));
    int t = 0;
    String line;
    while ((line = in.readLine()) != null && line.length() > 0) {
      String[] f = line.split(" ");
      Point target = new Point(Integer.parseInt(f[0]), Integer.parseInt(f[1]));
      PlayerControlParameters p = Tracker.paramsToTarget(t, ship, target);
      if (f[2].equals("1")) p = p.stop();
      ship = SimulationUtils.nextPosition(ship, p, arena, null);
      t++;
      System.out.println(t + " " + ship.x() + " " + ship.y() + " " + ship.vx() + " " + ship.vy() + " " + ship.dir().toDouble());
    }
  }
}
