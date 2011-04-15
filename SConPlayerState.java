/* Helper class used to tell the simulator what the
   player wants to do.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.

*/

import java.io.*;

class SConPlayerState implements Cloneable {
    /* Values transmitted to the clients. */
    public int playerNo;
    public int x;
    public int y;
    public int direction;
    public int energy;

    public SConPlayerState clone() {
	try {
	    return (SConPlayerState) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("#");
	    o.writeInt(playerNo);

	    o.writeBytes("x");
	    o.writeInt(x);

	    o.writeBytes("y");
	    o.writeInt(y);

	    o.writeBytes("d");
	    o.writeInt(direction);

	} catch (IOException ioe) {
	    assert(false);
	}

    }

    public static SConPlayerState parse(DataInputStream i) {

	SConPlayerState state = new SConPlayerState();

	Byte cmd;
	int p;

	try {

	    cmd = i.readByte();
	    assert(cmd == '#');

	    p = i.readInt();
	    assert(p == 1 || p == 2);
	    state.playerNo = cmd;

	    cmd = i.readByte();
	    assert(cmd == 'x');
	    state.x = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'y');
	    state.y = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'd');
	    state.direction = i.readInt();

	    
	} catch (IOException ioe) {
	    assert(false);
	    return null;
	}

	return state;
    }
}


class SConPlayerStateInternal {
    /* Values used internally by the simulation. */
    public int vx;
    public int vy;
}

