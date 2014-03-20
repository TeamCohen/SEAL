/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.rank.Graph.EdgeSet;
import com.rcwang.seal.rank.Graph.Node;
import com.rcwang.seal.rank.Graph.NodeSet;
import com.rcwang.seal.util.Cell;
import com.rcwang.seal.util.MatrixUtil;
import com.rcwang.seal.util.SparseMatrix;
import com.rcwang.seal.util.SparseVector;
import com.rcwang.seal.util.StringFactory;
import com.rcwang.seal.util.StringFactory.SID;

public abstract class GraphRanker extends Ranker {

  public static final double DEFAULT_CONVERGE_THRESHOLD = 1e-3;
  public static final int DEFAULT_NUM_ITERATION = 50;
  public static final boolean DEFAULT_DISABLE_WALK = false;
  public static final int NUM_ITER_TO_OUTPUT_LEVEL = 10;
  
  public static Logger log = Logger.getLogger(GraphRanker.class);
  
  private Graph graph;
  private int numIteration;
  private double damper;
  private double convergeThreshold;
  private boolean useRelation;
  private boolean useRestart;
  private boolean disableWalk;

  public static Graph toGraph(DocumentSet documentSet) {
    Graph graph = new Graph();
    graph.load(documentSet);
    return graph;
  }
  
  public GraphRanker() {
    super();
    graph = new Graph();
    setNumIteration(DEFAULT_NUM_ITERATION);
    setConvergeThreshold(DEFAULT_CONVERGE_THRESHOLD);
    setDisableWalk(DEFAULT_DISABLE_WALK);
  }
  
  public void clear() {
    super.clear();
    graph.clear();
  }
  
  public double getConvergeThreshold() {
    return convergeThreshold;
  }

  public double getDamper() {
    return damper;
  }

  public Graph getGraph() {
    return graph;
  }
  
  public int getNumIteration() {
    return numIteration;
  }

  public boolean isDisableWalk() {
    return disableWalk;
  }

  public boolean isUseRelation() {
    return useRelation;
  }
  
  public boolean isUseRestart() {
    return useRestart;
  }

  public void load(EntityList entities, DocumentSet documentSet) {
    // load documents into graph
    graph.load(documentSet);
    
    if (disableWalk) return;
    
    // build transition matrix from graph
    SparseMatrix matrix = makeMatrix(graph);
    
    // make initial state vector
    SparseVector stateVector = makeInitVector(matrix.getColumnIDs());
    
    Set<SID> startNodes = stateVector.keySet();
    entities.clearWeight(getRankerID());
    
    for (int numIter = 0; numIter < numIteration; numIter++) {
      SparseVector sv = MatrixUtil.dotProduct(matrix, stateVector.normalize());
      if (damper < 1) {
        for (Entry<SID, Cell> entry : sv.entrySet()) {
          Cell cell = entry.getValue();
      	  double prevalue = cell.value;
          cell.value *= damper;
          if (useRestart) {
            if (startNodes.contains(entry.getKey()))
              cell.value += (1-damper) / startNodes.size();
          } else cell.value += (1-damper) / sv.size();
          log.debug(String.format("gww\t%d\t%s\t%g\t%g",numIter,entry.getKey().toLiteral().toString(),prevalue,cell.value));
        }
      }
      double euclidDistScore = MatrixUtil.euclideanDistance(sv, stateVector);
      stateVector = sv;
      if (euclidDistScore < convergeThreshold) {
        log.info("[Level " + numIter + "] Converged!!!");
        break;
      }
      if (numIter != 0 && numIter % NUM_ITER_TO_OUTPUT_LEVEL == 0)
        log.info("[Level " + numIter + "] " + euclidDistScore);
    }
    
    // insert weights into each node of the graph
    graph.assignWeights(stateVector);
    
    // transfer weights from state vector to entity list
    transferWeights(stateVector, entities, Graph.CONTENT_NODE_NAME);
  }

  public void setConvergeThreshold(double convergeThreshold) {
    this.convergeThreshold = convergeThreshold;
  }

  public void setDamper(double damper) {
    this.damper = damper;
  }
  
  public void setDisableWalk(boolean disableWalk) {
    this.disableWalk = disableWalk;
  }
  
  public void setNumIteration(int numIteration) {
    this.numIteration = numIteration;
  }

  public void setUseRelation(boolean useRelation) {
    this.useRelation = useRelation;
  }

  public void setUseRestart(boolean useRestart) {
    this.useRestart = useRestart;
  }

  private SparseVector makeInitVector(Set<SID> ids) {
    SparseVector stateVector = new SparseVector();
    if (seedDist.isEmpty()) {
      // if no seeds specified, start from all nodes
      for (SID id : ids)
        stateVector.put(id, 1.0);
    } else {
      // assign probabilities to seeds in state vector
      for (Entry<SID, Cell> entry : seedDist) {
    	
        SID id = entry.getKey();
        stateVector.put(id.toLower(), seedDist.getProbability(id));
      }
    }
    return stateVector;
  }
  
  private SparseMatrix makeMatrix(Graph graph) {
    SparseMatrix matrix = new SparseMatrix();
    NodeSet toNodeSet = new NodeSet();
    
    // start making transition matrix    
    for (Node fromNode : graph.getAllNodes()) {
      if (fromNode == null) {
        log.error("Node " + fromNode + " is null!");
        continue;
      }
      toNodeSet.clear();
      EdgeSet edgeSet = graph.getEdges(fromNode);
      double linkProb = 1.0 / edgeSet.size();
      
      for (String edgeLabel : edgeSet) {
        NodeSet toNodes = graph.followEdge(fromNode, edgeLabel);
        if (useRelation) {
          updateMatrix(matrix, fromNode, toNodes, edgeLabel, linkProb);
        } else {
          toNodeSet.addAll(toNodes);
        }
      }
      if (!useRelation)
        updateMatrix(matrix, fromNode, toNodeSet, null, linkProb);
    }
    return matrix;
  }

  private void transferWeights(SparseVector from, EntityList to, String type) {
    for (Entry<SID, Cell> entry : from) {
      SID id = entry.getKey();
      Node node = graph.getNode(id);
      if (node == null) continue;
      if (!node.getType().equals(type)) continue;
      EntityLiteral s  = id.toLiteral();
      double w = entry.getValue().value;
      Entity entity = to.add(s);
      if (entity == null) continue;
      entity.setWeight(getRankerID(), w);
    }
  }

  private void updateMatrix(SparseMatrix matrix, Node fromNode, NodeSet toNodes, 
                            String linkLabel, double linkProb) {
    double nodeProb = 1.0 / toNodes.size();
    for (Node toNode : toNodes)
      matrix.add(fromNode.getID(), toNode.getID(), linkProb * nodeProb);
  }
}
