/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.eval.Evaluator;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class IterativeSeal {
  
  public static Logger log = Logger.getLogger(IterativeSeal.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();

  public static final boolean DEFAULT_REUSE_DOCUMENT = false;
  public static final int NUM_TO_PRINT_PER_ITER = 10;
  public static final int NUM_TO_PRINT_FINAL = 100;
  
  private List<EvalResult> evalResultList;
  private Evaluator evaluator;
  private EntityList wrapperSeeds;
  private String hint;
  private boolean reuseDocuments;
  private int numUrlsFetched;
  private int numActualExpansions;
  private int numExpansions;

  public static void main(String args[]) {
    // parse the command line argument
    if (args.length == 0) {
      log.error("Incorrect arguments in the command line");
      log.info("Usage 1: java " + IterativeSeal.class.getName() + " seed_file [hint_file]");
      log.info("Usage 2: java " + IterativeSeal.class.getName() + " seed_1 seed_2 ...");
      return;
    }

    // get the seeds
    File seedFile = new File(args[0]);
    String[] seedArr;
    String hint = null;
    if (seedFile.exists()) {
      seedArr = Helper.readFile(seedFile).split("\n");
      if (args.length >= 2) {
        File hintFile = Helper.toFileOrDie(args[1]);
        hint = Helper.readFile(hintFile).replaceAll("[\r\n]+", " ");
      }
    } else {
      for (int i = 0; i < args.length; i++)
        args[i] = args[i].replaceAll("_", " ");
      seedArr = args;
    }
    EntityList seeds = new EntityList(Arrays.asList(seedArr));
    Seal seal = new Seal();
    
    // set up the seed selector
    SeedSelector selector = new SeedSelector(gv.getPolicy());
    selector.setNumSeeds(gv.getNumTrueSeeds(), gv.getNumPossibleSeeds());
//    selector.setNumSeeds(Math.min(seeds.size(), 3), 0);
    selector.setFeature(seal.getFeature());
    selector.setTrueSeeds(seeds);
    
    // set up iSeal
    IterativeSeal iSeal = new IterativeSeal();
    iSeal.setNumExpansions(gv.getNumExpansions());
    iSeal.setHint(hint);
    
    EntityList entities = iSeal.expand(seal, null, selector);
//    entities.filterRelation(0);
    log.info(entities.toDetails(NUM_TO_PRINT_FINAL, seal.getFeature()));
  }
  
  public IterativeSeal() {
    evalResultList = new ArrayList<EvalResult>();
    wrapperSeeds = new EntityList();
    
    setReuseDocuments(DEFAULT_REUSE_DOCUMENT);
  }
  
  public void addWrapperSeeds(EntityList entities) {
    if (entities != null)
      wrapperSeeds.addAll(entities);
  }
  
  public void clear() {
    evalResultList.clear();
    wrapperSeeds.clear();
  }
  
  /**
   * Bootstrap the specified set expander
   * @param expander Set expander
   */
  public EntityList expand(SetExpander expander, DocumentSet documents, SeedSelector seedSelector) {
    EntityList possibleSeeds = null;
    EntityList sampledSeeds = null;

    numUrlsFetched = 0;
    numActualExpansions = 0;
    
    Feature feature = seedSelector.getFeature();
    Pinniped seal = (expander instanceof Pinniped) ? (Pinniped) expander : null;
    if (seal != null)
      seal.setFeature(feature);
    seedSelector.clear();
    
    for (int i = 0; i < numExpansions; i++) {
      log.info("[" + (i+1) + "/" + numExpansions + "] Performing " + seedSelector.getPolicy() + " iterative expansion...");
      log.info("\tusing " + seedSelector.getFeature() + "-based ranker with " + seedSelector.getNumTrueSeeds() + 
               " true and " + seedSelector.getNumPossibleSeeds() + " possible seeds...");
      
      sampledSeeds = seedSelector.select(possibleSeeds);
      if (sampledSeeds == null) {
        log.fatal(this.getClass().getSimpleName() + " is terminating early due to error in sampling seeds!");
        break;
      }
      
      if (seal != null) {
        wrapperSeeds.clear();
        wrapperSeeds.addAll(sampledSeeds);
        if (documents == null || documents.size() == 0)
          seal.expand(wrapperSeeds, sampledSeeds, hint);
        else seal.expand(wrapperSeeds, documents);
        numUrlsFetched += seal.getLastDocs().size();
        // reuse the data fetched in the initial round of expansion
        if (isReuseDocuments())
          documents = seal.getLastDocs();
      } else {
        expander.expand(sampledSeeds);
      }
      
      possibleSeeds = expander.getEntityList();
      
      if (evaluator != null) {
        EvalResult evalResult = evaluator.evaluate(possibleSeeds);
        evalResultList.add(evalResult);
      }
      log.info(possibleSeeds.toDetails(NUM_TO_PRINT_PER_ITER, feature));

//      Helper.printDebugInfo();
      numActualExpansions++;
      //System.out.println();
    }
    if (seal != null) seal.saveResults();
    return possibleSeeds;
  }
  
  public List<EvalResult> getEvalResultList() {
    return evalResultList;
  }

  public Evaluator getEvaluator() {
    return evaluator;
  }

  public String getHint() {
    return hint;
  }

  /**
   * @return number of actual expansions performed
   */
  public int getNumActualExpansions() {
    return numActualExpansions;
  }

  public int getNumExpansions() {
    return numExpansions;
  }

  /**
   * @return number of URLs actually fetched by this bootstrapper
   */
  public int getNumUrlsFetched() {
    return numUrlsFetched;
  }

  /**
   * @return true to reuse documents retrieved in the first iteration
   */
  public boolean isReuseDocuments() {
    return reuseDocuments;
  }

  public void setEvaluator(Evaluator evaluator) {
    this.evaluator = evaluator;
  }

  public void setHint(String hint) {
    this.hint = hint;
  }

  public void setNumExpansions(int numExpansions) {
    this.numExpansions = numExpansions;
  }

  /**
   * True to reuse the first bootstrapped documents retrieved by the set expander
   * @param reuseDocuments
   */
  public void setReuseDocuments(boolean reuseDocuments) {
    this.reuseDocuments = reuseDocuments;
  }
}
