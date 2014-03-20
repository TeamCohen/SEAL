/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.util.Distribution;
import com.rcwang.seal.util.SparseMatrix;
import com.rcwang.seal.util.SparseVector;
import com.rcwang.seal.util.StringFactory;
import com.rcwang.seal.util.StringFactory.SID;

public class BayesianRanker extends Ranker {

  public static final String DESCRIPTION = "Bayesian Set weighting";
  public static final double BAYES_CONSTANT = 2;  // constant C; same as described in Section 5
  public static final double BINARIZE_RATIO = 2;  // same as described in Section 5
  public static final double EPSILON = 1e-10;  // prevents 'mean' to become 0 or 1
  public static final boolean DEFAULT_IS_BINARIZE = false;  // binarize() needs to be fixed
  public static final boolean DEFAULT_IS_LOGSCORE = true; // will underflow if not in log space
  public static Logger log = Logger.getLogger(BayesianRanker.class);
  
  private SparseMatrix matrix;
  private boolean isBinarize;
  private boolean isLogScore;
  
  public BayesianRanker() {
    super();
    matrix = new SparseMatrix();
    setDescription(DESCRIPTION);
    setBinarize(DEFAULT_IS_BINARIZE);
    setLogScore(DEFAULT_IS_LOGSCORE);
  }
  
  public void clear() {
    super.clear();
    matrix.clear();
  }
  
  public boolean isBinarize() {
    return isBinarize;
  }
  
  public boolean isLogScore() {
    return isLogScore;
  }
  
  public void setBinarize(boolean isBinarize) {
    this.isBinarize = isBinarize;
  }

  public void setLogScore(boolean isLogScore) {
    this.isLogScore = isLogScore;
  }

  // TODO: This is broken; needs to be fixed
  private void binarize() {
    SparseMatrix binaryMatrix = new SparseMatrix();
    Distribution rowSumDist = new Distribution();
    
    for (SparseVector row : matrix.getRows())
      rowSumDist.add(row.id, row.getSum());

    for (SparseVector column : matrix.getColumns()) {
      double columnMean = column.getSum() / matrix.getNumRows();
      
      for (SID entityID : column.keySet()) {
        double rowSum = rowSumDist.getWeight(entityID);
        double weight = column.getValue(entityID);
        
        if (weight / rowSum > BINARIZE_RATIO * columnMean)
          binaryMatrix.add(entityID, column.id, 1);
      }
    }
    matrix = binaryMatrix;
  }

  // for binary features only (?)
  private int getNumPosQueries(SparseVector feature) {
    int numPosQueries = 0;
    for (SID queryID : seedDist.getKeys())
      if (feature.keySet().contains(queryID))
        numPosQueries++;
    return numPosQueries;
  }

  private void addToMatrix(DocumentSet documentSet) {
      // kmr 6 June 2012: Conversion to EntityLiteral for relations
    for (Document document : documentSet) {
        SID did = StringFactory.toID(document.getURL().toString());
      for (Wrapper wrapper : document.getWrappers()) {
          SID wid = StringFactory.toID(wrapper.toString());
        for (EntityLiteral content : wrapper.getContents()) {
          // use wrapper and document as features
            SID cid = StringFactory.toID(content);
          matrix.add(wid, cid, 1);
          matrix.add(did, cid, 1);
        }
      }
    }
  }

  public void load(EntityList entities, DocumentSet documentSet) {
    // lets create a matrix table first
    addToMatrix(documentSet);
    
    // This will remove un-informative features as described in Section 5 of the paper
    // sort of like feature de-selection?
    if (isBinarize) binarize();
    
    double constant = 0;
    double N = seedDist.size();
    entities.clearWeight(getRankerID());
//    entityDist.clear(); // added 06/21/2009
    
    // traverse through every column of the matrix table
    for (SparseVector feature : matrix.getColumns()) {
      
      // get number of seeds contained by this column (or possessing this feature)
      double numPosQueries = getNumPosQueries(feature);
      double mean = (double) feature.size() / matrix.getNumRows();
      double alpha = BAYES_CONSTANT * mean + EPSILON;
      double alphaHat = alpha + numPosQueries;
      double beta = BAYES_CONSTANT * (1 - mean) + EPSILON;
      double betaHat = beta + N - numPosQueries;
//      log.info("Mean:" + mean + " N:" + N + " alpha:" + alpha + " alpha^:" + alphaHat + " beta:" + beta + " beta^:" + betaHat);
      
      if (isLogScore) {
        // this is the q_j equation [14] described in the paper
        double logScore = Math.log(alphaHat) - Math.log(alpha) - Math.log(betaHat) + Math.log(beta);
        for (SID entityID : feature.keySet()) {
//          entityDist.add(entityID, logScore);
          Entity entity = entities.add(entityID.toLiteral());//StringFactory.toName(entityID));
          if (entity == null) continue;
          entity.addWeight(getRankerID(), logScore);
        }
        constant += Math.log(alpha + beta) - Math.log(alpha + beta + N) + Math.log(betaHat) - Math.log(beta);
      } else {
        // probabilistic score(x) equation [11] described in the paper
        // (it *will* underflow, not sure why I even implemented this...)
        double coefficient = (alpha + beta) / (alpha + beta + N);
        double bernoulli1 = alphaHat / alpha;
        double bernoulli2 = betaHat / beta;
        
        for (SID entityID : matrix.getRowIDs()) {
          Entity entity = entities.add(entityID.toLiteral());//StringFactory.toName(entityID));
          if (entity == null) continue;
          double w = entity.getWeight(getRankerID());
          if (w == 0) w = 1;
          if (feature.keySet().contains(entityID)) {
//            entityDist.multiply(entityID, coefficient * bernoulli1);
            w *= coefficient * bernoulli1;
          } else {
//            entityDist.multiply(entityID, coefficient * bernoulli2);
            w *= coefficient * bernoulli2;
          }
          entity.setWeight(getRankerID(), w);
        }
        // this will slow down the underflow, not sure if it's mathematically justified though
//        entityDist.normalize();
        entities.normalize(getRankerID());
      }
    }
    
    if (isLogScore) {
      // add the constant to every entity to complete equation [12]
//      entityDist.add(constant);
      entities.addWeight(getRankerID(), constant);
    }
  }
  
}
