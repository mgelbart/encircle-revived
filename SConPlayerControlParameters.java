/* Helper class used to tell the simulator what the
   player wants to do.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.
*/

import java.io.*;

class SConPlayerControlParameters implements Cloneable {
    public int targetSpeed;
    public int acceleration;
    public int targetDirection;
    public int angularSpeed;

    // Number of shots/boosts to take ASAP.
    // Decremented by the simulator when actually taken.
    public int shotsToTake;
    public int fakeShotsToTake;
    public int boostsToTake;


    public SConPlayerControlParameters clone() {
	try {
	    return (SConPlayerControlParameters) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
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

	SConPlayerControlParameters ctrl = new SConPlayerControlParameters();

	Byte cmd;

	try {
	    assert(i.readByte() == '$');

	    cmd = i.readByte();
	    assert(cmd == 'v');
	    ctrl.targetSpeed = i.readInt();
		
	    cmd = i.readByte();
	    assert(cmd == 'a');
	    ctrl.acceleration = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'd');
	    ctrl.targetDirection = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'w');
	    ctrl.angularSpeed = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 's');
	    ctrl.shotsToTake = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'f');
	    ctrl.fakeShotsToTake = i.readInt();
	    
	    cmd = i.readByte();
	    assert(cmd == 'b');
	    ctrl.boostsToTake = i.readInt();
	    
	} catch (IOException ioe) {
	    assert(false);
	    return null;
	}

	return ctrl;

    }
}
