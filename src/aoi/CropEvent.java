package aoi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CropEvent {

  // The number of times to transmit each event
  public static final int TIMES_TO_TRANSMIT = 20;
  
  public final int cropFrom;
  public final int cropTo;
  private final int timeCreated;
  public String cause = null; 
  // (above) This must be set to null here so that it remains null on
  // the client side indefinitely. 

  // The client must never call this constructor (see toExecute())
  public CropEvent(int cropFrom, int cropTo, int timeCreated, String cause) {
    assert(cause != null);
    this.cropFrom = cropFrom;
    this.cropTo = cropTo;
    this.timeCreated = timeCreated;
    this.cause = cause;
  }
  
  // Parse for a single event, in a constructor
  // Client only
  public CropEvent(DataInputStream i) throws IOException {
    Byte cmd;
    
    if (PlayerState.CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'f'); }
    cropFrom = i.readInt();

    if (PlayerState.CHECKBYTES) { cmd = i.readByte(); assert(cmd == 't'); }
    cropTo = i.readInt();
    
    timeCreated = "clientOnlySoDoesntMatter".hashCode();
  }
  
  public void serialize(DataOutputStream o) throws IOException {
    if (PlayerState.CHECKBYTES) o.writeBytes("f");
    o.writeInt(cropFrom);

    if (PlayerState.CHECKBYTES) o.writeBytes("t");
    o.writeInt(cropTo);
  }
  
  public boolean contains(int i) {
    return (i - cropFrom >= 0 && i - cropTo < 0);
  }
  
  public boolean toRemove(int time) { 
    return time - timeCreated >= TIMES_TO_TRANSMIT;
  }
  
  // The server only wants to execute this event at the
  // time of creation, not TIMES_TO_TRANSMIT times.
  // We check if this CropEvent lives on the server or the client
  // by the cause string, which should be null only on the client
  // side (since the client only uses the constructor that takes 
  // a DataInputStream, and this constructor does not set
  // the cause String).
  public boolean toExecute(int time) {
    if (cause == null) return true;
    else return time == timeCreated;
  }
  
  public int timeCreated() {
    return timeCreated;
  }
  
  public String toString() {
    String s = "(From " + cropFrom + " to " + cropTo + ")";
    if (cause != null) s += " caused by " + cause;
    return s;
  }
  
  public boolean equals(Object o) {
    CropEvent that = (CropEvent) o;
    return this.cropFrom == that.cropFrom && this.cropTo == that.cropTo;
  }
  
  
}
