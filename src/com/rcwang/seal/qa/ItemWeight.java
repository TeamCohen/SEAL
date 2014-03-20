/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.util.StringFactory;
import com.rcwang.seal.util.StringFactory.SID;

public class ItemWeight implements Comparable<ItemWeight>, Serializable {

  private static final long serialVersionUID = -891318175240381217L;
  private SID item;
  private double weight;

  public static double getMaxWeight(List<ItemWeight> iwList) {
    double maxWeight = Double.MIN_VALUE;
    for (ItemWeight iw : iwList)
      maxWeight = Math.max(iw.getWeight(), maxWeight);
    return maxWeight;
  }
  
  // returns the rank of the entity just one rank below the threshold
  // note: input list 'iwList' will be sorted in descending order
  public static int getRankAt(List<ItemWeight> iwList, double threshold, boolean isAbsolute) {
//    Collections.sort(iwList);
    double maxWeight = (iwList.isEmpty() || isAbsolute) ? 1 : iwList.get(0).getWeight();
    int index = 0;
    for (; index < iwList.size(); index++) {
      double weight = iwList.get(index).getWeight();
      if (weight < maxWeight * threshold)
        break;
    }
    return index;
  }
  
  public static List<ItemWeight> getTopItem(List<ItemWeight> iwList, double threshold, boolean isAbsolute) {
    Collections.sort(iwList);
    int rank = ItemWeight.getRankAt(iwList, threshold, isAbsolute);
    return iwList.subList(0, rank);
  }
  
  public static List<ItemWeight> intersect(List<ItemWeight> list1, List<ItemWeight> list2) {
    Map<String, Double> hash2 = toHashMap(list2, true);
    List<ItemWeight> iwList = new ArrayList<ItemWeight>();
    for (ItemWeight iw1 : list1) {
      String item1 = iw1.getItem().toLowerCase();
      Double weight2 = hash2.get(item1);
      if (weight2 == null) continue;
      iwList.add(new ItemWeight(item1, iw1.getWeight() * weight2));
    }
    Collections.sort(iwList);
    return iwList;
  }
  
  public static void normWeights(List<ItemWeight> iwList) {
    double maxWeight = getMaxWeight(iwList);
    for (ItemWeight iw : iwList)
      iw.setWeight(iw.getWeight() / maxWeight);
  }
  
  public static List<ItemWeight> parseEntityInfo(List<Entity> entityList) {
    List<ItemWeight> iwList = new ArrayList<ItemWeight>();
    for (Entity entity : entityList)
      iwList.add(new ItemWeight(entity.getName(), entity.getScore()));
//      iwList.add(new ItemWeight(entity.name, entity.gww));
    return iwList;
  }
  
  public static List<ItemWeight> parseStringList(List<String> strList, double weight) {
    List<ItemWeight> iwList = new ArrayList<ItemWeight>();
    for (String s : strList)
      iwList.add(new ItemWeight(s, weight));
    return iwList;
  }
  
  public static Map<String, Double> toHashMap(List<ItemWeight> iwList, boolean toLowerCase) {
    Map<String, Double> hash = new HashMap<String, Double>();
    for (ItemWeight iw : iwList) {
      String item = toLowerCase ? iw.getItem().toLowerCase() : iw.getItem();
      hash.put(item, iw.getWeight());
    }
    return hash;
  }
  
  public static List<String> toItemList(List<ItemWeight> iwList) {
    List<String> strList = new ArrayList<String>();
    for (ItemWeight iw : iwList)
      strList.add(iw.getItem());
    return strList;
  }
  
  public static List<ItemWeight> union(List<ItemWeight> list1, List<ItemWeight> list2, double weight) {
    Set<ItemWeight> iwSet = new HashSet<ItemWeight>();
    Map<String, Double> hash2 = toHashMap(list2, true);
    for (ItemWeight iw1 : list1) {
      String item1 = iw1.getItem().toLowerCase();
      Double weight2 = hash2.get(item1);
      weight2 = (weight2 == null) ? weight : weight2;
      iwSet.add(new ItemWeight(item1, iw1.getWeight() * weight2));
    }
    for (ItemWeight iw2 : list2) {
      String item2 = iw2.getItem().toLowerCase();
      iwSet.add(new ItemWeight(item2, iw2.getWeight() * weight));
    }
    List<ItemWeight> iwList = new ArrayList<ItemWeight>(iwSet);
    Collections.sort(iwList);
    return iwList;
  }

  public ItemWeight(String item, Double weight) {
    setItem(item);
    setWeight(weight);
  }
  public ItemWeight(EntityLiteral item, Double weight) {
      setItem(item);
      setWeight(weight);
  }
  
  public int compareTo(ItemWeight o) {
    return new Double(o.getWeight()).compareTo(getWeight());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final ItemWeight other = (ItemWeight) obj;
    if (item == null) {
      if (other.item != null)
        return false;
    } else if (!item.equals(other.item))
      return false;
    return true;
  }

  public String getItem() {
    return StringFactory.toName(item);
  }

  public double getWeight() {
    return weight;
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = PRIME * result + ((item == null) ? 0 : item.hashCode());
    return result;
  }

  public void setItem(String item) {
    this.item = StringFactory.toID(item);
  }
  public void setItem(EntityLiteral item) {
      this.item = StringFactory.toID(item);
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }
}
