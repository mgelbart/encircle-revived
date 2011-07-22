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
  
  public CropEvent(int cropFrom, int cropTo, int timeCreated) {
    this.cropFrom = cropFrom;
    this.cropTo = cropTo;
    this.timeCreated = timeCreated;
  }
  
  // Parse for a single event, in a constructor
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
  
  public int timeCreated() {
    return timeCreated;
  }
  
  public String toString() {
    return "(From " + cropFrom + " to " + cropTo + ")";
  }
  
  public boolean equals(Object o) {
    CropEvent that = (CropEvent) o;
    return this.cropFrom == that.cropFrom && this.cropTo == that.cropTo;
  }
  
  
}