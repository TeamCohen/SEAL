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
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class NoisyInstanceExpander {
  
  public static Logger log = Logger.getLogger(NoisyInstanceExpander.class);
  
  public static final int MIN_SEEDS_BRACKETED = 2;  // -2 = bracket most weights, -1 = bracket most seeds
  public static final int MIN_WRAPPER_SEEDS = 2;
  public static final int MAX_WRAPPER_SEEDS = 20; // [20] number of top instances to use
  public static final int NUM_RESULTS = 100; // per search engine
  public static final int MAX_TO_PRINT = 20;  // # of instances to print
  
  public static final int DEFAULT_ENGINE = -1; // 5 = use YahooAPI and GoogleWeb
  public static final int DEFAULT_MAX_ITERATION = 1;
  public static final boolean DEFAULT_REUSE_DOC = true; // reuse documents at every iteration?

  public static final double CONVERGE_THRESHOLD = 1e-2; // 1e-3
  public static final double INITIALS_BIAS = 0; // [0.5] degree of bias towards the initial set
  
  private Feature feature;
  private Evaluator evaluator;
  private List<EvalResult> evalResults;
  private int engine;
  private int maxIteration;
  private boolean isReuseDocuments;
  private DocumentSet documents;
  
  public NoisyInstanceExpander() {
    evalResults = new ArrayList<EvalResult>();
    setReuseDoc(DEFAULT_REUSE_DOC);
    setMaxIteration(DEFAULT_MAX_ITERATION);
    //setEngine(DEFAULT_ENGINE);
    setEngine(GlobalVar.getGlobalVar().getUseEngine());
  }
  
  public EntityList expand(String category, EntityList initials, Seal seal) {
    if (Helper.empty(initials)) {
      log.error("Error: Initial set of entities is empty!");
      return null;
    }

    if (documents == null)
      fetch(category, seal.getExtractLangID(), seal.getFetchLangID());
    
    if (documents == null || documents.size() == 0) {
      log.error("Error: No webpages to perform noisy instance expansion!");
      return initials;
    }
    
    final String className = this.getClass().getSimpleName();
    EntityList intersected = initials;
    EntityList prevSeeds = null;
    evalResults.clear();
    init(seal); // initialize SEAL
    
    for (int i = 0; i < maxIteration; i++) {
      // seed selection and convergence checking
//      EntityList currSeeds = intersected.getTopEntities(feature, 0, MIN_WRAPPER_SEEDS, MAX_WRAPPER_SEEDS);
      EntityList currSeeds = intersected.getTopEntities(feature, 0, MAX_WRAPPER_SEEDS);
      if (prevSeeds != null) {
        double distance = prevSeeds.euclideanDistance(currSeeds, feature);
        log.info("Euclidean distance of the seeds: " + distance);
        if (distance < CONVERGE_THRESHOLD) {
          log.info("Euclidean distance is < " + CONVERGE_THRESHOLD + ", aborting expansion...");
          return intersected;
        }
      }
      prevSeeds = currSeeds;

      log.info("[" + (i+1) + "] " + className + " is expanding noisy entities...");
      seal.expand(currSeeds, documents);
      EntityList entities = seal.getEntityList();
      
      // find duplicate entities, with a pre-specified bias towards the initial entities
      // creates a list of new entities
      intersected = initials.intersect(entities, feature, INITIALS_BIAS);
      
      if (Helper.empty(intersected)) {
        if (intersected != null)
          log.info("No additional entities extracted, returning initial entities...");
        return initials;
      }
      
      // evaluation and system output
      if (evaluator != null) {
        EvalResult evalResult = evaluator.evaluate(entities);
        evaluator.evaluate(intersected);
        evalResults.add(evalResult);
      }
//      intersected.assignScore(feature, seal.hasNoisySeeds());
      intersected.sortBy(feature, false);
//      log.info(intersected.toDetails(MAX_WRAPPER_SEEDS + 10, feature));
      log.info(className + " extracted the following instances for category: " + category);
      log.info(entities.toDetails(MAX_TO_PRINT, feature));
    }
    return intersected;
  }
  
  // for retrieving initial set of webpages
  public void fetch(String category, String extractLangID, String fetchLangID) {
    EntityList pageSeeds = new EntityList();
    pageSeeds.add(Helper.addQuote(category));
    String listQuery = HyponymProvider.getListQuery(extractLangID);
    this.documents = Seal.fetch(pageSeeds, listQuery, fetchLangID, NUM_RESULTS, engine);
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

  public void setMaxIteration(int maxIteration) {
    this.maxIteration = maxIteration;
  }

  public void setReuseDoc(boolean isReuseDocuments) {
    this.isReuseDocuments = isReuseDocuments;
  }

  // initialize SEAL
  private void init(Seal seal) {
    seal.setFeature(feature);
    seal.setEngine(engine);
    seal.setNumResults(NUM_RESULTS);
    seal.setMinSeedsBracketed(MIN_SEEDS_BRACKETED);
  }

}
