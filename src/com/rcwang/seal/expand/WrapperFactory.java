/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.expand.Wrapper.MiddleContext;
import com.rcwang.seal.fetch.BadWordFilterer;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.Originator;
import com.rcwang.seal.util.PairedTrie;

public class WrapperFactory {

  /********************** Parameters **************************/
  // optional characters in content words
  public static final String OPTIONAL_CHAR_STR = "(?:\\.|-|·|・)";
  public static final String PUNCT_CHAR_STR = "(?:\\p{Punct}|、|。|\\s|" + OPTIONAL_CHAR_STR + ")+";
  
  // bad extracted entities (usually incorrect)
  public static final String BAD_PATTERN_STR = "(?:[A-Za-z]|\\d+)";
  
  // initial context length if only one seed
  public static final int UNISEED_MIN_CONTEXT_LENGTH = 2;
  public static final int UNISEED_MAX_CONTEXT_LENGTH = 32;
  
  public static final int MIN_CONTENT_LENGTH = 2;
  public static final int MAX_CONTENT_LENGTH = 48;
  public static final int MAX_CONTEXT_LENGTH = 100;
  public static final int MAX_BAD_GOOD_CONTENT_RATIO = 100;
  public static final int MAX_CONTENTS_ALLOWED = 2000;
  /************************************************************/

  public static final Pattern BAD_PATTERN = Pattern.compile(BAD_PATTERN_STR);
  public static final Pattern PUNCT_PATTERN = Pattern.compile(PUNCT_CHAR_STR);
  
