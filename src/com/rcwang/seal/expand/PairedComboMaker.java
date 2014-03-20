/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.rank.Ranker.Feature;

public class PairedComboMaker {
  
  public static long DEFAULT_RANDOM_SEED = 0;
  public static Logger log = Logger.getLogger(PairedComboMaker.class);
  
  private RankedComboMaker rankedComboMaker;
  private List<EntityList> possComboList;
  private List<EntityList> trueComboList;
  private Set<EntityList> historicalSeeds;
  private boolean hasTrueSeeds = false;
  private boolean hasPossSeeds = false;
  private long randomSeed;
  
  public static void main(String args[]) {
    PairedComboMaker ss = new PairedComboMaker();
    ss.setRandomSeed(System.currentTimeMillis());
    
    EntityList trueSeeds = new EntityList();
    trueSeeds.add("a");
    trueSeeds.add("b");
    trueSeeds.add("c");
    
    Feature feature = Feature.WLW;
    EntityList possibleSeeds = new EntityList();
    Entity entity = possibleSeeds.add("d");
    entity.setWeight(feature, 0.9);
    entity = possibleSeeds.add("e");
    entity.setWeight(feature, 0.8);
    entity = possibleSeeds.add("f");
    entity.setWeight(feature, 0.7);
    
    ss.setTrueSeeds(trueSeeds, 2);
    ss.setPossibleSeeds(possibleSeeds, 2, feature);
    for (int i = 0; i < 10; i++) {
      EntityList seeds = ss.getNextCombo();
      if (seeds != null)
        log.info((i+1) + ". " + seeds + ": " + seeds.getSumOfLogWeights(feature));
    }
  }
  
  public PairedComboMaker() {
    setRandomSeed(DEFAULT_RANDOM_SEED);
    rankedComboMaker = new RankedComboMaker();
    historicalSeeds = new HashSet<EntityList>();
    possComboList = new ArrayList<EntityList>();
    trueComboList = new ArrayList<EntityList>();
  }
  
  public void clearHistoricalSeeds() {
    historicalSeeds.clear();
  }
  
  public void clearPossibleSeeds() {
    possComboList.clear();
    hasPossSeeds = false;
  }
  
  public void clearTrueSeeds() {
    trueComboList.clear();
    hasTrueSeeds = false;
  }

  public Set<EntityList> getHistoricalSeeds() {
    return historicalSeeds;
  }
  
  public EntityList getNextCombo() {
    if (!hasPossSeeds && !hasTrueSeeds) {
      log.error("Error: True and possible seeds were not defined!");
      return null;
    }
    EntityList sampledSeeds = new EntityList();
    if (hasPossSeeds && hasTrueSeeds) {
      // added 6/18/2008
      Collections.shuffle(trueComboList, new Random(randomSeed));
      for (EntityList possCombos : possComboList) {
        for (EntityList trueCombos : trueComboList)
          if (!isHistorical(trueCombos, possCombos, sampledSeeds))
            return sampledSeeds;
      }
    } else {
      // at this point, one of true/possSeeds is not empty
      List<EntityList> comboList = null;
      if (hasTrueSeeds) comboList = trueComboList;
      else if (hasPossSeeds) comboList = possComboList;
      for (EntityList combos : comboList)
        if (!isHistorical(hasTrueSeeds ? combos : null, hasPossSeeds ? combos : null, sampledSeeds))
          return sampledSeeds;
    }
    return null;
  }
  
  public long getRandomSeed() {
    return randomSeed;
  }
  
  public List<EntityList> makeTrueComboList(EntityList seeds, int numTrueSeeds) {
    List<EntityList> trueComboList = new ArrayList<EntityList>();
    Random random = new Random(randomSeed);
    for (int i = 0; i < RankedComboMaker.DEFAULT_MAX_COMBINATIONS; i++) {
      seeds.shuffle(random);
      EntityList subList = seeds.subList(0, numTrueSeeds);
      if (!trueComboList.contains(subList))
        trueComboList.add(subList);
    }
    return trueComboList;
  }

  public void clear() {
//    setRandomSeed(DEFAULT_RANDOM_SEED);
    clearHistoricalSeeds();
    clearTrueSeeds();
    clearPossibleSeeds();
  }
  
  public boolean setPossibleSeeds(EntityList seeds, int numSampling, Feature feature) {
    if (!check(seeds, numSampling)) return false;
    clearPossibleSeeds();
    possComboList = rankedComboMaker.make(seeds, feature, numSampling);
    if (possComboList != null && !possComboList.isEmpty())
      hasPossSeeds = true;
    return hasPossSeeds;
  }
  
  public void setRandomSeed(long seed) {
    randomSeed = seed;
  }
  
  public boolean setTrueSeeds(EntityList seeds, int numSampling) {
    if (!check(seeds, numSampling)) return false;
    clearTrueSeeds();
    trueComboList = makeTrueComboList(seeds, numSampling);
    if (trueComboList != null && !trueComboList.isEmpty())
      hasTrueSeeds = true;
    return hasTrueSeeds;
  }
  
  private boolean check(EntityList seeds, int numSampling) {
    if (seeds == null || seeds.isEmpty()) {
      log.error("Error: No seeds to process!");
      return false;
    }
    if (seeds.size() < numSampling) {
      log.error("Error: # of items to sample (" + numSampling + ") is greater than the # of seeds (" + seeds.size() + ")");
      return false;
    }
    return true;
  }

  private boolean isHistorical(EntityList trueCombo, EntityList possCombo, EntityList sampled) {
    sampled.clear();
    
    // if a seed exists in both true and possible seeds
    if (hasTrueSeeds && hasPossSeeds)
      if (possCombo.isIntersected(trueCombo))
        return true;
    
    if (hasTrueSeeds) sampled.addAll(trueCombo);
    if (hasPossSeeds) sampled.addAll(possCombo);
    if (historicalSeeds.contains(sampled))
      return true;
    
    if (hasTrueSeeds) log.info("    True Seeds: " + trueCombo);
    if (hasPossSeeds) log.info("Possible Seeds: " + possCombo);
    historicalSeeds.add(sampled);
    return false;
  }
}