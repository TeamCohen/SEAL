/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.util.Distribution;
import com.rcwang.seal.util.StringFactory;

public abstract class Ranker {
  
  public static enum Feature { ETF, EDF, EWF, WLW, BSW, PRW, GWW}

  // list of rankers that assign log weights
  public static final Feature[] LOG_RANKERS = new Feature[] { Feature.BSW };
  
  public static Logger log = Logger.getLogger(Ranker.class);
  
  private Feature rankerID;
  protected DocumentSet documentSet;
  protected Distribution seedDist;
//  protected Distribution entityDist;
  protected String description;
  protected boolean dataModified;
  
  public static String describe(Feature feature) {
    Ranker ranker = toRanker(feature);
    if (ranker == null) return null;
    return ranker.getDescription();
  }
  
  public static boolean isLogRanker(Object feature) {
    return Arrays.asList(LOG_RANKERS).contains(feature);
  }
  
  public static List<Object> toFeatureValuePairs(Entity entity) {
    List<Object> list = new ArrayList<Object>();
    for (Feature feature : Feature.values()) {
      if (entity.containsFeature(feature)) {
        list.add(feature);
        list.add(entity.getWeight(feature));
      }
    }
    return list;
  }
  
  public static Ranker toRanker(Feature feature) {
    Ranker ranker = null;
    switch(feature) {
      case ETF: ranker = new TermFreqRanker(); break;
      case EDF: ranker = new DocFreqRanker(); break;
      case EWF: ranker = new WrapperFreqRanker(); break;
      case WLW: ranker = new WrapperLengthRanker(); break;
      case PRW: ranker = new PageRanker(); break;
      case GWW: ranker = new GraphWalkRanker(); break;
      case BSW: ranker = new BayesianRanker(); break;
      default: log.error("Unsupported ranker: " + feature);
    }
    if (ranker != null)
      ranker.setRankerID(feature); 
    return ranker;
  }
  
  protected static int getHashCode(Object o1, Object o2, Object o3) {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((o1 == null) ? 0 : o1.hashCode());
    result = prime * result + ((o2 == null) ? 0 : o2.hashCode());
    result = prime * result + ((o3 == null) ? 0 : o3.hashCode());
    return result;
  }
  
  public Ranker() {
//    entityDist = new Distribution();
    seedDist = new Distribution();
  }
  
  public void addSeed(EntityLiteral seed, double weight) {
    seedDist.add(StringFactory.toID(seed), weight);
  }
  
  public void addSeeds(EntityList seeds, Object feature) {
    if (seeds == null || feature == null) return;
    for (Entity seed : seeds)
      addSeed(seed.getName(), seed.getWeight(feature));
  }
  
  public void addUniformSeeds(EntityList seeds) {
    if (seeds == null) return;
    for (Entity seed : seeds)
      addSeed(seed.getName(), 1);
  }
  
  /*public void assignWeights(EntityList entityList, DocumentSet documents) {
    Double weight;
    for (Entity entity : entityList) {
      weight = getRecommendedWeight(entity.getName());
      if (weight != null)
        entity.setWeight(getRankerID(), weight);
    }
    
    for (Document document : documents) {
      if (this instanceof GraphRanker) {
        // this ranker is a GraphRanker
        String urlStr = document.getURL().toString();
        weight = getProbability(urlStr);
      } else {
        // otherwise sum up weights of entities contained by this document
        weight = (double) 0;
        for (String content : document.getExtractions()) {
          Entity e = entityList.get(content);
          weight += (e == null) ? 0 : e.getWeight(getRankerID());
        }
      }
      document.setWeight(weight == null ? 0 : weight);
    }
  }*/

  public void clear() {
    seedDist.clear();
//    entityDist.clear();
  }
  
  public String getDescription() {
    return description;
  }
  
  /*public Double getProbability(String item) {
    return entityDist.getProbability(item);
  }*/
  
  public Feature getRankerID() {
    return rankerID;
  }
  
  /*public Double getRecommendedWeight(String item) {
    if (isLogRanker())
      return getWeight(item);
    return getProbability(item);
  }*/
  
  public Distribution getSeedDist() {
    return seedDist;
  }

  /*public Double getWeight(String item) {
    return entityDist.getWeight(item);
  }*/

  public boolean isLogRanker() {
    return isLogRanker(getRankerID());
  }
  
  public abstract void load(EntityList entities, DocumentSet documentSet);

  private void setRankerID(Feature rankerID) {
    this.rankerID = rankerID;
  }

  protected void setDescription(String description) {
    this.description = description;
  }
}
