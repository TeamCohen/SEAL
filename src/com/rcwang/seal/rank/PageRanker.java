/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

public class PageRanker extends GraphRanker {
  
  public static final String DESCRIPTION = "PageRank";
  public static final boolean USE_RESTART = false;
  public static final boolean USE_RELATION = false;
  public static final double DAMPER = 0.85;
  
  public PageRanker() {
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
}
