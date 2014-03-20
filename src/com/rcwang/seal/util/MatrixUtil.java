/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;

public class MatrixUtil {
  
  public static Logger log = Logger.getLogger(MatrixUtil.class);
  
  public static SparseMatrix dotProduct(SparseMatrix sm1, SparseMatrix sm2) {
    SparseMatrix sm = new SparseMatrix();
    for (SparseVector col2 : sm2.getColumns())
      sm.addColumn(dotProduct(sm1, col2));
    return sm;
  }
  
  public static SparseVector dotProduct(SparseMatrix sm, SparseVector sv1) {
    SparseVector sv = new SparseVector(sv1.id);
    for (SparseVector row : sm.getRows())
      sv.put(row.id, dotProduct(row, sv1));
    return sv;
  }
  
  public static double dotProduct(SparseVector sv1, SparseVector sv2) {
    if (sv2.size() < sv1.size()) {
      SparseVector sv = sv2;
      sv2 = sv1; sv1 = sv;
    }
    double sum = 0;
    for (Entry<SID, Cell> entry : sv1) {
      Cell c2 = sv2.get(entry.getKey());
      if (c2 == null) continue;
      Cell c1 = entry.getValue();
      sum += c1.value * c2.value;
    }
    return sum;
  }

  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    SparseMatrix m1 = new SparseMatrix();
    m1.add("0", "0", 0.86);
    m1.add("1", "0", 0.08);
    m1.add("0", "1", -0.12);
    m1.add("1", "1", 1.14);
    
    SparseVector v = new SparseVector();
    v.put("0", 200);
    v.put("1", 100);
    
//    SparseMatrix m2 = new SparseMatrix();
//    m2.add("0", "0", 200);
//    m2.add("1", "0", 100);
    
    SparseVector v2 = dotProduct(m1, v);
    log.info(v2);
    
//    SparseMatrix m3 = dotProduct(m1, m2);
//    log.info(m3);
    
    Helper.printElapsedTime(startTime);
    Helper.printMemoryUsed();
  }
  
  public static double euclideanDistance(SparseVector sv1, SparseVector sv2) {
    Set<SID> idSet = new HashSet<SID>();
    idSet.addAll(sv1.keySet());
    idSet.addAll(sv2.keySet());
    double sum = 0;
    for (SID id : idSet) {
      Cell c1 = sv1.get(id);
      double w1 = (c1 == null) ? 0 : c1.value;
      Cell c2 = sv2.get(id);
      double w2 = (c2 == null) ? 0 : c2.value;
      sum += Math.pow(w1 - w2, 2);
    }
    return Math.sqrt(sum);
  }
  
  public static SparseVector multiply(double value, SparseVector sv1, boolean makeNewVector) {
    SparseVector sv = makeNewVector ? new SparseVector(sv1.id) : sv1;
    for (Entry<SID, Cell> entry : sv1) {
      SID id = entry.getKey();
      Cell cell = entry.getValue();
      if (makeNewVector)
        sv.put(id, new Cell(cell.value * value));
      else cell.value *= value;
    }
    return sv;
  }
  
  public static SparseVector getUnitVector(Set<SID> ids, double value) {
    SparseVector sv = new SparseVector();
    for (SID id : ids)
      sv.put(id, new Cell(value));
    return sv;
  }
  
  public static SparseVector sum(SparseVector sv1, SparseVector sv2, boolean makeNewVector) {
    SparseVector sv = makeNewVector ? new SparseVector(sv1) : sv1;
    for (Entry<SID, Cell> entry : sv2) {
      SID id = entry.getKey();
      Cell cell1 = sv.get(id);
      Cell cell2 = entry.getValue();
      if (cell1 == null)
        sv.put(id, new Cell(cell2.value));
      else cell1.value += cell2.value;
    }
    return sv;
  }
}
