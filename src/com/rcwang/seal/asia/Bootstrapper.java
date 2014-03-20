/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.asia;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.eval.Evaluator;
import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.IterativeSeal;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SeedSelector;
import com.rcwang.seal.expand.SeedSelector.SeedingPolicy;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class Bootstrapper {
  
  public static Logger log = Logger.getLogger(Bootstrapper.class);
  
  public static final int MIN_SEEDS_BRACKETED = 0;  // -1 = bracket most seeds
  public static final int MIN_WRAPPER_SEEDS = 2;
  public static final int MAX_WRAPPER_SEEDS = 20;
  public static final int NUM_RESULTS = 100; // per search engine
  public static final int MAX_TO_PRINT = 10;  // # of instances to print at every iteration
  
  public static final int DEFAULT_ENGINE = -1; // 4 = YahooAPI, 6 = YahooAPI + GoogleAPI
  public static final int DEFAULT_MAX_ITERATION = 5;
  public static final boolean DEFAULT_REUSE_DOC = false;
  
  public static final SeedingPolicy SEEDER = SeedingPolicy.FSS_SUPERVISED;
  public static final int MAX_SAMPLED_SEEDS = 4;  // 3
  public static final double SEED_THRESHOLD = 0.5;  // 0.5: WLW; 0.1: GWW?
  
  private Feature feature;
  private Evaluator evaluator;
  private List<EvalResult> evalResults;
  private int maxIteration;
  private int engine;
  private boolean isReuseDocuments;
  
  public Bootstrapper() {
    evalResults = new ArrayList<EvalResult>();
    setReuseDoc(DEFAULT_REUSE_DOC);
    setMaxIteration(DEFAULT_MAX_ITERATION);
    //setEngine(DEFAULT_ENGINE);
    setEngine(GlobalVar.getGlobalVar().getUseEngine());
  }
  
  public EntityList bootstrap(String category, EntityList entities, Seal seal) {
    entities = entities.getTopEntities(Entity.SCORE, SEED_THRESHOLD, MAX_WRAPPER_SEEDS);
    log.info("Bootstrap using the following " + entities.size() + " intersected instances for category: " + category);
    log.info(entities.toDetails(MAX_TO_PRINT, feature));
    if (entities.size() < MIN_WRAPPER_SEEDS) {
      log.error("Error: Need at least " + MIN_WRAPPER_SEEDS + " seeds to bootstrap, skipping...");
      return entities;
    }
    
    evalResults.clear();
    seal.setEngine(engine);
    seal.setNumResults(NUM_RESULTS);
    seal.setMinSeedsBracketed(MIN_SEEDS_BRACKETED);
//    int maxSampledSeeds = isReuseDocuments ? MAX_SAMPLED_SEEDS_LOCAL : MAX_SAMPLED_SEEDS_WEB;
//    entities = entities.getTopEntities(Entity.SCORE, SEED_THRESHOLD, maxSampledSeeds+1, MAX_WRAPPER_SEEDS);

    // set up the seed selector
    SeedSelector selector = new SeedSelector(SEEDER);
    selector.setMaxSampledSeeds(MAX_SAMPLED_SEEDS);
    int numSeeds = Math.min(MAX_SAMPLED_SEEDS, entities.size());
    selector.setNumSeeds(numSeeds, 0);
    selector.setTrueSeeds(entities);
    selector.setFeature(feature);

    // set up the iterative seal
    IterativeSeal iSeal = new IterativeSeal();
    String listQuery = HyponymProvider.getListQuery(seal.getExtractLangID());
    String hintQuery = listQuery + " " + Helper.addQuote(category);
    iSeal.setHint(hintQuery);
    iSeal.setEvaluator(evaluator);
    iSeal.setNumExpansions(maxIteration);

    // perform bootstrapping!
    DocumentSet documents = isReuseDocuments ? seal.getLastDocs() : null;
    entities = iSeal.expand(seal, documents, selector);
    if (evaluator != null)
      evalResults.addAll(iSeal.getEvalResultList());
    if (entities != null)
      log.info("Bootstrapper extracted " + entities.size() + " members for category: " + category);
    return entities;
  }
  
  public int getEngine() {
    return engine;
  }

  public List<EvalResult> getEvalResults() {
    return evalResults;
  }

  public Evaluator getEvaluator() {
    return evaluator;
  }

  public Feature getFeature() {
    return feature;
  }

  public int getMaxIteration() {
    return maxIteration;
  }

  /*public int getNumSeeds() {
    return numSeeds;
  }*/

  public boolean isReuseDocuments() {
    return isReuseDocuments;
  }

  public void setEngine(int engine) {
    this.engine = engine;
  }

  public void setEvaluator(Evaluator evaluator) {
    this.evaluator = evaluator;
  }

  public void setFeature(Feature feature) {
    this.feature = feature;
  }

  public void setMaxIteration(int numBootstrap) {
    this.maxIteration = numBootstrap;
  }

  /*public void setNumSeeds(int numSeeds) {
    this.numSeeds = numSeeds;
  }*/

  public void setReuseDoc(boolean isReuseDocuments) {
    this.isReuseDocuments = isReuseDocuments;
  }
}
