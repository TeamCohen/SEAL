/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.ComboMaker;

public class RankedComboMaker {

  public static Logger log = Logger.getLogger(RankedComboMaker.class);
  
  public static final int DEFAULT_MAX_COMBINATIONS = 128;
  private ComboMaker<Entity> comboMaker;
  private int maxCombinations;
  
  public static void main(String args[]) {
    int n = 100, k = 1;
    System.out.println(choose(n, k));
    n = getN(k, DEFAULT_MAX_COMBINATIONS);
    System.out.println(n);
    System.out.println(choose(n, k));
  }

  private static long choose(int n, int k) {
    if (n < k) return 0;
    else if (n == k) return 1;
    double answer = 1;
    for (int i = 0; i < k; i++)
      answer *= (double)(n-i)/(k-i);
    return (long) answer;
  }
  
  // returns 'n' such that n choose k <= combo && (n+1) choose k > combo
  private static int getN(int k, int combo) {
    if (k <= 0) {
      log.error("Error: k=" + k + " must be greater than zero, returning -1...");
      return -1;
    }
    for (int n = k+1; ; n++)
      if (choose(n, k) > combo)
        return n-1;
  }
  
  public RankedComboMaker() {
    comboMaker = new ComboMaker<Entity>();
    setMaxCombinations(DEFAULT_MAX_COMBINATIONS);
  }
  
  public int getMaxCombinations() {
    return maxCombinations;
  }
  
  /**
   * Generates a list of entity sets of size k from the input entities, 
   * ranked by their joint scores for a specific feature.
   * @param entityList a list of entities
   * @param feature the feature to rank by
   * @param k number of entities to be contained in each ranked set
   * @return a list of entity combinations
   */
  public List<EntityList> make(EntityList entityList, Object feature, int k) {
    int n = Math.min(entityList.size(), getN(k, maxCombinations));
    if (k > n) {
      log.warn("Error: k=" + k + " cannot be greater than n=" + n + "!");
      return null;
    }
    List<Entity> subEntityList = entityList.getEntities().subList(0, n);
    List<List<Entity>> comboListList = comboMaker.make(subEntityList, k);
    List<EntityList> entityListList = merge(comboListList, feature);
    Collections.sort(entityListList);
    return entityListList;
  }
  
  /**
   * Set maximum number of combinations (too many could run out of memory)
   * @param maxCombinations
   */
  public void setMaxCombinations(int maxCombinations) {
    this.maxCombinations = maxCombinations;
  }
  
  private static List<EntityList> merge(List<List<Entity>> comboListList, Object feature) {
    List<EntityList> eListList = new ArrayList<EntityList>();
    for (List<Entity> list : comboListList) {
      EntityList eList = new EntityList();
      eList.addAll(list);
      double sum = eList.getSumOfLogWeights(feature);
      eList.setListWeight(sum);
      eListList.add(eList);
    }
    return eListList;
  }

}
