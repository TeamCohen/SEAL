/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.util.Helper;

public class CategoryPredictor {
  
  public static class MetaInfo {
    public String title, desc;
    public Set<String> keywords;
    public double weight;
    public MetaInfo() {
      weight = 0;
      keywords = new HashSet<String>();
    }
    public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append("Title: ").append(title).append("\n");
      buf.append("Desc: ").append(desc).append("\n");
      buf.append("Keywords: ");
      for (String keyword : keywords)
        buf.append(keyword).append(", ");
      buf.append("\n");
      return buf.toString();
    }
  }
  
  public static class Category implements Comparable<Category> {
    public String name;
    public int keywordFreq = 0;
    public int docFreq = 0;
    public double score = 0;
    public int isCorrect = -1;
    public int compareTo(Category category) {
      return Double.compare(category.score, score);
    }
    public boolean wasEvaluated() {
      return isCorrect > -1;
    }
    public String toString() {
      return keywordFreq + " " + docFreq + " " + (isCorrect == -1 ? "" : (isCorrect == 0 ? "-" : "+")) + name + ":\t" + score;
    }
  }
  
  public static Logger log = Logger.getLogger(CategoryPredictor.class);
  public static final int MAX_RANK_TO_EVALUATE = 10;
  public static final double MAX_KEYWORD_RATIO = 0.5;
  public static final Pattern TITLE_PATTERN = Pattern.compile("(?i)<title>([^<>]+?)</title>");
  public static final Pattern DESC_PATTERN = Pattern.compile("(?i)<meta\\s+name=(?:\"|')description(?:\"|')\\s+content=(?:\"|')([^<>]+?)(?:\"|')");
  public static final Pattern KEYWORD_PATTERN = Pattern.compile("(?i)<meta\\s+name=(?:\"|')keywords(?:\"|')\\s+content=(?:\"|')([^<>]+?)(?:\"|')");
  
  private Map<String, Category> categoryMap;
  private Set<MetaInfo> metaInfos;
  private int maxKeywordFreq;

  public CategoryPredictor() {
    maxKeywordFreq = 0;
    metaInfos = new HashSet<MetaInfo>();
    categoryMap = new HashMap<String, Category>();
  }
  
  public void clear() {
    categoryMap.clear();
    metaInfos.clear();
    maxKeywordFreq = 0;
  }
  
  public static void main(String args[]) {
    File htmlFile = new File("/www.cache/321/321.847938881.html");
    String content = Helper.readFile(htmlFile);
    Document docURL = new Document(content, null);
    docURL.setWeight(1.0);
    List<Document> docURLs = new ArrayList<Document>();
    docURLs.add(docURL);
//    CategoryPredictor.predict(docURLs);
  }

  public List<Category> predict() {
    for (MetaInfo metaInfo : metaInfos)
      weightNames(metaInfo);
    List<Category> categories = new ArrayList<Category>(categoryMap.values());
    Collections.sort(categories);    
    return categories;
  }
  
  public Set<String> parse(Document docURL, Collection<String> badNames) {
    MetaInfo metaInfo = extractMetaInfo(docURL);
    if (metaInfo == null)
      return null;
    metaInfos.add(metaInfo);
    return updateCategories(metaInfo, badNames);
  }
  
  public static MetaInfo extractMetaInfo(Document document) {
    MetaInfo metaInfo = new MetaInfo();
    metaInfo.weight = document.getWeight();
    if (document.getText() == null)
      return null;
    Matcher m = TITLE_PATTERN.matcher(document.getText());
    if (m.find())
      metaInfo.title = tidy(m.group(1));
    m = DESC_PATTERN.matcher(document.getText());
    if (m.find())
      metaInfo.desc = tidy(m.group(1));
    m = KEYWORD_PATTERN.matcher(document.getText());
    if (m.find()) {
      String[] keywords = m.group(1).split("(\\-|:|,|\\|)");
      for (String keyword : keywords) {
        String s = tidy(keyword);
        if (s != null)
          metaInfo.keywords.add(s.toLowerCase());
      }
    }
    if (metaInfo.title == null && 
        metaInfo.desc == null && 
        metaInfo.keywords.isEmpty())
      return null;
    return metaInfo;
  }
  
  private Set<String> updateCategories(MetaInfo metaInfo, Collection<String> badTerms) {
    Set<String> categories = new HashSet<String>();
    if (metaInfo == null || metaInfo.keywords.isEmpty())
      return categories;
    
    for (String keyword : metaInfo.keywords) {
      boolean isGood = true;
      // A keyword is *not* good if...
      // 1) the keyword is a substring of any badTerm or
      // 2) any badTerm is a substring of the keyword,
      for (String badTerm : badTerms) {
        if (keyword.contains(badTerm) || 
            badTerm.contains(keyword)) {
          isGood = false;
          break;
        }
      }
      if (!isGood) continue;
      Category category = categoryMap.get(keyword);
      if (category == null) {
        category = new Category();
        category.name = keyword;
        categoryMap.put(category.name, category);
        categories.add(category.name);
      }
      category.keywordFreq++;
      category.score += metaInfo.weight;
      maxKeywordFreq = Math.max(category.keywordFreq, maxKeywordFreq);
    }
    return categories;
  }
  
  private static String tidy(String s) {
    s = s.replaceAll("\\s+", " ").trim();
    return (s.length() == 0) ? null : s;
  }
  
  private void weightNames(MetaInfo metaInfo) {
    StringBuffer buf = new StringBuffer();
    if (metaInfo.title != null)
      buf.append(metaInfo.title).append(" ");
    if (metaInfo.desc != null)
      buf.append(metaInfo.desc).append(" ");
    if (!metaInfo.keywords.isEmpty()) {
      for (String keyword : metaInfo.keywords)
        buf.append(keyword).append(" ");
    }
    if (buf.length() == 0) return;
    String strings = buf.toString().toLowerCase();
    
    for (Iterator<String> i = categoryMap.keySet().iterator(); i.hasNext();) {
      String name = i.next();
      Category category = categoryMap.get(name);
      // remove keywords that are less than MAX_KEYWORD_RATIO frequent as the most frequent one
      if (category.keywordFreq < maxKeywordFreq * MAX_KEYWORD_RATIO) {
        i.remove();
        continue;
      }
      // A term is considered *not* useful when it:
      // 1) has only 1 char (in any language)
      // 2) has less than 3 chars and starts with a letter (i.e. English)
      // 3) is not found in the pool of words
      if (name.length() < 2 ||
          name.length() < 3 && name.matches("[A-Za-z].*") ||
          strings.indexOf(name) == -1)
        continue;
      category.score += metaInfo.weight;
      category.docFreq++;
    }
  }

  public Set<String> getCategoryNames() {
    return categoryMap.keySet();
  }
  
  public Collection<Category> getCategories() {
    return categoryMap.values();
  }  
}
