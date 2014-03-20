package com.rcwang.seal.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;

public class Originator {

  /**
   * Stores distribution and sources of instances
   * @author rcwang
   *
   */
  public static class DistKeys {
    
    public static final double DEFAULT_WEIGHT = 1;
    public static final double DASHED_STR_WEIGHT = 0.5;  // 0.5
    public static final double IDENTICAL_STR_WEIGHT = 0.1; // 0.1
    public static final int MAX_DIST_SIZE = 10;

    private Set<Integer> keys;
    private Distribution dist;
    private SID canonical;
    
    public DistKeys(SID canonical) {
      dist = new Distribution();
      keys = new HashSet<Integer>();
      this.canonical = canonical;
    }
    
    public void add(SID original, Object[] keys) {
      if (original == null || keys == null) return;
      int key = 31 * Arrays.hashCode(keys) + original.hashCode();
      if (!this.keys.add(key)) return;
      dist.add(original, getWeight(original));
      dist.reduceSize(MAX_DIST_SIZE);
    }
    
    public String getCanonical() {
      return StringFactory.toName(canonical);
    }
    
    public Distribution getDist() {
      return dist;
    }
    
    private double getWeight(SID original) {
      if (original.equals(canonical))
        return IDENTICAL_STR_WEIGHT;
      if (original.toString().contains("-"))
        return DASHED_STR_WEIGHT;
      return DEFAULT_WEIGHT;
    }
  }
  
  public static Logger log = Logger.getLogger(Originator.class);
  private static Map<SID, DistKeys> map = new Cache<SID, DistKeys>();
  
  public static void add(String canonical, String original, Object key) {
    add(canonical, original, new Object[] {key});
  }
  
  public static void add(String canonical, String original, Object[] keys) {
    if (canonical == null || original == null) return;
    
    SID cID = StringFactory.toID(canonical);
    SID oID = StringFactory.toID(original);
    DistKeys d = map.get(cID);
    if (d == null) {
      d = new DistKeys(cID);
      map.put(cID, d);
    }
    d.add(oID, keys);
  }
  
  public static void clear() {
    map.clear();
  }
  
  public static String getOriginal(String canonical) {
    if (canonical == null) return null;
    DistKeys d = map.get(StringFactory.toID(canonical));
    if (d == null) return canonical;
    return d.getDist().getBestKey();
  }
  
  public static String getOriginals(String canonical) {
    return getOriginals(canonical, Integer.MAX_VALUE);
  }
  
  public static String getOriginals(String canonical, int numTop) {
    if (canonical == null) return null;
    DistKeys d = map.get(StringFactory.toID(canonical));
    if (d == null) return canonical;
    return d.getDist().toString(numTop, true);
  }
  
  public static void main(String[] args) {
    for (int i = 0; i < 1e6; i++) {
      String randStr = ExampleTextMaker.getRandomStringAndLength(5);
      Originator.add(randStr.toLowerCase(), randStr, null);
      System.out.print("Number of items inserted: " + map.size() + "\r");
    }
  }
  
  public static int getNumStrings() {
    int num = 0;
    for (DistKeys dk : map.values())
      num += dk.getDist().size();
    return num;
  }
  
  public static int size() {
    return map.size();
  }  
}
