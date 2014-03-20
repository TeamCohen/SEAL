/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Builds a Trie tree that has a character over each branch
 */
public class CharTrie {
  private static final String REGEXP_SYMBOLS = "()[].*?+^$\\|";
  // the root node of the Trie
  private CharTrieNode root;

  @SuppressWarnings("unchecked")
  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    CharTrie t = new CharTrie();

//    t.addAll(Helper.readToList(new File("./lib/BritDict.txt")));
    // t.addAll(Helper.readToList(new File("./lib/lastNames.txt")));

//    t.add("a");
//    t.add("ab");
//    t.add("abc");
//    t.add("b");
//    t.add("bc");
//    t.add("c");
//    t.add("abb");
//    t.add("abc");
//    t.add("bc");

//    Set<String> set = new HashSet<String>();
//    set.add("rich");
//    set.add("abc");
//    set.add("123");
//    t.addCommonPrefix(set);
//    set.clear();
//    set.add("richard");
//    set.add("abcdefg");
//    set.add("1234567");
//    t.addCommonPrefix(set);
//    set.clear();
//    set.add("abdefg");
//    set.add("richwang");
//    set.add("13579");
//    t.addCommonPrefix(set);

    t.add("abcdefghijklmnopqrstuvwxyz.rich");
    t.add("abcdefghijklmnopqrstuvwxyz.richard");
    t.add("abcdefghijklmnopqrstuvwxyz.richrd");
    t.add("abcdefghijklmnopqrstuvwxyz.richar");
    t.add("abcdefghijklmnopqrstuvwxyz.richad");
    t.add("abcdefghijklmnopqrstuvwxyz.richa+rd");
    t.add("abcdefghijklmnopqrstuvwxyz.richa*d");

//    t.print();
//    Helper.out(t.probability("richard") + "\trichard");
//    Helper.out(t.probability("richrd") + "\trichrd");
//    Helper.out(t.probability("richar") + "\trichar");
//    Helper.out(t.probability("richad") + "\trichad");
//    Helper.out(t.probability("richa+rd") + "\tricha+rd");
//    Helper.out(t.probability("richa-d") + "\tricha-d");

//    Map<String, Double> strProbMap = t.getStringsAboveProb(0.05, true);
//    StringBuffer buf = new StringBuffer();
//    for (Map.Entry e : strProbMap.entrySet())
//      buf.append(e.getValue()).append("\t").append(e.getKey()).append(System.getProperty("line.separator"));
//    System.out.println(buf.toString());
//    Helper.writeToFile(new File("out.txt"), buf.toString());

//    Pattern regexp = t.getRegExpPattern(true);
//    System.out.println(regexp.pattern());
//    String test = "abbc";
//    Matcher m = regexp.matcher(test);
//    System.out.println(test);
//    int i = 0;
//    while (m.find(i++)) {
//      System.out.println("count: " + m.groupCount());
//      for (int j = 0; j < m.groupCount(); j++)
//        System.out.println(j + ". " + m.group(j));
//    }

//    Map<String, Integer> strCountMap = t.countStringsIn(test);
//    for (Entry<String, Integer> e : strCountMap.entrySet())
//      System.out.println(e.getValue() + "\t" + e.getKey());

//    List<String> list = t.getCommonPrefix();
//    for (String s : list)
//      System.out.println(s);

//    System.out.println("Trie size = " + t.size());

    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }

  /**
   * Builds an empty Trie with only a root node
   */
  public CharTrie() {
    root = new CharTrieNode();
  }

  /**
   * Adds the given String s to Trie
   * @param s the string to be added
   */
  public void add(String s) {
    CharTrieNode p = root;
    p.numStringPass++;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      CharTrieNode q = p.get(c);
      if (q == null) {
        q = new CharTrieNode();
        p.put(c, q);
      }
      // indicate that a string has passed through this node
      q.numStringPass++;
//      indexCounter[i]++;
      p = q;
    }
    // indicate that a string has ended here at this node
    p.numStringEnd++;
  }

  /**
   * Add a set of strings into Trie using {link #add(String, Object)}
   * @param collection
   */
  public void addAll(Collection<String> collection) {
    for (String s : collection)
      add(s);
  }

  public void addCommonPrefix(Collection<String> strings) {
    int initNumStringPass = root.numStringPass;

    for (String s : strings) {
      CharTrieNode p = root;
      if (p.numStringPass == initNumStringPass)
        p.numStringPass++;

      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        CharTrieNode q = p.get(c);
        if (q == null) {
          if (initNumStringPass == 0) {
            q = new CharTrieNode();
            p.put(c, q);
          } else break;
        }
        if (q.numStringPass == initNumStringPass)
          q.numStringPass++;
        else if (q.numStringPass < initNumStringPass)
          break;
        p = q;
      }
    }
  }

  /**
   * Resets this Trie
   */
  public void clear() {
    root.clear();
    root.numStringEnd = 0;
    root.numStringPass = 0;
  }

  /**
   * Checks if Trie contains string s
   * @param s the string to be checked
   * @return true if Trie contains the string s
   */
  public boolean contains(String s) {
    return counts(s) > 0;
  }

  /**
   * Counts the occurrence of string s stored in this Trie
   * @param s the string to be counted
   * @return the frequency of string s
   */
  public int counts(String s) {
    CharTrieNode p = root;
    for (int i = 0; i < s.length(); i++) {
      // get next character
      char c = s.charAt(i);
      // get the child node under the next character
      CharTrieNode q = p.get(c);
      // no branch with this character
      if (q == null)
        return 0;
      p = q;
    }
    return p.numStringEnd;
  }

  /**
   * Counts the occurrence of strings (stored in this Trie) in an input document.
   * @param document a sequence of characters
   * @return a mapping of string to its frequency in the input document
   */
  public Map<String, Integer> countStringsIn(String document) {
    Map<String, Integer> strCountMap = new HashMap<String, Integer>();

    for (int startIndex = 0; startIndex < document.length(); startIndex++) {
      CharTrieNode p = root;
      for (int currIndex = startIndex; currIndex < document.length(); currIndex++) {
        // get next character
        char c = document.charAt(currIndex);
        // get the child node under the next character
        CharTrieNode q = p.get(c);
        // no branch with this character
        if (q == null)
          break;
        // a string ends at this character
        if (q.numStringEnd > 0) {
          String currStr = document.substring(startIndex, currIndex + 1);
          Integer countInt = strCountMap.get(currStr);
          int count = (countInt == null) ? 0 : countInt;
          strCountMap.put(currStr, count + 1);
        }
        p = q;
      }
    }
    return strCountMap;
  }

  public List<String> getAllStrings() {
    List<String> list = new ArrayList<String>();
    getStrings(root, new StringBuffer(), list);
    return list;
  }

  public List<String> getCommonPrefix() {
    List<String> list = new ArrayList<String>();
    getCommonPrefix(root, new StringBuffer(), list);
    return list;
  }

  /**
   * Generates a regular expression pattern that will successfully match a
   * string if the string is identical to one of the strings stored in this Trie.
   * @param greedy true to generate a greedy matching pattern; false otherwise.
   * @return regular expression pattern
   */
  public Pattern getRegExpPattern(boolean greedy) {
    StringBuffer buf = new StringBuffer("(");
    getRegExpPattern(root, buf, greedy);
    return Pattern.compile(buf.append(")").toString());
  }

  public Map<String, Double> getStrings(double threshold, boolean allowPrefix) {
    Map<String, Double> strProbMap = new HashMap<String, Double>();
    getStrings(1, threshold, allowPrefix, root, new StringBuffer(), strProbMap);
    return strProbMap;
  }

  public double logLikelihood(String s) {
    CharTrieNode p = root;
    double ll = 0;
    for (int i = 0; i < s.length(); i++) {
      // get next character
      char c = s.charAt(i);
      // get the child node under the next character
      CharTrieNode q = p.get(c);
      // no branch with this character
      if (q == null)
        return 0;
      ll += Math.log((double) q.numStringPass / p.numStringPass);
      p = q;
    }
    return ll;
  }

  public void print() {
    List<String> list = getAllStrings();
    Collections.sort(list);
    for (int i = 0; i < list.size(); i++)
      System.out.println(list.get(i));
  }

  public double probability(String s) {
    CharTrieNode p = root;
    double probability = 1;
    for (int i = 0; i < s.length(); i++) {
      // get next character
      char c = s.charAt(i);
      // get the child node under the next character
      CharTrieNode q = p.get(c);
      // no branch with this character
      if (q == null)
        return 0;
      probability *= (double) q.numStringPass / p.numStringPass;
      p = q;
    }
    return probability;
  }

  public int size() {
    return root.numStringPass;
  }

  private void getCommonPrefix(CharTrieNode node, StringBuffer buf, List<String> list) {
    boolean passedAtLeastOnce = false;
    for (Entry<Character, CharTrieNode> e : node.entrySet()) {
      CharTrieNode nextNode = e.getValue();
      Character c = e.getKey();
      if (nextNode.numStringPass != root.numStringPass)
        continue;
      getCommonPrefix(nextNode, buf.append(c), list);
      buf.setLength(buf.length() - 1);
      passedAtLeastOnce = true;
    }
    if (!passedAtLeastOnce)
      list.add(buf.toString());
  }

  private void getRegExpPattern(CharTrieNode node, StringBuffer buf, boolean greedy) {
    if (node.size() == 0 || !greedy && node.numStringEnd > 0)
      return;
    if (node.size() > 1 || node.numStringEnd > 0)
      buf.append("(?:");
    for (Iterator<Entry<Character, CharTrieNode>> i = node.entrySet().iterator(); i.hasNext();) {
      Entry<Character, CharTrieNode> e = i.next();
      CharTrieNode nextNode = e.getValue();
      Character c = e.getKey();
      if (REGEXP_SYMBOLS.indexOf(c) > -1)
        buf.append('\\');
      buf.append(c);
      getRegExpPattern(nextNode, buf, greedy);
      if (node.size() > 1 && i.hasNext())
        buf.append("|");
    }
    if (node.size() > 1 || node.numStringEnd > 0) {
      buf.append(")");
      if (node.numStringEnd > 0)
        buf.append("?");
    }
  }

  private void getStrings(double prob,
                          double threshold,
                          boolean allowPrefix,
                          CharTrieNode node,
                          StringBuffer buf,
                          Map<String, Double> strProbMap) {

    if (!allowPrefix && node.numStringEnd > 0)
      strProbMap.put(buf.toString(), prob);

    boolean passedAtLeastOnce = false;
    for (Map.Entry<Character, CharTrieNode> e : node.entrySet()) {
      CharTrieNode nextNode = e.getValue();
      Character c = e.getKey();
      double nextProb = prob * nextNode.numStringPass / node.numStringPass;
      if (nextProb < threshold)
        continue;
      getStrings(nextProb, threshold, allowPrefix, nextNode, buf.append(c), strProbMap);
      buf.setLength(buf.length() - 1);
      passedAtLeastOnce = true;
    }
    if (allowPrefix && !passedAtLeastOnce && node != root)
      strProbMap.put(buf.toString(), prob);
  }

  private void getStrings(CharTrieNode node, StringBuffer buf, List<String> list) {
    if (node.numStringEnd > 0)
      list.add(buf.toString());
    for (Map.Entry<Character, CharTrieNode> e : node.entrySet()) {
      getStrings(e.getValue(), buf.append(e.getKey()), list);
      buf.setLength(buf.length() - 1);
    }
  }
}

class CharTrieNode extends HashMap<Character, CharTrieNode> {
  private static final long serialVersionUID = 8369430095254102837L;
  // number of strings that end at this node
  public int numStringEnd;
  // number of strings that pass through this node
  public int numStringPass;

  public CharTrieNode() {
    super();
    numStringEnd = 0;
    numStringPass = 0;
  }
}