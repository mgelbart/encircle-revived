package aoi;

import java.awt.Point;
import java.awt.Shape;
import java.util.TreeSet;
import java.util.Vector;

/** 
 * The tail. Immutable.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the addPoint() to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  // The trajectory in the two space coordinates
  private final NSPath trajectory;
  
  // The time coordinate of each point
  private final Vector<Integer> times;
  
  private final KnotGroup knots; 
  
  // ---------------------- start constructors ----------------------- //
  public Tail(Point p, int time) {
    trajectory = new NSPath();
    times = new Vector<Integer>();
    knots = new KnotGroup();
    
    trajectory.addPoint(p);
    times.add(time);
    
    assert(trajectory.nPoints() == times.size());
  }
  private Tail(NSPath trajectory, Vector<Integer> times, KnotGroup knots) {
    this.trajectory = trajectory;
    this.times = times;
    this.knots = knots;
    
    assert(trajectory.nPoints() == times.size());
  }
  // ---------------------- end constructors ----------------------- //  

  public boolean equals(Tail client) {
    boolean toReturn = true;
    Tail server = this;
    if (!server.knots.equals(client.knots)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "KnotGroup does not agree");
      toReturn = false;
    }
    if (!server.times.equals(client.times)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "times do not agree");
      toReturn = false;
    }
    if (!server.trajectory.equals(client.trajectory)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "NSPaths do not agree");
      toReturn = false;
    }
    
    return toReturn;  
  }
  
  
  // ------------------- start access functions --------------- //
  public int nPoints() {
    assert(trajectory.nPoints() == times.size());
    return Math.random()<0.5?trajectory.nPoints():times.size();
  }
  
  public int     nKnots  ()      { return knots.size();           }
  public double  length  ()      { return trajectory.length();    }
  public Shape  pathShape()      { return trajectory.clone();     }
  public boolean isEmpty ()      { return nPoints() == 0;         }
  public Point getPoint(int i)   { return trajectory.getPoint(i); }

  public boolean contains(double x, double y) {
    return trajectory.contains(x, y);
  }
  
  @SuppressWarnings("unchecked")
  public Iterable<Integer> getKnots() { 
    return (Iterable<Integer>)knots.clone(); 
  }
  
  public Point getPointFromTime(int time) {
    for (int i = 0; i < nPoints(); i++)
      if (times.get(i) == time)
        return getPoint(i);
    return null;
  }

  public Integer headTime() {
    if (isEmpty()) return null;
    else return times.lastElement();
  }
  public Integer baseTime() {
    if (isEmpty()) return null;
    else return times.firstElement();
  }

  // the time of the first (closest to base)
  // knot NOT INCLUDING THE KNOT AT BASE
  // Returns the head time if the knot
  // at the base is the only knot
  public int cropToForCrossing() {
    assert(!isEmpty());
    assert(nKnots() > 0);
    if (nKnots() == 1) return headTime(); 
    // (above) only knot at bases crop everything except the head    
    return times.get(knots.firstNotBase())+1;
    // (above) crop everything including this knot
  }

  // the first (absolute) time at which the path from 
  // index to that time has at least the specified length
  private int lengthFromIndexToTime(int index, double length) {
    assert(!isEmpty());
    NSPath subpath = new NSPath();

    for (int i = index; i < nPoints(); i++) {
      subpath.addPoint(getPoint(i)); 
      if (subpath.length() >= length)
        return times.get(i);
    }
    // what to do if the entire path is not long enough
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return headTime() + 1; 
  }
  public int lengthFromBaseToTime(double length) {
    return lengthFromIndexToTime(0, length);
  }
  public int lengthFromLastKnotToTime(double length) {
    return lengthFromIndexToTime(knots.last(), length);  
  }
  
  public double lengthFromLastKnotToHead() {
    assert(nKnots() > 0);
    double length = 0;
    for (int i = knots.last(); i < nPoints()-1; i++)
      length += getPoint(i).distance(getPoint(i+1));
    return length;
  }
  
  // Finds the angle of the line from i-1 to i
  public Angle tangentAngle(int i) {
    if (i <= 0 || i >= nPoints() || nPoints() < 2) return null;
    return new Angle(getPoint(i-1), getPoint(i)); 
  }
  public Angle tangentAngleHead() {
    return tangentAngle(nPoints()-1);
  }  

  // ------------- end access functions --------------- //
  
  
  // Adds a point to the tail directly, modifying the calling tail.
  // Do not make public.
  private void mutatingAdd(Point p, int time) {  
    
    Point prevPoint     = trajectory.lastPoint();
    Point prevPrevPoint = trajectory.lastlastPoint();
    
    if (prevPoint == null || !prevPoint.equals(p)) {
      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (prevPrevPoint != null &&
            p.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {        
        trajectory.removeLast();
        times.remove(times.size()-1);
      }
      trajectory.addPoint(p);
      times.add(time);
    }
    assert(trajectory.nPoints() == times.size());
  }
  public Tail addPoint(Point p, int time) {
    Tail newTail = clone();
    newTail.mutatingAdd(p, time);
    return newTail;
  }

  // clone are crop the tail using all the CropEvents in
  // the CropEventGroup.
  private Tail crop(CropEventGroup ceg) {
    if (ceg.isEmpty()) return clone();
    
    NSPath newPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();
    KnotGroup newKnots = new KnotGroup();
    
    for (int i = 0; i < nPoints(); i++) {
      if (!ceg.contains(times.get(i))) {
        newPath.addPoint(getPoint(i));
        newTimes.add(times.get(i));
        if (knots.contains(i)) newKnots.add(newTimes.size()-1);
      }
    }
    return new Tail(newPath, newTimes, newKnots);
  }
  public Tail crop(CropEvent ce) {
    return crop((new CropEventGroup()).addEvent(ce));
  }
  // (above) this is a bit slow, but only used by ScoringEffect
  
  
  // Add p, then crop with ceg. Do this all at once
  // To avoid cloning twice per turn in PlayerState parsing
  // This works even if prev.tail is empty
  public static Tail parse(PlayerState prev, Point p, int time, 
      CropEventGroup cropEvents, int score) {
    
    if (prev == null)
      return new Tail(p, time);

    // Execute all the pending crop events
    Tail newTail = prev.tail.crop(cropEvents);
    
    // If you were not going to crop the current point, then add it
    if (!cropEvents.contains(time)) {
      
      if (score > prev.score) {
        assert(!newTail.isEmpty());
        newTail.knots.add(newTail.nPoints()-1);
      }
      
      // Add the latest point
      newTail.mutatingAdd(p, time);
    }
    
    return newTail;
  }
  
  


  /**
   * Does this Tail intersect with *the last line of* another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   * 
   * @param excludePoints Number of points to exclude from the end of 'this'.
   *                      Useful is you want to check for loops (this==that).
   *                      
   *  When "this" != "that" and there is an intersection, the content
   *  of the returned Tail is undefined but it will not be null. 
   *                       
   *  The excludePoints is necessary to avoid spurious two segment loops
   *  for example when "this" == "that" and the player is not moving                       
   */
  public Tail intersects(Tail that, int excludePoints) {
    if (that.trajectory.lastLine() == null) return null; 

    int i = trajectory.subpath(0, nPoints()-excludePoints).intersects(that.trajectory.lastLine());
    if (i < 0) return null; // no intersection

    NSPath loop = trajectory.subpath(i, nPoints());
    Vector<Integer> loopTimes = new Vector<Integer>();
    KnotGroup loopKnots = new KnotGroup();
    
    // (below) change to "for (i++; .....)"
    // if you don't want to point right before the crossing
    // to be included at the beginning of the loop
    // also, the point right after the crossing is currently
    // included in the loop
    for (; i < nPoints(); i++) {
      loopTimes.add(times.get(i));
      if (knots.contains(i)) loopKnots.add(loopTimes.size()-1);
    }
  
    return new Tail(loop, loopTimes, loopKnots);
  }


  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail(trajectory.clone(), 
        (Vector<Integer>)times.clone(),
        (KnotGroup) knots.clone());
  }
  
  public String toString() { 
    return trajectory.toString() + "\n" +
           times.toString() + "\n" +
           knots.toString();
    }

  
  @SuppressWarnings("serial")
  public static class KnotGroup extends TreeSet<Integer> {
      
    public KnotGroup() {
      super();
      add(0);
    }  

    public Integer firstNotBase() {
      return higher(0);
    }
  }
  
}
