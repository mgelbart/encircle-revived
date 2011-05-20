package aoi;

/* A "read-only" class used to tell the simulator what the
   player wants to do.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.
*/

import java.io.*;

public class PlayerControlParameters implements Cloneable {
    public final int acceleration;
    public final int targetDirection;

    // Number of shots/boosts to take ASAP.
    // Decremented by the simulator when actually taken.
    public final int shotsToTake;
    public final int boostsToTake;

    public PlayerControlParameters(int acceleration, int targetDirection,
				       int shotsToTake, int boostsToTake) {
	this.acceleration = acceleration;
	this.targetDirection = targetDirection;
	this.shotsToTake = shotsToTake;
	this.boostsToTake = boostsToTake;
    }

    public PlayerControlParameters clone() {
	try {
	    return (PlayerControlParameters) super.clone();
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

    public void serialize(OutputStream o) {

	BufferedOutputStream bufferedStream = new BufferedOutputStream(o);
	DataOutputStream b = new DataOutputStream(bufferedStream);

	try {

	    // Signal start of a new command:
	    b.writeBytes("$");

	    b.writeBytes("a");
	    b.writeInt(acceleration);

	    b.writeBytes("d");
	    b.writeInt(targetDirection);

	    b.writeBytes("s");
	    b.writeInt(shotsToTake);

	    b.writeBytes("b");
	    b.writeInt(boostsToTake);

	    bufferedStream.flush();

	} catch (IOException ioe) {
	    System.err.println(ioe.toString());
	    assert(false);
	}

    }

    public static PlayerControlParameters parse(DataInputStream i) {

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


	return new PlayerControlParameters(a, d, s, b);

    }
}
