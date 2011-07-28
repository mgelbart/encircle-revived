package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.TreeMap;
import java.util.TreeSet;

import aoi.Logger.Rep;

/** 
 * The tail. Immutable.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the addPoint() to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  // A map from times to Points. Each key-value pair
  // in this map corresponds to a "3D point" in the
  // trajectory, the 3rd dimension being time.
  private final TreeMap<Integer, Point> trajectory;
  
  private final TreeSet<Integer> knots; 
  
  // ---------------------- start constructors ----------------------- //
  public Tail(int time, Point p) {
    trajectory = new TreeMap<Integer, Point>();
    knots = new TreeSet<Integer>();
    
    trajectory.put(time, p);
    knots.add(time);  // knot at base
  }
  private Tail(TreeMap<Integer, Point> trajectory, TreeSet<Integer> knots) {
    this.trajectory = trajectory;
    this.knots = knots;

    if (!isEmpty())
      knots.add(baseTime());  // make sure there is a knot at the base
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
    if (!server.trajectory.equals(client.trajectory)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "trajectories (times) do not agree");
      toReturn = false;
    }
    
    return toReturn;  
  }
  
  
  // ------------------- start access functions --------------- //
  public int     nPoints() { return trajectory.size();    }
  public int     nKnots () { return knots.size();         }
  public boolean isEmpty() { return trajectory.isEmpty(); }
  
  public Point getPoint(int time) { 
    return trajectory.get(time); 
  }
  
  @SuppressWarnings("unchecked")
  public Iterable<Integer> getKnotTimes() { 
    return (Iterable<Integer>)knots.clone(); 
  }

  public Integer headTime() {
    if (isEmpty()) return null;
    return new Integer(trajectory.lastKey());
  }
  public Integer baseTime() {
    if (isEmpty()) return null;
    return new Integer(trajectory.firstKey());
  }
  public Integer beforeHeadTime() {
    if (nPoints() < 2) return null;
    return new Integer(trajectory.lowerKey(headTime()));
  }
  public Integer afterBaseTime() {
    if (isEmpty()) return null;
    return new Integer(trajectory.higherKey(baseTime()));
  }
  public Point headPoint() {
    if (isEmpty()) return null;
    return new Point(getPoint(headTime()));
  }
  public Point basePoint() {
    if (isEmpty()) return null;
    return new Point(getPoint(baseTime()));
  }
  public Point beforeHeadPoint() {
    if (nPoints() < 2) return null;
    return new Point(getPoint(beforeHeadTime()));
  }
  public Point afterBasePoint() {
    if (nPoints() < 2) return null;
    return new Point(getPoint(afterBaseTime())); 
  }
  public int lastKnotTime() {
    return knots.last();
  }
  
  public Path2D toPath2D() { 
    return toPath2D(trajectory.values());
  }
  
  // The Path2D returned here is not closed by default
  private static Path2D toPath2D(Iterable<Point> points) {
    Path2D path = new Path2D.Double();
    
    int i = 0;
    for (Point p : points) {
      if (i++ == 0) path.moveTo(p.x, p.y);
      else path.lineTo(p.x, p.y);
    }
    return path;  
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
    return knots.higher(knots.first())+1;
    // (above) crop everything including this knot (second from base)
  }

  public double length() {
    double length = 0;
    for (int time : trajectory.headMap(headTime()).keySet())
      length += getPoint(time).distance(getPoint(trajectory.higherKey(time))); 
    return length;
  }
  
  // the first time at which the path from 
  // "startTime" to that time has at least the specified length
  public Integer convertLengthToTime(int startTime, double minLength, boolean forwardDir) {
    assert(getPoint(startTime) != null);
    if (isEmpty())      return null;
    if (minLength <= 0) return startTime;
    if (nPoints() < 2)  return startTime;
    // (above) needed to avoid bug in which the loop below
    // will caller higher/lowerKey and hit null pointer when
    // tail has only 1 point
    
    double length = 0;
    int time = startTime;
    Integer nextTime = forwardDir?trajectory.higherKey(startTime):trajectory.lowerKey(startTime);
    while (nextTime != null) {
      length += getPoint(time).distance(getPoint(nextTime));
      if (length >= minLength)
        return new Integer(nextTime);
      time = nextTime;
      nextTime = forwardDir?trajectory.higherKey(time):trajectory.lowerKey(time);
    }
    // what to do if the entire path is not long enough
    return null; 
  }
   
//   Returns the length from the head to the first time
//   (counting from the head) that is less than or equal to
//   desiredTime
  public double convertTimeToLengthStartingAtHead(int desiredTime) {
    if (desiredTime >= headTime()) return 0.;
    if (desiredTime <= baseTime()) return length();
    
    double length = 0;
    int nextTime;
    for (int time = headTime(); time > desiredTime; time = nextTime) {
      nextTime = trajectory.lowerKey(time);
      length += getPoint(time).distance(getPoint(nextTime));
    }
    return length;
  }

  // Returns the angle between the previous point before the point at "time"
  // and the point at time
  // Special case, if time == basetime(), then it returns the angle from base point to the next one
  public Angle tangentAngle(int time) {
    if (nPoints() < 2) return null;
    if (time == baseTime()) return new Angle(basePoint(), afterBasePoint());
    if (time - baseTime() <= 0 || time - headTime() > 0) return null;
    return new Angle(getPoint(trajectory.lowerKey(time)), getPoint(time));
  }
  public Angle tangentAngleHead() {
    return tangentAngle(headTime());
  }  

  // ------------- end access functions --------------- //
  
  
  // Adds a point to the tail directly, modifying the calling tail.
  // Do not make public.
  private void mutatingAdd(int time, Point p) {  
    
    // Always add the latest position to the trajectory
    // but combine it with the previous segment if both are short.
    // we need the second part after "||" for a special case when there
    // is exactly one point in the tail and you try to add
    // a point at the same location
    if ((beforeHeadPoint() != null && p.distance(beforeHeadPoint()) < MINTRAJECTORYSEGMENT)
           || (headPoint() != null && p.equals(headPoint()))) {    
      if (knots.contains(headTime())) {
        knots.remove(headTime());
        knots.add(time);
      }
      trajectory.remove(headTime()); 
    }
    trajectory.put(time, p);
  }
  public Tail addPoint(int time, Point p) {
    Tail newTail = clone();
    newTail.mutatingAdd(time, p);
    return newTail;
  }

  // clone are crop the tail using all the CropEvents in
  // the CropEventGroup.
  private Tail crop(CropEventGroup ceg) {
    if (ceg.isEmpty()) return clone();
    
    TreeMap<Integer, Point> newTrajectory = new TreeMap<Integer, Point>();
    TreeSet<Integer> newKnots = new TreeSet<Integer>();
    
    for (int time : trajectory.keySet()) {
      if (!ceg.contains(time)) {
        newTrajectory.put(time, trajectory.get(time));
        if (knots.contains(time)) newKnots.add(time);
      }
    }
    return new Tail(newTrajectory, newKnots);
  }
  private Tail crop(CropEvent ce) {
    return crop((new CropEventGroup()).addEvent(ce));
  }
  public Tail crop(int cropFrom, int cropTo) {
    return crop(new CropEvent(cropFrom, cropTo,
        "DoesntMatter".hashCode()));
  }
  // (above) this is a bit slow, but only used by ScoringEffect
  
  
  // Add p, then crop with ceg. Do this all at once
  // To avoid cloning twice per turn in PlayerState parsing
  // This works even if prev.tail is empty
  public static Tail parse(PlayerState prev, Point p, int time, 
      CropEventGroup cropEvents, int sinceScoring) {
    
    if (prev == null)
      return new Tail(time, p);

    // Execute all the pending crop events
    Tail newTail = prev.tail.crop(cropEvents);
    
    // If you were not going to crop the current point, then add it
    if (!cropEvents.contains(time)) {
      
      // Note: we use this instead of checking if the score is 
      // incremented because of a bug with scoring and getting
      // pwned on the same turn
      if ((sinceScoring - prev.sinceScoring < 0 ||
          sinceScoring == 0)) {
        assert(!newTail.isEmpty());
        newTail.knots.add(newTail.headTime());
      }
      
      // Add the latest point
      newTail.mutatingAdd(time, p);
    }
    
    return newTail;
  }
  
  


  /**
   * Does this Tail intersect with *the last line of* another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   *                      
   * When "this" != "that" and there is an intersection, the content
   * of the returned Tail is undefined but it will not be null.                    
   */
  public Tail intersects(Tail that) {
    // The excludePoints is necessary to avoid spurious two segment loops
    // for example when "this" == "that" and the player is not moving                       
    int excludePoints = this == that?2:0;
    
    if (this.nPoints() < 2+excludePoints) return null;
    if (that.nPoints() < 2) return null; 

    
    int lastTime;
    for (lastTime = headTime(); excludePoints-- > 0; lastTime = trajectory.lowerKey(lastTime));
    
    // Check if trajectory intersects the last line of that.trajectory
    // Excluding the last excludePoints of trajectory
    Point that1 = that.beforeHeadPoint();
    Point that2 = that.headPoint();
    Integer intersectionTime = null;
    for (int time : trajectory.headMap(lastTime).keySet()) {  // excludes lastTime
      Point this1 = trajectory.get(time);
      Point this2 = trajectory.get(trajectory.higherKey(time));
      if (Line2D.linesIntersect(this1.x, this1.y, this2.x, this2.y, that1.x, that1.y, that2.x, that2.y)) {
        intersectionTime = time;
        break;
      }
    }
    if (intersectionTime == null) return null; // no intersection

//    Logger.println(Rep.INFREQ, this);
//    Logger.println(Rep.INFREQ, that);
//    Logger.println(Rep.INFREQ, lastTime);
//    Logger.println(Rep.INFREQ, "intersectiontime = " + intersectionTime);

    
    // Include the point right before crossing
    // in the loop, as well as the point right after crossing
    return crop(baseTime(), intersectionTime);
  }

  

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail((TreeMap<Integer,Point>)trajectory.clone(), 
        (TreeSet<Integer>) knots.clone());
  }
  
  public String toString() { 
    return trajectory.toString() + "\n" +
           knots.toString();
    }

  
}
