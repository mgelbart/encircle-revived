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

  // Number of shots/boosts to take ASAP.
  // Decremented by the simulator when actually taken.
  public final int shotsToTake;
  public final int boostsToTake;

  public PlayerControlParameters(int effectiveOn, int acceleration, int targetDirection,
                                 int shotsToTake, int boostsToTake) {
    this.effectiveOn = effectiveOn;
    this.acceleration = acceleration;
    this.targetDirection = targetDirection;
    this.shotsToTake = shotsToTake;
    this.boostsToTake = boostsToTake;
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
      b.writeBytes("$");

      b.writeBytes("e");
      b.writeInt(effectiveOn);

      b.writeBytes("a");
      b.writeInt(acceleration);

      b.writeBytes("d");
      b.writeInt(targetDirection);

      b.writeBytes("s");
      b.writeInt(shotsToTake);

      b.writeBytes("b");
      b.writeInt(boostsToTake);

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  public static PlayerControlParameters parse(DataInputStream i) {

    Byte cmd;
    int e, a, d, s, b;

    try {
	    
      cmd = i.readByte();
      assert(cmd == '$');
		
      cmd = i.readByte();
      assert(cmd == 'e');
      e = i.readInt();
		
      cmd = i.readByte();
      assert(cmd == 'a');
      a = i.readInt();
	    
      cmd = i.readByte();
      assert(cmd == 'd');
      d = i.readInt();
	    
      cmd = i.readByte();
      assert(cmd == 's');
      s = i.readInt();
	    
      cmd = i.readByte();
      assert(cmd == 'b');
      b = i.readInt();
	    
    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
      return null;
    }


    return new PlayerControlParameters(e, a, d, s, b);

  }
}
