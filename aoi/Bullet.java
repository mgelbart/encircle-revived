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
    public static final int SPEED = Simulation.INTMAX / 4;

    /* Values transmitted to the clients. */
    public final int x;
    public final int y;
    public final int direction;

    public Bullet(int x, int y, int direction) {
	this.x = x;
	this.y = y;
	this.direction = direction;
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

	return new Bullet(x, y, d);
    }
}

