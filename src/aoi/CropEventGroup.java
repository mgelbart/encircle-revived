package aoi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

public class CropEventGroup {
  
  private Vector<CropEvent> events;

// ----------------------- start functions used by server --------------
  public CropEventGroup() {
    events = new Vector<CropEvent>();
  }
  
  private CropEventGroup(Vector<CropEvent> events) {
    this();
    // Remove expired events by copying the non-expired
    // events from the input Vector
    // Thus this constructor also effectively clones.
    for (CropEvent ce : events)
      if (!ce.toRemove())
        this.events.add(ce);
  }
  public CropEventGroup(CropEventGroup that) {
    this(that.events);
  }
  
  @SuppressWarnings("unchecked")
  public CropEventGroup addEvent(int cropFrom, int cropTo) {
    Vector<CropEvent> newEvents = (Vector<CropEvent>) events.clone();
    newEvents.add(new CropEvent(cropFrom, cropTo));
    return new CropEventGroup(newEvents);
  }
  
  public void serialize(DataOutputStream o) throws IOException {
    if (PlayerState.CHECKBYTES) o.writeBytes("n");
    o.write(events.size());
    
    for (CropEvent ce : events)
      ce.serialize(o);
  }
// ----------------------- end of functions used by server -------------
  public boolean equals(CropEventGroup that) {
    return this.events.equals(that.events);
  }
  public boolean isEmpty() {
    return events.isEmpty();
  }
  
  public boolean contains(int i) {
    for (CropEvent ce : events)
      if (ce.contains(i)) return true;
    return false;
  }
  
  public int nEvents() {
    return events.size();
  }
  
// ------------------------ start functions used by client -------------
  public CropEventGroup(DataInputStream i) throws IOException {
    Byte cmd;
    
    if (PlayerState.CHECKBYTES) { cmd = i.readByte(); assert(cmd == 'n'); }
    int numEvents = i.read();
    
    events = new Vector<CropEvent>(numEvents);
    for (int j = 0; j < numEvents; j++)
      events.add(new CropEvent(i));
  }
  @SuppressWarnings("unchecked")
  public Vector<CropEvent> getEvents() {
    return (Vector<CropEvent>)events.clone();
  }
//------------------------ end functions used by client ----------------  
  
  
  

}