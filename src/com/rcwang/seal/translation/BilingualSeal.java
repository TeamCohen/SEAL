package com.rcwang.seal.translation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.eval.Evaluator;
import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.GoogleSets;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SeedSelector;
import com.rcwang.seal.expand.SetExpander;
import com.rcwang.seal.expand.SeedSelector.SeedingPolicy;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.translation.Proximator.Proxistat;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.StringEditDistance;

public class BilingualSeal {
  
  public static Logger log = Logger.getLogger(BilingualSeal.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static int DEFAULT_NUM_EXPANSIONS = 10;
  public static int DEFAULT_NUM_TOP_TRANS = 3;
  public static double DEFAULT_SIM_THRESHOLD = 0.80;
  public static double DEFAULT_PCT_TOP_ENTITIES = 0.50;
  public static boolean DEFAULT_USE_TRANSLATION = true;
  
  private List<EvalResult> evalResultList;
  private EntityList historicalSeeds;
  private Evaluator sourceEval;
  private String sourceLangID;
  private String targetLangID;
  private int numExpansions;
  private int numTopTrans;
  private double simThreshold;
  private double pctTopEntities;
  private boolean useTranslation;
  
  public static void main(String args[]) {
    List<String> seeds = new ArrayList<String>();
//    seeds.add("小美人魚");
//    seeds.add("美女與野獸");
    
    seeds.add("匹茲堡海盜");
    seeds.add("紐約洋基");
    
    SeedSelector selector = new SeedSelector(SeedingPolicy.ISS_UNSUPERVISED);
    selector.setNumSeeds(1, 1);
    selector.setFeature(Feature.GWW);
    selector.setTrueSeeds(seeds);
    
    BilingualSeal bs = new BilingualSeal();
    bs.setLangID("zh-TW", "en");
    bs.expand(new Seal(), selector);
  }

  public BilingualSeal() {
    historicalSeeds = new EntityList();
    evalResultList = new ArrayList<EvalResult>();
    setNumExpansions(DEFAULT_NUM_EXPANSIONS);
    setUseTranslation(DEFAULT_USE_TRANSLATION);
    setPctTopEntities(DEFAULT_PCT_TOP_ENTITIES);
    setSimThreshold(DEFAULT_SIM_THRESHOLD);
    setNumTopTrans(DEFAULT_NUM_TOP_TRANS);
  }
  
  public EntityList expand(SetExpander sourceExpander, SeedSelector sourceSelector) {
    if (sourceExpander == null || sourceSelector == null) {
      return null;
    } else if (targetLangID == null || sourceLangID == null) {
      log.fatal("Source and target languages are not set!");
      return null;
    }

    // configure source expander
    if (sourceExpander instanceof Seal) {
      ((Seal) sourceExpander).setLangID(sourceLangID);
      ((Seal) sourceExpander).setFeature(sourceSelector.getFeature());
    }
    
    // create target expander
    SetExpander targetExpander = toTargetExpander(sourceExpander);
    if (targetExpander == null) return null;
    if (targetExpander instanceof Seal) {
      ((Seal) targetExpander).setLangID(targetLangID);
      ((Seal) targetExpander).setFeature(sourceSelector.getFeature());
    }
    
    // create translators (source <=> target)
    Proximator s2tTranslator = new Proximator(targetLangID);
    Proximator t2sTranslator = new Proximator(sourceLangID);
    s2tTranslator.setReverseProximator(t2sTranslator);
    t2sTranslator.setReverseProximator(s2tTranslator);

    // create target evaluator
    Evaluator targetEval = toTargetEval(sourceEval);
    SeedSelector targetSelector = new SeedSelector(sourceSelector);
    targetSelector.setTrueSeeds(targetEval.getFirstMentions());
    targetSelector.setRandomSeed(sourceSelector.getRandomSeed() + 1024);
    EntityList prevEntities = null;
    EntityList currEntities = null;
    EntityList possibleSeeds = null;
    
    for (int i = 0; i < numExpansions; i++) {
      // select the appropriate object based on the current iteration
      String langID = (i%2 == 0) ? sourceLangID : targetLangID;
      Evaluator evaluator = (i%2 == 0) ? sourceEval : targetEval;
      SetExpander expander = (i%2 == 0) ? sourceExpander : targetExpander;
      SeedSelector selector = (i%2 == 0) ? sourceSelector : targetSelector;
      Proximator translator = (i%2 == 0) ? s2tTranslator : t2sTranslator;

      System.out.println();
      String msg = "[" + langID + "." + (selector.getIterCounter()+1) + "] ";
      if (evaluator == null)
        log.info(msg + "Expanding...");
      else log.info(msg + "Evaluating " + evaluator.getDataName() + "...");

      if (i >= 2) {
        if (useTranslation) {
          possibleSeeds = intersect(prevEntities, currEntities, selector.getFeature(), selector.getNumPossibleSeeds(), translator);
          if (possibleSeeds == null)
            possibleSeeds = prevEntities;
        } else possibleSeeds = prevEntities;
      }
      
      EntityList seeds = selector.select(possibleSeeds);
      if (seeds == null) return null;
      historicalSeeds.addAll(seeds);

      expander.expand(seeds);
//      expander.assignWeights(selector.getFeature());
      prevEntities = currEntities;
      currEntities = expander.getEntityList();

      if (evaluator != null) {
        EvalResult evalResult = evaluator.evaluate(currEntities);
        log.info("==> Mean Average Precision: " + Helper.formatNumber(evalResult.meanAvgPrecision*100, 2) + "%");
        evalResultList.add(evalResult);
      }
    }
    // fills up the rest of the evaluation result list if seeds for the next iteration are missing
    if (sourceEval != null && possibleSeeds == null) {
      for (int i = evalResultList.size(); i < numExpansions; i++)
        evalResultList.add(evalResultList.get(i-2));
    }
    return currEntities;
  }
  
  public List<EvalResult> getEvalResultList() {
    return evalResultList;
  }
  
  public int getNumExpansions() {
    return numExpansions;
  }
  
  public int getNumTopTrans() {
    return numTopTrans;
  }
  
  public double getPctTopEntities() {
    return pctTopEntities;
  }

  public double getSimThreshold() {
    return simThreshold;
  }

  public Evaluator getSourceEval() {
    return sourceEval;
  }
  
  public String getSourceLangID() {
    return sourceLangID;
  }

  public String getTargetLangID() {
    return targetLangID;
  }

  public boolean isUseTranslation() {
    return useTranslation;
  }
  
  public void setEval(Evaluator sourceEval) {
    this.sourceEval = sourceEval;    
  }

  public void setLangID(String sourceLangID, String targetLangID) {
    this.sourceLangID = sourceLangID;
    this.targetLangID = targetLangID;
  }

  public void setNumExpansions(int numIteration) {
    this.numExpansions = numIteration;
  }

  public void setNumTopTrans(int numTopTrans) {
    this.numTopTrans = numTopTrans;
  }

  public void setPctTopEntities(double pctTopEntities) {
    this.pctTopEntities = pctTopEntities;
  }

  public void setSimThreshold(double simThresh) {
    this.simThreshold = simThresh;
  }

  public void setUseTranslation(boolean useTranslation) {
    this.useTranslation = useTranslation;
  }

  private EntityList intersect(EntityList entities1, EntityList entities2, Object feature, int numPossibleSeeds, Proximator translator) {
    EntityList seeds = new EntityList();
//    TranslationPair transPair = translator.getTransPair();
    
    for (int i = 0; i < entities1.size(); i++) {
      if ( (double)(i+1) / entities1.size() > pctTopEntities) break;
      Entity entity1 = entities1.get(i);
      // skip if the entity has been used as seeds
      if (historicalSeeds.contains(entity1)) continue;
      // get the best translation pair
      // kmr possible gotcha
      String name1 = entity1.getName().toString();
      EntityList translations = translator.getTargets(name1);
      Entity entity2 = getBestMatch(translations, entities2, feature);
      if (entity2 == null) continue;
      // kmr possible gotcha
      String name2 = entity2.getName().toString();
      // store the translation pair for future use
//      transPair.add(name1, name2);
      log.info("--> " + (i+1) + ". \"" + name1 + "\" translates to \"" + name2 + "\"");
      seeds.add(entity1);
      if (seeds.size() >= numPossibleSeeds)
        return seeds;
    }
    log.warn("!!! Could not find matching translations... expanded set is too small?!");
    return null;
  }

  private Entity getBestMatch(EntityList transList, EntityList inputList, Object inputFeature) {
    if (transList == null || inputList == null || inputFeature == null)
      return null;

    Entity bestInputEnt = null;
    double maxWeight = Double.MIN_VALUE;

    for (int i = 0; i < Math.min(numTopTrans, transList.size()); i++) {
      Entity transEntity = transList.get(i);
      double transWeight = transEntity.getWeight(Proxistat.PROXISCORE);
      
      for (int j = 0; j < inputList.size(); j++) {
        if ( (double)(j+1) / inputList.size() > pctTopEntities) break;
        Entity inputEntity = inputList.get(j);
        // kmr possible gotcha
        double similarity = StringEditDistance.levenshtein(inputEntity.getName().toString(), transEntity.getName().toString());
        if (similarity < simThreshold) continue;

        double inputWeight = inputEntity.getWeight(inputFeature);
        log.info(transEntity.getName() + "(" + Math.round(transWeight*100) + "%)" + 
                 " --> " + (j+1) + ". " + inputEntity.getName() + "(" + Math.round(inputWeight*100) + "%)");
        double weight = transWeight * inputWeight * similarity;
        if (weight > maxWeight) {
          bestInputEnt = inputEntity;
          maxWeight = weight;
        }
      }
    }
    return bestInputEnt;
  }

  private Evaluator toTargetEval(Evaluator sourceEval) {
    if (sourceEval == null) return null;
    String goldFilePath = sourceEval.getGoldFile().getPath();
    goldFilePath = goldFilePath.replace(sourceLangID, targetLangID);
    File targetGoldFile = new File(goldFilePath);
    Evaluator targetEval = null;
    if (targetGoldFile.exists()) {
      targetEval = new Evaluator();
      targetEval.loadGoldFile(targetGoldFile);
    }
    return targetEval;
  }

  private SetExpander toTargetExpander(SetExpander sourceExpander) {
    SetExpander targetExpander;
    if (sourceExpander instanceof Seal) {
      targetExpander = new Seal();
    } else if (sourceExpander instanceof GoogleSets) {
      targetExpander = new GoogleSets();
    } else {
      log.fatal("Error: Could not understand: " + sourceExpander.getClass().getSimpleName());
      return null;
    }
    return targetExpander;
  }
}
