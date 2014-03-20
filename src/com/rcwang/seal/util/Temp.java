package com.rcwang.seal.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Temp {

  /**
   * @param args
   */
  public static void main(String[] args) {
    File f = new File("data/en/countries.gold.txt");
    File f2 = new File("data/en/car-maker-vs-country.2.txt.bak");
    
    List<Set<String>> setList = new ArrayList<Set<String>>();
    
    /*Comparator<String> c = new Comparator<String>() {
      public int compare(String e1, String e2) {
        return Double.compare(e1.length(), e2.length());
      }
    };*/
    
    String[] lines = Helper.readFile(f).split("\n");
    for (String line : lines) {
//      Set<String> set = new TreeSet<String>(c);
      Set<String> set = new HashSet<String>();
      set.addAll(Arrays.asList(line.split("\t")));
      setList.add(set);
    }
    
    StringBuffer buf = new StringBuffer();
    lines = Helper.readFile(f2).split("\n");
    for (String line : lines) {
      String[] entries = line.split("\t");
      for (String entry : entries) {
        String[] e = entry.split("::");
        
        Set<String> set = getSet(e[1], setList);
        if (set == null) buf.append(entry).append("\t");
        else {
          for (String s : set) {
            buf.append(e[0]).append("::").append(s).append("\t");
          }
        }
      }
      buf.setLength(buf.length()-1);
      buf.append("\n");
    }
    
    System.out.println(buf);
    Helper.writeToFile(new File("car-maker-vs-country.2.txt"), buf.toString());

  }
  
  public static Set<String> getSet(String s, List<Set<String>> list) {
    for (Set<String> set : list) {
      if (set.contains(s)) {
        return set;
      }
    }
    return null;
  }

}
