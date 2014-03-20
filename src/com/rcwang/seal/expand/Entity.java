/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.Originator;
import com.rcwang.seal.util.StringFactory;

public class Entity implements Comparable<Entity> {

  public static Logger log = Logger.getLogger(Entity.class);
  
  /** final score of this entity */
  public static final String SCORE = "Score";
  /** a name for the weight of string length */
  public static final String NAME_LENGTH = "Length";
  public static final String RELATION_SEPARATOR = "::";
  
  /** unknown correctness */
  public static final int UNKNOWN_CORRECT = -1;
  /** incorrect */
  public static final int INCORRECT = 0; 
  /** first correct */
  public static final int FIRST_CORRECT = 1;
  /** duplicated correct (synonym) */
  public static final int DUP_CORRECT = 2; 
  /** total # of types of correctness (ignoring the unknown) */
  public static final int NUM_CORRECT_TYPES = 3;  
  
  // a storage for weights
  private Map<String, Double> weightMap = null;
  // the canonical name of this entity
  private String[] names;
  // correctness of the entity (assigned by evaluator)
  private int correct = UNKNOWN_CORRECT;
  // total length of names
  private int length = 0;
  // is this a seed entity?
  private boolean isSeed = false;
  
  public static String[] splitRelation(String name) {
    return name.split(Pattern.quote(RELATION_SEPARATOR));
  }
  
  public Entity(Entity entity) {
    if (entity == null) return;
    setNames(entity.getNames());
    setSeed(entity.isSeed());
    setCorrect(entity.getCorrect());
  }
  
  public static Entity parseEntity(String name) {
      String[] names = splitRelation(name);
      switch (names.length) {
      case 1: return new Entity(names[0]);
      case 2: return new Entity(names[0],names[1]);
      }
      log.error("Bad entity string '"+name+"': Entities for parsing should have zero or one instance of the relation delimiter '"+RELATION_SEPARATOR+"'.");
      
      return null;
  }
  
  public Entity(String name) {
      if (name.contains(RELATION_SEPARATOR) && !GlobalVar.getGlobalVar().getExplicitRelations()) {
          log.warn(name+": Creating a relation using the Entity(String) constructor has been disabled. Use Entity.parseEntity(String) instead.");
      }
    setNames(new String[] {name});
  }
  
  public Entity(String name1, String name2) {
    setNames(new String[] {name1, name2});
  }
  
  public Entity(EntityLiteral content) {
      if (content.arg2 == null) setNames(new String[]{content.arg1});
      else setNames(new String[]{content.arg1,content.arg2});
}

public void addWeight(Object feature, double weight) {
    setWeight(feature, getWeight(feature) + weight);
  }
  
  public void clear() {
    if (weightMap != null)
      weightMap.clear();
  }
  
  /**
   * Compare in the order of:
   * 1) descending score
   * 2) ascending length
   * 3) ascending string
   */
  public int compareTo(Entity entity) {
    int result1 = Double.compare(entity.getScore(), getScore());
    if (result1 == 0) {
      int result2 = Double.compare(length(), entity.length());
      return (result2 == 0) ? getName().toString().compareTo(entity.getName().toString()) : result2;
    } else return result1;
  }
  
  public boolean containsFeature(Object feature) {
    return weightMap != null && weightMap.containsKey(feature.toString());
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass())
      return false;
    final Entity other = (Entity) obj;
    if (!Arrays.equals(names, other.names))
      return false;
    return true;
  }
  
  public int getCorrect() {
    return correct;
  }

  public String getCorrectMarker() {
    switch (correct) {
      case 0: return "-";
      case 1: return "+";
      case 2: return "*";
      default: return "";
    }
  }
  
  public EntityLiteral getName() {
    if (names == null) return null;
    return new EntityLiteral(names);
  }
  
  public String[] getNames() {
    return names;
  }
  
  public String getOriginal() {
    if (names == null) return null;
    String s1 = Originator.getOriginal(names[0]);
    if (names.length == 1) return s1;
    String s2 = Originator.getOriginal(names[1]);
    return s1 + RELATION_SEPARATOR + s2;
  }
  
  public String[] getOriginals() {
    if (names == null) return null;
    String[] array = new String[names.length];
    for (int i = 0; i < names.length; i++)
      array[i] = Originator.getOriginal(names[i]);
    return array;
  }
  
  public double getScore() {
    return getWeight(SCORE);
  }
  
  public double getWeight(Object feature) {
    if (feature == null) return 0;
    if (feature.equals(NAME_LENGTH)) return length();
    if (weightMap == null) return 0;
    Double weight = weightMap.get(feature.toString());
    return weight == null ? 0 : weight;
  }
  
  @Override
  public int hashCode() {
    return 31 + Arrays.hashCode(names);
  }

  /*public boolean isBad() {
    return names == null;
  }*/
  
  public boolean isCorrect() {
    return correct > 0;
  }
  
  public boolean isIncorrect() {
    return correct == INCORRECT;
  }
  
  public boolean isRelational() {
    return names != null && names.length > 1;
  }
  
  public boolean isSeed() {
    return isSeed;
  }

  public int length() {
    return length;
  }

  /**
   * Add all weights of the input entity
   * @param entity
   */
  public void merge(Entity entity) {
    if (entity.weightMap == null) return;
    for (Map.Entry<String, Double> entry : entity.weightMap.entrySet()) {
      String key = entry.getKey();
      addWeight(key, entry.getValue());
    }
  }

  public boolean remove(Object feature) {
    if (weightMap == null) return false;
    return (weightMap.remove(feature.toString()) != null);
  }

  public void setCorrect(int correct) {
    this.correct = correct;
  }
  
  public void setScore(double score) {
    setWeight(SCORE, score);
  }

  public void setSeed(boolean isSeed) {
    this.isSeed = isSeed;
  }

  public void setWeight(Object feature, double weight) {
    if (weightMap == null)
      weightMap = new HashMap<String, Double>();
    weightMap.put(StringFactory.get(feature.toString()), weight);
  }

  @Override
  public String toString() {
    return toString(null);
  }
  
  public String toString(Object feature) {
//    final int NUM_ORIGINALS = 3;
    final int NUM_DECIMAL = 6;
    
    Set<Object> features = new TreeSet<Object>();
    if (weightMap != null) {
      if (feature == null) {
        features.addAll(weightMap.keySet());
        features.remove(Entity.SCORE);
      } else features.add(feature);
    }
    StringBuffer buf = new StringBuffer();
    for (Object f : features) {
      String weightStr = Helper.formatNumber(getWeight(f), NUM_DECIMAL);
      buf.append(f).append(":").append(weightStr).append("\t");
    }
    String scoreStr = Helper.formatNumber(getScore(), NUM_DECIMAL);
    buf.append(scoreStr).append(" ").append(getCorrectMarker());
    String originals = getOriginal();
//    String originals = isRelational() ? getOriginal() : Originator.getOriginals(getName(), NUM_ORIGINALS);
    buf.append(originals);

    return buf.toString();
  }

  // TODO: change from String to SID?
  private void setNames(String[] inputs) {
    if (inputs == null) return;
    length = 0;
    String[] names = new String[inputs.length];
    for (int i = 0; i < inputs.length; i++) {
//      String name = WrapperFactory.tidy(inputs[i]);
      String name = inputs[i];
      if (Helper.empty(name)) return;
      names[i] = StringFactory.get(name);
      length += name.length();
    }
    this.names = names;
  }
}