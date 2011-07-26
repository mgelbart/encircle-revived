package aoi;

import java.awt.Point;
import java.io.*;

import aoi.Logger.Rep;

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
  public final Tail tail;

  // The segment of the tail that the client should cut off from the tail
  // expressed as an interval in time, including from and excluding to.
  public final CropEventGroup cropEvents;
  
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
  public final int untilRespawn; // transmitted as a SHORT!
  public final int sinceScoring;
  public final int sinceLooping;
  public final int sinceCrossing; // the other player's path.
  public final int sinceHitByBullet;
  public final int sincePwned; // Lost all points.
  public final int sinceCrashed;


  // Whether to transmit marker bytes before each transmitted value.
  // Helps to make sure that the client isn's out of sync with the server (due to a programming error).
  public static final boolean CHECKBYTES = true;


  public PlayerState(int playerNo, Ship ship,
      CropEventGroup cropEvents, 
      int score, int untilReload, int untilRespawn,
      int sinceScoring, int sinceLooping, int sinceCrossing,
      int sinceHitByBullet, int sincePwned, int sinceCrashed,
      Tail tail) {
    this.playerNo         = playerNo;
    this.ship             = ship;
    this.cropEvents       = cropEvents;
    this.score            = score;
    this.untilReload      = SimulationUtils.enforceByte(untilReload);
    this.untilRespawn     = SimulationUtils.enforceShort(untilRespawn);
    this.sinceScoring     = SimulationUtils.enforceByte(sinceScoring);
    this.sinceLooping     = SimulationUtils.enforceByte(sinceLooping);
    this.sinceCrossing    = SimulationUtils.enforceByte(sinceCrossing);
    this.sinceHitByBullet = SimulationUtils.enforceShort(sinceHitByBullet);
    this.sincePwned       = SimulationUtils.enforceByte(sincePwned);
    this.sinceCrashed     = SimulationUtils.enforceByte(sinceCrashed);
    this.tail             = tail;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
  }	
  public PlayerState(int playerNo, Ship s, Tail t) {
    this(playerNo, s, new CropEventGroup(), 
        0, 0, 0, 0xFF, 0xFF, 0xFF, Short.MAX_VALUE, 0xFF, 0xFF, t);
    
  }
  
  
  public boolean equals(PlayerState that) {
    if (this.playerNo != that.playerNo)                 return false;
    if (!this.ship.equals(that.ship))                   return false;
    if (!this.tail.equals(that.tail))                   return false;
    if (!this.cropEvents.equals(that.cropEvents))       return false;
    if (this.score != that.score)                       return false;
    if (this.untilReload != that.untilReload)           return false;
    if (this.untilRespawn != that.untilRespawn)         return false;
    if (this.sinceCrossing != that.sinceCrossing)       return false;
    if (this.sinceHitByBullet != that.sinceHitByBullet) return false;
    if (this.sinceLooping != that.sinceLooping)         return false;
    if (this.sincePwned != that.sincePwned)             return false;
    if (this.sinceScoring != that.sinceScoring)         return false;
    if (this.sinceCrashed != that.sinceCrashed)        return false;
    return true;
  }
  
  
  
  // Make a deep copy but set new ship
  public PlayerState clone(Ship newShip) {
    return new PlayerState(playerNo, newShip, 
        cropEvents,
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, sinceCrashed,
        tail);
  }
  public PlayerState cloneAndPwn() {
    int score        = 0;
    int sincePwned   = 0;

    return new PlayerState(playerNo, ship, 
        cropEvents,
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, sinceCrashed,
        tail);
  }
  public PlayerState clone() {
    return new PlayerState(playerNo, ship, 
        cropEvents, 
        score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, sinceCrashed,
        tail);
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

    s.append("\nuntilReload: ");
    s.append(untilReload);

    s.append("\nuntilRespawn: ");
    s.append(untilRespawn);

    s.append("\nsinceScoring: ");
    s.append(sinceScoring);

    s.append("\nsinceLooping: ");
    s.append(sinceLooping);

    s.append("\nsinceCrossing: ");
    s.append(sinceCrossing);

    s.append("\nsinceHitByBullet: ");
    s.append(sinceHitByBullet);

    s.append("\nsincePwned: ");
    s.append(sincePwned);
    
    s.append("\nsinceCrashed: ");
    s.append(sinceCrashed);
    
    s.append("\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) throws IOException {

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

    cropEvents.serialize(o);
          
    assert(score >= 0);
    assert(score <= 0xFF);
    if (CHECKBYTES) o.writeBytes("s");
    o.write(score);

    if (CHECKBYTES) o.writeBytes("l");
    o.write(untilReload);
    
    if (CHECKBYTES) o.writeBytes("r");
    o.writeShort(untilRespawn);
    
    if (CHECKBYTES) o.writeBytes("s");
    o.write(sinceScoring);
    
    if (CHECKBYTES) o.writeBytes("l");
    o.write(sinceLooping);
    
    if (CHECKBYTES) o.writeBytes("c");
    o.write(sinceCrossing);
    
    if (CHECKBYTES) o.writeBytes("b");
    o.writeShort(sinceHitByBullet);
    
    if (CHECKBYTES) o.writeBytes("p");
    o.write(sincePwned);
    
    if (CHECKBYTES) o.writeBytes("c");
    o.write(sinceCrashed);
  }

  // Construct the new PlayerState using the players state data received from the server
  // and the previous PlayerState (needed to construct things like Tail that are sent incrementally).
  public static PlayerState parse(DataInputStream i, PlayerState prevState, int time) throws IOException {

    Byte cmd;
    int playerNo, x, y, vx, vy, score,
        untilReload, 
        untilRespawn, 
        sinceScoring, 
        sinceLooping, 
        sinceCrossing,
        sinceHitByBullet, 
        sincePwned,
        sinceCrashed;
    Angle dir;
    CropEventGroup cropEvents;

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

    cropEvents = new CropEventGroup(i);
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
    score = i.read();

    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'l'); }
    untilReload = i.read();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'r'); }
    untilRespawn = i.readShort();
      
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
    sinceScoring = i.read();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'l'); }
    sinceLooping = i.read();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'c'); }
    sinceCrossing = i.read();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'b'); }
    sinceHitByBullet = i.readShort();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'p'); }
    sincePwned = i.read();

    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'c'); }
    sinceCrashed = i.read();
    
//    if (!cropEvents.isEmpty()) Logger.println(Rep.INFREQ, time + "  " + cropEvents);
    

    Point p = new Point(x, y);
    Ship ship = new Ship(p, vx, vy, dir);
    Tail newTail = Tail.parse(prevState, p, time, cropEvents, sinceScoring);
    
    
    return new PlayerState(playerNo, ship, 
        cropEvents, score, untilReload, untilRespawn,
        sinceScoring, sinceLooping, sinceCrossing,
        sinceHitByBullet, sincePwned, sinceCrashed,
        newTail);
  }
  

}
