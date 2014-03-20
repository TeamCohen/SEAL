package com.rcwang.seal.eval;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.translation.Proximator;
import com.rcwang.seal.translation.TranslationPair;
import com.rcwang.seal.util.Helper;

public class TranslationExperiment {

  public static Logger log = Logger.getLogger(TranslationExperiment.class);
  // true to force to translate the source on the fly (ignoring the given targets)
  public static final boolean IS_FORCE_TO_TRANSLATE = true;
  public static final String DATADIR = "/afs/cs/user/rcwang/seal/data/unary";
  public static final String[] DATASETS = new String[] {
    "classic-disney", 
    "constellations", 
    "countries", 
    "mlb-teams", 
    "nba-teams", 
    "nfl-teams", 
    "popular-car-makers", 
    "us-presidents", 
    "us-states"
  };
  
  private Map<String, Set<String>> transMap;
  private TranslationPair transPair;
  private Proximator proximator;
  
  public static void main(String args[]) {
    File inputFile = new File(args[0]);
    String sourceLangID = args[1];
    String targetLangID = args[2];
    
    TranslationExperiment te = new TranslationExperiment();
    te.loadTransPair(inputFile, targetLangID);
    
    for (String dataset : DATASETS) {
      File source = new File(new File(DATADIR, sourceLangID), dataset + Evaluator.GOLD_FILE_SUFFIX);
      File target = new File(new File(DATADIR, targetLangID), dataset + Evaluator.GOLD_FILE_SUFFIX);
      te.loadEval(source, target);
    }
    
    te.evaluate();
  }
  
  private static HashSet<String> toStringSet(String s) {
    HashSet<String> set = new HashSet<String>();
    if (s == null) return set;
    String[] entries = s.split("\t");
    for (String entry : entries)
      set.add(entry.trim().toLowerCase());
    return set;
  }
  
  public TranslationExperiment() {
    transMap = new HashMap<String, Set<String>>();
    transPair = new TranslationPair();
  }
  
  public void evaluate() {
    int numCorrect = 0;
    for (int i = 0; i < transPair.size(); i++) {
      String[] pair = transPair.get(i);
      Set<String> targetSet = transMap.get(pair[0]);
      if (targetSet == null || !targetSet.contains(pair[1])) {
        log.info("Incorrect: " + pair[0] + " => " + pair[1]);
        continue;
      }
      numCorrect++;
    }
    double precision = (double) numCorrect / transPair.size();
    log.info("# of Correct Translations: " + numCorrect);
    log.info("Total # of Translations: " + transPair.size());
    log.info("Translation Precision: " + precision);
  }
  
  public void loadEval(File source, File target) {
    String sourceContents = Helper.readFile(source);
    if (sourceContents == null) return;
    
    String targetContents = Helper.readFile(target);
    if (targetContents == null) return;

    String[] sourceLines = sourceContents.split("\n");
    String[] targetLines = targetContents.split("\n");
    
    if (sourceLines.length != targetLines.length) {
      log.error("Error: Size of source and target sets are different!");
      return;
    }
    
    for (int i = 0; i < sourceLines.length; i++) {
      String sourceLine = sourceLines[i];
      String targetLine = targetLines[i];
      
      Set<String> sourceSet = toStringSet(sourceLine);
      Set<String> targetSet = toStringSet(targetLine);
      
      insertMapping(sourceSet, targetSet);
      insertMapping(targetSet, sourceSet);
    }
  }
  
  public String translate(String s, String targetLangID) {
//    if (proximator == null)
    proximator = new Proximator(targetLangID);
    EntityList entities = proximator.getTargets(s);
    return entities.size() > 0 ? entities.get(0).getOriginal() : null;
  }
  
  private void insertMapping(Set<String> sourceSet, Set<String> targetSet) {
    for (String s : sourceSet) {
      Set<String> existSet = transMap.get(s);
      if (existSet == null) {
        transMap.put(s, targetSet);
      } else {
        existSet.addAll(targetSet);
      }
    }
  }
  
  private void loadTransPair(File file, String targetLangID) {
    String content = Helper.readFile(file);
    if (content == null) return;
    String[] contents = content.split("\n");

    for (String line : contents) {
      String[] entries = line.split("\t");
      if (entries.length == 0) continue;
      String source = entries[0].trim();
      if (entries.length == 1 || IS_FORCE_TO_TRANSLATE) {
        // only one entry, requires translation
        String target = translate(source, targetLangID);
        log.info(source + " translates to " + target);
        if (target != null)
          target = target.toLowerCase();
        transPair.add(source, target);
      } else {
        // two entries
        String target = entries[1].trim().toLowerCase();
        transPair.add(source, target);
      }
    }
  }
}
