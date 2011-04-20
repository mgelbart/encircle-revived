package aoi;

/* Helper class used to tell the simulator what the
   player wants to do.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.
*/

import java.io.*;

public class SConPlayerControlParameters implements Cloneable {
    public final int acceleration;
    public final int targetDirection;

    // Number of shots/boosts to take ASAP.
    // Decremented by the simulator when actually taken.
    public final int shotsToTake;
    public final int boostsToTake;

    public SConPlayerControlParameters(int acceleration, int targetDirection,
				       int shotsToTake, int boostsToTake) {
	this.acceleration = acceleration;
	this.targetDirection = targetDirection;
	this.shotsToTake = shotsToTake;
	this.boostsToTake = boostsToTake;
    }

    public SConPlayerControlParameters clone() {
	try {
	    return (SConPlayerControlParameters) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("\nacceleration: ");
	s.append(acceleration);
	
	s.append("\ntarget direction: ");
	s.append(targetDirection);

	s.append("\n");

	return s.toString();
    }

    public void serialize(DataOutputStream o) {
	try {

	    // Signal start of a new command:
	    o.writeBytes("$");

	    o.writeBytes("a");
	    o.writeInt(acceleration);

	    o.writeBytes("d");
	    o.writeInt(targetDirection);

	    o.writeBytes("s");
	    o.writeInt(shotsToTake);

	    o.writeBytes("b");
	    o.writeInt(boostsToTake);

	} catch (IOException ioe) {
	    assert(false);
	}

    }

    public static SConPlayerControlParameters parse(DataInputStream i) {

	Byte cmd;
	int a, d, s, b;

	try {
	    
	    cmd = i.readByte();
	    assert(cmd == '$');
		
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
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}


	return new SConPlayerControlParameters(a, d, s, b);

    }
}
