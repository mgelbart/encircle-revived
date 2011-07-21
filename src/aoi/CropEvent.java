package aoi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CropEvent {
  public final int cropFrom;
  public final int cropTo;
  
  // The number of times to transmit each event
  private int timesToTransmit = 20;
  
  public CropEvent(int cropFrom, int cropTo) {
    this.cropFrom = cropFrom;
    this.cropTo = cropTo;
  }
  
  // Parse for a single event, in a constructor
  public CropEvent(DataInputStream i) throws IOException {
    Byte cmd;
    
    if (PlayerState.CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'f'); }
    cropFrom = i.readInt();

    if (PlayerState.CHECKBYTES) { cmd = i.readByte(); assert(cmd == 't'); }
    cropTo = i.readInt();
  }
  
  public void serialize(DataOutputStream o) throws IOException {
    if (PlayerState.CHECKBYTES) o.writeBytes("f");
    o.writeInt(cropFrom);

    if (PlayerState.CHECKBYTES) o.writeBytes("t");
    o.writeInt(cropTo);
    
    timesToTransmit--;
  }
  
  public boolean contains(int i) {
    return (i - cropFrom >= 0 && i - cropTo < 0);
  }
  
  public boolean toRemove() { 
    return timesToTransmit <= 0;
  }
  
  public CropEvent clone() {
    return new CropEvent(cropFrom, cropTo);
  }
  
  public boolean equals(Object o) {
    CropEvent that = (CropEvent) o;
    return this.cropFrom == that.cropFrom && this.cropTo == that.cropTo;
  }
  
  
}