/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.eval;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.GoogleSets;
import com.rcwang.seal.expand.IterativeSeal;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SeedSelector;
import com.rcwang.seal.expand.SetExpander;
import com.rcwang.seal.expand.SeedSelector.SeedingPolicy;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
//import com.rcwang.seal.util.Mailer; //wwc

public class IterativeExperiment {
  
  public static final String FROM_EMAIL_ADDR = "Iterative Results <exp.results@cmu.edu>";
  public static final String TO_EMAIL_ADDR = "Richard Wang <rcwang@gmail.com>";
  public static final int NUM_TO_PRINT = 100;
  public static Logger log = Logger.getLogger(IterativeExperiment.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  protected Evaluator evaluator;
  protected Feature feature;
  protected SeedingPolicy policy;
  protected int numTrueSeeds;
  protected int numPossibleSeeds;
  protected int numExpansions;
  protected int numTrials;
  protected boolean useGoogleSets;
  
  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    IterativeExperiment experiment = new IterativeExperiment();
    experiment.setUseGoogleSets(gv.isUseGoogleSets());
    experiment.setNumTrials(gv.getNumTrials());
    experiment.setNumExpansions(gv.getNumExpansions());
    experiment.setPolicy(gv.getPolicy(), gv.getNumTrueSeeds(), gv.getNumPossibleSeeds());

//    WebManager.setFetchFromWeb(false);
    
    List<Feature> features = gv.getExpFeatures();
    if (features == null)
      features = Arrays.asList(Feature.values());
    
    for (Feature feature : features) {
      gv.setFeature(feature);
      experiment.setFeature(feature);
      experiment.run();
    }
    Helper.printElapsedTime(startTime);
  }
  
  private static void mailResults(String experimentID, String content) {
      /* wwc
    String subject = Helper.extract(content, "Average", "\n");
    if (subject == null) return;
    subject = experimentID + ": " + subject.replaceAll("\\s+", " ").trim();
    Mailer.sendEmail(FROM_EMAIL_ADDR, TO_EMAIL_ADDR, subject, content);    
      */
  }
  
  public IterativeExperiment() {
    evaluator = new Evaluator();
  }
  
  public Feature getFeature() {
    return feature;
  }
  
  public int getNumExpansions() {
    return numExpansions;
  }

  public int getNumPossibleSeeds() {
    return numPossibleSeeds;
  }

  public int getNumTrials() {
    return numTrials;
  }

  public int getNumTrueSeeds() {
    return numTrueSeeds;
  }

  public SeedingPolicy getPolicy() {
    return policy;
  }

  public void run() {
    long startTime = System.currentTimeMillis();
    List<File> goldFiles;
    goldFiles = Evaluator.getGoldFiles(gv.getDataDir());
    File evalDir = Helper.createDir(gv.getEvalDir());
    String evalFileName = getExperimentID() + "_" + Helper.getUniqueID() + Evaluator.EVAL_FILE_SUFFIX;
    File evalFile = new File(evalDir, evalFileName);
    Helper.recursivelyRemove(gv.getResultDir());

    StringBuffer buf = new StringBuffer("Dataset\t");
    for (int i = 0; i < numExpansions; i++)
      buf.append("E").append(i+1).append("\t");
    buf.setCharAt(buf.length()-1, '\n');
    Helper.writeToFile(evalFile, buf.toString());
    int bufStartIndex = buf.length();

    double[] maps = new double[numExpansions];
    int goldFileCounts = 0;
    for (File goldFile : goldFiles) {
      String dataName = Evaluator.getDataName(goldFile);
      String langID = Evaluator.getLangID(goldFile);
      gv.setLangID(langID);

      if (!isExpDataset(langID, dataName)) {
        log.warn(dataName + " (" + langID + ") is not interesting, skipping...");
        continue;
      }
      evaluator.loadGoldFile(goldFile);
      goldFileCounts++;
      
      for (int trial = 0; trial < numTrials; trial++) {
        log.info(Helper.repeat('-', 80));
        log.info("Evaluating trial " + (trial+1) + " out of " + numTrials + 
                 " on " + dataName + " (" + langID + ")...");
        List<EvalResult> evalResults = evaluate(trial);

        buf.append(langID).append(".").append(dataName).append("\t");
        double map = 0;
        for (int expansion = 0; expansion < numExpansions; expansion++) {
          if (expansion < evalResults.size())
            map = evalResults.get(expansion).meanAvgPrecision;
          maps[expansion] += map;
          buf.append(map).append("\t");
        }
        buf.setCharAt(buf.length()-1, '\n');
        Helper.writeToFile(evalFile, buf.substring(bufStartIndex), true);
        bufStartIndex = buf.length();
      }
    }
    
    buf.append("Average\t");
    int totalTrials = goldFileCounts * numTrials;
    for (int i = 0; i < numExpansions; i++)
      buf.append(maps[i] / totalTrials).append("\t");
    buf.setCharAt(buf.length()-1, '\n');
    
    log.info("Writing evaluation results to: " + evalFile);
    Helper.writeToFile(evalFile, buf.substring(bufStartIndex), true);
    mailResults(getExperimentID(), buf.toString());
    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }
  
