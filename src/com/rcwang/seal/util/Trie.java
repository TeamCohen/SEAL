/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;

public class Trie implements Iterable<Trie.TrieNode> {
  
  // a pair of text (String) and trie node
  public static class TextNode {
    public String text;
    public TrieNode node;
    
    public TextNode(String text, TrieNode node) {
      this.text = text;
      this.node = node;
    }
  }
  
  public static class TrieNode {
    // the text represented by this node
    public String text;
    // the chars split out from this node
    public String letters;
    // the nodes that the chars split to
    public List<TrieNode> children;
    // the IDs of contextual pairs
    public Set<Integer> ids;
    // the length of context starting from the root node
    public int length = 0;
    // number of types passes through this node
    // can only be set using finalize()
//   public int numTypes = 0;
    
    public TrieNode(String substr) {
      this.text = substr;
      letters = "";
      children = new LinkedList<TrieNode>();
      ids = new TreeSet<Integer>();
    }
    
    public TrieNode(String substr, int id) {
      this(substr);
      ids.add(id);
    }
    
    @Override
    public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append(text.replaceAll("\\s", " ")).append(" [");
      for (int i : ids)
        buf.append(i).append(",");
      buf.setLength(buf.length()-1);
      buf.append("] (").append(length).append(")");
      return buf.toString();
    }
  }
  
  public static Logger log = Logger.getLogger(Trie.class);
  /*public static final int MIN_TYPES = 2;  // minimum number of seeds
  public static final int MIN_LENGTH = 1; // minimum context (left or right) length
  public static final double MIN_WEIGHT = 0;  // minimum weight
*/
  private StringBuffer buf;
  private TrieNode root;
  private PairedTrie pairedTrie;
  
  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();
    Trie t = new Trie();
