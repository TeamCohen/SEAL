/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.eval;

import java.io.File;

import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.Helper;

public class EvalResult {
  public int trialID = 0;
  public int numGoldEntity = 0;
  public int numGoldSynonym = 0;
  public double numCorrectSynonym = 0;
  public double numCorrectEntity = 0;
  public double numResultsAboveThreshold = 0;
  public double maxF1Precision = 0;
  public double maxF1Recall = 0;
  public double maxF1Threshold = 0;
  public double precision = 0;
  public double recall = 0;
  public double threshold = 0;
  public double meanAvgPrecision = 0;
  public File goldFile = null;
  public String seeds = "";
  public Feature method = null;
  private StringBuffer buf = new StringBuffer();
  public double getF1() { return Evaluator.computeF1(precision, recall); }
  public double getMaxF1() { return Evaluator.computeF1(maxF1Precision, maxF1Recall); }
  
  public String toString() {
    buf.setLength(0);
    buf.append("===================================================\n");
    if (goldFile != null && method != null)
      buf.append("Results of \"" + goldFile.getName() + "\" using method: " + method + "\n");
    if (seeds != null && seeds.trim().length() > 0)
      buf.append("Seeds: " + seeds + "\n");
    buf.append("Avg. number of results above threshold: " + Helper.formatNumber(numResultsAboveThreshold, 3) + "\n");
    buf.append("Avg. number of correct synonyms: " + Helper.formatNumber(numCorrectSynonym, 3) + " out of " + numGoldSynonym + "\n");
    buf.append("Avg. number of correct entities: " + Helper.formatNumber(numCorrectEntity, 3) + " out of " + numGoldEntity + "\n");
    buf.append("Max. Possible F1: " + Helper.formatNumber(getMaxF1(), 3));
    buf.append(" (Precision: " + Helper.formatNumber(maxF1Precision, 3));
    buf.append(", Recall: " + Helper.formatNumber(maxF1Recall, 3) + ")");
    buf.append(" at threshold: " + Helper.formatNumber(maxF1Threshold, 3) + "\n");
    buf.append("F1: " + Helper.formatNumber(getF1(), 3));
    buf.append(" (Precision: " + Helper.formatNumber(precision, 3));
    buf.append(", Recall: " + Helper.formatNumber(recall, 3) + ")");
    buf.append(" at threshold: " + Helper.formatNumber(threshold, 3) + "\n");
    buf.append("Mean Avg. Precision: " + Helper.formatNumber(meanAvgPrecision, 3));
    return buf.toString();
  }
  
  public String toTabSeperated() {
    buf.setLength(0);
    buf.append(Evaluator.getDataName(goldFile)).append("\t");
    buf.append(method).append("\t");
    buf.append(getMaxF1()).append("\t");
    buf.append(maxF1Precision).append("\t");
    buf.append(maxF1Recall).append("\t");
    buf.append(maxF1Threshold).append("\t");
    buf.append(getF1()).append("\t");
    buf.append(precision).append("\t");
    buf.append(recall).append("\t");
    buf.append(threshold).append("\t");
    buf.append(meanAvgPrecision).append("\n");
    return buf.toString();
  }    
}