  public void setFeature(Feature feature) {
    this.feature = feature;
  }
  
  public void setNumExpansions(int numExpansions) {
    this.numExpansions = numExpansions;
  }

  public void setNumTrials(int numTrials) {
    this.numTrials = numTrials;
  }

  public void setPolicy(SeedingPolicy policy, int numTrueSeeds, int numPossibleSeeds) {
    this.policy = policy;
    this.numTrueSeeds = numTrueSeeds;
    this.numPossibleSeeds = numPossibleSeeds;
  }

  public void setUseGoogleSets(boolean useGoogleSets) {
    this.useGoogleSets = useGoogleSets;
  }

  public boolean useGoogleSets() {
    return useGoogleSets;
  }

  protected SetExpander makeSetExpander() {
      String className = gv.getSetExpander();
      try {
          Class clazz = null;
          try {
              clazz = Class.forName(className);
          } catch (ClassNotFoundException e) {
              try {
                  clazz = Class.forName("com.rcwang.seal.expand."+className);
              } catch (ClassNotFoundException e2) {
                  log.fatal("Make sure \"setExpander\" is set to a valid class name in seal.properties.",e);
                  return null;
              }
          } 
          SetExpander s = (SetExpander) clazz.newInstance();
          return s;
      }catch (InstantiationException e) {
          log.fatal("Couldn't create a "+className,e);
      } catch (IllegalAccessException e) {
          log.fatal("Couldn't create a "+className,e);
      }
      return null;
  }
  
  protected List<EvalResult> evaluate(int trial) {
    SeedSelector seedSelector = new SeedSelector(policy);
    seedSelector.setFeature(feature);
    seedSelector.setNumSeeds(numTrueSeeds, numPossibleSeeds);
    EntityList initialSeeds = new EntityList(evaluator.getFirstMentions());
    
    seedSelector.setTrueSeeds(initialSeeds);
    seedSelector.setRandomSeed(trial);

    IterativeSeal iSeal = new IterativeSeal();
    iSeal.setEvaluator(evaluator);
    iSeal.setNumExpansions(numExpansions);
    SetExpander expander = makeSetExpander();
    EntityList entities = iSeal.expand(expander, null, seedSelector);
    log.info(entities.toDetails(NUM_TO_PRINT, feature));
    return iSeal.getEvalResultList();
  }

  private String getExperimentID() {
    String[] evalFileNameArr = new String[] {
        policy.toString().toLowerCase(),
        feature.toString().toLowerCase(),
        Integer.toString(numTrueSeeds),
        Integer.toString(numPossibleSeeds),
        Integer.toString(numExpansions)
    };
    return Helper.merge(evalFileNameArr, ".");
  }

  private boolean isExpDataset(String langID, String dataName) {
    String s = langID + "." + dataName;
    s = s.toLowerCase().trim();
    List<String> datasets = gv.getExpDatasets();
    return datasets == null || datasets.contains(s);
  }
}
