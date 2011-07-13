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

  public final int acceleration;
  public final int targetDirection;

  // The client tells the server it wants to shoot/boost by changing these variables from their values
  // in the previous control parameters.
  // This is a packet loss tolerant way of transmitting these commands.
  public final int shoot; // only the least significant byte is transmitted to the server.
  public final int boost; // only the least significant byte is transmitted to the server.

  // Whether to transmit marker bytes before each transmitted value.
  // Helps to make sure that the client isn's out of sync with the server (due to a programming error).
  private static final boolean CHECKBYTES = true;
  
  public PlayerControlParameters(int effectiveOn, int acceleration, int targetDirection,
                                 int shoot, int boost) {
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

  public void serialize(OutputStream o) {

    DataOutputStream b = new DataOutputStream(o);

    try {

      // Signal start of a new command:
      if (CHECKBYTES) b.writeBytes("e");
      b.writeInt(effectiveOn);

      if (CHECKBYTES) b.writeBytes("a");
      b.writeInt(acceleration);

      if (CHECKBYTES) b.writeBytes("d");
      b.writeInt(targetDirection);

      if (CHECKBYTES) b.writeBytes("s");
      b.write(shoot);

      if (CHECKBYTES) b.writeBytes("b");
      b.write(boost);

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  public static PlayerControlParameters parse(DataInputStream i) {

    Byte cmd;
    int e, a, d, s, b;

    try {
	    
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'e'); }
      e = i.readInt();
		
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'a'); }
      a = i.readInt();
	    
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'd'); }
      d = i.readInt();
	    
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 's'); }
      s = i.read();
	    
      if (CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'b'); }
      b = i.read();
	    
    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
      return null;
    }


    return new PlayerControlParameters(e, a, d, s, b);

  }
}
