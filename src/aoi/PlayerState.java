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
  public final int tailStarted;
  
  
  public final int cropTailFrom, cropTailTo;
  
  // How many clusters the player has collected already
  public final int score;

  // True if the player is currently waiting to be respawn.
  public final boolean respawning;


  public PlayerState(int playerNo, Ship ship, int energy, 
      int tailStarted, int cropTailFrom, int cropTailTo, 
      int score, boolean respawning, Tail tail) {
    this.playerNo = playerNo;
    this.ship = new Ship(ship);
    this.energy = energy;                     // NOT USED
    this.tailStarted = tailStarted;
    this.cropTailFrom = cropTailFrom;
    this.cropTailTo = cropTailTo;
    this.score = score;
    this.respawning = respawning;
    this.tail = tail;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }	

  public PlayerState(int playerNo, int x, int y, int vx, int vy, int direction, 
      int energy, int tailStarted, int cropTailFrom, int cropTailTo, 
      int score, boolean respawning, Tail tail) {
    this(playerNo, new Ship(x, y, vx, vy, direction), 
        energy, tailStarted, cropTailFrom, cropTailTo,
        score, respawning, tail);
  }	


  // Make a deep copy but set some parameter
  public PlayerState clone(String key, int value) {

    int playerNo = this.playerNo;
    int energy = this.energy;
    int tailStarted = this.tailStarted;
    int score = this.score;
    boolean respawning = this.respawning;

    // TODO: use an enum for these.
    if (key.equals("score"))
      score = value;
    else if (key.equals("loopStarted"))
      tailStarted = value;
    else if (key.equals("respawning"))
      respawning = value != 0;
    else 
      assert(false);  // lol

    return new PlayerState(playerNo, ship, 
        energy, tailStarted, cropTailFrom, cropTailTo, 
        score, respawning, tail);
  }

  // Make a deep copy but set new ship
  public PlayerState clone(Ship newShip) {
    return new PlayerState(playerNo, newShip, 
        energy, tailStarted, cropTailFrom, cropTailTo,
        score, respawning, tail);
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
      o.writeInt(tailStarted);

      o.writeBytes("m");
      o.writeInt(cropTailFrom);

      o.writeBytes("n");
      o.writeInt(cropTailTo);
      
      o.writeBytes("s");
      o.writeInt(score);

      o.writeBytes("r");
      o.writeBoolean(respawning);

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  // Construct the new PlayerState using the players state data received from the server
  // and the previous PlayerState (needed to construct things like Tail that are sent incrementally).
  public static PlayerState parse(DataInputStream i, PlayerState prevState, int time) {

    Byte cmd;
    int playerNo, x, y, vx, vy, dir, energy, tailStarted, cropTailFrom, cropTailTo, score;
    boolean respawning;

    try {

      cmd = i.readByte();
      assert(cmd == '#');

      playerNo = i.readInt();
      assert(playerNo == 0 || playerNo == 1);

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
      dir = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'e');
      energy = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'l');
      tailStarted = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'm');
      cropTailFrom = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'n');
      cropTailTo = i.readInt();

      cmd = i.readByte();
      assert(cmd == 's');
      score = i.readInt();

      cmd = i.readByte();
      assert(cmd == 'r');
      respawning = i.readBoolean();

	    
    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
      return null;
    }

    // Add the latest player location to the tail unless
    // the transmitted loop started parameter indicates that a
    // new tail was started. also crop loop if indicated by
    // cropfrom/to parameters.
    

    
    
    Tail newTail;
    if (prevState == null) {
      // first time step
      newTail = new Tail();
      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
      Logger.println(Logger.RepetitionRate.INFREQUENT, "first tail init");
    } else if (prevState.tail.startTime() - tailStarted < 0 &&
        prevState.tailStarted != cropTailFrom) {
      // you died or got shot, etc. so just re-initialize tail
      newTail = new Tail();
      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
      Logger.println(Logger.RepetitionRate.INFREQUENT, "dead or shot - tail init");
    } else if (prevState.tail.startTime() - tailStarted < 0 &&
        prevState.tailStarted == cropTailFrom) {
      // you spent part of your tail on something like shooting
//      Logger.println(Logger.RepetitionRate.INFREQUENT, ""+prevState.tail.pathLength());
      newTail = prevState.tail.crop(cropTailFrom, cropTailTo);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, " -> " + newTail.pathLength());
      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
      Logger.println(Logger.RepetitionRate.INFREQUENT, "SHOT - shorted tain");
    } else if (prevState.cropTailTo != cropTailTo) {
      // you made a loop
      newTail = prevState.tail.crop(cropTailFrom, cropTailTo);
      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
      Logger.println(Logger.RepetitionRate.INFREQUENT, "made a loop - crop tail");
    } else {
      // normal step
      newTail = prevState.tail.clone();
    }

    
    
    newTail = newTail.addPoint(x, y, time);

    
    
    return new PlayerState(playerNo, x, y, vx, vy, dir, 
        energy, tailStarted, cropTailFrom, cropTailTo, 
        score, respawning, newTail);
  }

  
  

}
