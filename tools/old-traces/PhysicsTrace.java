package aoi;
import java.awt.Point;
import java.io.*;
import java.util.*;
// Feeds scripted key states through the 2011 physics (SimulationUtils.nextPosition)
// using KeyboardPilot's thrust/turn ramps, one line of output per 40 ms tick.
public class PhysicsTrace {
  public static void main(String[] a) throws Exception {
    BufferedReader in = new BufferedReader(new FileReader(a[0]));
    Arena arena = new Arena(new Cluster[0], new int[]{1});
    Ship ship = new Ship(new Point(0, 0), 0, 0, new Angle(0.0));
    int thrustTicks = 0, turnTicks = 0, t = 0;
    String line;
    while ((line = in.readLine()) != null && line.length() > 0) {
      String[] f = line.split(" ");
      boolean th = f[0].equals("1"), l = f[1].equals("1"), r = f[2].equals("1");
      int acc = th ? (int) Math.min(SimulationUtils.MAXACC, SimulationUtils.MAXACC * (0.7 + 0.3 * (thrustTicks * 40 / 300.))) : 0;
      double omega = Math.min(SimulationUtils.OMEGA, SimulationUtils.OMEGA * (0.4 + 0.6 * (turnTicks * 40 / 300.)));
      Angle target = ship.dir().add(omega * ((l ? 1 : 0) - (r ? 1 : 0)));
      ship = SimulationUtils.nextPosition(ship, new PlayerControlParameters(t, acc, target, false, false), arena, null);
      thrustTicks = th ? thrustTicks + 1 : 0;
      turnTicks = (l || r) ? turnTicks + 1 : 0;
      t++;
      System.out.println(t + " " + ship.x() + " " + ship.y() + " " + ship.vx() + " " + ship.vy() + " " + ship.dir().toDouble());
    }
  }
}
