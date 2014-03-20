/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

public class GraphWalkRanker extends GraphRanker {

  public static final String DESCRIPTION = "Random Walk";
  public static final boolean USE_RESTART = true;
  public static final boolean USE_RELATION = true;
  public static final double DAMPER = 0.99; // 0.85
  
  
  public GraphWalkRanker() {
    super();
    setDescription(DESCRIPTION);
    setUseRestart(USE_RESTART);
    setUseRelation(USE_RELATION);
    setDamper(DAMPER);
  }
  
  /*public void reset() {
    super.clear();
    setUseRestart(USE_RESTART);
    setUseRelation(USE_RELATION);
    setDamper(DAMPER);
  }*/
  
  /*protected Distribution getNodeWeights(String linkLabel, Set<GraphId> ids) {
    if (!linkLabel.equals("derives"))
      return super.getNodeWeights(linkLabel, ids);
    Distribution dist = new Distribution();
    for (GraphId id : ids)
      dist.add(id.getShortName(), id.getShortName().length());
    return dist;
  }*/
  
}
