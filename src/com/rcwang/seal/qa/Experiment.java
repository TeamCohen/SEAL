package com.rcwang.seal.qa;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.rcwang.seal.qa.Evaluator.HybridMethod;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
//import com.rcwang.seal.util.Mailer; //wwc

public class Experiment {

  public static Logger log = Logger.getLogger(Experiment.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  static { gv.merge(Helper.loadPropertiesFile(new File("qa.properties"))); }
  
  /********************* Parameters ***********************/
  public static int numCVFold = 5;
  
  // relative threshold for Ephyra and SEAL
  public static double ephyraRelThresh = gv.getEphyraThreshold();
  public static double sealRelThresh = gv.getSealThreshold();
  public static double interRelThresh = gv.getInterThreshold();
  public static double unionRelThresh = gv.getUnionThreshold();
  public static double method1RelThresh = gv.getMethod1Threshold();
  
  // absolute threshold for Ephyra and SEAL
  public static double ephyraAbsThresh = 0.0;
  public static double sealAbsThresh = 0.0;
  public static double interAbsThresh = ephyraAbsThresh * sealAbsThresh;
  public static double unionAbsThresh = 0.0001;
  public static double method1AbsThresh = 0.0;
  /********************************************************/

  public static final int FIRST_TREC = 15;
  public static final int LAST_TREC = 15;

  public static final String FROM_EMAIL_ADDR = "Experimental Results <experimental.results@cmu.edu>";
  public static final String TO_EMAIL_ADDR = "Richard Wang <rcwang@gmail.com>";
  
  @SuppressWarnings("unchecked")
  public static String crossValidate(List<Stat> statList, int numFold) {
    Collections.shuffle(statList, new Random(0));
    
    List<Stat>[] statListArr = new List[numFold];
    double avgQPerFold = (double)statList.size()/numFold;
    double startIndex = 0, endIndex;
    for (int i = 0; i < numFold; i++) {
      endIndex = startIndex + avgQPerFold;
      statListArr[i] = statList.subList((int)Math.round(startIndex), (int)Math.round(endIndex));
      startIndex = endIndex;
    }
    
    AvgStats avgStats = new AvgStats();
    double avgF1 = 0, avgThreshold = 0;
    List<Stat> trainStats = new ArrayList<Stat>();
    for (int i = 0; i < numFold; i++) {
      List<Stat> testStats = statListArr[i];
      trainStats.clear();
      for (int j = 0; j < numFold; j++) {
        if (i == j) continue;
        trainStats.addAll(statListArr[j]);
      }
      avgStats.compute(trainStats, 1);
      double bestThreshold = avgStats.getBestThresh();
      avgThreshold += bestThreshold / numFold;
      
      avgStats.compute(testStats, bestThreshold);
      avgF1 += avgStats.getF1AtPresetThresh() / numFold;
    }
    
    StringBuffer buf = new StringBuffer();
    buf.append(numFold + "-Fold CV Avg F1: ").append(avgF1);
    buf.append(", Avg Threshold: ").append(avgThreshold).append("\n");
    return buf.toString();
  }
  
  public static List<Stat> getColumn(List<Stat[]> statsList, HybridMethod method) {
    List<Stat> list = new ArrayList<Stat>();
    for (Stat[] stats : statsList)
      list.add(stats[method.ordinal()]);
    return list;
  }

  /*public static File getPropFile() {
    return new File(propFileName);
  }*/
  
  public static double getThreshold(Evaluator.HybridMethod method, boolean isAbsolute) {
    double threshold = Double.MIN_VALUE;
    switch (method) {
      case EPHYRA: threshold = isAbsolute ? ephyraAbsThresh : ephyraRelThresh; break;
      case SEAL: threshold = isAbsolute ? sealAbsThresh : sealRelThresh; break;
      case INTERSECT: threshold = isAbsolute ? interAbsThresh : interRelThresh; break;
      case UNION: threshold = isAbsolute ? unionAbsThresh : unionRelThresh; break;
      case METHOD1: threshold = isAbsolute ? method1AbsThresh : method1RelThresh; break;
      default: Evaluator.log.error("Unknown method: " + method);
    }
    return threshold;
  }

  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    Evaluator eval = new Evaluator();
    AvgStats avgStats = new AvgStats();
    StringBuffer buf = new StringBuffer();
    
    List<List<String>> qIDsList = Experiment.sample1(eval);
    
    int qIDBatch = 1;
    for (List<String> qIDs : qIDsList) {
      List<Stat[]> statsList = eval.evaluate(qIDs);
      buf.setLength(0);
      buf.append("[" + qIDBatch + "] Total Number of TREC Questions Evaluated: ").append(qIDs.size()).append("\n");
      
      for (HybridMethod method : HybridMethod.values()) {
        buf.append("Results of using ").append(method).append(" method:").append("\n");
        List<Stat> statList = getColumn(statsList, method);
        avgStats.compute(statList, getThreshold(method, false));
        buf.append(avgStats);
        buf.append(crossValidate(statList, numCVFold)).append("\n");
      }
      
      log.info(buf.toString());
      if (Evaluator.mailResults) {
          /*
        String subject = "QID Batch " + qIDBatch + " Results";
        Mailer.sendEmail(FROM_EMAIL_ADDR, TO_EMAIL_ADDR, subject, buf.toString());
          */
      }
      qIDBatch++;
    }
    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }

  public static List<List<String>> sample1(Evaluator eval) {
    List<List<String>> qIDsList = new ArrayList<List<String>>();
    for (int trecID = FIRST_TREC; trecID <= LAST_TREC; trecID++)
      qIDsList.add(new ArrayList<String>(eval.loadAnswers(trecID)));
    return qIDsList;
  }

  // 50%-50%
  public static List<List<String>> sample2(Evaluator eval) {
    List<String> qIDs = new ArrayList<String>();
    for (int trecID = FIRST_TREC; trecID <= LAST_TREC; trecID++)
      qIDs.addAll(new ArrayList<String>(eval.loadAnswers(trecID)));
    Random random = new Random(0);
    Collections.shuffle(qIDs, random);
    List<List<String>> qIDsList = new ArrayList<List<String>>();
    qIDsList.add(qIDs.subList(0, qIDs.size()/2));
    qIDsList.add(qIDs.subList(qIDs.size()/2, qIDs.size()));
    return qIDsList;
  }

  public static List<List<String>> sample3(Evaluator eval) {
    List<String> qIDs = new ArrayList<String>();
    for (int trecID = FIRST_TREC; trecID <= LAST_TREC; trecID++)
      qIDs.addAll(new ArrayList<String>(eval.loadAnswers(trecID)));
    List<List<String>> qIDsList = new ArrayList<List<String>>();
    qIDsList.add(qIDs);
    return qIDsList;
  }
  
  public static List<List<String>> sample4(Evaluator eval) {
    for (int trecID = FIRST_TREC; trecID <= LAST_TREC; trecID++)
      eval.loadAnswers(trecID);
    List<String> qIDs = new ArrayList<String>();
    qIDs.add("170.6");
    qIDs.add("188.3");
    List<List<String>> qIDsList = new ArrayList<List<String>>();
    qIDsList.add(qIDs);
    return qIDsList;
  }

}
