package aoi;

import java.awt.geom.Line2D;
import java.io.*;

/** A "read-only" class used to represent a bullet in
 * the game.
 *
 * Note that ints are used although floats might be
 * more natural. This is because ints don't need
 * any special serialization/parsing to be transmitted
 * over the network.
 */ 
public class Bullet {

 
  /* Values transmitted to the clients. */
  public final int x;
  public final int y;
  public final Angle direction;

  /* Player who shot the bullet. Used to make sure the bullet doesn't
     kill the player as it gets fired! */
  public final int playerNo; // only least significant byte transmitted.

  // Whether to transmit marker bytes before each transmitted value.
  // Helps to make sure that the client isn's out of sync with the server (due to a programming error).
  private static final boolean CHECKBYTES = true;
  
  public Bullet(int playerNo, int x, int y, Angle direction) {
    this.x = x;
    this.y = y;
    this.direction = direction;
    this.playerNo = playerNo;
  }

  public Line2D getLine() {
    Bullet nextPos = SimulationUtils.nextBulletPosition(this);
    return new Line2D.Double(x, y, 2*x-nextPos.x, 2*y-nextPos.y);
  }
  
  public boolean equals(Object that) {
    Bullet thatBullet = (Bullet) that;
    if (this.x != thatBullet.x) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.RepetitionRate.INFREQUENT, "x");
      return false;
    }
    if (this.y != thatBullet.y) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.RepetitionRate.INFREQUENT, "y");
      return false;
    }
    if (!this.direction.equals(thatBullet.direction)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.RepetitionRate.INFREQUENT, "dir");
      return false;
    }
    if (this.playerNo != thatBullet.playerNo) {
      if (Simulation.VERIFYSERIALIZATION) 
        Logger.println(Logger.RepetitionRate.INFREQUENT, "playerNo");
      return false;
    }
    return true;
  }

  public String toString() {
    StringBuilder s = new StringBuilder();

    s.append("Bullet");
    
    s.append("\nx: ");
    s.append(x);

    s.append("\ny: ");
    s.append(y);

    s.append("\ndirection: ");
    s.append(direction);

    s.append("\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) {

    try {

      if (CHECKBYTES) o.writeBytes("p");
      o.write(playerNo);

      if (CHECKBYTES) o.writeBytes("x");
      o.writeInt(x);
      
      if (CHECKBYTES) o.writeBytes("y");
      o.writeInt(y);

      if (CHECKBYTES) o.writeBytes("d");
      direction.serialize(o);

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  public static Bullet parse(DataInputStream i) {

    Byte cmd;
    int p, x, y;
    Angle d;

    try {

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'p'); }
      p = i.read(); 
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'x'); }
      x = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'y'); }
      y = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'd'); }
      d = Angle.parse(i);
	    
    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
      return null;
    }

    return new Bullet(p, x, y, d);
  }
}
