package com.rcwang.seal.util;

import java.io.File;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.LangProvider;

public class CombineDatasets {

  /**
   * @param args
   */
  public static void main(String[] args) {
    final String DATA_NAME = "nba-teams";
    final String LANG_ID1 = LangProvider.CHT[LangProvider.ID];
    final String LANG_ID2 = LangProvider.ENG[LangProvider.ID];
    final String OUTPUT = "un/cht-nba-vs-en-nba.2.txt";
    
    File f1 = new File("data/" + LANG_ID1 + "/" + DATA_NAME + ".gold.txt");
    File f2 = new File("data/" + LANG_ID2 + "/" + DATA_NAME + ".gold.txt");
    
    String[] lines1 = Helper.readFile(f1).split("\n");
    String[] lines2 = Helper.readFile(f2).split("\n");
    
    int minLines = Math.min(lines1.length, lines2.length);
    
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < minLines; i++) {
      String[] entries1 = lines1[i].split("\t");
      String[] entries2 = lines2[i].split("\t");
      
      for (String entry1 : entries1)
        for (String entry2 : entries2)
          buf.append(entry1.trim()).append(Entity.RELATION_SEPARATOR).append(entry2.trim()).append("\t");
      buf.setLength(buf.length()-1);
      buf.append("\n");
      
      System.out.println(buf);
    }
    Helper.writeToFile(new File("data", OUTPUT), buf.toString());
  }

}