//    t.add("abcdefghijklmnopqrstuvwxyz.rich");
//    t.add("abcdefghijklmnopqrstuvwxyz.richard");
//    t.add("abcdefghijklmnopqrstuvwxyz.richrd");
//    t.add("abcdefghijklmnopqrstuvwxyz.richar");
//    t.add("abcdefghijklmnopqrstuvwxyz.richad");
//    t.add("abcdefghijklmnopqrstuvwxyz.richa+rd");
//    t.add("abcdefghijklmnopqrstuvwxyz.richa*d");
    
    t.add("rich", 0);
    t.add("abc", 0);
    t.add("123", 0);

    t.add("richard", 1);
    t.add("abcdefg", 1);
    t.add("1234567", 1);

    t.add("abcdefg", 2);
    t.add("richwang", 2);
    t.add("123579", 2);
    
    t.add("zsfasdf", 3);
    
    /*List<TextNode> textNodes = t.getCommonString(-1, 3);
    for (TextNode textNode : textNodes)
      log.info(textNode.text);*/
    
    log.info(t);
    
    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }

  private static void append(TrieNode node, String substr, int id) {
    TrieNode newNode = new TrieNode(substr, id);
    newNode.length = node.length + substr.length();
    node.children.add(newNode);
    node.letters += newNode.text.charAt(0);
  }
  
  // get the left-most index of where s1 and s2's characters differ
  private static int getDiffPosition(String s1, String s2) {
    int i, length = Math.min(s1.length(), s2.length());
    for (i = 0; i < length; i++)
      if (s1.charAt(i) != s2.charAt(i))
        return i;
    return i;
  }
  
  // inserts a String s into node with an assigned id
  private static void insert(TrieNode node, String s, int id) {
    int pos = getDiffPosition(node.text, s);
    
    if (pos == node.text.length()) {
      node.ids.add(id);
      if (pos == s.length()) return;
      int index = node.letters.indexOf(s.charAt(pos));
      if (index == -1) {
        // node doesn't have the child, so create one
        append(node, s.substring(pos), id);
      } else {
        // node has the child, so follow it
        insert(node.children.get(index), s.substring(pos), id);
      }
    } else { // must split this node
      // copy stuff over from this node to splitNode
      TrieNode splitNode = new TrieNode(node.text.substring(pos));
      splitNode.ids.addAll(node.ids);
      splitNode.children.addAll(node.children);
      splitNode.letters = node.letters;
      splitNode.length = node.length;

      // reset this node
      node.letters = Character.toString(node.text.charAt(pos));
      node.children.clear();
      node.children.add(splitNode);
      node.length -= node.text.length()-pos;
      node.text = node.text.substring(0, pos);
      node.ids.add(id);

      if (pos < s.length())
        append(node, s.substring(pos), id);
    }
  }
  
  public Trie() {
    root = new TrieNode("");
    buf = new StringBuffer();
  }
  
  /**
   * Inserts a String s into this trie with a type
   * @param s a String
   * @param type some type
   */
  public void add(String s, int id) {
    if (s == null)
      throw new IllegalArgumentException("Input string cannot be null!");
    insert(root, s, id);
  }
  
  /**
   * Aassign each node the number of types it belongs to
   */
  /*public void assignNumTypes() {
    assignNumTypes(root);
  }*/
  
  public void clear() {
    root = new TrieNode("");
    buf.setLength(0);
  }
  
  /**
   * Get longest common Strings that belong to at least minType types
   * @param minType minimum number of types the returned Strings should belong to
   * @return a list of TextNode objects
   */
  /*public List<TextNode> getCommonString(int minType, int minLength, double minWeight) {
    return getCommonString(minType, minLength, minWeight, null);
  }*/
  
  /**
   * Similar to {@link #getCommonString(int, int, double)}, except constrained by ids
   * @param minType minimum number of types the returned Strings should belong to
   * @param ids constrains the number of types that each node belongs to
   * @return a list of TextNode objects
   */
  /*public List<TextNode> getCommonString(int minType, int minLength, double minWeight, Set<Integer> ids) {
    buf.setLength(0);
    minType = Math.max(minType, MIN_TYPES);
    minLength = Math.max(minLength, MIN_LENGTH);
    minWeight = Math.max(minWeight, MIN_WEIGHT);
    List<TextNode> textNodes = new ArrayList<TextNode>();
    getCommonString(root, minType, minLength, minWeight, ids, textNodes);
    return textNodes;
  }*/

  public List<TextNode> getDeepestNodes(int minType, int minLength, double minWeight, 
                                        Collection<TrieNode> matchNodes) {
    buf.setLength(0);
    List<TextNode> nodes = new ArrayList<TextNode>();
    
    /*log.debug("Given top nodes:");
    for (TrieNode node : topNodes)
      log.debug(node);*/
    
    getDeepestNodes(root, minType, minLength, minWeight, matchNodes, nodes);
    return nodes;
  }
  
  /**
   * Returns a list of trie nodes for all nodes in this trie
   * @return
   */
  public List<TrieNode> getNodes() {
    List<TrieNode> nodes = new ArrayList<TrieNode>();
    getNodes(root, nodes);
    return nodes;
  }
  
  public PairedTrie getPairedTrie() {
    return pairedTrie;
  }
  
  public TrieNode getRoot() {
    return root;
  }
  
  /*private void assignNumTypes(TrieNode node) {
    node.numTypes = numTypes(node.ids);
    for (TrieNode child : node.children)
      assignNumTypes(child);
  }*/
  
  /**
   * Returns the top nodes whose length is at least minLength
   * @param minLength
   * @return
   */
  public List<TrieNode> getTopNodes(int minLength) {
    List<TrieNode> nodes = new ArrayList<TrieNode>();
    getTopNodes(root, minLength, nodes);
    return nodes;
  }
  
  public boolean isEmpty() {
    return root.letters.length() == 0;
  }
  
  public Iterator<TrieNode> iterator() {
    return getNodes().iterator();
  }
  
  public void setPairedTrie(PairedTrie pairedTrie) {
    this.pairedTrie = pairedTrie;
  }
  
  public String toString() {
//    assignNumTypes();
    buf.setLength(0);
    print(root, 0);
    return buf.toString();
  }

  private void getDeepestNodes(TrieNode node, int minType, int minLength, double minWeight, 
                               Collection<TrieNode> matchNodes, Collection<TextNode> nodes) {
    boolean passedAtLeastOnce = false;
    for (TrieNode child : node.children) {
      if (!matches(child, minType, minWeight, matchNodes)) continue;
      buf.append(child.text);
      getDeepestNodes(child, minType, minLength, minWeight, matchNodes, nodes);
      buf.setLength(buf.length()-child.text.length());
      passedAtLeastOnce = true;
    }
    if (!passedAtLeastOnce && node.length >= minLength && !isOnlySpace(node))
      nodes.add(new TextNode(buf.toString(), node));
  }
  
  private void getNodes(TrieNode node, List<TrieNode> nodes) {
    for (TrieNode child : node.children) {
      nodes.add(node);
      getNodes(child, nodes);
    }
  }
  
  private void getTopNodes(TrieNode node, int minLength, List<TrieNode> nodes) {
    for (TrieNode child : node.children) {
      if (child.length >= minLength && !isOnlySpace(child))
        nodes.add(child);
      else getTopNodes(child, minLength, nodes);
    }
  }
  
  private static boolean isOnlySpace(TrieNode node) {
    return node.length == 1 && node.text.equals(" ");
  }
  
  /*private void getCommonString(TrieNode node, int minType, int minLength, double minWeight,
                               Set<Integer> ids, List<TextNode> textNodes) {
    boolean passedAtLeastOnce = false;
    for (TrieNode child : node.children) {
      Set<Entity> entities = pairedTrie.getCommonTypes(child.ids, ids);
      if (entities.size() < minType) continue;
      if (pairedTrie.sumWeight(entities) < minWeight) continue;
      buf.append(child.text);
      getCommonString(child, minType, minLength, minWeight, ids, textNodes);
      buf.setLength(buf.length()-child.text.length());
      passedAtLeastOnce = true;
    }
    if (!passedAtLeastOnce && buf.length() >= minLength)
      textNodes.add(new TextNode(buf.toString(), node));
  }*/

  private boolean matches(TrieNode node, int minType, double minWeight, 
                          Collection<TrieNode> matchNodes) {
    if (node == null || matchNodes == null || 
        node.ids.size() < PairedTrie.MIN_TYPES)
      return false;
    for (TrieNode matchNode : matchNodes) {
      if (matchNode.ids.size() < PairedTrie.MIN_TYPES) continue;
      Set<Entity> entities = pairedTrie.getCommons(node.ids, matchNode.ids);
//      log.debug("Matching " + node + " to " + matchNode + " (Entity size: " + entities.size() + ")");
      if (entities == null || entities.size() < minType) continue;
      if (pairedTrie.sumWeight(entities) < minWeight) continue;
//      log.debug("Matched!");
      return true;
    }
    return false;
  }

  private void print(TrieNode node, int count) {
    for (int i = 0; i < count; i++)
      buf.append("|");
    buf.append(node).append("\n");
    for (TrieNode child : node.children) {
//      if (child.ids.size() >= PairedTrie.MIN_TYPES)
        print(child, count + node.text.length());
    }
  }
}

