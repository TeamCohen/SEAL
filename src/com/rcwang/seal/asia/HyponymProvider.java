/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.asia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.util.Helper;

public class HyponymProvider {

  public static class Hyponym {
    private List<String> hyponymPhrases;
    private List<String> listWords;
    private List<String> beginWords;
    private List<String> splitWords;
    private String langID;
    
    public Hyponym(String langID) {
      this.langID = langID;
      hyponymPhrases = new ArrayList<String>();
      listWords = new ArrayList<String>();
      beginWords = new ArrayList<String>();
      splitWords = new ArrayList<String>();
    }

    public void addBeginWords(String[] words) {
      add(words, beginWords);
      Helper.sortByLength(beginWords, false);
    }
    
    public void addHyponymPhrases(String[] phrases) {
      add(phrases, hyponymPhrases);
    }
    
    public void addListWords(String[] words) {
      add(words, listWords);
    }
    
    public void addSplitWords(String[] words) {
      add(words, splitWords);
      Helper.sortByLength(splitWords, false);
    }
    
    public List<String> getBeginWords() {
      return beginWords;
    }
    
    public List<String> getHyponymPhrases() {
      return hyponymPhrases;
    }

    public String getLangID() {
      return langID;
    }

    public String getListQuery() {
      StringBuffer buf = new StringBuffer("(");
      for (String word : listWords) {
        if (buf.length() > 1)
          buf.append(" OR ");
        buf.append(word);
      }
      buf.append(")");
      return buf.toString();
    }

    public List<String> getSplitWords() {
      return splitWords;
    }

    private void add(String[] strs, List<String> list) {
      for (String s : strs)
        if (!list.contains(s))
          list.add(s);
    }
  }
  
  public static final String C = "<1>";
  public static Map<String, Hyponym> hyponymMap = initialize();
  
  public static Hyponym getHyponym(String langID) {
    return hyponymMap.get(langID);
  }

  public static Hyponym getLangEnglish() {
    Hyponym hyponym = new Hyponym(LangProvider.ENG[LangProvider.ID]);
    hyponym.addHyponymPhrases(new String[] {
        C + " such as",
        C + " i.e.", 
        C + " e.g.",
     // C + " include",
        C + " including",
        C + " like",
        "and other " + C,
        "or other " + C, // test
        "such " + C + " as", // test
    });
    hyponym.addBeginWords(new String[] { 
        "the ", "a " 
    });
    hyponym.addSplitWords(new String[] { 
        "i.e", "e.g", "etc", " or ", " and ", "as well as" 
    });
    hyponym.addListWords (new String[] { 
        "list", "names", "top", "best", "hot", "popular", "famous", "common"
    });
    return hyponym;
  }
  
  public static Hyponym getLangJapanese() {
    Hyponym hyponym = new Hyponym(LangProvider.JAP[LangProvider.ID]);
    hyponym.addHyponymPhrases(new String[] {
        C + " 例えば",
        C + " たとえば",
        C + "は 例えば",
        C + "は たとえば",
        C + "と言えば",
        C + "といえば",
        "等の" + C,
        "などの" + C,
        "とかの" + C,
     // "のような" + C,  // test
    });
    hyponym.addBeginWords(new String[] {});
    hyponym.addSplitWords(new String[] { 
        "例えば", "たとえば", "と言えば", "といえば", "と", "や", "および", "など", "とか",
        "が", "を", "に", "で", "は", "も"
    });
    hyponym.addListWords (new String[] { 
        "一覧", "リスト", "有名", "普及" 
    });
    return hyponym;
  }
  
  public static Hyponym getLangKorean() {
    Hyponym hyponym = new Hyponym(LangProvider.KOR[LangProvider.ID]);
    hyponym.addHyponymPhrases(new String[] {
        C + "로는",
        C + "으로",
        C + " 예를 들면",
        C + " 예를 들어",
        "등과 같은 " + C,
    });
    hyponym.addBeginWords(new String[] {});
    hyponym.addSplitWords(new String[] { 
        "등", "과", "로", "를", "이", "가", "그", "저", "도", "에",
        "은", "는", "을", "고", "에", "지", "었", "와", "어", "겠",
        "로는", "으로", "또는", "있다", "없다", "하고", "으시", "에서", "까지", "어요",
        "지요", "으러", "지만", "에게", "으로", "어서", "그리고",
    });
    hyponym.addListWords (new String[] { 
        "목록", "리스트", "최고", "인기" 
    });
    return hyponym;
  }
  
