/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;

public class VotedPerceptron {
  
  public static class WeightedPerceptron {
    public double weight = 0;
    public double[] perceptron;
    public WeightedPerceptron(int size) { perceptron = new double[size]; }
    public WeightedPerceptron(double[] perceptron) { this.perceptron = perceptron; }
  }

  public static int MIN_PERCEPTRON_WEIGHT = 1;
  public static boolean FAIL_EARLY = false;
  public static Logger log = Logger.getLogger(VotedPerceptron.class);
  
  private List<WeightedPerceptron> weightedPerceptrons;
  private Map<SID, Double> trueLabels;
  private SparseMatrix matrix;
  private boolean moreInstancesAdded;
  private int numEpochs;

  public static void main(String[] args) {
    VotedPerceptron vp = new VotedPerceptron(10);
    
    // training
    double[][] train = new double[][] {
        {1, 0, 1, 1, 0, 1},
        {1, 0, 0, 0, 1, 0},
        {1, 1, 1, 1, 1, 1},
        {0, 1, 0, 1, 0, 1},
        {0, 0, 0, 0, 0, 0},
    };
    double[] labels = new double[] {
        1, 1, 1, -1, -1
    };
    for (int i = 0; i < train.length; i++)
      for (int j = 0; j < train[i].length; j++)
        vp.addExample(String.valueOf(i), String.valueOf(j), train[i][j], labels[i]);
    
    // testing
    double[][] test = new double[][] {
        {1, 0, 0, 0, 0, 0},
        {0, 1, 1, 1, 1, 1},
        {0, 0, 1, 0, 0, 0},
        {0, 0, 0, 0, 0, 0},
    };
    SparseVector instance = new SparseVector();
    for (int i = 0; i < test.length; i++) {
      instance.clear();
      for (int j = 0; j < test[i].length; j++)
        instance.put(String.valueOf(j), test[i][j]);
      double votes = vp.vote(instance);
      log.info("Prediction for " + instance + " is " + votes + " (" + (sign(votes) > 0 ? "TRUE" : "FALSE") + ")");
    }
  }
  
  public static int sign(double d) {
//    return (d >= 0) ? 1 : -1;
    return (d > 0) ? 1 : (d < 0 ? -1 : 0);
  }
  
  public VotedPerceptron(int numEpochs) {
    this.numEpochs = numEpochs;
    moreInstancesAdded = true;
    matrix = new SparseMatrix();
    trueLabels = new HashMap<SID, Double>();
    weightedPerceptrons = new ArrayList<WeightedPerceptron>();
  }
  
  public void addExample(SID instanceID, SID featureID, double weight, double label) {
    matrix.add(featureID, instanceID, weight);
    trueLabels.put(instanceID, label);
    moreInstancesAdded = true;
  }
  
  public void addExample(SID instanceID, SparseVector featureVector, double label) {
    for (Entry<SID, Cell> entry : featureVector) {
      SID feautreID = entry.getKey();
      double value = entry.getValue().value;
      addExample(instanceID, feautreID, value, label);
    }
  }
  
  public void addExample(String instanceName, String featureName, double weight, double label) {
    SID instanceID = StringFactory.toID(instanceName);
    SID featureID = StringFactory.toID(featureName);
    addExample(instanceID, featureID, weight, label);
  }
  
  public double dotProduct(double[] perceptron, SparseVector instance) {
    int sum = 0, index = 0;
    for (SID featureID : matrix.getColumnIDs()) {
      Cell cell = instance.get(featureID);
      double x = (cell == null) ? 0 : cell.value;
      sum += perceptron[index++] * x;
    }
    return sum;
  }
  
  public int getNumExamples() {
    return matrix.getRowIDs().size();
  }
  
  public int getNumFeatures() {
    return matrix.getColumnIDs().size();
  }
  
  public void reset() {
    moreInstancesAdded = true;
    matrix.clear();
    trueLabels.clear();
    weightedPerceptrons.clear();
  }
  
  public double vote(SparseVector instance) {
    if (moreInstancesAdded) {
      batchTrain();
      moreInstancesAdded = false;
    }
    double votes = 0;
    for (WeightedPerceptron weightedPerceptron : weightedPerceptrons)
      votes += weightedPerceptron.weight * sign(dotProduct(weightedPerceptron.perceptron, instance));
    return votes;
  }
  
  private void batchTrain() {
    log.info("Training on " + getNumExamples() + " examples...");
    long startTime = System.currentTimeMillis();
    WeightedPerceptron weightedPerceptron = new WeightedPerceptron(matrix.getNumColumns());
    weightedPerceptrons.add(weightedPerceptron);
    
    for (int i = 0; i < numEpochs; i++) {
      String epochID = "[" + (i+1) + "/" + numEpochs + "]";
      log.info(epochID + " Training (constructed " + weightedPerceptrons.size() + " perceptrons so far)...");
      
      for (SparseVector instance : matrix.getRows()) {
        int binaryTrueLabel = sign(trueLabels.get(instance.id));
        int binaryPredictedLabel = sign(dotProduct(weightedPerceptron.perceptron, instance));
//        log.info(epochID + instance.id + " Instance: " + instance + ", Prediction: " + binaryPredictedLabel + ", True: " + binaryTrueLabel);
        
        if (FAIL_EARLY && weightedPerceptron.weight == matrix.getNumRows()) {
          // current perceptron has survived through an epoch; so terminate training process
          i = numEpochs;
          break;
        } else if (binaryTrueLabel != binaryPredictedLabel) {
          // incorrect prediction; so update and create a new perceptron
          weightedPerceptron = update(weightedPerceptron, instance, binaryTrueLabel);
//          log.info(epochID + instance.id + " New Perceptrion: " + weightedPerceptron + ", Weight: " + weightedPerceptron.weight);
        } else {
          // correct prediction; so increment current perceptron's weight
          weightedPerceptron.weight++;
        }
      }
    }
    Helper.printElapsedTime(startTime);
    Helper.printMemoryUsed();
  }
  
  private WeightedPerceptron update(WeightedPerceptron weightedPerceptron, SparseVector instance, double trueLabel) {
    boolean makeNewPerceptron = (weightedPerceptron.weight >= MIN_PERCEPTRON_WEIGHT);
    double[] oldPerceptron = weightedPerceptron.perceptron;
    double[] newPerceptron = makeNewPerceptron ? new double[oldPerceptron.length] : oldPerceptron;

    int index = 0;
    for (SID featureID : matrix.getColumnIDs()) {
      double v = oldPerceptron[index];
      Cell cell = instance.get(featureID);
      double x = (cell == null) ? 0 : cell.value;
      newPerceptron[index++] = v + trueLabel * x;
    }
    
    if (makeNewPerceptron) {
      weightedPerceptron = new WeightedPerceptron(newPerceptron);
      weightedPerceptrons.add(weightedPerceptron);
    }
    weightedPerceptron.weight = 1;
    return weightedPerceptron;
  }
}
