package aoi;

import java.io.*;

/**
 * A "read-only" class used to tell the simulator what the
 * player wants to do.
 *
 * Note that ints are used although floats might be
 * more natural. This is because ints don't need
 * any special serialization/parsing to be transmitted
 * over the network.
*/
public class PlayerControlParameters implements Comparable<PlayerControlParameters> {

  // The specified parameters will first become active in
  // the simulation step effectiveOn...effectiveOn+1
  // and remain active until another command becomes
  // active.
  public final int effectiveOn;

  public final int acceleration; // between 0 and SimulationUtils.MAXACC.
  public final Angle targetDirection;
  
  // Whether to shoot/boost if possible.
  public final boolean shoot;
  public final boolean boost;
  
  // Internally the client tells the server it wants to shoot/boost by changing these variables from their values
  // in the previous control parameters, i.e by incrementing them.
  // This is a packet loss tolerant way of transmitting these commands.
  private static int shootCtr = 0; // only the least significant byte is transmitted to the server.
  private static int boostCtr = 0; // only the least significant byte is transmitted to the server.

  // These values are used only on the server side to compare whether the counter values have changed.
  private int parsedShootCtr = -1;
  private int parsedBoostCtr = -1;
  
  // Whether to transmit marker bytes before each transmitted value.
  // Helps to make sure that the client isn's out of sync with the server (due to a programming error).
  private static final boolean CHECKBYTES = true;
  
  public PlayerControlParameters(int effectiveOn, int acceleration, Angle targetDirection,
                                 boolean shoot, boolean boost) {
    this.effectiveOn = effectiveOn;
    this.acceleration = acceleration;
    this.targetDirection = targetDirection;
    this.shoot = shoot;
    this.boost = boost;
  }

  /** The 'natural' order is time ordering.
   *
   * Note: Since time is periodic, the ordering is also periodic
   *       and transitivity _is_ violated if the GameStates in
   *       the set of interest differ by more than half the full
   *       time range.
   */
  public int compareTo(PlayerControlParameters that) {
    return this.effectiveOn - that.effectiveOn;
  }

  public boolean equals(PlayerControlParameters that) {
    return this.effectiveOn == that.effectiveOn;
  }

  public PlayerControlParameters shoot() {
    return new PlayerControlParameters(effectiveOn, acceleration, targetDirection, true, boost);
  }

  public PlayerControlParameters stop() {
    return new PlayerControlParameters(effectiveOn, 0, targetDirection, shoot, boost);
  }
  
  public String toString() {
    StringBuilder s = new StringBuilder();

    s.append("effective: ");
    s.append(effectiveOn);
	
    s.append("\nacceleration: ");
    s.append(acceleration);
	
    s.append("\ntarget direction: ");
    s.append(targetDirection);

    s.append("\n");

    return s.toString();
  }

  public void serialize(DataOutputStream o) throws IOException {

    // Signal start of a new command:
    if (CHECKBYTES) o.writeBytes("e");
    o.writeInt(effectiveOn);

    if (CHECKBYTES) o.writeBytes("a");
    o.writeInt(acceleration);

    if (CHECKBYTES) o.writeBytes("d");
    targetDirection.serialize(o);

    if (CHECKBYTES) o.writeBytes("s");
    if (shoot) ++shootCtr;
    o.write(shootCtr);

    if (CHECKBYTES) o.writeBytes("b");
    if (boost) ++boostCtr;
    o.write(boostCtr);

  }

  public static PlayerControlParameters parse(DataInputStream i, PlayerControlParameters prev) throws IOException {

    Byte cmd;
    int e, a, s, b;
    Angle d;


    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'e'); }
    e = i.readInt();
	
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'a'); }
    a = i.readInt();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'd'); }
    d = Angle.parse(i);
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
    s = i.read();
    
    if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'b'); }
    b = i.read();

    PlayerControlParameters params = new PlayerControlParameters(e, a, d,
                                                                  (s != prev.parsedShootCtr),
                                                                  (b != prev.parsedBoostCtr));
    params.parsedShootCtr = s;
    params.parsedBoostCtr = b;
    return params;

  }
}
