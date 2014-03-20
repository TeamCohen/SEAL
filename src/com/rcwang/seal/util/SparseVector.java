/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;

//import edu.cmu.minorthird.classify.Feature; //wwc
//import edu.cmu.minorthird.classify.Instance; //wwc

public class SparseVector extends HashMap<SID, Cell> implements Iterable<Entry<SID, Cell>> {
  public static Logger log = Logger.getLogger(SparseVector.class);
  
  private static final long serialVersionUID = -7130650729068881981L;
  private static final StringBuffer buf = new StringBuffer();
  
  public SID id;

  /* wwc
  @SuppressWarnings("unchecked")
  public static SparseVector toSparseVector(Instance instance) {
    SparseVector sv = new SparseVector();
    for (Iterator<Feature> i = instance.featureIterator(); i.hasNext();) {
      Feature feature = i.next();
      SID featureID = StringFactory.toID(feature.toString());
      double weight = instance.getWeight(feature);
      sv.put(featureID, weight);
    }
    return sv;
  }
  */
  
  public SparseVector() {
    id = null;
  }
  
  public SparseVector(SID id) {
    super();
    this.id = id;
  }
  
  public SparseVector(SparseVector sv) {
    this(sv.id);
    for (Entry<SID, Cell> entry : sv)
      this.put(entry.getKey(), entry.getValue().value);
  }
  
  public void clear() {
    super.clear();
    id = null;
  }
  
  public double getSum() {
    double sum = 0;
    for (Cell cell : this.values())
      sum += cell.value;
    return sum;
  }

  public Double getValue(SID id) {
    Cell cell = this.get(id);
    return (cell == null) ? null : cell.value; 
  }
  
  public Double getValue(String item) {
    return getValue(StringFactory.toID(item));
  }

  public Iterator<Entry<SID, Cell>> iterator() {
    return this.entrySet().iterator();
  }

  public SparseVector normalize() {
    double sum = getSum();
    if (sum > 0)
      for (Cell cells : this.values())
        cells.value /= sum;
    return this;
  }
  
  public void put(SID id, double value) {
    Cell cell = this.get(id);
    if (cell == null)
      this.put(id, new Cell(value));
    else cell.value = value;
  }

  public void put(String name, double value) {
    put(StringFactory.toID(name), value);
  }
  
  public String toString() {
    buf.setLength(0);
    for (SID id : StringFactory.sortIDs(this.keySet())) {
      String idStr = StringFactory.toName(id);
      double value = this.get(id).value;
      buf.append("[").append(idStr).append("|").append(value).append("]");
    }
    return buf.toString();
  }

  public Map<String, Double> toStringValueMap() {
    Map<String, Double> hash = new HashMap<String, Double>();
    for (Entry<SID, Cell> entry : this.entrySet())
      hash.put(StringFactory.toName(entry.getKey()), entry.getValue().value);
    return hash;
  }
}
