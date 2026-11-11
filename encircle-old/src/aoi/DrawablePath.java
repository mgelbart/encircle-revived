package aoi;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Vector;

import aoi.Logger.Rep;

/**
 * This is a general purpose class for drawing a path on
 * the screen.
 */
public class DrawablePath extends DefaultDrawable {

  private Path2D path;
  
  /**
   * Creates a new Drawable path.
   * 
   * @param playerNo the player this path is associated with, if any.
   * @param stroke the Stroke used to draw the object. If null, the default stroke is used.
   * @param color the Paint used to draw the object. If null, the default color for the player is used.
   * @param pts the path to be drawn as a sequence of points.
   */
  public DrawablePath(int playerNo, Stroke stroke, Paint color, Vector<Point> pts) {
    super(playerNo, stroke);
    setPath(pts);
    this.stroke = (stroke == null)? DrawUtils.DEFAULTSTROKE : stroke;
    this.paint = (color == null)? DefaultDrawable.getColor(playerNo) : color;
  }

  public void setPath(Vector<Point> pts) {
    if (pts == null || pts.size() < 2) {
      path = null;
      return;
    }

    Path2D newPath = new Path2D.Double();
    newPath.moveTo(pts.get(0).x, pts.get(0).y);
    for (Point p : pts.subList(1, pts.size()))
      newPath.lineTo(p.x, p.y);
    path = newPath;
  }
  
  public Shape[] shapes(GameState state) {
    if (path != null)
      return new Shape[] {path};
    else
      return new Shape[0];
  }
}
