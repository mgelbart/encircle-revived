package aoi;

import java.util.TreeSet;

@SuppressWarnings("serial")
public class KnotGroup extends TreeSet<Integer> {
    
  public KnotGroup() {
    super();
    add(0);
  }  

  public Integer firstNotBase() {
    return higher(0);
  }
 
  
}
