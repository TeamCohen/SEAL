/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.rank.Ranker;
import com.rcwang.seal.util.Cache;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public class EntityList implements Iterable<Entity>, Comparable<EntityList>, Serializable {
  
  public static Logger log = Logger.getLogger(EntityList.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final int MAX_SIZE = Cache.DEFAULT_MAX_SIZE;
  public static final double DELTA = 1e-8;
  public static final double OUTLIER_LENGTH_THRESHOLD = 1e-4;
  public static final double DEFAULT_LENGTH_RATIO = 0.1;  // 0.3
  
  public static final double THRESH = 0.1;  // precision at threshold
  public static final int RANK = 100; // precision at rank
  
  private static final long serialVersionUID = 7211782411429869181L;
  
  protected Map<EntityLiteral, Entity> entityMap;
  protected List<Entity> entityList;
  private EvalResult evalResult;
  private double listWeight;
  private int maxEntities;
  
  public EntityList() {
    entityMap = new HashMap<EntityLiteral, Entity>();
    entityList = new ArrayList<Entity>();
    setMaxEntities(WrapperFactory.MAX_CONTENTS_ALLOWED);
  }
  
  public EntityList(Collection<String> entityNames) {
    this();
    addAll(entityNames);
  }
  
  public EntityList(EntityList entityList) {
    this();
    addAll(entityList);
  }
  
  public EntityList(List<Entity> entityList) {
    this();
    addAll(entityList);
  }
  
  public EntityList(Set<Entity> entityList) {
    this();
    addAll(entityList);
  }
  
  /**
   * Remove all entities with a feature that is less than minValue
   * @param feature
   * @param minValue
   */
  public int absoluteFilter(Object feature, double minValue) {
    if (feature == null) return 0;
    boolean[] toRemove = new boolean[size()];
    for (int i = 0; i < size(); i++)
      if (get(i).getWeight(feature) < minValue)
        toRemove[i] = true;

    int numRemoved = remove(toRemove);
    if (numRemoved > 0)
      log.debug(numRemoved + " entities having " + feature + " < " + Helper.formatNumber(minValue, 5) + 
                " were filtered and " + size() + " remaining!");
    return numRemoved;
  }
  
  public Entity add(Entity entity) {
    if (entity == null) return null;
//    if (entity.isBad()) return;
    Entity e = get(entity);
    if (e == entity) return entity;
    
    // overwrites any existing entity
    if (e == null) {
      entityList.add(entity);
    } else {
      int index = entityList.indexOf(entity);
      if (index < 0) {
          log.error(entity.toString()+" not in entityList, but get("+entity.toString()+") returned "+e.toString()+", not entity or null");
      }
      entityList.set(index, entity);
    }
    entityMap.put(entity.getName(), entity);
    evalResult = null;
    return entity;
  }
  
  /**
   * Reduce the size of this list by the following procedure:
   * 1) sort this list by input 'feature' in descending order
   * 2) remove entities ranked lower than or equal to r
   * 3) reduce weight of entities above r by the weight of entity at r
   * where r is the half of MAX_SIZE
   * @param feature
   */
  public void reduceSize(Object feature) {
    if (size() <= MAX_SIZE) return;
    final int SIZE = (int) (MAX_SIZE * 0.5);
    log.info("Number of entities exceeds " + MAX_SIZE + ", reducing it to " + SIZE + "...");
    sortBy(feature, false);
    double midWeight = get(SIZE).getWeight(feature);
    
    for (int i = size()-1; i >= 0; i--) {
      if (i >= SIZE) remove(i);
      else get(i).addWeight(feature, -midWeight);
    }
  }
  
  public Entity add(String name) {
    if (Helper.empty(name)) return null;
//    Entity e1 = new Entity(name);
//    if (e1.isBad()) return null;
//    Entity e2 = get(e1);
//    if (e2 != null) return e2;
//    add(e1);
    Entity e = get(new EntityLiteral(name));
    if (e != null) return e;
    return add(new Entity(name));
  }
  
  public void addAll(Collection<String> names) {
    if (names == null) return;
    for (String name : names)
      add(name);
  }
  
  public void addAll(EntityList entities) {
    if (entities == null) return;
    for (Entity entity : entities)
      add(entity);
    addWeight(entities.getListWeight());
  }
  
  public void addAll(List<Entity> entities) {
    if (entities == null) return;
    for (Entity entity : entities)
      add(entity);
  }
  
  public void addAll(Set<Entity> entities) {
    if (entities == null) return;
    for (Entity entity : entities)
      add(entity);
  }
  
  /**
   * Add a weight to this entity list
   * @param weight
   */
  public void addWeight(double weight) {
    setListWeight(getListWeight() + weight);
  }
  
  /**
   * Add a weight to every entity in this list
   * @param feature
   * @param weight
   */
  public void addWeight(Object feature, double weight) {
    for (Entity entity : entityList)
      entity.addWeight(feature, weight);
  }
  
  public void assignScore(Object feature, boolean hasNoisySeeds) {
    if (feature == null) return;
    
    double maxScore = Double.NEGATIVE_INFINITY;
    double minScore = Double.POSITIVE_INFINITY;
    double weight;
    
    for (Entity entity : entityList) {
      weight = entity.getWeight(feature);
      maxScore = Math.max(weight, maxScore);
      minScore = Math.min(weight, minScore);
    }
    
    // prevents maxScore equals to minScore
    minScore -= DELTA;
    for (Entity entity : entityList) {
      weight = entity.getWeight(feature);
      if (!hasNoisySeeds && entity.isSeed()) // 2008-11-27
        weight = maxScore + weight * DELTA;
      weight = (weight - minScore) / (maxScore + Math.abs(maxScore) * DELTA - minScore);
      entity.setScore(weight);
    }
//    sortBy(Entity.SCORE, false);
  }
  
  public int averageFilter(Object feature) {
    double avg = this.getAverage(feature);
    return absoluteFilter(feature, avg);
  }
  
  public void clear() {
    entityMap.clear();
    entityList.clear();
    evalResult = null;
  }
  
  public int compareTo(EntityList entityList) {
    return Double.compare(entityList.getListWeight(), getListWeight());
  }
  
  public boolean contains(Entity entity) {
    return entityMap.containsKey(entity.getName());
  }
  
//  public boolean contains(String s) {
//    return entityMap.containsKey(s);
//  }
  
  public boolean contains(EntityLiteral e) {
      return entityMap.containsKey(e);
  }
  
  public boolean containsFeature(Object feature) {
    for (Entity entity : this)
      if (!entity.containsFeature(feature))
        return false;
    return true;
  }
  
  public int count(Object feature, double minValue) {
    int count = 0;
    for (Entity entity : this)
      if (entity.getWeight(feature) >= minValue)
        count++;
    return count;
  }
  
  /**
   * For every entity x that is contained by both lists, create a new entity x 
   * having such weight: r*w1 + (1-r)*w2, where w1 and w2 are the weights of an 
   * entity in *this* list and in the input list respectively.
   * @param entityList Input list
   * @param feature
   * @param ratio The proportion of weights taken from weight1 and weight2 for the new entity x.
   * @return A new list of intersected entities
   */
  public EntityList intersect(EntityList entityList, Object feature, double ratio) {
    if (ratio < 0 || ratio > 1)
      throw new IllegalArgumentException("Ratio must be between 0 and 1 (inclusively)");
    EntityList list = new EntityList();
    for (Entity e1 : this) {
      Entity e2 = entityList.get(e1);
      if (e2 == null) continue;
      
      // compute weight and score
      double weight = ratio * e1.getWeight(feature) + (1-ratio) * e2.getWeight(feature);
      if (weight <= 0) continue;
      double score = ratio * e1.getScore() + (1-ratio) * e2.getScore();
      if (score <= 0) continue;
      
      // assign weight and score
      Entity entity = new Entity(e1);
      entity.setWeight(feature, weight);
      entity.setScore(score);
      list.add(entity);
    }
    return list;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass())
      return false;
    final EntityList other = (EntityList) obj;
    if (entityMap == null) {
      if (other.entityMap != null)
        return false;
    } else if (!entityMap.keySet().equals(other.entityMap.keySet()))
      return false;
    return true;
  }
  
  /**
   * Computes the Euclidean Distance between entities of this class and the input entities
   * @param entities
   * @param feature
   * @return Euclidean distance
   */
  public double euclideanDistance(EntityList entities, Object feature) {
    Set<Entity> set = new HashSet<Entity>();
    set.addAll(this.getEntities());
    set.addAll(entities.getEntities());
    double sum = 0;
    for (Entity entity : set) {
      Entity e1 = this.get(entity);
      double w1 = (e1 == null) ? 0 : e1.getWeight(feature);
      Entity e2 = entities.get(entity);
      double w2 = (e2 == null) ? 0 : e2.getWeight(feature);
      sum += Math.pow(w1 - w2, 2);
    }
    return Math.sqrt(sum);
  }
  
  /**
   * Use for breaking ties between entities having the same score
   * @param feature
   */
  public void scoreByLength() {
    // calculate average length
    if (isEmpty()) return;
    double avg = getSumLength() / size();

    // calculate standard deviation
    double std = 0;
    for (Entity entity : this)
      std += Math.pow(entity.length() - avg, 2);
    std = Math.sqrt(std / size());
    if (std == 0) return;

    log.info("Diminishing entities with abnormal length (avg: " + Helper.formatNumber(avg, 2) + 
             ", std: " + Helper.formatNumber(std, 2) + ")...");
    
    // assign probability
    for (int i = 0; i < size(); i++) {
      Entity entity = get(i);
      double p = Helper.getPDF(entity.length(), avg, std);
      
      if (p < OUTLIER_LENGTH_THRESHOLD) {
        log.debug("Diminishing \"" + entity.getName() + "\": " + p);
        entity.setScore(0);
      } else {
        // add a very tiny weight to break any tie
        entity.addWeight(Entity.SCORE, p * DELTA);
      }
    }
  }
  
  public void sortByScore() {
    sortBy(Entity.SCORE, false);
  }
  
  /**
   * Filter out relational entities based on the type of relation
   * @param type 0: Many to Many, 1: One to Many, 2: Many to One, 3: One to One
   */
  public void filterRelation(int type) {
    if (!isRelational() || type == 0) return;
    boolean[] toRemove = new boolean[size()];
    Set<String> set = new HashSet<String>();
    
    for (int i = 0; i < size(); i++) {
      String[] names = get(i).getNames();
      
      if (type == 1 || type == 2) {
        String s = (type == 1) ? names[0] : names[1];
        toRemove[i] = !set.add(s);
      } else if (type == 3) {
        toRemove[i] = !set.add(names[0]) || !set.add(names[1]);
      }
    }
    int numRemoved = remove(toRemove);
    if (numRemoved > 0) {
      String s = null;
      switch (type) {
        case 1: s = "One to Many"; break;
        case 2: s = "Many to One"; break;
        case 3: s = "One to One"; break;
      }
      log.info(numRemoved + " entities were removed: relational type of " + type + " (" + s + ")");
    }
  }
  
  public Entity get(Entity entity) {
    return entityMap.get(entity.getName());
  }
  
  public Entity get(int i) {
    return entityList.get(i);
  }
  
  public Entity get(EntityLiteral name) {
      return entityMap.get(name);
  }
  
//  public Entity get(String name) {
//    return entityMap.get(name);
//  }
  
  public double getAverage(Object feature) {
    double sum = this.getSumWeights(feature);
    return sum / this.size();
  }
  
  public List<Entity> getEntities() {
    return entityList;
  }
  
  /**
   * Gather entities containing the input feature
   * @param feature
   * @return
   */
  public EntityList getEntities(Object feature) {
    return getEntities(feature, Double.MIN_VALUE);
  }
  
  /**
   * Gather entities with the input feature having at least some input weight
   * @param feature
   * @param weight
   * @return an EntityList of entities with a feature of at least some weight
   */
  public EntityList getEntities(Object feature, double weight) {
    EntityList list = new EntityList();
    for (Entity entity : this)
      if (entity.containsFeature(feature) && 
          entity.getWeight(feature) >= weight)
        list.add(entity);
    return list;
  }
  
  public List<EntityLiteral> getEntityLiterals() {
      List<EntityLiteral> list = new ArrayList<EntityLiteral>();
      for (Entity entity : entityList)
        list.add(entity.getName());
      return list;
    }
  public List<String[]> getEntityNames() {
    List<String[]> list = new ArrayList<String[]>();
    for (Entity entity : entityList)
      list.add(entity.getNames());
    return list;
  }
  
  public EvalResult getEvalResult() {
    return evalResult;
  }

  public double getListWeight() {
    return listWeight;
  }
  
  public int getMaxEntities() {
    return maxEntities;
  }
  
  public Double getMaxWeight(Object feature) {
    Double maxWeight = null;
    for (Entity entity : entityList) {
      double weight = entity.getWeight(feature);
      if (maxWeight == null) maxWeight = weight;
      else maxWeight = Math.max(weight, maxWeight);
    }
    return maxWeight;
  }
  
  public Integer getMinStringLength() {
    Integer min = null;
    for (Entity entity : this) {
      String[] names = entity.getNames();
      for (String name : names) {
        if (min == null) min = name.length();
        else min = Math.min(name.length(), min);
      }
    }
    return min;
  }
  
  /**
   * Returns the counts of incorrect (0), correct (1), and synonym (2) entities
   * @param threshold count only the entities having
   * 1) a rank higher than threshold (if threshold is at least one), or
   * 2) scores above threshold (if threshold is less than one)
   * @return an array of counts, indexed by the 'correctness' integer (see above)
   */
  public int[] getCorrectness(double threshold) {
    boolean isRank = (threshold >= 1);
    int[] counters = new int[Entity.NUM_CORRECT_TYPES];
    if (threshold <= 0) return counters;
    for (Entity entity : this) {
      if (isRank) {
        if (counters[Entity.INCORRECT] + counters[Entity.FIRST_CORRECT] == threshold) break;
      } else if (entity.getScore() < threshold) break;
      int correctness = entity.getCorrect();
      if (correctness == Entity.UNKNOWN_CORRECT) {
        log.warn("Found an entity with unknown correctness!");
        continue;
      }
      else counters[correctness]++;
    }
    return counters;
  }
  
  public double getPrecision(double threshold, int[] counters) {
    if (counters == null)
      counters = getCorrectness(threshold);
    int total = counters[Entity.INCORRECT] + counters[Entity.FIRST_CORRECT];
    return (total == 0) ? 0 : (double) counters[Entity.FIRST_CORRECT] / total;
  }
  
  public double getRecall(double threshold, int[] counters) {
    int numTrue = evalResult.numGoldEntity;
    if (numTrue <= 0) return 0;
    if (counters == null)
      counters = getCorrectness(threshold);
    return (double) counters[Entity.FIRST_CORRECT] / numTrue;
  }
  
  public double getF1(double threshold, int[] counters) {
    if (counters == null)
      counters = getCorrectness(threshold);
    double p = getPrecision(threshold, counters);
    double r = getRecall(threshold, counters);
    return (p+r == 0) ? 0 : (2*p*r) / (p+r);
  }
  
  public double getSumLength() {
    double sum = 0;
    for (Entity entity : this)
      sum += entity.length();
    return sum;
  }
  
  public double getSumOfLogWeights(Object feature) {
    double sum = 0;
    boolean isLogRanker = Ranker.isLogRanker(feature);
    for (Entity entity : entityList) {
      double weight = entity.getWeight(feature);
      sum += isLogRanker ? weight : Math.log(weight);
    }
    return sum;
  }
  
  public double getSumWeights(Object feature) {
    double sum = 0;
    for (Entity entity : entityList)
      sum += entity.getWeight(feature);
    return sum;
  }
  
  /**
   * Returns top entities
   * 1) having at least minEntities and at most maxEntities
   * 2) where each feature weight is at least the input threshold
   * @param feature
   * @param threshold
//   * @param minEntities
   * @param maxEntities
   * @return a sorted EntityList object
   */
//  public EntityList getTopEntities(Object feature, double threshold, int minEntities, int maxEntities) {
  public EntityList getTopEntities(Object feature, double threshold, int maxEntities) {
    EntityList list = this.getEntities(feature, threshold);
    /*if (list.size() < minEntities) {
      list = new EntityList(this);
      list.sortBy(feature, false);
      return list.subList(0, minEntities);
    } else {*/
      list.sortBy(feature, false);
      if (list.size() > maxEntities)
        return list.subList(0, maxEntities);
      else return list;
//    }
  }
  
  @Override
  public int hashCode() {
    return 31 + (entityMap == null ? 0 : entityMap.hashCode());
  }

  public boolean hasSpace() {
    for (Entity entity : this)
      if (entity.getName().toString().contains(" "))
        return true;
    return false;
  }
  
  /**
   * Preserves the order of the EntityList in this object
   * Retains weights of the entities in this object
   * @param entityList
   * @return overlapping entities of the input list and this class
   */
  public EntityList intersect(EntityList entityList) {
    EntityList list = new EntityList();
    for (Entity entity : this)
      if (entityList.contains(entity))
        list.add(entity);
    return list;
  }
  
  public boolean isEmpty() {
    return size() == 0;
  }
  
  public boolean isIntersected(EntityList entityList) {
    EntityList smaller = entityList, larger = this;
    if (this.size() < entityList.size()) {
      smaller = this;
      larger = entityList;
    }
    for (Entity entity : smaller)
      if (larger.contains(entity))
        return true;
    return false;
  }
  
  public boolean isRelational() {
    for (Entity entity : this)
      if (!entity.isRelational())
        return false;
    return true;
  }
  
  public Iterator<Entity> iterator() {
    return entityList.iterator();
  }
  
  public void merge(Entity entity) {
    Entity e = get(entity);
    if (e == null) add(entity);
    else e.merge(entity);
  }
  
  public void merge(EntityList entityList) {
    for (Entity entity : entityList)
      merge(entity);
  }
  
  public void normalize(Object feature) {
    double sum = getSumWeights(feature);
    if (sum == 0) return;
    for (Entity entity : this) {
      double weight = entity.getWeight(feature);
      if (weight < 0) {
        log.warn("Warn: " + entity.getName() + " contains negative weight of " + weight + " for feature: " + feature);
        continue;
      }
      entity.setWeight(feature, weight/sum);
    }
  }
  
  public void clearWeight(Object feature) {
    if (feature == null) return;
    for (Entity entity : this)
      entity.remove(feature);
  }

  public EntityList product(EntityList entityList, Object feature) {
    EntityList list = new EntityList();
    for (Entity e1 : this) {
      Entity e2 = entityList.get(e1);
      if (e2 == null) continue;
      double weight = e1.getWeight(feature) * e2.getWeight(feature);
      if (weight <= 0) continue;
      Entity entity = new Entity(e1);
      entity.setWeight(feature, weight);
      list.add(entity);
    }
    return list;
  }
  
  public int relativeFilter(Object feature, double ratio) {
    if (feature == null || ratio <= 0) return 0;
    Double maxWeight = getMaxWeight(feature);
    if (maxWeight == null) return 0;
    return absoluteFilter(feature, maxWeight * ratio);
  }
  
  /**
   * remove those marked entities
   * @param toRemove
   */
  public int remove(boolean[] toRemove) {
    if (toRemove == null) return 0;
    int size = Math.min(entityList.size(), toRemove.length);
    int offset = 0;
    
    for (int i = 0; i < size; i++) {
      if (!toRemove[i]) continue;
//      log.debug("Removing: " + entityList.get(i-offset).getName());
      remove(i-offset);
      offset++;
    }
    return offset;
  }
  
  public void remove(Entity entity) {
    if (entity == null) return;
    entityList.remove(entity);
    entityMap.remove(entity.getName());
  }
  
  public Entity remove(int i) {
    Entity entity = entityList.remove(i);
    entityMap.remove(entity.getName());
    return entity;
  }
  
  public void remove(Object feature) {
    for (Entity entity : this)
      entity.remove(feature);
  }
  
  public void remove(String entity) {
    if (entity == null) return;
    entityList.remove(entity);
    entityMap.remove(entity);
  }
  
  public void removeAll(EntityList list) {
    if (list == null) return;
    for (Entity entity : list)
      this.remove(entity);
  }
  
  public int removeAll(Object feature) {
    int sum = 0;
    for (Entity entity : entityList)
      sum += entity.remove(feature) ? 1 : 0;
    return sum;
  }
  
  public void removeContains(String superStr) {
    if (superStr == null) return;
    boolean[] toRemove = new boolean[this.size()];
    for (int i = 0; i < this.size(); i++) {
      String s = this.get(i).getName().toString();
      toRemove[i] = superStr.contains(s);
    }
    remove(toRemove);
  }

  public void setEvalResult(EvalResult evalResult) {
    this.evalResult = evalResult;
  }

  /**
   * Set the weight of this entity list
   * @param weight
   */
  public void setListWeight(double weight) {
    this.listWeight = weight;
  }
  
  public void setMaxEntities(int maxEntitiesInXML) {
    this.maxEntities = maxEntitiesInXML;
  }
  
  /**
   * Set weight to all entities in this list
   * @param feature
   * @param weight
   */
  public void setWeight(Object feature, double weight) {
    for (Entity entity : this)
      entity.setWeight(feature, weight);
  }
  
  public void shrink(Object feature, double shrinkPower) {
    shrink(feature, shrinkPower, DEFAULT_LENGTH_RATIO);
  }
  
  /**
   * Collapse a sorted list of entities, from top to bottom
   * Reduce the number of entities to maxEntities
   * @param byNameLength true to pre-sort by string length, otherwise by score, then collapse!
   * @param entityList
   * @return
   */
  public void shrink(Object feature, double shrinkPower, double lengthRatio) {
    log.info("Shrinking list with a shrinking power of " + shrinkPower + 
             " and length ratio of " + lengthRatio + "...");
    if (size() <= 1) return;
//    boolean[] toRemove = new boolean[size()];
    String hasSpace = hasSpace() ? " " : "";
    sortByNameLength(false);
    
    for (int i = 0; i < size(); i++) {
      Entity e1 = get(i);
      String name1 = e1.getName().toString();
      double w1 = e1.getWeight(feature);
      
      for (int j = i+1; j < size(); j++) {
//        if (toRemove[j]) continue;
        Entity e2 = get(j);
        double w2 = e2.getWeight(feature);
        if (w2 == 0) continue;
        String name2 = e2.getName().toString();

        if (name1.length() <= name2.length()) continue;
        if (w1 * shrinkPower <= w2) continue;
        double z = (double) name2.length() / name1.length();
        if (z < lengthRatio) continue;
        
        if (name1.startsWith(name2 + hasSpace) || 
            name1.endsWith(hasSpace + name2)) {
          log.debug(name1 + " (" + feature + ": " + Helper.formatNumber(w1, 5) + ") ===> " + 
                    name2 + " (" + feature + ": " + Helper.formatNumber(w2, 5) + ")");
          if (w2 > w1) {
            // assign first entity the weight of second entity
            e1.setWeight(feature, w2);
            e2.setWeight(feature, 0);
          }
//          toRemove[j] = true;
        }
      }
    }
    /*int numRemoved = remove(toRemove);
    if (numRemoved > 0)
      log.info(numRemoved + " entities were shrunk and " + size() + " remaining!");*/
    sortBy(feature, false);
  }
  
  public void shuffle(Random random) {
    if (random == null) return;
    Collections.shuffle(entityList, random);
  }
  
  public int size() {
    return entityList.size();
  }

  public void sort() {
    sortBy(Entity.SCORE, false);
  }

  public void sortBy(final Object feature, final boolean ascending) {
    Comparator<Entity> c = new Comparator<Entity>() {
      public int compare(Entity e1, Entity e2) {
        double w1 = e1.getWeight(feature), w2 = e2.getWeight(feature);
        if (ascending) return Double.compare(w1, w2);
        else return Double.compare(w2, w1);
      }
    };
    sortBy(c, feature, ascending);
  }
  
  public void sortByNameLength(final boolean ascending) {
    sortBy(Entity.NAME_LENGTH, ascending);
  }
  
  public EntityList subList(int fromIndex, int toIndex) {
    EntityList subList = new EntityList();
    toIndex = Math.min(toIndex, entityList.size());
    for (int i = fromIndex; i < toIndex; i++)
      subList.add(entityList.get(i));
    return subList;
  }
  
  public String toDetails(int numTop) {
    return toDetails(numTop, null);
  }
  
  public String getOneLineStat() {
    StringBuffer buf = new StringBuffer("[ ");
    String map = Helper.formatNumber(evalResult.meanAvgPrecision*100, 2);
    buf.append("MAP: ").append(map).append(", ");
    
    String pRank = Helper.formatNumber(getPrecision(RANK, null)*100, 2);
    buf.append("P@" + RANK + ": ").append(pRank).append(", ");
    
    int[] counters = getCorrectness(THRESH);
    int num = counters[Entity.FIRST_CORRECT] + counters[Entity.INCORRECT];
    double p = getPrecision(THRESH, counters), r = getRecall(THRESH, counters);
    String pStr = Helper.formatNumber(p*100, 2), rStr = Helper.formatNumber(r*100, 2);
    String f1Str = Helper.formatNumber(getF1(THRESH, counters)*100, 2);
    buf.append("F1@" + THRESH + ": ").append(f1Str).append(" ");
    buf.append("(P:").append(pStr).append(", ");
    buf.append("R:").append(rStr).append(", ");
    buf.append("#:").append(num).append(")");
    return buf.append(" ]").toString();
  }
  
  public String toDetails(int numTop, Object feature) {
    final int LINE_WIDTH = 80;
    numTop = Math.min(numTop, entityList.size());
    
//    StringBuffer buf = new StringBuffer("\n");
    StringBuffer buf = new StringBuffer();
    String title = "[ Listing " + numTop + " out of " + entityList.size() + " Entities ]";
    buf.append("\t").append(Helper.center(title, '-', 80)).append("\n");
    for (int i = 0; i < numTop; i++) {
      Entity entity = entityList.get(i);
      buf.append(i+1).append(".\t");
      buf.append(entity.toString(feature)).append("\n");
    }
    buf.append("\t");
    if (evalResult != null) {
      buf.append(Helper.center(getOneLineStat(), '-', LINE_WIDTH));
    } else {
      buf.append(Helper.repeat('-', LINE_WIDTH));
    }
    return buf.toString();
  }

  /**
   * Returns a list of entities sorted in score order
   */
  public String toString() {
    Set<Entity> entities = new TreeSet<Entity>();
    entities.addAll(getEntities());
    
    StringBuffer buf = new StringBuffer();
    for (Entity entity : entities) {
      if (buf.length() > 0) buf.append(", ");
      buf.append(entity.getCorrectMarker()).append(entity.getName());
    }
    return buf.toString();
  }
  
  public String toXML() {
    return XMLUtil.document2String(toXMLElement(null));
  }
  
  public Element toXMLElement(Document document) {
    XMLUtil xml = new XMLUtil(document);
    Element entitiesNode = xml.createElement("entities", null);
    xml.createAttrsFor(entitiesNode, new Object[]{
        "numEntities", entityList.size(),
    });
    int rank = 1;
    for (Entity entity : entityList) {
      // reduce the amount of garbage saved to the disk
      if (rank > maxEntities) break;
      
      List<Object> pairs = Ranker.toFeatureValuePairs(entity);
      pairs.add("rank"); pairs.add(rank++);
      pairs.add("score"); pairs.add(entity.getScore());
      if (entity.getCorrect() != -1) {
        pairs.add("correct"); pairs.add(entity.getCorrect());
      }
      Element entityNode = xml.createElementBelow(entitiesNode, "entity", entity.getOriginal());
      xml.createAttrsFor(entityNode, pairs.toArray(new Object[pairs.size()]));
    }
    return entitiesNode;
  }

  public void transferWeight(Object from, Object to) {
    for (Entity entity : this)
      entity.setWeight(to, entity.getWeight(from));
  }

  private void sortBy(Comparator<Entity> c, Object feature, boolean ascending) {
    log.debug("Sorting entities by " + feature + " in " + (ascending ? "a" : "de") + "scending order...");
    Collections.sort(entityList, c);
  }

    public Entity add(EntityLiteral content) {
        Entity e = new Entity(content);
        return add(e);
    }
}