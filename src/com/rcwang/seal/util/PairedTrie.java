/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.MiddleContext;
import com.rcwang.seal.util.Trie.TextNode;
import com.rcwang.seal.util.Trie.TrieNode;

public class PairedTrie {
  
  public static Logger log = Logger.getLogger(PairedTrie.class);
  public static final int BRACKET_MOST_WEIGHT = -2;
  public static final int BRACKET_MOST_SEEDS = -1;
  public static final int BRACKET_ALL_SEEDS = 0;
  public static final double DEFAULT_MAX_WEIGHT_RATIO = 1;  // 0.5
  public static final int MIN_TYPES = 2;  // minimum number of seeds
  public static final int MIN_LENGTH = 1; // minimum context (left or right) length
  public static final double MIN_WEIGHT = 0;  // minimum weight
  
  private Trie leftTrie, rightTrie;
  private StringBuffer reverseBuf;
  private List<Entity> entities;
  private List<MiddleContext> mcList;
  private Set<Entity> entitySet;
  private Map<MiddleContext, Set<Entity>> middleEntityMap;
  private MiddleContext middleContext;
  private Object feature;
  private double maxWeightRatio;
  
  public static void main(String args[]) {
    PairedTrie pTrie = new PairedTrie();
    pTrie.add("ef", "at", new Entity("1"));
    pTrie.add("ef", "aq", new Entity("2"));
    pTrie.add("uf", "at", new Entity("2"));
    Set<Wrapper> wrappers = pTrie.getWrappers(2, 1, 0);
    for (Wrapper wrapper : wrappers)
      log.info(wrapper);
  }
  
  public static String toMinTypeStr(int minType) {
    if (minType == BRACKET_MOST_WEIGHT)
      return "most weight";
    else if (minType == BRACKET_MOST_SEEDS)
      return "most seeds";
    else if (minType == BRACKET_ALL_SEEDS)
      return "all seeds";
    else if (minType > 0)
      return "at least " + minType + " seeds";
    else return "<unknown seeds>";
  }
  
  public PairedTrie() {
    leftTrie = new Trie();
    leftTrie.setPairedTrie(this);
    rightTrie = new Trie();
    rightTrie.setPairedTrie(this);
    reverseBuf = new StringBuffer();
    entities = new ArrayList<Entity>();
    mcList = new ArrayList<MiddleContext>();
    entitySet = new HashSet<Entity>();
    middleEntityMap = new HashMap<MiddleContext, Set<Entity>>();
    setMaxWeightRatio(DEFAULT_MAX_WEIGHT_RATIO);
  }
  
  public void add(String left, MiddleContext middle, String right, Entity entity) {
    add(left, right, entity);
    if (middle != null)
      mcList.add(middle);
  }
  
  public void add(String left, String right, Entity entity) {
    leftTrie.add(reverse(left), entities.size());
    rightTrie.add(right, entities.size());
    entities.add(entity);
  }
  
  public void clear() {
    leftTrie.clear();
    rightTrie.clear();
    reverseBuf.setLength(0);
    entities.clear();
    mcList.clear();
    entitySet.clear();
    middleEntityMap.clear();
    middleContext = null;
  }
  
  public Object getFeature() {
    return feature;
  }
  
  public Trie getLeftTrie() {
    return leftTrie;
  }
  
  public double getMaxWeightRatio() {
    return maxWeightRatio;
  }
  
  public Trie getRightTrie() {
    return rightTrie;
  }

  /**
   * retrieves longest common left and right context (LCC)
   * @param minType min. number of seeds each LCC should bracket
   * @return a set of Context objects
   */
  public Set<Wrapper> getWrappers(int minType, int minLength, double minWeight) {
    if (minType == BRACKET_MOST_WEIGHT && feature == null) {
      log.error("Error: Feature must be specified when minType is " + BRACKET_MOST_WEIGHT);
      log.error("Using minType of " + BRACKET_MOST_SEEDS + "...");
      minType = BRACKET_MOST_SEEDS;
    }
    minLength = Math.max(minLength, MIN_LENGTH);
    minWeight = Math.max(minWeight, MIN_WEIGHT);
    Set<Wrapper> wrappers = new HashSet<Wrapper>();
    
    if (minType == BRACKET_MOST_WEIGHT) {
      // contexts that bracket the most seed weights
      double maxWeight = getMaxWeight(MIN_TYPES, minLength);
      if (maxWeight < minWeight) return wrappers;
      minWeight = maxWeight * maxWeightRatio;
      
    } else if (minType == BRACKET_MOST_SEEDS) {
      // contexts that bracket the most seed types
      minType = (int) getMaxType(MIN_TYPES, minLength);
      if (minType < MIN_TYPES) return wrappers;
      
    } else if (minType == BRACKET_ALL_SEEDS) {
      // contexts that bracket exactly the input seeds
      minType = new HashSet<Entity>(entities).size();
      if (minType < MIN_TYPES) return wrappers;
    }
    minType = Math.max(minType, MIN_TYPES);
    
//    log.info("MinType: " + minType + " MinLength: " + minLength + " MinWeight: " + minWeight);
//    log.debug(this);
    
    // get longest contexts of the left and right tries
    getWrappers(leftTrie, rightTrie, true, minType, minLength, minWeight, wrappers);
    getWrappers(rightTrie, leftTrie, false, minType, minLength, minWeight, wrappers);
    return wrappers;
  }
  
