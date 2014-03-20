/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 *
 * ... and William Cohen (wcohen@cs.cmu.edu)
 **************************************************************************/
package com.rcwang.seal.asia;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.eval.Evaluator;
import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.expand.WrapperSaver;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

/**
 * Automatic Set Instance Acquirer
 * @author rcwang
 */
public class WrapperSavingAsia {

  public static Logger log = Logger.getLogger(WrapperSavingAsia.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final int MIN_WRAPPERS = 3; // number of wrappers required to increment confidence
  public static final int MAX_TO_PRINT = 50;  // number of instances to print
  public static final double SCORE_THRESHOLD = 0.01;  // filter out instances with 'SCORE' less than this
  public static final double SHRINK_POWER = 1;  // 1: shrink mildly, 5: shrink strongly
  
  private NoisyInstanceExtractor extractor;
  private NoisyInstanceExpander expander;
  private Bootstrapper bootstrapper;
  private int confidence = 0;
  
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: java " + WrapperSavingAsia.class.getName() + " category [eval_dataset]");
      return;
    }
    long startTime = System.currentTimeMillis();
    
    String langID = "en";
    String category = args[0].replace("_", " ");
    String dataset = (args.length > 1) ? args[1] : null;
    
    WrapperSavingAsia asia = new WrapperSavingAsia();
    asia.setNumExpansions(gv.getNumExpansions());
    asia.setRanker(gv.getFeature());
    
    if (dataset != null) {
      Evaluator evaluator = new Evaluator();
      evaluator.loadGoldFile(new File(dataset));
      asia.setEvaluator(evaluator);
    }
    Seal seal = new Seal(langID);
    EntityList entities = asia.expand(seal, category);
    if (entities == null) return;
    if (dataset != null) {
      List<EvalResult> evalResultList = asia.getBootstrapResults();
      for (int i = 0; i < evalResultList.size(); i++) {
        EvalResult evalResult = evalResultList.get(i);
        log.info((i+1) + ". MAP: " + evalResult.meanAvgPrecision);
      }
    }
    if (gv.getWrapperSaving()==1) {
        log.warn("Asia does not know how to save wrappers as xml in the results directory");
    } else if (gv.getWrapperSaving()==2) {
        WrapperSaver wrapperSaver = new WrapperSaver();
        wrapperSaver.saveAsDocuments(gv.getSavedWrapperDir(),seal.getLastDocs(),seal.getEntityList(),seal.getLastSeeds());
    }

    log.info(entities.toDetails(MAX_TO_PRINT, asia.getRanker()));
    Helper.printElapsedTime(startTime);
  }
  
  private static void promoteEntities(EntityList entities, String className, double ratio) {
    boolean hasSpace = entities.hasSpace();
    for (Entity entity : entities) {
      if (entity.getName().toString().endsWith((hasSpace ? " " : "") + className)) {
        double w = entity.getScore();
        entity.setScore(w + (1-w)*ratio);
      }
    }
  }
  
  public WrapperSavingAsia() {
    extractor = new NoisyInstanceExtractor();
    expander = new NoisyInstanceExpander();
    bootstrapper = new Bootstrapper();
    
    setRanker(gv.getFeature());
    setNumExpansions(gv.getNumExpansions());
  }
  
  public EntityList expand(Seal seal, String category) {
    if (seal == null || category == null) return null;
    category = WrapperFactory.tidy(category);
    if (category.length() == 0) return null;
    
    seal.addStopword(category, false);
    seal.setHasNoisySeeds(true);
    
    Thread fetcherThread = null;
    if (getNumExpansions() >= 1) {
      WSAsiaFetcherThread ft = new WSAsiaFetcherThread(expander, category, seal.getExtractLangID(), seal.getFetchLangID());
      fetcherThread = new Thread(ft);
      fetcherThread.start();
    }
    
    WSAsiaExtractorThread et = new WSAsiaExtractorThread(extractor, category, seal.getExtractLangID());
    Thread extractorThread = new Thread(et);
    extractorThread.start();
    
    // extracting using 'such as' patterns
    try { extractorThread.join(); } catch (InterruptedException e) {}
    EntityList entities = extractor.getEntities();
    confidence = extractor.getConfidence();
    boolean useBackoff = (confidence == 1);
    if (Helper.empty(entities)) return null;

    if (getNumExpansions() >= 1) {
      // expand the noisy set of initial entities
      try { fetcherThread.join(); } catch (InterruptedException e) {}
      EntityList intersects = expander.expand(category, entities, seal);
      
      // increment confidence by one if many wrappers were constructed
      if (seal.getNumPastWrappers() > MIN_WRAPPERS)
        confidence++;

      // bootstrap only when the back-off was not used
      if (getNumExpansions() >= 2) {
        if (useBackoff) {
          log.warn("Back-off was used, skipping bootstrapping...");
        } else {
          bootstrapper.bootstrap(category, intersects, seal);
        }
      }
    }

    if (seal.isEmpty()) {
      // if seal did not extract anything, then manually promote some instances
      promoteEntities(entities, category, 0.1);
    } else {
      entities = seal.getEntityList();
    }
    
    // filter out instances with SCORE of less than 'SCORE_THRESHOLD'
    entities.relativeFilter(getRanker(), SCORE_THRESHOLD);
    
    // collapse instances where one is a substring of another
    entities.shrink(getRanker(), SHRINK_POWER);
    
//    log.info(entities.toDetails(MAX_TO_PRINT, getRanker()));
    return entities;
  }
  
  public List<EvalResult> getBootstrapResults() {
    return bootstrapper.getEvalResults();
  }
  
  public int getConfidence() {
    return confidence;
  }
  
  public Evaluator getEvaluator() {
    return bootstrapper.getEvaluator();
  }
  
  public List<EvalResult> getExpandResults() {
    return expander.getEvalResults();
  }

  public EvalResult getExtractResult() {
    return extractor.getEvalResult();
  }

  public int getNumExpansions() {
    return bootstrapper.getMaxIteration()+1;
  }

  public Feature getRanker() {
    return bootstrapper.getFeature();
  }

  public void setEvaluator(Evaluator evaluator) {
    extractor.setEvaluator(evaluator);
    expander.setEvaluator(evaluator);
    bootstrapper.setEvaluator(evaluator);
  }

  public void setNumExpansions(int numExpansions) {
    bootstrapper.setMaxIteration(numExpansions-1);
  }
  
  public void setRanker(Feature ranker) {
    extractor.setOutputFeature(ranker);
    expander.setFeature(ranker);
    bootstrapper.setFeature(ranker);
  }
}

class WSAsiaExtractorThread implements Runnable {
  
  private NoisyInstanceExtractor extractor;
  private String category, langID;
  
  public WSAsiaExtractorThread(NoisyInstanceExtractor extractor, String category, String langID) {
    this.extractor = extractor;
    this.category = category;
    this.langID = langID;
  }
  
  public void run() {
    extractor.extract(category, langID);
  }
}

class WSAsiaFetcherThread implements Runnable {
  
  private String extractLangID;
  private String fetchLangID;
  private String category;
  private NoisyInstanceExpander expander;
  
  public WSAsiaFetcherThread(NoisyInstanceExpander expander, String category, 
                       String extractLangID, String fetchLangID) {
    this.expander = expander;
    this.category = category;
    this.extractLangID = extractLangID;
    this.fetchLangID = fetchLangID;
  }
  
  public void run() {
    expander.fetch(category, extractLangID, fetchLangID);
  }
}