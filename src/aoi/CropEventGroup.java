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
    this.events = events;
  }
  @SuppressWarnings("unchecked")
  public CropEventGroup addEvent(int cropFrom, int cropTo) {
    Vector<CropEvent> newEvents = (Vector<CropEvent>) events.clone();
    newEvents.add(new CropEvent(cropFrom, cropTo));
    return new CropEventGroup(newEvents);
  }
  @SuppressWarnings("unchecked")
  public void serialize(DataOutputStream o) throws IOException {
    if (PlayerState.CHECKBYTES) o.writeBytes("n");
    o.write(events.size());
    
    Vector<CropEvent> eventsCopy = (Vector<CropEvent>)events.clone();
    for (int i = 0; i < events.size(); i++)
      if (eventsCopy.get(i).toRemove())
        events.remove(i);
    
    for (CropEvent ce : events)
      ce.serialize(o);
    
  }
  @SuppressWarnings("unchecked")
  public CropEventGroup clone() {
    return new CropEventGroup((Vector<CropEvent>)events.clone());
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