  public boolean isEmpty() {
    return leftTrie.isEmpty() || rightTrie.isEmpty();
  }
  
  public boolean isRelational() {
    return !mcList.isEmpty();
  }
  
  /**
   * Features is only used for minNumType of -2
   * @param feature
   */
  public void setFeature(Object feature) {
    this.feature = feature;
  }
  
  public void setMaxWeightRatio(double maxWeightRatio) {
    this.maxWeightRatio = maxWeightRatio;
  }
  
  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append(Helper.center("[ List of Seed Occurrences ]", '-', 80)).append("\n");
    for (int i = 0; i < entities.size(); i++)
      buf.append(i).append(". ").append(entities.get(i).getName()).append(", ");
    buf.append("\n");
    if (!mcList.isEmpty()) {
      buf.append(Helper.center("[ Middle Contexts ]", '-', 80)).append("\n");
      for (int i = 0; i < mcList.size(); i++)
        buf.append(i).append(". ").append(mcList.get(i)).append("\n");
    }
    buf.append(Helper.center("[ Left Trie ]", '-', 80)).append("\n");
    buf.append(leftTrie.toString());
    buf.append(Helper.center("[ Right Trie ]", '-', 80)).append("\n");
    buf.append(rightTrie.toString());
    return buf.toString();
  }
  
  private double getMax(boolean isMaxWeight, int minType, int minLength) {
    double max = -1;
    for (TrieNode lNode : leftTrie.getTopNodes(minLength)) {
      if (lNode.ids.size() < MIN_TYPES) continue;
      
      for (TrieNode rNode : rightTrie.getTopNodes(minLength)) {
        if (rNode.ids.size() < MIN_TYPES) continue;
        Set<Entity> entities = getCommons(lNode.ids, rNode.ids);
        if (entities.size() < minType) continue;
        double w = isMaxWeight ? sumWeight(entities) : entities.size();
        max = Math.max(w, max);
      }
    }
    return max;
  }
  
  private double getMaxType(int minType, int minLength) {
    return getMax(false, minType, minLength);
  }
  
  private double getMaxWeight(int minType, int minLength) {
    return getMax(true, minType, minLength);
  }

  private void getWrappers(Trie trie1, Trie trie2, boolean trie1AtLeft,
                           int minType, int minLength, double minWeight, Set<Wrapper> wrappers) {
    
    // get top nodes in trie2 that have at least minLength
    List<TrieNode> topNodes = trie2.getTopNodes(minLength);
    
    // find deepest nodes in trie1 that matches the top nodes in trie2
    List<TextNode> textNodes1 = trie1.getDeepestNodes(minType, minLength, minWeight, topNodes);
    
    // a list that will only contain one trie node
    List<TrieNode> aList = new ArrayList<TrieNode>();
    
    for (TextNode textNode1 : textNodes1) {
      aList.clear();
      aList.add(textNode1.node);
      
      // find deepest nodes in trie2 that matches the deepest node in trie1
      List<TextNode> textNodes2 = trie2.getDeepestNodes(minType, minLength, minWeight, aList);
      
      for (TextNode textNode2 : textNodes2) {
        
        // constructs a Wrapper using contexts from trie1 and trie2
        String left = trie1AtLeft ? textNode1.text : textNode2.text;
        String right = trie1AtLeft ? textNode2.text : textNode1.text;
        
        // if is relational, middleContext will be set by the following
        Set<Entity> commons = getCommons(textNode1.node.ids, textNode2.node.ids);
        Wrapper wrapper = new Wrapper(reverse(left), middleContext, right);
        if (wrappers.add(wrapper))
          wrapper.setSeeds(commons);
      }
    }
  }

  private String reverse(String s) {
    reverseBuf.setLength(0);
    return reverseBuf.append(s).reverse().toString();
  }
  
  // intersects ids1 and ids2
  protected Set<Entity> getCommons(Collection<Integer> ids1, Collection<Integer> ids2) {
    middleContext = null;
    
    if (!isRelational()) {
      entitySet.clear();
      for (int id : ids1)
        if (ids2.contains(id))
          entitySet.add(entities.get(id));
      return entitySet;
    }
    
    middleEntityMap.clear();
    int maxCount = 0;
    
    for (int id : ids1) {
      if (!ids2.contains(id)) continue;
      MiddleContext mc = mcList.get(id);
      Set<Entity> entitySet = middleEntityMap.get(mc);
      if (entitySet == null) {
        entitySet = new HashSet<Entity>();
        middleEntityMap.put(mc, entitySet);
      }
      entitySet.add(entities.get(id));
      if (entitySet.size() > maxCount) {
        maxCount = entitySet.size();
        middleContext = mc;
      }
    }
    Set<Entity> commons = middleEntityMap.get(middleContext);
    if (commons == null) return new TreeSet<Entity>();
    return commons;
  }

  protected double sumWeight(Collection<Entity> entities) {
    double w = 0;
    for (Entity entity : entities)
      w += entity.getWeight(feature);
    return w;
  }
}