  public static Hyponym getLangSChinese() {
    Hyponym hyponym = new Hyponym(LangProvider.CHS[LangProvider.ID]);
    hyponym.addHyponymPhrases(new String[] {
        C + " 例如",
        C + " 比如",
        C + " 包括",
        C + " 包含",
        C + " 譬如",
     // C + " 如",
        "等" + C,
    });
    hyponym.addBeginWords(new String[] { 
        "了", "说"
    });
    hyponym.addSplitWords(new String[] { 
        "例如", "比如", "包括", "包含", "譬如",
        "等", "和", "及", "与", "或", "加", "还有", "外加", "以及", "等等", "此外", "另外"
    });
    hyponym.addListWords (new String[] { 
        "列表", "名单", "清单", "一览", "有名", "着名", "热门" 
    });
    return hyponym;
  }
  
  public static Hyponym getLangTChinese() {
    Hyponym hyponym = new Hyponym(LangProvider.CHT[LangProvider.ID]);
    hyponym.addHyponymPhrases(new String[] {
        C + " 例如",
        C + " 比如",
        C + " 包括",
        C + " 包含",
        C + " 譬如",
     // C + " 如",
        "等" + C,
    });
    hyponym.addBeginWords(new String[] { 
        "了", "說"
    });
    hyponym.addSplitWords(new String[] { 
        "例如", "比如", "包括", "包含", "譬如",
        "等", "和", "及", "與", "或", "加", "還有", "外加", "以及", "等等", "此外", "另外"
    });
    hyponym.addListWords (new String[] { 
        "列表", "名單", "清單", "一覽", "有名", "著名", "熱門" 
    });
    return hyponym;
  }
  
  /*public static Hyponym getLangUniversal() {
    Hyponym hyponym = new Hyponym(LangProvider.UNI_LANG);
    hyponym.addHyponymPhrases(new String[] {});
    hyponym.addBeginWords(new String[] {});
    hyponym.addSplitWords(new String[] {});
    hyponym.addListWords (new String[] {});
    return hyponym;
  }*/
  
  public static String getListQuery(String langID) {
    Hyponym hyponym = hyponymMap.get(langID);
    if (hyponym == null) return null;
    return hyponym.getListQuery();
  }

  public static Map<String, Hyponym> initialize() {
    List<Hyponym> hyponymPatterns = new ArrayList<Hyponym>();
    hyponymPatterns.add(getLangEnglish());
    hyponymPatterns.add(getLangTChinese());
    hyponymPatterns.add(getLangSChinese());
    hyponymPatterns.add(getLangJapanese());
    hyponymPatterns.add(getLangKorean());
//    hyponymPatterns.add(getLangUniversal());
    
    Map<String, Hyponym> hyponymMap = new HashMap<String, Hyponym>();
    for (Hyponym hyponym : hyponymPatterns)
      hyponymMap.put(hyponym.getLangID(), hyponym);
    return hyponymMap;
  }
  
  public static String removeBeginWord(String s, String langID) {
    if (s == null || langID == null) return null;
    s = s.trim();
    Hyponym hyponym = hyponymMap.get(langID);
    String lc = s.toLowerCase();
    
    for (String word : hyponym.getBeginWords()) {
      if (lc.startsWith(word.toLowerCase()))
        return s.substring(word.length());
    }
    return s;
  }

  public static List<String> split(String s, String langID) {
    if (s == null || langID == null) return null;
    Hyponym hyponym = hyponymMap.get(langID);
    
    StringBuffer buf = new StringBuffer("((?i:");
    for (String word : hyponym.getSplitWords())
      buf.append(Pattern.quote(word)).append("|");
    // a period that (possibly) indicates the end of sentence (11/25/2008)
    buf.setLength(buf.length()-1);
    buf.append(")|(?<=[a-z]{3,})\\.)");
    
    s = " " + s + " ";
    String[] splits = s.split(buf.toString());
    List<String> list = new ArrayList<String>();
    
    for (String split : splits) {
      split = split.trim();
      if (split.length() > 0)
        list.add(split);
    }
    if (list.isEmpty())
      list.add(s.trim());
    return list;
  }

  public static boolean supports(String langID) {
    return hyponymMap.keySet().contains(langID);
  }
}
