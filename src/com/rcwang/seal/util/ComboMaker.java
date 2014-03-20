/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ComboMaker<T> {
  
  public static void main(String args[]) {
    Set<String> set = new HashSet<String>();
    set.add("a");
    set.add("b");
    set.add("c");
    set.add("d");
    
    ComboMaker<String> comboMaker = new ComboMaker<String>();
    List<List<String>> comboLists = comboMaker.make(set, 2);
    for (int i = 0; i < comboLists.size(); i++) {
      List<String> comboList = comboLists.get(i);
      System.out.print((i+1) + ". ");
      for (String s : comboList)
        System.out.print(s + ", ");
      System.out.println();
    }
  }
  
  /**
   * Generates all possible combinations of size n from the input entities.
   * The generated list of combinations are in random order.
   * @param entities the input entities
   * @param n the size n
   * @return a list of entity combinations
   */
  public List<List<T>> make(Collection<T> entities, int n) {
    return make(new ArrayList<T>(entities), n);
  }

  public List<List<T>> make(List<T> entities, int n) {
    if (n > entities.size()) return null;
    List<List<T>> resultList = new ArrayList<List<T>>();
    int[] arr = null;

    while (true) {
      if (arr != null && arr[0] == entities.size()-arr.length)
        break;
      if (arr == null) {
        arr = new int[n];
        for (int i = 0; i < n; i++)
          arr[i] = i;
      } else if (arr[arr.length-1] == entities.size()-1) {
        for (int i = 0; i < arr.length;i++) {
          if (arr[i] == entities.size()-arr.length+i) {
            arr[i-1]++;
            for (int j = i; j < arr.length; j++)
              arr[j] = arr[j-1] + 1;
            break;
          }
        }
      } else arr[arr.length-1]++;

      List<T> list = new ArrayList<T>();
      for (int i = 0; i < arr.length ; i++)
        list.add(entities.get(arr[i]));
      resultList.add(list);
    }
    return resultList;
  }
}
