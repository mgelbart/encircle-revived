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
  public final int playerNo; // only least significant byte transmitted to client.
  public final Ship ship;
  public final int energy; // only least significant byte transmitted to client.
  public final Tail tail;

  // The time coordinate of the start of the current tail.
  public final int tailStarted;
  
  // The segment of the tail that the client should cut off from the tail
  // expressed as an interval in time, including from and excluding to.
  public final int cropTailFrom, cropTailTo;
  
  // How many clusters the player has collected already
  public final int score; // only least significant byte transmitted to client.

  /* The following are essentially boolean values but,
   * since they are 'singular' events (happen on a single turn)
   * and we want to transmit them to the Clients in
   * a packet loss tolerant way, we transmit them as
   * "time since last X" or "time until next Y".
   * 
   * However, to save bandwidth we only transmit one byte per since/until* 
   * variable. Any value >255 is transmitted as 255. */
  public final int untilReload;
  public final int untilRespawn;
  public final int sinceScoring;
  public final int sinceLooping;
  public final int sinceCrossing; // the other player's path.
  public final int sinceHitByBullet;
  public final int sincePwned; // Lost all points.


  // Whether to transmit marker bytes before each transmitted value.
  // Helps to make sure that the client isn's out of sync with the server (due to a programming error).
  private static final boolean CHECKBYTES = true;


  public PlayerState(int playerNo, Ship ship, int energy, 
      int tailStarted, int cropTailFrom, int cropTailTo, 
      int score, int untilReload, int untilRespawn,
      int sinceScoring, int sinceLooping, int sinceCrossing,
      int sinceHitByBullet, int sincePwned, Tail tail) {
    this.playerNo = playerNo;
    this.ship = new Ship(ship);
    this.energy = energy;                     // NOT USED
    this.tailStarted = tailStarted;
    this.cropTailFrom = cropTailFrom;
    this.cropTailTo = cropTailTo;
    this.score = score;
    this.untilReload = untilReload;
    this.untilRespawn = untilRespawn;
    this.sinceScoring = sinceScoring;
    this.sinceLooping = sinceLooping;
    this.sinceCrossing = sinceCrossing;
    this.sinceHitByBullet = sinceHitByBullet;
    this.sincePwned = sincePwned;
    this.tail = tail;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }	

  public PlayerState(int playerNo, int x, int y, int vx, int vy, Angle direction, 
      int energy, int tailStarted, int cropTailFrom, int cropTailTo, 
      int score, int untilReload, int untilRespawn,
      int sinceScoring, int sinceLooping, int sinceCrossing,
      int sinceHitByBullet, int sincePwned, Tail tail) {
    this(playerNo, new Ship(x, y, vx, vy, direction), 
        energy, tailStarted, cropTailFrom, cropTailTo,
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, tail);
  }	


  // Make a deep copy but set some parameter
  public PlayerState clone(String key, int value) {

    int playerNo = this.playerNo;
    int energy = this.energy;
    int tailStarted = this.tailStarted;
    int score = this.score;
    int untilRespawn = this.untilRespawn;
    int untilReload = this.untilReload;
    int sincePwned = this.sincePwned;

    // TODO: use an enum for these.
    if (key.equals("score"))
      score = value;
    else if (key.equals("tailStarted"))
      tailStarted = value;
    else if (key.equals("untilRespawn"))
      untilRespawn = value;
    else if (key.equals("untilReload"))
      untilReload = value;
    else if (key.equals("sincePwned"))
      sincePwned = value;
    else 
      assert(false);  // lol

    return new PlayerState(playerNo, ship, 
        energy, tailStarted, cropTailFrom, cropTailTo, 
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, tail);
  }

  // Make a deep copy but set new ship
  public PlayerState clone(Ship newShip) {
    return new PlayerState(playerNo, newShip, 
        energy, tailStarted, cropTailFrom, cropTailTo,
        score,  untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, tail);
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
      
      assert(playerNo >= 0);
      assert(playerNo <= 0xFF);
      if (CHECKBYTES) o.writeBytes("#");
      o.write(playerNo);

      if (CHECKBYTES) o.writeBytes("x");
      o.writeInt(ship.x());

      if (CHECKBYTES) o.writeBytes("y");
      o.writeInt(ship.y());

      if (CHECKBYTES) o.writeBytes("u");
      o.writeInt(ship.vx());

      if (CHECKBYTES) o.writeBytes("v");
      o.writeInt(ship.vy());

      if (CHECKBYTES) o.writeBytes("d");
      ship.dir().serialize(o);
      
      assert(energy >= 0);
      assert(energy <= 0xFF);
      if (CHECKBYTES) o.writeBytes("e");
      o.write(energy);

      if (CHECKBYTES) o.writeBytes("l");
      o.writeInt(tailStarted);

      if (CHECKBYTES) o.writeBytes("f");
      o.writeInt(cropTailFrom);

      if (CHECKBYTES) o.writeBytes("t");
      o.writeInt(cropTailTo);
      
      assert(score >= 0);
      assert(score <= 0xFF);
      if (CHECKBYTES) o.writeBytes("s");
      o.write(score);

      if (CHECKBYTES) o.writeBytes("l");
      o.write((untilReload > 0xFF)? 0xFF:untilReload); // 255 = 0xFF (as an unsigned byte)
      
      if (CHECKBYTES) o.writeBytes("r");
      o.write((untilRespawn > 0xFF)? 0xFF:untilRespawn);
      
      if (CHECKBYTES) o.writeBytes("s");
      o.write((sinceScoring > 0xFF)? 0xFF:sinceScoring);
      
      if (CHECKBYTES) o.writeBytes("l");
      o.write((sinceLooping > 0xFF)? 0xFF:sinceLooping);
      
      if (CHECKBYTES) o.writeBytes("c");
      o.write((sinceCrossing > 0xFF)? 0xFF:sinceCrossing);
      
      if (CHECKBYTES) o.writeBytes("b");
      o.write((sinceHitByBullet > 0xFF)? 0xFF:sinceHitByBullet);
      
      if (CHECKBYTES) o.writeBytes("p");
      o.write((sincePwned > 0xFF)? 0xFF:sincePwned);
      

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  // Construct the new PlayerState using the players state data received from the server
  // and the previous PlayerState (needed to construct things like Tail that are sent incrementally).
  public static PlayerState parse(DataInputStream i, PlayerState prevState, int time) {

    Byte cmd;
    int playerNo, x, y, vx, vy, energy, tailStarted, cropTailFrom, cropTailTo, score,
        untilReload, untilRespawn, sinceScoring, sinceLooping, sinceCrossing,
          sinceHitByBullet, sincePwned;
    Angle dir;

    try {

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == '#'); }
      playerNo = i.read();
      assert(playerNo == 0 || playerNo == 1);

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'x'); }
      x = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'y'); }
      y = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'u'); }
      vx = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'v'); }
      vy = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'd'); }
      dir = Angle.parse(i);

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'e'); }
      energy = i.read();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'l'); }
      tailStarted = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'f'); }
      cropTailFrom = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 't'); }
      cropTailTo = i.readInt();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
      score = i.read();

      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'l'); }
      untilReload = i.read();
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'r'); }
      untilRespawn = i.read();
        
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
      sinceScoring = i.read();
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'l'); }
      sinceLooping = i.read();
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'c'); }
      sinceCrossing = i.read();
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'b'); }
      sinceHitByBullet = i.read();
      
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'p'); }
      sincePwned = i.read();
      

	    
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
//      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, "first tail init");
    } else if (untilRespawn > 0) { 
      newTail = prevState.tail.crop(cropTailFrom, cropTailTo);
    } else if (deadOrShot(prevState, tailStarted, cropTailFrom, cropTailTo)) {
      // you died or got shot, etc. so just re-initialize tail
      newTail = new Tail();
//      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
//      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, "dead or shot - tail init");
    } else if (spentTail(prevState, tailStarted, cropTailFrom, cropTailTo)) {
      // you spent part of your tail on something like shooting
      newTail = prevState.tail.crop(cropTailFrom, cropTailTo);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
//      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, "SHOT - shorted tain");
    } else if (madeLoop(prevState, tailStarted, cropTailFrom, cropTailTo)) {
      // you made a loop
      newTail = prevState.tail.crop(cropTailFrom, cropTailTo);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, prevState.tailStarted + " / " + prevState.cropTailFrom + " / " + prevState.cropTailTo + " / ");
//      Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropTailFrom + " / " + cropTailTo + " / " + time);
//      Logger.println(Logger.RepetitionRate.INFREQUENT, "made a loop - crop tail");
    } else {
      // normal step
      newTail = prevState.tail.clone();
    }

    if (untilRespawn == 0)
      newTail = newTail.addPoint(x, y, time);

    return new PlayerState(playerNo, x, y, vx, vy, dir, 
        energy, tailStarted, cropTailFrom, cropTailTo, 
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, newTail);
  }
  
  public static boolean deadOrShot(PlayerState prevState, 
                                   int tailStarted, int cropTailFrom, int cropTailTo) {
    return prevState.tailStarted - tailStarted < 0 &&
           prevState.tailStarted != cropTailFrom;
  }
  public static boolean spentTail(PlayerState prevState, 
                                  int tailStarted, int cropTailFrom, int cropTailTo) {
    return prevState.tailStarted - tailStarted < 0 &&
           prevState.tailStarted == cropTailFrom;
  }
  public static boolean madeLoop(PlayerState prevState, 
      int tailStarted, int cropTailFrom, int cropTailTo) {
    return prevState.tailStarted == tailStarted &&
           prevState.cropTailTo != cropTailTo;
  }
  
  
  

}
