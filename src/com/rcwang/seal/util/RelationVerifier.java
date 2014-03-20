package com.rcwang.seal.util;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RelationVerifier {

  /**
   * @param args
   */
  public static void main(String[] args) {
    File f = new File("federal3.txt");
    
    String[] lines = Helper.readFile(f).split("\n");
    
    Map<String, Set<Integer>> map = new HashMap<String, Set<Integer>>();
    for (int i = 0; i < lines.length; i++) {
      String[] e = lines[i].split("\t");
      for (String s : e) {
        String[] p = s.split("::");
        Set<Integer> set = map.get(p[1]);
        if (set == null) {
          map.put(p[1], set = new HashSet<Integer>());
        }
        set.add(i);
      }
    }
    
    for (Set<Integer> set : map.values()) {
      if (set.size() == 1) continue;
      for (int i : set)
        System.out.println(i + ". " + lines[i]);
      System.out.println();
    }

  }

}
