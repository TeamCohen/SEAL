/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.rank.Ranker.Feature;

public class PairedComboMaker2 {
  
//  public static long DEFAULT_RANDOM_SEED = 0;
  public static Logger log = Logger.getLogger(PairedComboMaker2.class);
  
  private RankedComboMaker rankedComboMaker;
  private List<EntityList> list1;
  private List<EntityList> list2;
  private Set<EntityList> historicalEntities;
//  private long randomSeed;
  private boolean hasList1 = false;
  private boolean hasList2 = false;

  public static void main(String args[]) {
    PairedComboMaker2 ss = new PairedComboMaker2();
//    ss.setRandomSeed(System.currentTimeMillis());
    
    Feature feature = Feature.GWW;

    EntityList trueSeeds = new EntityList();
    Entity entity = trueSeeds.add("a");
    entity.setWeight(feature, 1.0);
    entity = trueSeeds.add("b");
    entity.setWeight(feature, 1.0);
    entity = trueSeeds.add("c");
    entity.setWeight(feature, 1.0);
    
    EntityList possibleSeeds = new EntityList();
    entity = possibleSeeds.add("d");
    entity.setWeight(feature, 0.9);
    entity = possibleSeeds.add("e");
    entity.setWeight(feature, 0.8);
    entity = possibleSeeds.add("f");
    entity.setWeight(feature, 0.7);
    
    ss.setList1(trueSeeds, 2, feature);
    ss.setList2(possibleSeeds, 2, feature);
    for (int i = 0; i < 10; i++) {
      EntityList seeds = ss.getNextCombo();
      if (seeds != null)
        log.info((i+1) + ". " + seeds + ": " + seeds.getSumOfLogWeights(feature));
    }
  }
  
  public PairedComboMaker2() {
//    setRandomSeed(DEFAULT_RANDOM_SEED);
    rankedComboMaker = new RankedComboMaker();
    historicalEntities = new HashSet<EntityList>();
    list2 = new ArrayList<EntityList>();
    list1 = new ArrayList<EntityList>();
  }
  
  public void clearHistoricalEntities() {
    historicalEntities.clear();
  }
  
  public void clearList1() {
    list1.clear();
    hasList1 = false;
  }
  
  public void clearList2() {
    list2.clear();
    hasList2 = false;
  }

  public Set<EntityList> getHistoricalEntities() {
    return historicalEntities;
  }
  
  public EntityList getNextCombo() {
    List<EntityList> eLList;
    if (hasList1 && !hasList2)
      eLList = list1;
    else if (!hasList1 && hasList2)
      eLList = list2;
    else if (hasList1 && hasList2)
      eLList = merge();
    else return null;
    for (EntityList eList : eLList)
      if (!isHistorical(eList))
        return eList;
    return null;
  }
  
  /*public List<EntityList> makeTrueComboList(EntityList seeds, int numTrueSeeds) {
    List<EntityList> trueComboList = new ArrayList<EntityList>();
    Random random = new Random(randomSeed);
    for (int i = 0; i < RankedComboMaker.DEFAULT_MAX_COMBINATIONS; i++) {
      seeds.shuffle(random);
      EntityList subList = seeds.subList(0, numTrueSeeds);
      if (!trueComboList.contains(subList))
        trueComboList.add(subList);
    }
    return trueComboList;
  }*/
  
  public void reset() {
//    setRandomSeed(DEFAULT_RANDOM_SEED);
    clearHistoricalEntities();
    clearList1();
    clearList2();
  }

  public boolean setList1(EntityList entities, int numSampling, Object feature) {
    return setList(entities, numSampling, feature, true);
  }
  
  public boolean setList2(EntityList entities, int numSampling, Object feature) {
    return setList(entities, numSampling, feature, false);
  }
  
  /*public void setRandomSeed(long seed) {
    randomSeed = seed;
  }*/
  
  private boolean check(EntityList entities, int numSampling) {
    if (entities == null || entities.isEmpty()) {
      log.error("Error: no entities?");
      return false;
    }
    if (entities.size() < numSampling) {
      log.error("Error: # of sampling items must be less than # of entities!");
      return false;
    }
    return true;
  }
  
  private boolean isHistorical(EntityList eList) {
    if (historicalEntities.contains(eList)) {
      return true;
    } else {
      log.info("Entities: " + eList);
      historicalEntities.add(eList);
      return false;
    }
  }
  
  private List<EntityList> merge() {
    List<EntityList> eListList = new ArrayList<EntityList>();
    for (EntityList eList1 : list1) {
      for (EntityList eList2 : list2) {
        if (eList1.isIntersected(eList2))
          continue;
        EntityList eList = new EntityList();
        eList.addAll(eList1);
        eList.addAll(eList2);
        eListList.add(eList);
      }
    }
    Collections.sort(eListList);
    return eListList;
  }
  
  private boolean setList(EntityList entities, int numSampling, Object feature, boolean isList1) {
    if (!check(entities, numSampling))
      return false;
    List<EntityList> list = rankedComboMaker.make(entities, feature, numSampling);
    boolean hasList = (list != null && !list.isEmpty());
    if (isList1) {
      clearList1();
      list1 = list;
      return (hasList1 = hasList);
    } else {
      clearList2();
      list2 = list;
      return (hasList2 = hasList);
    }
  }
}