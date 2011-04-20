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
    public final int targetSpeed;
    public final int acceleration;
    public final int targetDirection;
    public final int angularSpeed;

    // Number of shots/boosts to take ASAP.
    // Decremented by the simulator when actually taken.
    public final int shotsToTake;
    public final int fakeShotsToTake;
    public final int boostsToTake;

    public SConPlayerControlParameters(int targetSpeed, int acceleration, int targetDirection,
				       int angularSpeed, int shotsToTake, int fakeShotsToTake,
				       int boostsToTake) {
	this.targetSpeed = targetSpeed;
	this.acceleration = acceleration;
	this.targetDirection = targetDirection;
	this.angularSpeed = angularSpeed;
	this.shotsToTake = shotsToTake;
	this.fakeShotsToTake = fakeShotsToTake;
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
	
	s.append("target speed: ");
	s.append(targetSpeed);
	
	s.append("\nacceleration: ");
	s.append(acceleration);
	
	s.append("\ntarget direction: ");
	s.append(targetDirection);
	
	s.append("\nangular speed: ");
	s.append(angularSpeed);

	s.append("\n");

	return s.toString();
    }

    public void serialize(DataOutputStream o) {
	try {

	    // Signal start of a new command:
	    o.writeBytes("$");

	    o.writeBytes("v");
	    o.writeInt(targetSpeed);

	    o.writeBytes("a");
	    o.writeInt(acceleration);

	    o.writeBytes("d");
	    o.writeInt(targetDirection);

	    o.writeBytes("w");
	    o.writeInt(angularSpeed);

	    o.writeBytes("s");
	    o.writeInt(shotsToTake);

	    o.writeBytes("f");
	    o.writeInt(fakeShotsToTake);

	    o.writeBytes("b");
	    o.writeInt(boostsToTake);

	} catch (IOException ioe) {
	    assert(false);
	}

    }

    public static SConPlayerControlParameters parse(DataInputStream i) {

	Byte cmd;
	int v, a, d, w, s, f, b;

	try {
	    
	    cmd = i.readByte();
	    assert(cmd == '$');

	    cmd = i.readByte();
	    assert(cmd == 'v');
	    v = i.readInt();
		
	    cmd = i.readByte();
	    assert(cmd == 'a');
	    a = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'd');
	    d = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'w');
	    w = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 's');
	    s = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'f');
	    f = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'b');
	    b = i.readInt();
	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}


	return new SConPlayerControlParameters(v, a, d, w, s, f, b);

    }
}
