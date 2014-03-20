package com.rcwang.seal.util;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.expand.LangProvider.Lang;

public class RelationIntersecter {

  /**
   * @param args
   */
  public static void main(String[] args) {
    File f1 = new File("federal1.txt"); // input
    File f2 = new File("federal2-2.txt"); // dictionary
    File f3 = new File("federal3.txt");  // intersect

    Set<String> names = new HashSet<String>();
    String[] lines = Helper.readFile(f2).split("\n");
    Lang lang = LangProvider.getLang(LangProvider.ENG);
    
    StringBuffer buf = new StringBuffer();
    for (String line : lines) {
      Matcher m = lang.getPattern().matcher(line);
      if (m.find()) {
        String s = m.group().trim();
        names.add(s);
        
        Matcher m2 = Pattern.compile("\\(([A-Z]+)\\)$").matcher(line);
        if (m2.find()) {
          String s2 = m2.group(1);
          buf.append(s2).append("::").append(s).append("\n");
        }
//        System.out.println("Found: " + m.group());
      }
    }
    
    System.out.println(buf);
    System.out.println(Helper.repeat('-', 80));
    
    lines = Helper.readFile(f1).split("\n");
    for (String line : lines) {
      String[] e = line.split("::");
      if (names.contains(e[1]))
        buf.append(line.trim()).append("\n");
    }
    
    System.out.println(buf);
    Helper.writeToFile(f3, buf.toString());
  }

}
