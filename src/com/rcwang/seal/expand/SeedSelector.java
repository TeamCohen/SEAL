/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.Collection;

import org.apache.log4j.Logger;

import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;

public class SeedSelector {

  public static enum SeedingPolicy {
    FSS_SUPERVISED,
    FSS_SEMI_SUPERVISED,
    FSS_UNSUPERVISED,
    FSS_UNSUPERVISED_V2,
    ISS_SUPERVISED,
    ISS_UNSUPERVISED,
    CIS_SUPERVISED,
  };

  public static final SeedingPolicy DEFAULT_POLICY = SeedingPolicy.FSS_SUPERVISED;
  public static final Feature DEFAULT_FEATURE = Feature.WLW;
  public static final int DEFAULT_NUM_TRUE_SEEDS = 2;
  public static final int DEFAULT_NUM_POSSIBLE_SEEDS = 0;
  public static final int MAX_SAMPLED_REGULAR_SEEDS = 4;  // for ISS policies only
  public static final int MAX_SAMPLED_RELATIONAL_SEEDS = 4;  // for ISS policies only
  
  public static Logger log = Logger.getLogger(SeedSelector.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  private PairedComboMaker pairedComboMaker;
  private EntityList trueSeeds;
  private EntityList usedTrueSeeds;
  private EntityList sampledSeeds;
  private SeedingPolicy policy;
  private Feature feature;
  private int maxSampledSeeds = 0;
  private int numTrueSeeds;
  private int numPossibleSeeds;
  private int iterCounter = 0;

  public static void main(String args[]) {}
  
  public SeedSelector(SeedingPolicy policy) {
    this.policy = policy;
    trueSeeds = new EntityList();
    usedTrueSeeds = new EntityList();
    pairedComboMaker = new PairedComboMaker();
    setFeature(DEFAULT_FEATURE);
    setNumSeeds(DEFAULT_NUM_TRUE_SEEDS, DEFAULT_NUM_POSSIBLE_SEEDS);
//    setMaxSampledSeeds(DEFAULT_MAX_SAMPLED_SEEDS);
  }
  
  public SeedSelector(SeedSelector selector) {
    this(selector.getPolicy());
    setFeature(selector.getFeature());
    setNumSeeds(selector.getNumTrueSeeds(), selector.getNumPossibleSeeds());
  }
  
  public void clear() {
    pairedComboMaker.clearHistoricalSeeds();
    iterCounter = 0;
  }

  public Feature getFeature() {
    return feature;
  }

  public int getIterCounter() {
    return iterCounter;
  }

  public int getNumPossibleSeeds() {
    return numPossibleSeeds;
  }

  public int getNumTrueSeeds() {
    return numTrueSeeds;
  }
  
  public SeedingPolicy getPolicy() {
    return policy;
  }

  public EntityList getTrueSeeds() {
    return trueSeeds;
  }

  /**
   * Selects the next set of seeds for expansion
   * @param possibleSeeds expanded entities at the i^th iteration
   * @return a set of seeds for (i+1)^th iteration
   */
  public EntityList select(EntityList possibleSeeds) {
    boolean success;
    
    /**
     * Consecutive Index Shift
     * Selects N consecutive entities starting at index 0, 1, 2, ...
     */
    if (policy == SeedingPolicy.CIS_SUPERVISED) {
      int endIndex = iterCounter + numTrueSeeds;
      if (endIndex > trueSeeds.size()) return null;
      return trueSeeds.subList(iterCounter++, endIndex);
    }
    
    if (iterCounter == 0) {
      int numTotalSeeds = numTrueSeeds + numPossibleSeeds;
      // start off with supervised expansion of numTotalSeeds of true seeds
      success = pairedComboMaker.setTrueSeeds(trueSeeds, numTotalSeeds);
      if (!success) return null;
      iterCounter++;
      return sampledSeeds = pairedComboMaker.getNextCombo();
    }

    switch (policy) {

      case FSS_SUPERVISED:
        /****************************************************
         * numTrueSeeds of true seeds from the evaluation set
         * numPossibleSeeds is always 0
         ****************************************************/
        pairedComboMaker.clearPossibleSeeds();
        break;
      
      case FSS_SEMI_SUPERVISED:
        /***********************************************
         * A mixture of: 
         * 1) numTrueSeeds of true seeds from the evaluation set
         * 2) numPossibleSeeds of top extracted items in the last iteration
         ***********************************************/ 
        success = pairedComboMaker.setTrueSeeds(trueSeeds, numTrueSeeds);
        if (!success) return null;
        success = pairedComboMaker.setPossibleSeeds(possibleSeeds, numPossibleSeeds, feature);
        if (!success) return null;
        break;

      case FSS_UNSUPERVISED:
        /*********************************************
         * numTrueSeeds is always 0
         * numPossibleSeeds of top extracted items in the last iteration 
         *********************************************/
        pairedComboMaker.clearTrueSeeds();
        success = pairedComboMaker.setPossibleSeeds(possibleSeeds, numPossibleSeeds, feature);
        if (!success) return null;
        break;

      case FSS_UNSUPERVISED_V2:
        /**********************************************
         * A mixture of:
         * 1) numTrueSeeds of true seeds from the first iteration
         * 2) numPossibleSeeds of top extracted items in the last iteration
         **********************************************/
        if (iterCounter == 1) {
          success = pairedComboMaker.setTrueSeeds(sampledSeeds, numTrueSeeds);
          if (!success) return null;
        }
        success = pairedComboMaker.setPossibleSeeds(possibleSeeds, numPossibleSeeds, feature);
        if (!success) return null;
        break;

      case ISS_UNSUPERVISED:
        /**************************************************** 
         * Increasing number of seeds from numTotalSeeds to MAX_SAMPLED_SEEDS,
         * which is a mixture of:
         * 1) (MAX_SAMPLED_SEEDS - numPossibleSeeds) of items previously used as seeds (variable)
         * 2) numPossibleSeeds of top extracted item in the last iteration (constant)
         ****************************************************/

      case ISS_SUPERVISED:
        /****************************************************
         * Increasing number of seeds from numTotalSeeds to MAX_SAMPLED_SEEDS,
         * which is a mixture of:
         * 1) (MAX_SAMPLED_SEEDS - numPossibleSeeds) of items previously used as seeds (variable)
         * 2) numPossibleSeeds of true seeds from the evaluation set (constant)
         ****************************************************/
        usedTrueSeeds.addAll(sampledSeeds);
        if (maxSampledSeeds == 0) { // added 04/29/2009
          if (trueSeeds.isRelational())
            maxSampledSeeds = MAX_SAMPLED_RELATIONAL_SEEDS;
          else maxSampledSeeds = MAX_SAMPLED_REGULAR_SEEDS;
        }
        int numSampledSeeds = Math.min(sampledSeeds.size(), maxSampledSeeds - numPossibleSeeds);
        success = pairedComboMaker.setTrueSeeds(usedTrueSeeds, numSampledSeeds);
        if (!success) return null;
        if (policy == SeedingPolicy.ISS_SUPERVISED)
          possibleSeeds = new EntityList(trueSeeds);
        else possibleSeeds = new EntityList(possibleSeeds);
        possibleSeeds.removeAll(usedTrueSeeds);
        success = pairedComboMaker.setPossibleSeeds(possibleSeeds, numPossibleSeeds, feature);
        if (!success) return null;
        break;
    }
    iterCounter++;
    return sampledSeeds = pairedComboMaker.getNextCombo();
  }

  public void setFeature(Feature feature) {
    this.feature = feature;
  }

  public void setNumSeeds(int numTrueSeeds, int numPossibleSeeds) {
    this.numTrueSeeds = (policy == SeedingPolicy.FSS_UNSUPERVISED) ? 0 : numTrueSeeds;
    this.numPossibleSeeds = (policy == SeedingPolicy.FSS_SUPERVISED) ? 0 : numPossibleSeeds;
  }
  
  public void setRandomSeed(int seed) {
    pairedComboMaker.setRandomSeed(seed);
  }
  
  public int getRandomSeed() {
    return (int) pairedComboMaker.getRandomSeed();
  }
  
  public void setTrueSeeds(Collection<String> seeds) {
    setTrueSeeds(new EntityList(seeds));
  }

  public void setTrueSeeds(EntityList seeds) {
    if (seeds == null) return;
    trueSeeds.clear();
    trueSeeds.addAll(seeds);
    
    // removed 04/29/2009
    /*if (policy == SeedingPolicy.ISS_SUPERVISED ||
        policy == SeedingPolicy.ISS_UNSUPERVISED)
      usedTrueSeeds.addAll(seeds);  // added 11/25/2008
     */  
    }

  public void addUsedTrueSeeds(EntityList seeds) {
    if (seeds != null)
      usedTrueSeeds.addAll(seeds);
  }
  
  // de-commented on 02/02/2009
  /*public void setIterCounter(int iterCounter) {
    this.iterCounter = iterCounter;
  }*/

  public int getMaxSampledSeeds() {
    return maxSampledSeeds;
  }

  public void setMaxSampledSeeds(int maxSampledSeeds) {
    this.maxSampledSeeds = maxSampledSeeds;
  }
}
