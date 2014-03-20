/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.rcwang.seal.asia.Asia;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
//import com.rcwang.seal.util.Mailer;  //wwc

public class AsiaExperiment {
  
  public static Logger log = Logger.getLogger(AsiaExperiment.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final String FROM_EMAIL_ADDR = "Member Results <exp.results@cmu.edu>";
  public static final String TO_EMAIL_ADDR = "Richard Wang <rcwang@gmail.com>";
//  public static final boolean USE_BACK_OFF = true;
//  public static final boolean REUSE_DOCUMENT = false;
  
  private Evaluator evaluator;
  private Feature feature;
  private int numBootstraps;
  
  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    AsiaExperiment experiment = new AsiaExperiment();
    experiment.setNumBootstraps(gv.getNumExpansions());

    List<Feature> features = gv.getExpFeatures();
    if (features == null)
      features = Arrays.asList(Feature.values());
    
    for (Feature feature : features) {
      experiment.setFeature(feature);
      experiment.run();
    }
    Helper.printElapsedTime(startTime);
  }
  
  private static String getCategory(String langID, String dataName) {
    Map<String, String> categoryMap = new HashMap<String, String>();
    
    /**************** English Categories *****************/
    categoryMap.put("en.classic-disney", "classic disney movies");
    categoryMap.put("en.cmu-buildings", "cmu buildings");
    categoryMap.put("en.common-diseases", "common diseases");
    categoryMap.put("en.common-fish", "common fish");
    categoryMap.put("en.constellations", "constellations");
    categoryMap.put("en.countries", "countries");
    categoryMap.put("en.mlb-teams", "mlb teams");
    categoryMap.put("en.nba-teams", "nba teams");
    categoryMap.put("en.nfl-teams", "nfl teams");
    categoryMap.put("en.periodic-comets", "periodic comets");
    categoryMap.put("en.popular-car-makers", "car makers");
    categoryMap.put("en.us-presidents", "us presidents");
    categoryMap.put("en.us-states", "us states");
    /**************** Chinese Categories ****************/
    categoryMap.put("zh-TW.classic-disney", "迪士尼動畫");
    categoryMap.put("zh-TW.china-dynasties", "朝代");
    categoryMap.put("zh-TW.china-provinces", "省");
    categoryMap.put("zh-TW.constellations", "星座");
    categoryMap.put("zh-TW.countries", "國家");
    categoryMap.put("zh-TW.mlb-teams", "mlb 球隊");
    categoryMap.put("zh-TW.nba-teams", "nba 球隊");
    categoryMap.put("zh-TW.nfl-teams", "nfl 球隊");
    categoryMap.put("zh-TW.popular-car-makers", "車廠");
    categoryMap.put("zh-TW.taiwan-cities", "縣市");
    categoryMap.put("zh-TW.us-presidents", "美國總統");
    categoryMap.put("zh-TW.us-states", "州");
    /**************** Japanese Categories ***************/
    categoryMap.put("ja.classic-disney", "ディズニー映画");
    categoryMap.put("ja.constellations", "星座");
    categoryMap.put("ja.countries", "国");
    categoryMap.put("ja.japan-emperors", "天皇");
    categoryMap.put("ja.japan-prime-ministers", "総理大臣");
    categoryMap.put("ja.japan-provinces", "県");
    categoryMap.put("ja.mlb-teams", "mlb チーム");
    categoryMap.put("ja.nba-teams", "nba チーム");
    categoryMap.put("ja.nfl-teams", "nfl チーム");
    categoryMap.put("ja.popular-car-makers", "自動車メーカー");
    categoryMap.put("ja.us-presidents", "アメリカ大統領");
    categoryMap.put("ja.us-states", "アメリカの州");
    
    return categoryMap.get(langID + "." + dataName);
  }
  
  private static void mailResults(String experimentID, String content) {
      /*
    String subject = Helper.extract(content, "Average", "\n");
    if (subject == null) return;
    subject = experimentID + ": " + subject.replaceAll("\\s+", " ").trim();
    Mailer.sendEmail(FROM_EMAIL_ADDR, TO_EMAIL_ADDR, subject, content);    
      */
  }
    
  public AsiaExperiment() {
    evaluator = new Evaluator();
  }
  
  public Feature getFeature() {
    return feature;
  }

  public int getNumBootstraps() {
    return numBootstraps;
  }
  
  public void run() {
    long startTime = System.currentTimeMillis();
    List<File> goldFiles = Evaluator.getGoldFiles(gv.getDataDir());
    File evalDir = Helper.createDir(gv.getEvalDir());
    String evalFileName = getExperimentID() + "_" + Helper.getUniqueID() + Evaluator.EVAL_FILE_SUFFIX;
    File evalFile = new File(evalDir, evalFileName);
    Helper.recursivelyRemove(gv.getResultDir());

    List<Double> maps = new ArrayList<Double>();
    StringBuffer buf = new StringBuffer();
    int bufStartIndex = 0;
    int goldFileCounts = 0;
    
    for (File goldFile : goldFiles) {
      String langID = goldFile.getParentFile().getName();
      String dataName = Evaluator.getDataName(goldFile);
      if (!isExpDataset(langID, dataName)) continue;
      String category = getCategory(langID, dataName);
      if (category == null) continue;
      evaluator.loadGoldFile(goldFile);
      goldFileCounts++;
      
      log.info("\t" + Helper.repeat('=', 80));
      log.info("Evaluating " + dataName + " (" + langID + ") using category: " + category);
      List<EvalResult> evalResults = evaluate(langID, category);

      buf.append(langID).append(".").append(dataName).append("\t");
      double map;
      for (int expansion = 0; expansion < evalResults.size(); expansion++) {
        map = evalResults.get(expansion).meanAvgPrecision;
        if (maps.size() == expansion)
          maps.add(map);
        else maps.set(expansion, maps.get(expansion) + map);
        buf.append(map).append("\t");
      }
      buf.setCharAt(buf.length()-1, '\n');
      Helper.writeToFile(evalFile, buf.substring(bufStartIndex), true);
      bufStartIndex = buf.length();
    }
    
    buf.append("Average\t");
    int totalTrials = goldFileCounts;
    for (double map : maps)
      buf.append(map / totalTrials).append("\t");
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
  
  public void setNumBootstraps(int numBootstraps) {
    this.numBootstraps = numBootstraps;
  }

  private List<EvalResult> evaluate(String langID, String category) {
    Asia asia = new Asia();
//    asia.setReuseDocuments(REUSE_DOCUMENT);
    asia.setEvaluator(evaluator);
    asia.setNumExpansions(numBootstraps);
    asia.setRanker(feature);
    Seal seal = new Seal(langID);
    asia.expand(seal, category);
    
    List<EvalResult> resultList = new ArrayList<EvalResult>();
    resultList.add(asia.getExtractResult());
    List<EvalResult> rerankedResults = asia.getExpandResults();
    if (rerankedResults != null && rerankedResults.size() > 0)
      resultList.add(rerankedResults.get(rerankedResults.size()-1));
    else resultList.add(new EvalResult());
    List<EvalResult> bootstrapResults = asia.getBootstrapResults();
    if (bootstrapResults != null)
      resultList.addAll(bootstrapResults);
    return resultList;
  }

  private String getExperimentID() {
    String[] evalFileNameArr = new String[] {
        "member",
        feature.toString().toLowerCase(),
        Integer.toString(numBootstraps),
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
