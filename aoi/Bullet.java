package aoi;

/* A "read-only" class used to represent a bullet in
   the game.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.

*/

import java.io.*;

public class Bullet implements Cloneable {

    /* The bullet speed is a universal constant. */
    public static final int SPEED = 1000;
 
    /* Minimum time between firing two bullets. */
    public static final int RELOADTIME = 10;
 
    /* The cross-sectional radius. */
    public static final int RADIUS = 1;

    /* Values transmitted to the clients. */
    public final int x;
    public final int y;
    public final int direction;

    /* Player who shot the bullet. Used internally
       by the simulator to make sure the bullet doesn't
       kill the player as it gets fired! */
    public final int playerNo;

    public Bullet(int playerNo, int x, int y, int direction) {
	this.x = x;
	this.y = y;
	this.direction = direction;
	this.playerNo = playerNo;
    }

    public Bullet clone() {
	try {
	    return (Bullet) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public String toString() {
	StringBuilder s = new StringBuilder();

	s.append("Bullet");

	s.append("\nx: ");
	s.append(x);

	s.append("\ny: ");
	s.append(y);

	s.append("\ndirection: ");
	s.append(direction);

	s.append("\n");

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("x");
	    o.writeInt(x);

	    o.writeBytes("y");
	    o.writeInt(y);

	    o.writeBytes("d");
	    o.writeInt(direction);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static Bullet parse(DataInputStream i) {

	Byte cmd;
	int x, y, d;

	try {

	    cmd = i.readByte();
	    assert(cmd == 'x');
	    x = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'y');
	    y = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'd');
	    d = i.readInt();
	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new Bullet(-1, x, y, d);
    }
}

