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
  public CropEventGroup(CropEventGroup that) {
    this.events = that.events;
  }

  // Remove events that have been transmitted enough times 
  public CropEventGroup removeExpired(int time) {
    Vector<CropEvent> newEvents = new Vector<CropEvent>();
    for (CropEvent ce : events)
      if (!ce.toRemove(time))
        newEvents.add(ce);
    return new CropEventGroup(newEvents);
  }
  
  // Group together CropEvents so that we are not
  // transmitting many reduntantly to the client
  // over the network. In the case where two CropEvents
  // partially overlap, we take the larger of the 
  // number of remaining times to transmit, or
  // in practice the later of the timeCreated.
  public CropEventGroup optimizeEvents(int time) {
    if (events.size() <= 1) return this;
    
    Vector<CropEvent> newEvents = new Vector<CropEvent>();
    
    // First, find the minimum cropFrom
    int minCropFrom = Integer.MAX_VALUE;
    for (CropEvent ce : events)
      minCropFrom = Math.min(minCropFrom, ce.cropFrom);
    // And the maximum cropTo
    int maxCropTo = Integer.MIN_VALUE;
    for (CropEvent ce : events)
      maxCropTo = Math.max(maxCropTo, ce.cropTo);

    
    int latestTimeCreated = 0;
    Integer currentCropFrom = null;
    // For every time between minCropFrom and maxCropTo
    for (int i = minCropFrom; i <= maxCropTo; ++i) {
      boolean contains = false;  // Do any of the events contain this time?
      for (CropEvent ce : events) {
        if (ce.contains(i)) {
          currentCropFrom = currentCropFrom==null?i:Math.min(currentCropFrom, i);
          latestTimeCreated = Math.max(latestTimeCreated, ce.timeCreated());
          contains = true;
        }
      }
      if (!contains && currentCropFrom != null) {
        // if no events contain i and we are in the middle of an event
        newEvents.add(new CropEvent(currentCropFrom, i, latestTimeCreated));
        currentCropFrom = null;
        latestTimeCreated = 0;
      }
    }      

    return new CropEventGroup(newEvents);
  }
  
  
  @SuppressWarnings("unchecked")
  public CropEventGroup addEvent(CropEvent ce) {
    Vector<CropEvent> newEvents = (Vector<CropEvent>) events.clone();
    newEvents.add(ce);
    return new CropEventGroup(newEvents);
  }
  public CropEventGroup addEvent(int cropFrom, int cropTo, int time) {
    return addEvent(new CropEvent(cropFrom, cropTo, time));
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
  
  public String toString() {
    String s = "";
    for (CropEvent ce : events)
      s += ce + " and ";
    return s;
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