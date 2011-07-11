package aoi;

import java.io.*;

/** 
 * A "read-only" class that describes a player's state.
 *
 * Note that ints are used although floats might be
 * more natural. This is because ints don't need
 * any special serialization/parsing to be transmitted
 * over the network.
*/
public class PlayerState {

  public static final int SHIPLENGTH = SimulationUtils.ARENASIZE * 7/200;
  public static final int SHIPWIDTH  = SimulationUtils.ARENASIZE * 4/200;

  /* Values transmitted to the clients. */
  public final int playerNo;
  public final Ship ship;
  public final int energy;
  public final Tail tail;

  // The time at which the last loop was completed/current one started.
  public final int loopStarted;
  
  // original path started
  // last path ended
  // current path started

  // How many clusters the player has collected already
  public final int score;

  // True if the player is currently waiting to be respawn.
  public final boolean respawning;


  public PlayerState(int playerNo, Ship ship, int energy, int loopStarted, int score, boolean respawning, Tail tail) {
    this.playerNo = playerNo;
    this.ship = new Ship(ship);
    this.energy = energy;                     // NOT USED
    this.loopStarted = loopStarted;
    this.score = score;
    this.respawning = respawning;
    this.tail = tail;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }	

  public PlayerState(int playerNo, int x, int y, int vx, int vy, int direction, int energy, int loopStarted, int score, boolean respawning, Tail tail) {
    this(playerNo, new Ship(x, y, vx, vy, direction), energy, loopStarted, score, respawning, tail);
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

    return new PlayerState(playerNo, ship, energy, loopStarted, score, respawning, tail);
  }

  // Make a deep copy but set new ship
  public PlayerState clone(Ship newShip) {
    return new PlayerState(playerNo, newShip, energy, loopStarted, score, respawning, tail);
  }


  public String toString() {
    StringBuilder s = new StringBuilder();

    s.append("Player #");
    s.append(playerNo);

    s.append("\nx: ");
    s.append(ship.x());

    s.append("\ny: ");
    s.append(ship.y());

    s.append("\ndirection: ");
    s.append(ship.dir());

    s.append("\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) {

    try {

      o.writeBytes("#");
      o.writeInt(playerNo);

      o.writeBytes("x");
      o.writeInt(ship.x());

      o.writeBytes("y");
      o.writeInt(ship.y());

      o.writeBytes("u");
      o.writeInt(ship.vx());

      o.writeBytes("v");
      o.writeInt(ship.vy());

      o.writeBytes("d");
      o.writeInt(ship.dir());

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

  // Construct the new PlayerState using the players state data received from the server
  // and the previous PlayerState (needed to construct things like Tail that are sent incrementally).
  public static PlayerState parse(DataInputStream i, PlayerState prevState, int time) {

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

    final Tail newTail = (prevState == null) ?
        new Tail(x, y, time) : prevState.tail.addPoint(x, y, time);
    
    return new PlayerState(p, x, y, vx, vy, d, e, l, s, r, newTail);
  }

  
  

}
