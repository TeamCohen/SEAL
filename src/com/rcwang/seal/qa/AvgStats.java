/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.util.List;

public class AvgStats {

  // set precision of finding best threshold to (1/numThreshold)
  public static int numThresholds = 10000;
  
  private double f1AtPresetThresh, precAtPresetThresh, recallAtPresetThresh;
  private double f1AtBestThresh, precAtBestThresh, recallAtBestThresh;
  private double presetThresh, bestThresh;
  private double meanAvgPrec, meanOptimalF1;
  
  public static int getNumThresholds() {
    return numThresholds;
  }
  
  /**
   * Outputs average result statistics
   * @param statList a list of result statistics
   */
  public void compute(List<Stat> statList, double threshold) {
    double[] fScoreAtAllThresh = new double[numThresholds];
    double[] precAtAllThresh = new double[numThresholds];
    double[] recallAtAllThresh = new double[numThresholds];
    
    f1AtPresetThresh = 0;
    precAtPresetThresh = 0;
    recallAtPresetThresh = 0;
    meanAvgPrec = 0;
    meanOptimalF1 = 0;
    presetThresh = threshold;
    
    for (Stat stats : statList) {
      // find precision, recall, and F1 at best threshold
      for (int i = 0; i < numThresholds; i++) {
        double t = (double)i/numThresholds;
        fScoreAtAllThresh[i] += stats.getF1At(t, false) / statList.size();
        precAtAllThresh[i] += stats.getPrecisionAt(t, false) / statList.size();
        recallAtAllThresh[i] += stats.getRecallAt(t, false) / statList.size();
      }
      // find precision, recall, and F1 at user preset's threshold
      f1AtPresetThresh += stats.getF1At(threshold, false) / statList.size();
      precAtPresetThresh += stats.getPrecisionAt(threshold, false) / statList.size();
      recallAtPresetThresh += stats.getRecallAt(threshold, false) / statList.size();
      meanAvgPrec += stats.getAvgPrec() / statList.size();
      meanOptimalF1 += stats.getOptimalF1() / statList.size();
    }
    
    int optimalThresh = 0;
    for (int i = 0; i < fScoreAtAllThresh.length; i++)
      if (fScoreAtAllThresh[i] >= fScoreAtAllThresh[optimalThresh])
        optimalThresh = i;

    bestThresh = (double)optimalThresh/numThresholds;
    f1AtBestThresh = fScoreAtAllThresh[optimalThresh];
    precAtBestThresh = precAtAllThresh[optimalThresh];
    recallAtBestThresh = recallAtAllThresh[optimalThresh];
  }

  public double getBestThresh() {
    return bestThresh;
  }

  public double getF1AtBestThresh() {
    return f1AtBestThresh;
  }

  public double getF1AtPresetThresh() {
    return f1AtPresetThresh;
  }

  public double getMeanAvgPrec() {
    return meanAvgPrec;
  }

  public double getMeanOptimalF1() {
    return meanOptimalF1;
  }

  public double getPrecAtBestThresh() {
    return precAtBestThresh;
  }

  public double getPrecAtPresetThresh() {
    return precAtPresetThresh;
  }

  public double getPresetThresh() {
    return presetThresh;
  }

  public double getRecallAtBestThresh() {
    return recallAtBestThresh;
  }

  public double getRecallAtPresetThresh() {
    return recallAtPresetThresh;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("Mean Avg Prec: ").append(meanAvgPrec).append(" ");
    buf.append("Avg. Optimal F1: ").append(meanOptimalF1).append("\n");
    
    // outputs precision, recall, and F1 at user preset's threshold
    buf.append("Preset: ").append(presetThresh).append(", ");
    buf.append("F1: ").append(f1AtPresetThresh).append(", ");
    buf.append("P: ").append(precAtPresetThresh).append(", ");
    buf.append("R: ").append(recallAtPresetThresh).append("\n");
    
    // outputs precision, recall, and F1 at best threshold    
    buf.append("Best: ").append(bestThresh).append(", ");
    buf.append("F1: ").append(f1AtBestThresh).append(", ");
    buf.append("P: ").append(precAtBestThresh).append(", ");
    buf.append("R: ").append(recallAtBestThresh).append("\n");
    return buf.toString();
  }
}
