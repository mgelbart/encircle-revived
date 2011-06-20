package aoi;

import java.io.*;

/** 
 * A "read-only" class that describes a player's
 * position in phase space.
 *
 * Note that ints are used although floats might be
 * more natural. This is because ints don't need
 * any special serialization/parsing to be transmitted
 * over the network.
*/
public class PlayerState {

  public static final int SHIPLENGTH = SimulationUtils.ARENASIZE * 7/200;
  public static final int SHIPWIDTH = SimulationUtils.ARENASIZE * 4/200;

  /* Values transmitted to the clients. */
  public final int playerNo;
  public final Position pos;
  public final int energy;

  // The time at which the last loop was completed/current one started.
  public final int loopStarted;
  
  // original path started
  // last path ended
  // current path started

  // How many clusters the player has collected already
  public final int score;

  // True if the player is currently waiting to be respawn.
  public final boolean respawning;


  public PlayerState(int playerNo, Position pos, int energy, int loopStarted, int score, boolean respawning) {
    this.playerNo = playerNo;
    this.pos = pos;
    this.energy = energy;                     // NOT USED
    this.loopStarted = loopStarted;
    this.score = score;
    this.respawning = respawning;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }	

  public PlayerState(int playerNo, int x, int y, int vx, int vy, int direction, int energy, int loopStarted, int score, boolean respawning) {
    this(playerNo, new Position(x, y, vx, vy, direction), energy, loopStarted, score, respawning);
  }	


  // Make a deep copy but set some parameter
  public PlayerState clone(String key, int value) {

    int playerNo = this.playerNo;
    int energy = this.energy;
    int loopStarted = this.loopStarted;
    int score = this.score;
    boolean respawning = this.respawning;

    // TODO: use an enum for these.
    if (key.equals("score"))
      score = value;
    else if (key.equals("loopStarted"))
      loopStarted = value;
    else if (key.equals("respawning"))
      respawning = value != 0;
    else 
      assert(false);  // lol

    return new PlayerState(playerNo, this.pos, energy, loopStarted, score, respawning);
  }

  // Make a deep copy but set some parameter
  public PlayerState clone(Position pos) {
    return new PlayerState(this.playerNo, pos, this.energy, this.loopStarted, this.score, this.respawning);
  }


  public String toString() {
    StringBuilder s = new StringBuilder();

    s.append("Player #");
    s.append(playerNo);

    s.append("\nx: ");
    s.append(pos.x);

    s.append("\ny: ");
    s.append(pos.y);

    s.append("\ndirection: ");
    s.append(pos.direction);

    s.append("\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) {

    try {

      o.writeBytes("#");
      o.writeInt(playerNo);

      o.writeBytes("x");
      o.writeInt(pos.x);

      o.writeBytes("y");
      o.writeInt(pos.y);

      o.writeBytes("u");
      o.writeInt(pos.vx);

      o.writeBytes("v");
      o.writeInt(pos.vy);

      o.writeBytes("d");
      o.writeInt(pos.direction);

      o.writeBytes("e");
      o.writeInt(energy);

      o.writeBytes("l");
      o.writeInt(loopStarted);

      o.writeBytes("s");
      o.writeInt(score);

      o.writeBytes("r");
      o.writeBoolean(respawning);

    } catch (IOException ioe) {
      System.out.println(ioe.toString());
      assert(false);
    }

  }

  public static PlayerState parse(DataInputStream i) {

    Byte cmd;
    int p, x, y, vx, vy, d, e, l, s;
    boolean r;

    try {

      cmd = i.readByte();
      assert(cmd == '#');

      p = i.readInt();
      assert(p == 0 || p == 1);

      cmd = i.readByte();
      assert(cmd == 'x');
      x = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'y');
      y = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'u');
      vx = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'v');
      vy = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'd');
      d = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'e');
      e = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'l');
      l = i.readInt();

      cmd = i.readByte();
      assert(cmd == 's');
      s = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'r');
      r = i.readBoolean();

	    
    } catch (IOException ioe) {
      System.out.println(ioe.toString());
      assert(false);
      return null;
    }

    return new PlayerState(p, x, y, vx, vy, d, e, l, s, r);
  }


  public int[][] shape() {
    assert(playerNo >= 0);
    assert(playerNo < SimulationUtils.NUMPLAYERS);

    double cos = Math.cos(Math.PI * (pos.direction / (double) SimulationUtils.INTMAX));
    double sin = Math.sin(Math.PI * (pos.direction / (double) SimulationUtils.INTMAX));

    int x, y;

    int px[] = new int[3];
    int py[] = new int[3];

    for (int j=0; j<3; ++j) {

      // Generate one of the three corners relative to origin.
      x = ((j==1) ? 1:-1) * SHIPLENGTH / 2;
      y = (j-1) * SHIPWIDTH / 2;

      // Rotate each point by direction (multiply by {{cos -sin} {sin cos}})
      // and translate to player location.
      px[j] = this.pos.x + (int) (cos*x - sin*y);
      py[j] = this.pos.y + (int) (sin*x + cos*y);

    }

    return new int[][]{px, py};
  }

/**
 * Variables relating to the position of the player in the 'positional'
 * phase space.
 */
  public static class Position {
    public int x;
    public int y;
    public int vx;
    public int vy;
    public int direction;

    public Position(int x, int y, int vx, int vy, int direction) {
      this.x = x;
      this.y = y;
      this.vx = vx;
      this.vy = vy;
      this.direction = direction;
    }
  }

}
