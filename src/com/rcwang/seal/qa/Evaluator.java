/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.GoogleSets;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SetExpander;
import com.rcwang.seal.fetch.WebManager;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.StringFactory;

public class Evaluator {

  public static Logger log = Logger.getLogger(Evaluator.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  /************** Evaluation Parameters ******************/
  public static enum HybridMethod {EPHYRA, SEAL, INTERSECT, UNION, METHOD1}
  // true: enable fetching documents from the web, false otherwise
  static { WebManager.setFetchFromWeb(true); }
  // true: disable expanding seeds using SEAL/GSets, false otherwise
  public static boolean disableExpansion = false;
  // true: read serialized statistics (if exist), false otherwise
  public static boolean readSerializedStats = false;
  // true: write serialized statistics to file, false otherwise
  public static boolean writeSerializedStats = false;
  // true: use Google Sets instead of SEAL
  public static boolean useGoogleSets = false;
  // number of least frequent hint words (keywords) to use
  // (3 hints with quote is the best so far)
  public static int numHints = 3;
  // true: add quotations around each hint phrase
  public static boolean hintsQuoted = false;
  // true: randomly select N true answers to use as seeds
  // false: use the top N answers from QA system as seeds
  public static boolean getSeedsFromTrueAnswers = false;
  // set to -1 to disable (use Ephyra)
  public static int topSystem = gv.getTopSystem();
  // true: use the new graph walk
  // false: use the CCL (common context length)
  public static boolean useNewGraphWalk = true;
  // true: save expanded results to XML file using trecID as filename
  // false: not to save results
  public static boolean saveExpansionToXML = false;
  // true: send results via e-mail
  public static boolean mailResults = false;
  /******************************************************/
  
  public static final String[] TOP_SYSTEM_TREC_13 = new String[] {
    "lcc1", "NUSCHUA2", "uwbqitekat04",
  };
  public static final String[] TOP_SYSTEM_TREC_14 = new String[] {
    "lcc05", "NUSCHUA3", "IBM05C3PD",
  };
  public static final String[] TOP_SYSTEM_TREC_15 = new String[] {
    "lccPA06", "cuhkqaepisto", "NUSCHUAQA1", "FDUQAT15A", "QACTIS06C", "ISL3"
  };
  public static String serialDirName = topSystem > 0 ? TOP_SYSTEM_TREC_15[topSystem-1] : "serials";
  public static String serialFileName = "qid%d.stats.ser";
  public static String baseDirName = "/afs/cs/user/rcwang/seal/qa-data";
  public static String ephyraDirName = "ephyra-output.2008-01-24";
  public static String ansFileName = "trec%d/trec%d_list_answers.txt";
  public static String ansPatFileName = "trec%d/trec%dpatterns_list";

  public static final int MAX_SEAL_RESULTS = 1000;
  public static final double DEFAULT_WEIGHT = 1e-3;
  
  private Map<String, List<Pattern>> qidPatternMap;
  private Map<String, List<String>> qidAnswerMap;
  private Random random = new Random(0);
  private Feature rankingMethod;
  private InputLoader inputLoader;
  private Seal seal;
  private GoogleSets gSets;
  
  public static File getAnsFile(int trecID) {
    return new File(baseDirName, ansFileName.replace("%d", Integer.toString(trecID)));
  }
  
  public static File getAnsPatternFile(int trecID) {
    return new File(baseDirName, ansPatFileName.replace("%d", Integer.toString(trecID)));
  }
  
  public static File getEphyraXML(String qID) {
    File xmlDir = new File(baseDirName, ephyraDirName);
    return new File(xmlDir, qID + ".xml");
  }
  
  public static File getSerialFile(int trecID) {
    return new File(baseDirName, serialFileName.replace("%d", Integer.toString(trecID)));
  }
  
  public static File getSerialFile(String qID) {
    File serialDir = new File(baseDirName, serialDirName);
    if (!serialDir.isDirectory())
      serialDir.mkdirs();
    return new File(serialDir, serialFileName.replace("%d", qID));
  }
  
  public static File getInputFile(int trecID) {
    if (topSystem < 1) return null;
    String systemID = null;
    switch (trecID) {
      case 13: systemID = TOP_SYSTEM_TREC_13[topSystem-1]; break;
      case 14: systemID = TOP_SYSTEM_TREC_14[topSystem-1]; break;
      case 15: systemID = TOP_SYSTEM_TREC_15[topSystem-1]; break;
      default: log.error("Unknown trecID: " + trecID);
    }
    if (systemID == null) return null;
    return new File(baseDirName, "trec" + trecID + "/input/input." + systemID);
  }
  
  private static String toString(List<String> strList, boolean isQuoted) {
    StringBuffer buf = new StringBuffer();
    for (String s : strList) {
      if (isQuoted)
        buf.append("\"").append(s).append("\" ");
      else buf.append(s).append(" ");
    }
    return buf.toString().trim();
  }
  
  public Evaluator() {
    qidPatternMap = new LinkedHashMap<String, List<Pattern>>();
    qidAnswerMap = new HashMap<String, List<String>>();
    rankingMethod = useNewGraphWalk ? Feature.GWW : Feature.WLW;
    inputLoader = new InputLoader();
    seal = new Seal();
    gSets = new GoogleSets();
    
    // clean up result directory
    if (saveExpansionToXML)
      Helper.recursivelyRemove(gv.getResultDir());
  }
  
  public void clear() {
    qidPatternMap.clear();
    qidAnswerMap.clear();
    seal.clear();
    gSets.clear();
  }
  
  public List<Stat[]> evaluate(List<String> qIDs) {
    List<Stat[]> statsList = new ArrayList<Stat[]>();
    
    for (String qID : qIDs) {
      if (!qidPatternMap.containsKey(qID)) {
        log.error("Answers for Question ID: " + qID + " is missing!");
        continue;
      }
      
      Stat[] stats;
      File serialFile = getSerialFile(qID);
      if (readSerializedStats && serialFile != null && serialFile.exists()) {
        stats = (Stat[]) Helper.loadSerialized(serialFile, Object.class);
      } else {
        stats = evaluate(qID);
        if (stats == null)
          continue;
        if (writeSerializedStats)
          Helper.saveSerialized(stats, serialFile, true);
      }
      statsList.add(stats);
      
//      double ephyra = 0, seal = 0, hybrid = 0;
      for (HybridMethod method : HybridMethod.values()) {
        Stat stat = stats[method.ordinal()];
        log.info("Result of using " + method + " method:");
        stat.setAbsThreshold(Experiment.getThreshold(method, true));
        stat.setRelThreshold(Experiment.getThreshold(method, false));
        log.info(stat);
        
        // added for paired t-test 09/12/2008
        /*if (method == HybridMethod.EPHYRA)
          ephyra = stat.getF1At(0, false);
        if (method == HybridMethod.SEAL)
          seal = stat.getF1At(gv.getSealThreshold(), false);
        if (method == HybridMethod.INTERSECT)
          hybrid = stat.getF1At(gv.getInterThreshold(), false);*/
      }
//      Helper.writeToFile(new File(gv.getEvalDir(), "t-test" + gv.getTopSystem() + "-2.xls"), qID + "\t" + ephyra + "\t" + seal + "\t" + hybrid + "\n", true);
      
      // clean up the item names to save memory
      for (Stat stat : stats)
        stat.clearItems();
      StringFactory.clear();
    }
    return statsList;
  }
  
  /**
   * Evaluate Ephyra's results and perform set expansion using the web
   * @param qID the question ID
   * @return an array of {@link Stat}
   */
  public Stat[] evaluate(String qID) {
    // read data from Ephyra's output
    File xmlFile = getEphyraXML(qID);
    if (xmlFile == null || !xmlFile.exists()) {
      log.error("Could not find Ephyra's output for QID " + qID + ": " + xmlFile);
      return null;
    }
    EphyraXML ephyraXML = EphyraLoader.load(xmlFile);
    if (topSystem > 0)
      ephyraXML.setAnswers(inputLoader.get(qID), 1.0);
    log.info(ephyraXML);
    
    // intializing statistics
    int numTrueAnswers = getNumTrueAnswers(qID);
    if (numTrueAnswers == 0) {
      log.error("Does not have true answer keys for QID " + qID + ". Skipping...");
      return null;
    }
    Stat[] stats = new Stat[HybridMethod.values().length];
    for (int i = 0; i < HybridMethod.values().length; i++) {
      stats[i] = new Stat();
      stats[i].setQID(qID);
      stats[i].setNumTrueAnswers(numTrueAnswers);
    }

    List<ItemWeight> seeds;
    if (getSeedsFromTrueAnswers) {
      // get random seeds from the true answer set
      List<String> trueAnswers = getTrueAnswers(qID);
      Collections.shuffle(trueAnswers, random);
      int minNumSeeds = Math.min(trueAnswers.size(), gv.getNumTrueSeeds());
      seeds = ItemWeight.parseStringList(trueAnswers.subList(0, minNumSeeds), 1.0);
    } else {
      // get seeds from the QA answer set
      int minNumSeeds = Math.min(ephyraXML.getNumAnswers(), gv.getNumTrueSeeds());
      seeds = ephyraXML.getTopAnswers(minNumSeeds);
    }
    
    // filter out question keywords from SEAL's answer set
    seal.addStopwords(ephyraXML.getKeywordsString(), true);
//    seal.setHasNoisySeeds(false); // TODO: to test QA systems 6/27/2008
    seal.setHasNoisySeeds(!getSeedsFromTrueAnswers);

    // use hinted expansion
    List<String> hints = new ArrayList<String>();
    if (numHints > 0) {
      List<String> keywords = ephyraXML.getKeywordsString();
      int n = Math.min(keywords.size(), numHints);
      hints.addAll(keywords.subList(keywords.size()-n, keywords.size()));
    }

    List<ItemWeight> sealResults;
    if (disableExpansion) {
      sealResults = seeds;
    } else {
      /*if (ephyraXML.getNumAnswers() <= gv.getNumTrueSeeds()) // TODO: this is good
        sealResults = ephyraXML.getAnswers();
      else {*/
        sealResults = expand(seeds, ephyraXML.getAnswers(), hints, qID);
        if (sealResults.size() < ephyraXML.getNumAnswers())
          sealResults = ephyraXML.getAnswers();
//      }
    }

    for (HybridMethod method : HybridMethod.values()) {
      Stat stat = stats[method.ordinal()];
      List<ItemWeight> resultsList = null;
      switch (method) {
        case EPHYRA: resultsList = ephyraXML.getAnswers(); break;
        case SEAL: resultsList = sealResults; break;
        case INTERSECT: resultsList = ItemWeight.intersect(ephyraXML.getAnswers(), sealResults); break;
        case UNION: resultsList = ItemWeight.union(ephyraXML.getAnswers(), sealResults, DEFAULT_WEIGHT); break;
        case METHOD1: resultsList = ItemWeight.union(ephyraXML.getAnswers(), sealResults, 1.0);
        default: log.error("Unknown hybrid method: " + method);
      }
      checkAnswer(resultsList, stat);
    }
    return stats;
  }
  
  public int getNumTrueAnswers(String trecID) {
    List<Pattern> patternList = qidPatternMap.get(trecID);
    return (patternList == null) ? 0 : patternList.size();
  }
  
  public Set<String> getQIDs() {
    return qidPatternMap.keySet();
  }
  
  public List<Pattern> getTrueAnswerPatterns(String trecID) {
    return qidPatternMap.get(trecID);
  }
  
  public List<String> getTrueAnswers(String trecID) {
    return qidAnswerMap.get(trecID);
  }
  
  public Set<String> loadAnswerPatterns(File file) {
    String content = Helper.readFile(file);
    String[] lines = content.split("\n");
    Set<String> qids = new LinkedHashSet<String>();
    for (String line : lines) {
      String[] pairs = line.split("\\s+", 2);
      if (pairs.length != 2)
        continue;
      String qid = pairs[0];
      // fix the dot problem in the trec pattern list
//      pairs[1] = pairs[1].replaceAll("\\.", "\\.");
      Pattern p = Pattern.compile("(?i)" + pairs[1]);
      List<Pattern> patternList = qidPatternMap.get(qid);
      if (patternList == null) {
        patternList = new ArrayList<Pattern>();
        qidPatternMap.put(qid, patternList);
      }
      patternList.add(p);
      qids.add(qid);
    }
    return qids;
  }
  
  public Set<String> loadAnswers(int trecID) {
    inputLoader.load(getInputFile(trecID));
    loadAnswerStrings(getAnsFile(trecID));
    return loadAnswerPatterns(getAnsPatternFile(trecID));
  }
  
  public Set<String> loadAnswerStrings(File file) {
    String content = Helper.readFile(file);
    String[] lines = content.split("\n");
    String qid = null;
    String s = "# ", q = "Q:";
    Set<String> qids = new LinkedHashSet<String>();
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith(s)) {
        qid = line.substring(s.length()).trim();
        continue;
      } else if (line.startsWith(q) || line.length() == 0) {
        continue;
      }
      List<String> answerList = qidAnswerMap.get(qid);
      if (answerList == null) {
        answerList = new ArrayList<String>();
        qidAnswerMap.put(qid, answerList);
      }
      String[] e = line.split(","); // added 02/19/2008
      answerList.add(e[0].trim());  // added 02/19/2008
//      answerList.add(line);
      qids.add(qid);
    }
    return qids;
  }

  private void checkAnswer(List<ItemWeight> results, Stat stat) {
    List<Pattern> patterns = getTrueAnswerPatterns(stat.getQID());
    // get a temp list of patterns since we will remove its entries
    List<Pattern> tempPatterns = new ArrayList<Pattern>(patterns);
    
    for (ItemWeight iw : results) {
      String entity = iw.getItem();
      if (entity == null) continue;
      boolean isCorrect = false;
      for (Iterator<Pattern> i = tempPatterns.iterator(); i.hasNext();) {
        Pattern p = i.next();
        if (p.matcher(entity).matches()) {
          // if matches, remove the pattern so it will not match others
          i.remove();
          isCorrect = true;
          break;
        }
      }
      stat.addAnswer(iw, isCorrect);
    }
  }
  
  private List<ItemWeight> expand(List<ItemWeight> seedWeights, List<ItemWeight> ansWeights, List<String> hints, String id) {
    if (seedWeights.isEmpty() || ansWeights.isEmpty())
      return new ArrayList<ItemWeight>();
    EntityList seeds = new EntityList(ItemWeight.toItemList(seedWeights));
    EntityList ans = new EntityList(ItemWeight.toItemList(ansWeights));
    
    if (useGoogleSets) {
      gSets.expand(seeds);
    } else {
      seal.setFeature(rankingMethod);
      seal.expand(ans, seeds, toString(hints, hintsQuoted));
//      seal.assignWeights(rankingMethod);
    }
    
    SetExpander expander = useGoogleSets ? gSets : seal;
    List<Entity> entityList = expander.getEntities();
    if (entityList.size() > MAX_SEAL_RESULTS)
      entityList = entityList.subList(0, MAX_SEAL_RESULTS);
    List<ItemWeight> resultList = ItemWeight.parseEntityInfo(entityList);
    
    // make sure the results contains at least the seeds
    if (saveExpansionToXML)
      expander.saveResults(id);
    expander.clear();
    return resultList;
  }
}
