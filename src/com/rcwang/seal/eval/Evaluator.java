/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.util.Helper;

public class Evaluator {
  
  public static class GoldEntity {
    public Set<String> synonyms = new LinkedHashSet<String>();
  }
  
  public static Logger log = Logger.getLogger(Evaluator.class);
  public static final String EVAL_FILE_SUFFIX = ".xls"; // evaluation results
  public static final String GOLD_FILE_SUFFIX = ".txt"; // evaluation datasets
  public static final double DEFAULT_THRESHOLD = 0.8;
  
  private List<GoldEntity> goldList;
  private Set<String> goldSynonyms;
  private File goldFile;

  public static double computeF1(double prec, double recall) {
    return (prec + recall == 0) ? 0 : (2 * prec * recall) / (prec + recall);
  }
  
  public static String getLangID(File goldFile) {
    if (goldFile == null) return null;
    File parentFile = goldFile.getParentFile();
    if (parentFile == null) return null;
    return parentFile.getName();
  }
  
  public static String getDataName(File goldFile) {
    if (goldFile == null) return null;
    String name = goldFile.getName();
    if (name.endsWith(GOLD_FILE_SUFFIX))
      name = name.substring(0, name.length()-GOLD_FILE_SUFFIX.length());
    return name;
  }
  
  public static List<String> getFirstMentions(File goldFile) {
    List<GoldEntity> goldList = new ArrayList<GoldEntity>();
    Evaluator.loadGoldFile(goldFile, goldList);
    return getFirstMentions(goldList);
  }
  
  public static List<File> getGoldFiles(File dataDir) {
    return getGoldFiles(dataDir, null);
  }
  
  public static List<File> getGoldFiles(File dataDir, String langID) {
    File[] langDirs = dataDir.listFiles();
    List<File> list = new ArrayList<File>();
    
    for (File langDir : langDirs) {
      if (!langDir.isDirectory()) continue;
      if (langID != null && !langDir.getName().equals(langID)) continue;
      File[] files = langDir.listFiles();
      for (File file : files)
        if (file.isFile() && file.getName().endsWith(GOLD_FILE_SUFFIX))
          list.add(file);
    }
    return list;
  }
  
  public static Set<String> getGoldSynonyms(File goldFile) {
    Set<String> allGoldSynonyms = new HashSet<String>();
    List<GoldEntity> goldList = new ArrayList<GoldEntity>();
    loadGoldFile(goldFile, goldList);
    for (GoldEntity ge : goldList)
      allGoldSynonyms.addAll(ge.synonyms);
    return allGoldSynonyms;
  }
  
  public static void loadGoldFile(File goldFile, List<GoldEntity> goldList) {
    if (goldList == null) return;
    goldList.clear();
    log.info("Loading gold file: " + goldFile);
    String content = Helper.readFile(goldFile);
    String[] lines = content.split("\n");

    for (String line : lines) {
      String[] synonyms = line.split("\t");
      GoldEntity ge = makeGoldEntity(synonyms);
      goldList.add(ge);
    }
    log.info("Number of entities in gold file: " + goldList.size());
  }

  public static EvalResult mergeEvalResults(EvalResult[] evalResults) {
    int numTrials = 0;
    for (EvalResult evalResult : evalResults)
      if (evalResult != null)
        numTrials++;
    EvalResult result = new EvalResult();
    for (EvalResult evalResult : evalResults) {
      if (evalResult == null)
        continue;
      mergeEvalResults(evalResult, result, numTrials);
      result.seeds += evalResult.seeds;
      result.method = evalResult.method;
      result.goldFile = evalResult.goldFile;
      result.threshold = evalResult.threshold;
      result.numGoldEntity = evalResult.numGoldEntity;
      result.numGoldSynonym = evalResult.numGoldSynonym;
    }
    return result;
  }
  
  private static List<String> getFirstMentions(List<GoldEntity> goldList) {
    List<String> names = new ArrayList<String>();
    for (GoldEntity ge : goldList)
      names.add(ge.synonyms.iterator().next());
    return names;
  }
  
  private static GoldEntity makeGoldEntity(String[] synonyms) {
    GoldEntity ge = new GoldEntity();
    if (synonyms == null || synonyms.length == 0) return ge;
    
    for (String synonym : synonyms) {
      synonym = synonym.toLowerCase().replace('-', ' ').trim(); // added 04/03/2008
      if (synonym.length() == 0) continue;
      ge.synonyms.add(synonym);
      
      // remove punctuations and add to synonyms
      String optionalChars = WrapperFactory.OPTIONAL_CHAR_STR;
      if (synonym.matches("^.+" + optionalChars + ".+$")) {
        String s = synonym.replaceAll(optionalChars, "").trim();
        if (s.length() > 0)
          ge.synonyms.add(s);
        s = synonym.replaceAll(optionalChars, " ").replaceAll("\\s+", " ").trim();
        if (s.length() > 0)
          ge.synonyms.add(s);
      }
      
      // if is English, do something (added 10/20/2008)
      if (synonym.matches(".*[a-zA-Z].*")) {
        if (synonym.endsWith("y")) {
          ge.synonyms.add(synonym.substring(0, synonym.length()-1) + "ies");
        } else {
          ge.synonyms.add(synonym + "s");
          ge.synonyms.add(synonym + "es");
          ge.synonyms.add("the " + synonym);
//          ge.synonyms.add("comet " + synonym);
        }
      }
    }
    return ge;
  }
  
