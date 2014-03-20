package com.rcwang.seal.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationPair {
  private List<String> sourceList;
  private List<String> targetList;
  
  public TranslationPair() {
    sourceList = new ArrayList<String>();
    targetList = new ArrayList<String>();
  }
  
  public void add(String source, String target) {
    sourceList.add(source);
    targetList.add(target);
  }
  
  public boolean containsSource(String source) {
    return sourceList.contains(source);
  }
  
  public boolean containTarget(String target) {
    return targetList.contains(target);
  }
  
  public String[] get(int i) {
    String[] array = new String[2];
    array[0] = sourceList.get(i);
    array[1] = targetList.get(i);
    return array;
  }
  
  public int size() {
    return sourceList.size();
  }
  
  public String toString() {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < this.size(); i++) {
      String[] trans = this.get(i);
      buf.append((i+1)).append(". ").append(trans[0]).append(" --> ").append(trans[1]).append("\n");
    }
    buf.setLength(buf.length()-1);
    return buf.toString();
  }
}
