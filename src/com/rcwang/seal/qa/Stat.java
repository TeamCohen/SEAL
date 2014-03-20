/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rcwang.seal.util.Helper;

public class Stat implements Serializable {

  public static final int NUM_RANK_TO_DISPLAY = 10;
  
  private static final long serialVersionUID = -5925068569243144003L;
  private List<Integer> numCorrect;
  private List<ItemWeight> entities;
  private List<Boolean> correct;
  private String qID;
  private int numTrueAnswers = 0;
  private double relThreshold = 0;
  private double absThreshold = 0;
  private double maxWeight = Double.MIN_VALUE;
  private double totalWeight = 0;
  private boolean isSorted = false;

  public Stat() {
    entities = new ArrayList<ItemWeight>();
    correct = new ArrayList<Boolean>();
    numCorrect = new ArrayList<Integer>();
  }
  
  public void addAnswer(ItemWeight iw, boolean b) {
    maxWeight = Math.max(iw.getWeight(), maxWeight);
    totalWeight += iw.getWeight();
    entities.add(iw);
    correct.add(b);
    int prevNumCorrect = numCorrect.isEmpty() ? 0 : numCorrect.get(numCorrect.size()-1);
    numCorrect.add(prevNumCorrect + (b ? 1 : 0));
    isSorted = false;
  }
  
  public void clearItems() {
    for (ItemWeight iw : entities)
      iw.setItem((String) null);
  }
  
  public double getAbsThreshold() {
    return absThreshold;
  }
  
  public double getAvgPrec() {
    double precisionSum = 0;
    for (int i = 0; i < correct.size(); i++)
      if (correct.get(i))
        precisionSum += getPrecisionAt(i+1);
    return precisionSum / numTrueAnswers;
  }
  
  public double getF1() {
    return getF1At(correct.size());
  }
  
  public double getF1At(double threshold, boolean isAbsolute) {
    sort();
    int rank = ItemWeight.getRankAt(entities, threshold, isAbsolute);
    return getF1At(rank);
  }
  
  public double getF1At(int rank) {
    if (!checkRank(rank)) return 0;
    double p = getPrecisionAt(rank);
    double r = getRecallAt(rank);
    return (p+r) == 0 ? 0 : (2*p*r)/(p+r);
  }
  
  public double getMaxWeight() {
    return maxWeight;
  }
  
  public int getNumCorrectAt(int rank) {
    if (!checkRank(rank)) return 0;
    return numCorrect.get(rank-1);
  }
  
  public int getNumTrueAnswers() {
    return numTrueAnswers;
  }
  
  public double getOptimalF1() {
    return getF1At(getRankWithOptimalF1());
  }
  
  public double getPrecision() {
    return getPrecisionAt(correct.size());
  }
  
  public double getPrecisionAt(double threshold, boolean isAbsolute) {
    sort();
    int rank = ItemWeight.getRankAt(entities, threshold, isAbsolute);
    return getPrecisionAt(rank);
  }
  
  public double getPrecisionAt(int rank) {
    if (!checkRank(rank)) return 0;
    return (double) getNumCorrectAt(rank) / rank;
  }
  
  public String getQID() {
    return qID;
  }
  
  public int getRankWithOptimalF1() {
    double maxF1 = Double.MIN_VALUE;
    int optimalRank = 0;
    for (int rank = 1; rank <= correct.size(); rank++) {
      double f1 = getF1At(rank);
      if (f1 > maxF1) {
        maxF1 = f1;
        optimalRank = rank;
      }
    }
    return optimalRank;
  }
  
  public double getRecall() {
    return getRecallAt(correct.size());
  }
  
  public double getRecallAt(double threshold, boolean isAbsolute) {
    sort();
    int rank = ItemWeight.getRankAt(entities, threshold, isAbsolute);
    return getRecallAt(rank);
  }
  
  public double getRecallAt(int rank) {
    if (!checkRank(rank)) return 0;
    return (double) getNumCorrectAt(rank) / numTrueAnswers;
  }
  
  public double getRelThreshold() {
    return relThreshold;
  }

  public double getTotalWeight() {
    return totalWeight;
  }

  public void setAbsThreshold(double absThreshold) {
    this.absThreshold = absThreshold;
  }

  public void setNumTrueAnswers(int numTrueAnswers) {
    this.numTrueAnswers = numTrueAnswers;
  }

  public void setQID(String qID) {
    this.qID = qID;
  }
  
  public void setRelThreshold(double relThreshold) {
    this.relThreshold = relThreshold;
  }

  public void sort() {
    if (isSorted) return;
    Collections.sort(entities);
    isSorted = true;
  }

  public String toF1PrecRecall(boolean isAbsolute) {
    StringBuffer buf = new StringBuffer();
    double threshold = isAbsolute ? absThreshold : relThreshold;
    String thresholdStr = Helper.formatNumber(threshold*100, 5);
    String s = isAbsolute ? "Abs" : "Rel";
    buf.append(s).append(" F1@").append(thresholdStr).append("%: ").append(getF1At(threshold, isAbsolute)).append(", ");
    buf.append("P: ").append(getPrecisionAt(threshold, isAbsolute)).append(", ");
    buf.append("R: ").append(getRecallAt(threshold, isAbsolute)).append("\n");
    return buf.toString();
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("Rank\tEntity\tWeight\tPrecision\tRecall\tF1\n");
    for (int i = 0; i < Math.min(entities.size(), NUM_RANK_TO_DISPLAY); i++) {
      int rank = i+1;
      buf.append(rank).append("\t");
      buf.append(correct.get(i) ? "+" : "-").append(entities.get(i).getItem()).append("\t");
      buf.append(entities.get(i).getWeight()).append("\t");
      buf.append(getPrecisionAt(rank)).append("\t");
      buf.append(getRecallAt(rank)).append("\t");
      buf.append(getF1At(rank)).append("\n");
    }
    buf.append(toF1PrecRecall(true));
    buf.append(toF1PrecRecall(false));
    buf.append("Avg Prec: ").append(getAvgPrec()).append(", ");
    buf.append("Optimal F1: ").append(getOptimalF1()).append("\n");
    return buf.toString();
  }

  private boolean checkRank(int rank) {
    return ( 1 <= rank && rank <= correct.size() );
  }
}