/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;

public class Distribution implements Iterable<Entry<SID, Cell>> {
  
  public static Logger log = Logger.getLogger(Distribution.class);
  
  private SparseVector sv;
  private SID bestSID;
  private double maxWeight;
  private double totalWeight;
  private boolean hasNegativeValue = false;
  
  public Distribution() {
    totalWeight = 0;
    sv = new SparseVector();
  }
  
  public Distribution(Distribution d) {
    sv = new SparseVector(d.sv);
    bestSID = d.bestSID;
    maxWeight = d.maxWeight;
    totalWeight = d.totalWeight;
    hasNegativeValue = d.hasNegativeValue;
  }
  
  /**
   * Add a constant value to all items in this distribution
   * @param value
   */
  public void add(double value) {
    for (Entry<SID, Cell> entry : sv.entrySet())
      add(entry.getKey(), value);
  }
  
  public void add(SID id, double value) {
    check(id, value);
    Cell c = sv.get(id);
    if (c == null) {
      sv.put(id, value);
      c = sv.get(id);
    } else {
      c.value += value;
    }
    if (c.value > maxWeight) {
      bestSID = id;
      maxWeight = c.value;
    }
    totalWeight += value;
  }
  
  /**
   * Reduce the size of this distribution by the following procedure:
   * 1) make this distribution into a list and sort it in descending order
   * 2) remove entities ranked lower than or equal to r
   * 3) reduce weight of entities above r by the weight of entity at r
   * where r is the half of maxSize
   * @param maxSize
   */
  public void reduceSize(int maxSize) {
    if (size() <= maxSize) return;
    int midSize = (int) (maxSize * 0.5);
    List<Entry<SID, Cell>> list = getSortedEntries();
    double midWeight = list.get(midSize).getValue().value;
    
    for (int i = 0; i < list.size(); i++) {
      Entry<SID, Cell> entry = list.get(i);
      if (i >= midSize) {
        sv.remove(entry.getKey());
        totalWeight -= entry.getValue().value;
      } else {
        entry.getValue().value -= midWeight;
        totalWeight -= midWeight;
      }
    }
  }
  
  public void add(String item, double value) {
    add(StringFactory.toID(item), value);
  }
  
  public void addAll(Distribution d) {
    addAll(d.sv);
  }
  
  public void addAll(SparseVector sv) {
    for (Entry<SID, Cell> entry : sv.entrySet())
      add(entry.getKey(), entry.getValue().value);
  }
  
  public void clear() {
    totalWeight = 0;
    sv.clear();
  }
  
  public Set<SID> getKeys() {
    return sv.keySet();
  }
  
  public Double getProbability(SID id) {
    if (hasNegativeValue)
      log.warn("Warn: At least one negative value has been added, the probability returned will be incorrect...");
    if (totalWeight == 0)
      return (double) 0;
    Double w = getWeight(id);
    return (w == null) ? null : (w/totalWeight);
  }
  
  public Double getProbability(String name) {
    return getProbability(StringFactory.toID(name));
  }
  
  public double getTotalWeight() {
    return totalWeight;
  }
  
  // why not keep a maxWeight variable?
  /*public Entry<SID, Cell> getMaxEntry() {
    Entry<SID, Cell> maxEntry = null;
    double maxWeight = Double.NEGATIVE_INFINITY;

    for (Entry<SID, Cell> entry : sv) {
      double weight = entry.getValue().value;
      if (weight > maxWeight) {
        maxEntry = entry;
        maxWeight = weight;
      }
    }
    return maxEntry;
  }*/
  
  public String getBestKey() {
    return bestSID.toString();
  }
  
  public double getMaxWeight() {
    return maxWeight;
  }
  
  public Double getWeight(SID id) {
    Cell c = sv.get(id);
    return (c == null) ? null : c.value;
  }
  
  public Double getWeight(String name) {
    return getWeight(StringFactory.toID(name));
  }
  
  /**
   * Get sorted entries in descending order
   * @return
   */
  public List<Entry<SID, Cell>> getSortedEntries() {
    List<Entry<SID, Cell>> entryList = new ArrayList<Entry<SID, Cell>>(sv.entrySet());
    Collections.sort(entryList, new Comparator<Entry<SID, Cell>>() {
      public int compare(Entry<SID, Cell> e1, Entry<SID, Cell> e2) {
        return Double.compare(e2.getValue().value, e1.getValue().value);
      }
    });
    return entryList;
  }
  
  /*public List<SID> getSortedKeys() {
    List<Entry<SID, Cell>> entryList = getSortedEntries();
    List<SID> sortedKeys = new ArrayList<SID>();
    for (Entry<SID, Cell> entry : entryList)
      sortedKeys.add(entry.getKey());
    return sortedKeys;
  }*/
  
  public boolean isEmpty() {
    return sv.isEmpty();
  }
  
  public Iterator<Entry<SID, Cell>> iterator() {
    return sv.entrySet().iterator();
  }
  
  /*public void multiply(SID id, double value) {
    check(id, value);
    Cell c = sv.get(id);
    if (c == null) {
      sv.put(id, value);
      totalWeight += value;
    } else {
      totalWeight -= c.value;
      c.value *= value;
      totalWeight += c.value;
    }
  }*/

  public String toString() {
    return toString(true);
  }
  
  public String toString(boolean showValue) {
    return toString(Integer.MAX_VALUE, showValue);
  }
  
  public String toString(int numTop, boolean showValue) {
    List<Entry<SID, Cell>> sortedEntries = getSortedEntries();
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < Math.min(numTop, sortedEntries.size()); i++) {
      Entry<SID, Cell> entry = sortedEntries.get(i);
      if (buf.length() > 0) buf.append(", ");
      buf.append(entry.getKey());
      if (showValue) {
        String valueStr = Helper.formatNumber(entry.getValue().value, 2);
        buf.append(":").append(valueStr);
      }
    }
    return buf.toString();
  }
  
  public void normalize() {
    sv.normalize();
    totalWeight = sv.getSum();
  }
  
  public int size() {
    return sv.size();
  }
  
  private boolean check(SID id, double value) {
    if (value < 0 && !hasNegativeValue) {
//      log.warn("Warn: Item \"" + id + "\" was added/mutiplied a value <= zero (" + value + ")");
      log.warn("Warn: A weight of negative value has been added to the distribution!");
      hasNegativeValue = true;
      return false;
    }
    return true;
  }
}
