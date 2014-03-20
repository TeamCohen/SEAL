package com.rcwang.seal.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An access-order hash map
 * @author rcwang
 *
 */
public class Cache<K, V> extends LinkedHashMap<K, V> {
  
  public static final int DEFAULT_MAX_SIZE = (int) 1e4; // 100,000
  public static final int INITIAL_CAPACITY = (int) 1e4; // 1,000
  public static final float LOAD_FACTOR = (float) 0.75;
  public static final boolean ACCESS_ORDER = true;
  
  private static final long serialVersionUID = -6168239695015010506L;

  private double maxSize;

  public Cache() {
    super (INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER);
    setMaxSize(DEFAULT_MAX_SIZE);
  }
  
  public double getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(double maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * Removes the eldest entry when size exceeds maxSize
   */
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
     return size() > maxSize;
  }
}