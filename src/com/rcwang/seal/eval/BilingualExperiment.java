package com.rcwang.seal.eval;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SeedSelector;
import com.rcwang.seal.translation.BilingualSeal;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
//import com.rcwang.seal.util.Mailer; //wwc

public class BilingualExperiment {
  
  public static Logger log = Logger.getLogger(BilingualExperiment.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final String FROM_EMAIL_ADDR = "Bilingual Results <exp.results@cmu.edu>";
  public static final String TO_EMAIL_ADDR = "Richard Wang <rcwang@gmail.com>";

  private static String sourceLangID = gv.getLangID();
  private static String targetLangID = gv.getLangID2();
  private static boolean useTranslation = true;
  
  public static void main(String args[]) {
     BilingualExperiment.run();
  }
  
  public static void run() {
    if (sourceLangID == null || targetLangID == null) {
      log.fatal("Source and/or target language is not defined!");
      return;
    } else if (sourceLangID.equals(targetLangID)) {
      log.fatal("Source and target languages (" + sourceLangID + ") must be different!");
      return;
    }
    
    // get evaluation file
    File evalDir = Helper.createDir(gv.getEvalDir());
    String evalFileName = getExperimentID() + "_" + Helper.getUniqueID() + Evaluator.EVAL_FILE_SUFFIX;
    File evalFile = new File(evalDir, evalFileName);
    
    // prepare output buffer
    StringBuffer buf = new StringBuffer("Dataset\t");
    for (int i = 0; i < gv.getNumExpansions(); i++)
      buf.append("E").append(i+1).append("\t");
    buf.setCharAt(buf.length()-1, '\n');
    int bufStartIndex = buf.length();
    
    // preparing experiment
    Helper.recursivelyRemove(gv.getResultDir());
    Evaluator evaluator = new Evaluator();
    double[] maps = new double[gv.getNumExpansions()];
    long startTime = System.currentTimeMillis();
    
    // load each dataset
    for (String dataName : gv.getExpDatasets()) {
      File sourceGoldFile = toGoldFile(dataName, sourceLangID);
      if (sourceGoldFile == null) continue;
      evaluator.loadGoldFile(sourceGoldFile);
      
      // conduct trials
      for (int trialID = 0; trialID < gv.getNumTrials(); trialID++) {
        log.info("-----------------------------------------------------");
        log.info("Evaluating trial " + (trialID+1) + "/" + gv.getNumTrials() + 
                 " on " + dataName + " (" + sourceLangID + " <--> " + targetLangID + ")...");
        
        List<EvalResult> evalResults = evaluate(evaluator, trialID);
        if (evalResults == null) continue;

        // process evaluation results
        buf.append(sourceLangID).append("/").append(targetLangID).append(".").append(dataName).append("\t");
        double map = 0;
        for (int expansionID = 0; expansionID < gv.getNumExpansions(); expansionID++) {
          if (expansionID < evalResults.size())
            map = evalResults.get(expansionID).meanAvgPrecision;
          maps[expansionID] += map;
          buf.append(map).append("\t");
        }
        buf.setCharAt(buf.length()-1, '\n');
        Helper.writeToFile(evalFile, buf.substring(bufStartIndex), true);
        bufStartIndex = buf.length();
      }
    }
    
    // finishing the experiment
    buf.append("Average\t");
    int totalTrials = gv.getExpDatasets().size() * gv.getNumTrials();
    for (int i = 0; i < gv.getNumExpansions(); i++)
      buf.append(maps[i] / totalTrials).append("\t");
    buf.setCharAt(buf.length()-1, '\n');
    Helper.writeToFile(evalFile, buf.substring(bufStartIndex), true);
    mailResults(getExperimentID(), buf.toString());
    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }

  private static List<EvalResult> evaluate(Evaluator evaluator, int trialID) {
    SeedSelector selector = new SeedSelector(gv.getPolicy());
    selector.setNumSeeds(gv.getNumTrueSeeds(), gv.getNumPossibleSeeds());
    selector.setFeature(gv.getFeature());
    selector.setTrueSeeds(evaluator.getFirstMentions());
    selector.setRandomSeed(trialID);
    
    BilingualSeal biSeal = new BilingualSeal();
    biSeal.setEval(evaluator);
    biSeal.setLangID(sourceLangID, targetLangID);
    biSeal.setNumExpansions(gv.getNumExpansions());
    biSeal.setUseTranslation(useTranslation);
    biSeal.expand(new Seal(), selector);
    return biSeal.getEvalResultList();
  }
  
  private static String getExperimentID() {
    String[] evalFileNameArr = new String[] {
        gv.getFeature().toString().toLowerCase(),
        gv.getLangID().toLowerCase(),
        gv.getLangID2().toLowerCase(),
        useTranslation ? Integer.toString(1) : Integer.toString(0),
        Integer.toString(gv.getNumResults()),
    };
    return Helper.merge(evalFileNameArr, ".");
  }
  
  private static void mailResults(String experimentID, String content) {
      /* wwc
    String subject = Helper.extract(content, "Average", "\n");
    if (subject == null) return;
    subject = experimentID + ": " + subject.replaceAll("\\s+", " ").trim();
    Mailer.sendEmail(FROM_EMAIL_ADDR, TO_EMAIL_ADDR, subject, content);    
      */
  }

  private static File toGoldFile(String dataset, String langID) {
    File goldFile = new File(new File(gv.getDataDir(), langID), dataset + Evaluator.GOLD_FILE_SUFFIX);
    if (!goldFile.exists()) {
      log.fatal("Evaluation data " + goldFile + " could not be found!");
      return null;
    }
    return goldFile;
  }
}