  public static Logger log = Logger.getLogger(WrapperFactory.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  private List<Pattern[]> seedPatterns;
  private EntityList seeds;
  private Pattern contentPattern;
  private PairedTrie pairedTrie;
  private BadWordFilterer badWordFilterer;
  private int minSeedsBracketed;
  private int minContextLength;
  private int minContentLength;
  private int wrapperLevel;
  
  public static String addApostrophe(String s) {
    int index = s.indexOf(" s ");
    if (index != -1)
      s = s.substring(0, index) + "'" + s.substring(index+1);
    if (s.endsWith(" s"))
      s = s.substring(0, s.length()-2) + "'s";
    return s;
  }
  
  public static boolean checkSimpleContent(String s) {
    return checkSimpleContent(s, MIN_CONTENT_LENGTH);
  }
  
  public static boolean checkSimpleContent(String s, int minContentLength) {
    if (s.length() < minContentLength) return false;
    if (s.length() > MAX_CONTENT_LENGTH) return false;
    if (BAD_PATTERN.matcher(s).matches()) return false;
    if (PUNCT_PATTERN.matcher(s).matches()) return false;
    return true;
  }
  
  public static boolean isPunct(char c) {
    return PUNCT_PATTERN.matcher(Character.toString(c)).matches();
  }

  public static String tidy(String s) {
    return tidy(s, false);
  }
  
  public static String tidy(String s, boolean isCaseSensitive) {
    if (s == null || s.length() == 0) return s;
    if (isCaseSensitive)
      s = s.replaceAll("[\\s_]+", " ");
    else s = s.toLowerCase().replaceAll("[\\s_-]+", " ");  // [!] should be the same as the one below
    
    int startOffset = 0;
    for (; startOffset < s.length(); startOffset++)
      if (!isPunct(s.charAt(startOffset))) break;
    
    int endOffset = s.length();
    for (; endOffset > startOffset; endOffset--)
      if (!isPunct(s.charAt(endOffset-1))) break;
    
    s = s.substring(startOffset, endOffset);
    s = addApostrophe(s); // "pamela_s" => "pamela's"
    return s;
  }
  
  public static String toEntityRE(String s) {
    // lenient on punctuations
    String re = Pattern.quote(s).replaceAll(OPTIONAL_CHAR_STR, "\\\\E.?\\\\Q");
    // lenient on spaces
    re = re.replaceAll("[\\s']+", "\\\\E[\\\\s_'-]+\\\\Q");  // [!] should be the same as the one above
    return re;
  }
  
  public static String toPhraseRE(String s) {
    // lenient on punctuations
    String re = Pattern.quote(s).replaceAll(OPTIONAL_CHAR_STR, "\\\\E.?\\\\Q");
    // lenient on spaces
    re = re.replaceAll("\\s+", "\\\\E[\\\\s\\\\p{Punct}]*\\\\Q");
    return re;
  }
  
  // 1st index is each seed, 2nd index is the regexps for that seed
  private static List<Pattern[]> toSeedPatterns(EntityList seeds) {
    seeds.sortByNameLength(false);
    List<Pattern[]> patternList = new ArrayList<Pattern[]>();
    
    for (int i = 0; i < seeds.size(); i++) {
      Entity seed = seeds.get(i);
      Pattern[] patterns;
      
      if (seed.isRelational()) {
        String[] s = seed.getNames();
        String s1 = toEntityRE(s[0]);
        String s2 = toEntityRE(s[1]);
        String middle = "(?:(?!" + s1 + "|" + s2 + ").)+?";
        Pattern p1 = Pattern.compile("(?is:" + s1 + "(" + middle + ")" + s2 + ")");
        Pattern p2 = Pattern.compile("(?is:" + s2 + "(" + middle + ")" + s1 + ")");
        patterns = new Pattern[] {p1, p2};
        
      } else {
        Pattern p = Pattern.compile("(?is:" + toEntityRE(seed.getName().toString()) + ")");
        patterns = new Pattern[] {p};
        
        // remove rest of the seeds that match the current pattern
        boolean[] toRemove = new boolean[seeds.size()];
        for (int j = i+1; j < seeds.size(); j++)
          if (p.matcher(seeds.get(j).getName().toString()).matches())
            toRemove[j] = true;
        seeds.remove(toRemove);
      }
      patternList.add(patterns);
      
      // for debugging
      for (int j = 0; j < patterns.length; j++)
        log.debug((i+1) + "." + (patterns.length > 1 ? j+1 : "") + " Seed Matching RegExp: " + patterns[j]);
    }
    return patternList;
  }
  
  public WrapperFactory() {
    setLangID(gv.getLangID());
    setMinContextLength(gv.getMinContextLength());
    setMinSeedsBracketed(gv.getMinSeedsBracketed());
    setWrapperLevel(gv.getWrapperLevel());
    minContentLength = MIN_CONTENT_LENGTH;
    
    seeds = new EntityList();
    pairedTrie = new PairedTrie();
    badWordFilterer = new BadWordFilterer();
  }
  
  public WrapperFactory(String langID) {
    this();
    setLangID(langID);
  }
  
  public void addStopword(String stopword, boolean isUnigram) {
    badWordFilterer.add(stopword, isUnigram);
  }
  
  public void addStopwords(Collection<String> stopwords, boolean isUnigram) {
    badWordFilterer.add(stopwords, isUnigram);
  }
  
  public Set<Wrapper> build(Document document) {
    if (seeds == null || seeds.isEmpty()) {
      log.fatal("Seeds were not set for " + this.getClass().getSimpleName());
      return null;
    }
    Set<Wrapper> wrappers = new HashSet<Wrapper>();
    if (document == null || document.getText().trim().length() == 0)
      return wrappers;

    // for each seed, extract all contexts and insert them into the corresponding trie
    pairedTrie.clear();
    
    for (int i = 0; i < seeds.size(); i++) {
      try {
        // updates 'pairedTrie'
        getContext(seedPatterns.get(i), seeds.get(i), document.getText());
      } catch (StackOverflowError soe) {
        log.warn("A stack overflow error occurred!");
        return wrappers;
      }
    }
//    log.info(pairedTrie.toString());

    // get longest common context
    Set<Wrapper> contexts = pairedTrie.getWrappers(minSeedsBracketed, minContextLength, 0);
    
    // make wrappers based on context numTypes
    Wrapper wrapper;
    for (Wrapper context : contexts) {
      if (context.getNumCommonTypes() == 1) {
        // repeatedly make wrappers until one succeeds (i.e. extracts something)
        wrapper = toUniSeedWrapper(context, document);
      } else {
        // setting maxLength to zero means MAX_CONTENT_LENGTH
        wrapper = toWrapper(context, document, 0);
      }
      if (wrapper != null)
        wrappers.add(wrapper);
    }
    return wrappers;
  }
  
  public void clear() {
    seeds.clear();
    pairedTrie.clear();
    badWordFilterer.clear();
  }
  
  public Object getFeature() {
    return pairedTrie.getFeature();
  }
  
  public int getMinContextLength() {
    return minContextLength;
  }
  
  public int getMinSeedsBracketed() {
    return minSeedsBracketed;
  }
  
  public void loadStopwords(File listFile) {
    badWordFilterer.loadStopwords(listFile);
  }
  
  public void setFeature(Object feature) {
    pairedTrie.setFeature(feature);
  }
  
  public void setLangID(String langID) {
    contentPattern = LangProvider.getLang(langID).getPattern();
    log.debug("Content Pattern [" + langID + "]: " + contentPattern);
  }

  public void setMinContextLength(int minContextLength) {
    this.minContextLength = minContextLength;
  }

  public void setMinSeedsBracketed(int minSeedsBracketed) {
    this.minSeedsBracketed = minSeedsBracketed;
  }

  public void setSeeds(EntityList seeds) {
    this.seeds.clear();
    this.seeds.addAll(seeds);
    seedPatterns = toSeedPatterns(this.seeds);
    
    // reset the minimum content length according to the seeds
    Integer minSeedLength = seeds.getMinStringLength();
    if (minSeedLength != null)
      minContentLength = Math.min(minSeedLength, minContentLength);
  }
  
  private boolean checkContent(String s) {
    if (!checkSimpleContent(s, minContentLength)) return false;
    if (badWordFilterer.isBad(s)) return false;
    if (!contentPattern.matcher(s).matches()) return false;
    return true;
  }

  private void extractUnaryContents(Wrapper wrapper, String document) {
    int leftStart = document.indexOf(wrapper.getLeft());
    int rightStart = 0;
    int badContent = 0, goodContent = 0;
    Object[] keys = new Object[] {wrapper, document};

    while (leftStart != -1) {
      int leftEnd = leftStart + wrapper.getLeft().length();
      if (leftEnd > rightStart) {
        rightStart = document.indexOf(wrapper.getRight(), leftEnd + minContentLength);
        if (rightStart == -1) break;
      }
      int nextLeftStart = document.indexOf(wrapper.getLeft(), leftEnd);
      int nextLeftEnd = nextLeftStart + wrapper.getLeft().length();
      
      if (nextLeftStart == -1 || nextLeftEnd > rightStart) {  // added 11/10/2008
        String content = tidy(document.substring(leftEnd, rightStart), true);
        String name = tidy(content);
        if (checkContent(name)) {
          goodContent++;
          wrapper.addContent(new EntityLiteral(name));
          Originator.add(name, content, keys);
        } else {
          badContent++;
          // to reduce processing time (added 04/14/2009)
          double ratio = (double) (badContent+1) / (goodContent+1);
          if (ratio > MAX_BAD_GOOD_CONTENT_RATIO) break;
        }
      }
      leftStart = nextLeftStart;
    }
  }

  private void extractBinaryContents(Wrapper wrapper, String document) {
    if (!wrapper.isRelational()) return;
    int leftStart = document.indexOf(wrapper.getLeft());
    int middleStart = 0, rightStart = 0;
    int badContent = 0, goodContent = 0;
    Object[] keys = new Object[] {wrapper, document};
    
    while (leftStart != -1) {
      int leftEnd = leftStart + wrapper.getLeft().length();
      
      if (leftEnd > middleStart) {
        middleStart = document.indexOf(wrapper.getMiddle(), leftEnd + minContentLength);
        if (middleStart == -1) break;
      }
      
      int middleEnd = middleStart + wrapper.getMiddle().length();
      if (middleEnd > rightStart) {
        rightStart = document.indexOf(wrapper.getRight(), middleEnd + minContentLength);
        if (rightStart == -1) break;
      }
      
      int nextLeftStart = document.indexOf(wrapper.getLeft(), leftEnd);
      int nextLeftEnd = nextLeftStart + wrapper.getLeft().length();
      
      if (nextLeftStart == -1 || nextLeftEnd > middleStart) {  // added 11/10/2008
        String o1 = tidy(document.substring(leftEnd, middleStart), true);
        String o2 = tidy(document.substring(middleEnd, rightStart), true);
        
        if (wrapper.isReversed()) {
          // flip o1 and o2
          String o3 = o1; o1 = o2; o2 = o3;
        }
        String s1 = tidy(o1), s2 = tidy(o2);
        
//        if (checkContent(s1) && checkContent(s2) && !s1.equals(s2)) {
        if (checkContent(s1) && checkContent(s2) && !o1.contains(o2) && !o2.contains(o1)) {
          goodContent++;
          wrapper.addContent(new EntityLiteral(s1, s2));
          Originator.add(s1, o1, keys);
          Originator.add(s2, o2, keys);
        } else {
          badContent++;
          // to reduce processing time (added 04/14/2009)
          double ratio = (double) (badContent+1) / (goodContent+1);
          if (ratio > MAX_BAD_GOOD_CONTENT_RATIO) break;
        }
      }
      leftStart = nextLeftStart;
    }
  }

  private void getContext(Pattern[] seedPatterns, Entity seed, String document) throws StackOverflowError {
    boolean relationReversed = false;
    
    for (Pattern p : seedPatterns) {
      Matcher m = p.matcher(document);
      
      // the following line consumes the most running time!
      while (m.find()) {
        
        // extract 'left' context
        int index = Math.max(m.start() - MAX_CONTEXT_LENGTH, 0);
        String leftContext = document.substring(index, m.start());
        if (leftContext.length() == 0) continue;

        // extract 'right' context
        index = Math.min(m.end() + MAX_CONTEXT_LENGTH, document.length());
        String rightContext = document.substring(m.end(), index);
        if (rightContext.length() == 0) continue;

        // extract 'middle' context
        MiddleContext middleContext = null;
        if (m.groupCount() > 0 && m.group(1) != null) {
          if (m.group(1).equals(" ")) continue;
          String middle = m.group(1).trim();
          if (middle.length() == 0)
            middle = m.group(1);
          middleContext = new MiddleContext(middle, relationReversed);
        }
        pairedTrie.add(leftContext, middleContext, rightContext, seed);
      }
      relationReversed = !relationReversed;
    }
  }

  private Wrapper toUniSeedWrapper(Wrapper context, Document document) {
    for (int maxLength = UNISEED_MAX_CONTEXT_LENGTH; maxLength >= UNISEED_MIN_CONTEXT_LENGTH; maxLength /= 2) {
      // make wrappers with constrained context length
      Wrapper wrapper = toWrapper(context, document, maxLength);
      if (wrapper != null) return wrapper;
    }
    return null;
  }

  // use the wrappers to extract content from document
  private Wrapper toWrapper(Wrapper wrapper, Document document, int maxContextLength) {
    // discard context of only one space character (almost every English word is surrounded by spaces)
    String left = wrapper.getLeft(), right = wrapper.getRight();
    if (left.equals(" ") || right.equals(" "))
      return null;
    
    if (!match(wrapper, wrapperLevel)) {
      log.debug(Helper.repeat('-', 80) + "\nContext: " + wrapper + "\n" + 
                "WARN: The wrapper failed to match level: " + wrapperLevel);
      return null;
    }

    // restrict the maximum length (for one-seed queries only)
    if (maxContextLength > 0) {
      if (left.length() > maxContextLength)
        wrapper.left = left.substring(left.length() - maxContextLength);
      if (right.length() > maxContextLength)
        wrapper.right = right.substring(0, maxContextLength); 
    }
    
    // extracts contents without using regexp => faster (added 05/03/2007)
    if (wrapper.isRelational())
      extractBinaryContents(wrapper, document.getText());
    else extractUnaryContents(wrapper, document.getText());
    
    wrapper.setURL(document.getURL());
//    log.debug("Seed Weights:" + wrapper.getCommonTypes().getSumWeights(getFeature()));
    log.debug(Helper.repeat('-', 80) + "\n" + wrapper.toDetails());
    
    // verifying the correctness of the wrapper
    int numSeeds = 0, numNonSeeds = wrapper.getContents().size();
    for (Pattern[] patterns : seedPatterns) {
      boolean matches = false;
      for (Pattern p : patterns)
        for (EntityLiteral content : wrapper.getContents())
          // a good wrapper extracts all seeds plus at least one non-seed
          if (p.matcher(content.toString()).matches()) {
            numNonSeeds--;
            matches = true;
          }
      if (matches) numSeeds++;
    }
    
    // return false if the wrapper is not good
    if (numSeeds < wrapper.getNumCommonTypes()) {
      log.debug("WARN: Found " + numSeeds + " out of a required minimum of " + 
                wrapper.getNumCommonTypes() + " seeds: " + wrapper.getSeeds());
      return null;
    } else if (numNonSeeds == 0) {
      log.debug("WARN: Found " + numNonSeeds + " non-seeds!");
      return null;
    }
    return wrapper;
  }
  
  private static boolean match(Wrapper wrapper, int level) {
    int leftLevel = level / 10;
    int rightLevel = level - (leftLevel*10);
    
    if (wrapper.isRelational()) {
      return match(wrapper.getLeft(), leftLevel, true) && 
             match(wrapper.getMiddle(), rightLevel, false) &&
             match(wrapper.getMiddle(), leftLevel, true) && 
             match(wrapper.getRight(), rightLevel, false);
    } else {
      return match(wrapper.getLeft(), leftLevel, true) && 
             match(wrapper.getRight(), rightLevel, false);
    }
  }
  
  /**
   * 0: no restriction
   * 1: contains either '<' or '>'
   * 2: for left : ends with '>'
   *    for right: starts with '<'
   * 3: contains a complete HTML tag "<[^<>]+>"
   * 4: for left : ends with a complete tag,
   *    for right: starts with a compelete tag
   */
  private static boolean match(String s, int level, boolean isLeft) {
    s = s.trim();
    switch (level) {
      case 0: return true;
      case 1: return s.contains("<") || s.contains(">");
      case 2: return isLeft ? s.endsWith(">") : s.startsWith("<");
      case 3: return s.matches("(?s).*<[^<>]+>.*");
      case 4: return isLeft ? s.matches("(?s).*<[^<>]+>") : s.matches("(?s)<[^<>]+>.*");
    }
    return false;
  }

  public int getWrapperLevel() {
    return wrapperLevel;
  }

  public void setWrapperLevel(int wrapperLevel) {
    this.wrapperLevel = wrapperLevel;
  }
}