  private static EvalResult mergeEvalResults(EvalResult fromResult, EvalResult toResult, int numTrials) {
    toResult.numResultsAboveThreshold += fromResult.numResultsAboveThreshold / numTrials;
    toResult.numCorrectEntity += fromResult.numCorrectEntity / numTrials;
    toResult.numCorrectSynonym += fromResult.numCorrectSynonym / numTrials;
    toResult.maxF1Precision += fromResult.maxF1Precision / numTrials;
    toResult.maxF1Recall += fromResult.maxF1Recall / numTrials;
    toResult.maxF1Threshold += fromResult.maxF1Threshold / numTrials;
    toResult.precision += fromResult.precision / numTrials;
    toResult.recall += fromResult.recall / numTrials;
    toResult.meanAvgPrecision += fromResult.meanAvgPrecision / numTrials;
    return toResult;
  }
  
  public Evaluator() {
    goldList = new ArrayList<GoldEntity>();
    goldSynonyms = new HashSet<String>();
  }

  public EvalResult evaluate(EntityList entityList) {
    return evaluate(entityList, DEFAULT_THRESHOLD);
  }
  
  public EvalResult evaluate(EntityList entityList, double threshold) {
    EvalResult eval = new EvalResult();
    if (goldList.size() == 0) {
      log.error("No gold answers loaded!");
      return eval;
    }
    double maxF1 = 0;
    double sumOfPrecisions = 0;
    int numResultAboveThreshold = 0;
    int numCorrectSynonym = 0;
    int numCorrectEntity = 0;
    boolean[] seenGold = new boolean[goldList.size()];

    // traverse through the entire result list
    for (int i = 0; i < entityList.size(); i++) {
      Entity entity = entityList.get(i);
      entity.setCorrect(0);
      
      // count the number of correct synonyms
      if (goldSynonyms.contains(entity.getName().toString()))
        numCorrectSynonym++;

      // get precision at current rank
      double precision = (double) numCorrectSynonym / (i+1);
      
      // count the number of correct entities
      for (int j = 0; j < goldList.size(); j++) {
        Set<String> goldSynonyms = goldList.get(j).synonyms;
        if (goldSynonyms.contains(entity.getName().toString())) {
          entity.setCorrect(seenGold[j] ? 2 : 1);
          if (seenGold[j]) continue;
        } else continue;
        
        // this is the first 'correct' synonym encountered for an entity
        sumOfPrecisions += precision;
        numCorrectEntity++;
        seenGold[j] = true;
        break;
      }
      // get recall and F1 at current rank
      double recall = (double) numCorrectEntity / goldList.size();
      double F1 = computeF1(precision, recall);

      if (F1 > maxF1) {
        eval.maxF1Precision = precision;
        eval.maxF1Recall = recall;
        eval.maxF1Threshold = entity.getScore();
        maxF1 = F1;
      }

      if (entity.getScore() >= threshold) {
        eval.precision = precision;
        eval.recall = recall;
        eval.numCorrectEntity = numCorrectEntity;
        eval.numCorrectSynonym = numCorrectSynonym;
        numResultAboveThreshold++;
      }
    }
    eval.threshold = threshold;
    eval.numResultsAboveThreshold = numResultAboveThreshold;
    eval.meanAvgPrecision = sumOfPrecisions / goldList.size();
    eval.numGoldEntity = goldList.size();
    eval.numGoldSynonym = goldSynonyms.size();
    eval.goldFile = goldFile;
    entityList.setEvalResult(eval);
    return eval;
  }
  
  public String getDataName() {
    return getDataName(goldFile);
  }
  
  public List<String> getFirstMentions() {
    return getFirstMentions(goldList);
  }
  
  public List<GoldEntity> getGoldEntityList() {
    return goldList;
  }
  
  public File getGoldFile() {
    return goldFile;
  }
  
  public Set<String> getGoldSynonyms() {
    return goldSynonyms;
  }

  public void loadGoldFile(File goldFile) {
    this.goldFile = goldFile;
    loadGoldFile(goldFile, goldList);
    goldSynonyms.clear();
    for (GoldEntity ge : goldList)
      goldSynonyms.addAll(ge.synonyms);
  }

}
