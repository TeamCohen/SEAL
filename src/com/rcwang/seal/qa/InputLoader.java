package com.rcwang.seal.qa;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rcwang.seal.util.Helper;

public class InputLoader {

  private Map<String, List<String>> qidInputs;
  
  public static void main(String[] args) {
    InputLoader inputLoader = new InputLoader();
    File inputFile = new File(Evaluator.baseDirName, "trec13/input/input.lcc1");
    inputLoader.load(inputFile);
    inputFile = new File(Evaluator.baseDirName, "trec14/input/input.lcc05");
    inputLoader.load(inputFile);
    inputFile = new File(Evaluator.baseDirName, "trec15/input/lccPA06");
    inputLoader.load(inputFile);
    List<String> list = inputLoader.get("67.6");
    for (String s : list)
      System.out.println(s);
  }
  
  public InputLoader() {
    qidInputs = new LinkedHashMap<String, List<String>>();
  }
  
  public void load(File inputFile) {
    if (inputFile == null) return;
    String content = Helper.readFile(inputFile);
    String[] lines = content.split("\n");
    for (String line : lines) {
      String[] entries = line.split("\\s+", 4);
      if (entries.length < 4 || entries[1].equals("Q0"))
        continue;
      String qid = entries[0];
      List<String> list = qidInputs.get(qid);
      if (list == null) {
        list = new ArrayList<String>();
        qidInputs.put(qid, list);
      }
      String answer = entries[3].trim();
      if (answer.equals("NIL") || answer.length() == 1)
        continue;
      answer = answer.replaceAll("[ _]+", " ").trim();
      answer = answer.replaceAll("(``|''|,$)", "");
      list.add(answer.toLowerCase());
    }
  }

  public List<String> get(String qid) {
    return qidInputs.get(qid);
  }
  
}
