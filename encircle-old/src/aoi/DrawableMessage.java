package aoi;

import java.awt.*;
import java.awt.geom.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

/**
 *  A GUI element for showing a message to the player.
 */
public class DrawableMessage extends DefaultDrawable {

  protected long msgSetAt; // time
  protected long duration = 5000; // in ms
  private boolean removeAfterwards;
  private Shape[] msg;

  public DrawableMessage() {
    super(ClientConfiguration.playerNo, DrawUtils.DEFAULTSTROKE);
    this.fill = true;
    this.paint = Color.CYAN;
  }
  
  public void setMsg(String message, int duration, double fontHeight, boolean removeAfterwards) {
    if (message == null) { msg = new Shape[0]; return; }
    this.duration = duration;
    this.removeAfterwards = removeAfterwards;

    msgSetAt = System.currentTimeMillis();
    
    // This is a bit hackz but we need to create an
    // actual graphics object in order to get the FontMetrics object.
    Graphics2D graphics = (new BufferedImage(1, 1,
                                             BufferedImage.TYPE_INT_ARGB))
      .createGraphics();
    Font f = new Font("Monospaced", Font.PLAIN, 10);
    GlyphVector glyphs = f.createGlyphVector(graphics.getFontMetrics(f).getFontRenderContext(), message);

    final double height = glyphs.getOutline().getBounds2D().getHeight();
    final double width  = glyphs.getOutline().getBounds2D().getWidth();
    final double scaleFact = fontHeight / height;  // make the letters 30 px high
    
    // Precalculate everything except the translation for the coordinate transformation.
    // The amount of translation depends on the canvas size which may change.
    AffineTransform at = new AffineTransform();
    at.scale(scaleFact, scaleFact);
    at.translate(-width/2, -height/2); // Center
    at.scale(1, -1); // Java Graphics use a left-handed coordinate system.

    msg = new Shape[] { at.createTransformedShape(glyphs.getOutline()) };
  }
  
  public Shape[] shapes(GameState game) {
    return msg;
  }
  
  public void postpare(GameState gState) {
    if (removeAfterwards && System.currentTimeMillis() - msgSetAt > duration)
      remove();
  }
  
  public boolean ingameCoordinates() { return false; }
}
