/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.translation;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.asia.HyponymProvider;
import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.expand.LangProvider.Lang;
import com.rcwang.seal.fetch.BadWordFilterer;
import com.rcwang.seal.fetch.Excerpt;
import com.rcwang.seal.fetch.Snippet;
import com.rcwang.seal.fetch.WebFetcher;
import com.rcwang.seal.translation.Annotator.Annotation;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.Originator;
import com.rcwang.seal.util.StringEditDistance;

// TODO: use Trie?
public class Proximator {
  
  public static enum Proxistat { DIST, CHUNK, EXCERPT, SNIPPET, SOURCE, PROXISCORE }
  
  public static Logger log = Logger.getLogger(Proximator.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final double DEFAULT_LAMBDA = 0.1;
  public static final double DEFAULT_SIM_THRESHOLD = 0.8;
  public static final int DEFAULT_EXTRACTION_MODE = 0;
  public static final int DEFAULT_MIN_SNIPPET_FREQ = 3;
  public static final int DEFAULT_NUM_TOP_TRANS = 3;
  public static final int DEFAULT_NUM_RESULTS = 100;
  public static final int DEFAULT_ENGINE = -1; // 4: use Yahoo! only
  
  private Map<Entity, Set<Snippet>> snippetMap;
  private Proximator reverseProximator;
//  private TranslationPair transPair;
  private BadWordFilterer badWordFilterer;
  private Set<String> sourceSet;
  private Pattern contentPattern;
  private EntityList entityList;
  private String targetLangID;
  private WebFetcher fetcher;
  private Entity total;
  
  private int numTopTrans;
  private int minSnippetFreq;
  private int extractionMode; // 0: translation, 1: e.g. member, 2: general member
  private double simThreshold;
  private double lambda;
  
  public static void main(String args[]) {
    Proximator proximator = new Proximator("en");
    Proximator rProximator = new Proximator("zh-TW");
    proximator.setReverseProximator(rProximator);

//    translator.setSource("約翰甘迺迪");
//    translator.setSource("侏儸紀公園");
//    translator.setSource("不可能的任務");
//    translator.setSource("George W. Bush");
//    translator.setSource("Doraemon");
//    translator.setSource("도라에몽");
//    translator.setSource("Wanted");
//    translator.setSource("The Dark Knight");
//    translator.setSource("senba");

    String[] sources = new String[] {
        "海角七號",
        "校樹青青",
        "悲情城市",
        "匹茲堡海盜",
        "紐約洋基",
        "洛杉磯道奇",
        "不可能的任務",
        "甘迺迪",
        "侏儸紀公園",
        "救難小英雄澳洲歷險記",
        "小美人魚",
        "哈利波特",
    };
    
    /*String[] sources = new String[] {
        "獅子王",
    };*/
    
//    translator.setToLangID("zh-TW");
    /*String[] sources = new String[] {
        "The Lord of the Rings",
        "The Lion King",
        "The Little Mermaid",
        "Beauty and the Beast",
    };*/
    
//    for (String source : sources)
    proximator.addSources(Arrays.asList(sources), false);

    for (String source : sources) {
      EntityList entities = proximator.getTargets(source);
      log.info("-----------------------------" + source + "-------------------------------");
      for (Entity entity : entities)
        log.info(entity);
//      Entity entity = translator.getTarget(source);
//      if (entity != null)
//        log.info(source + " translates to " + entity.getName());
    }
  }

  private static void assignDistScore(String transID, EntityList entities, Set<Excerpt> excerpts) {
    for (Entity entity : entities) {
      for (Excerpt excerpt : excerpts) {
        excerpt.clearTarget();
        // kmr possible gotcha
        excerpt.annotateTarget(entity.getName().toString());
        double distScore = excerpt.getDistScore();
        
        if (distScore > 0) {
          entity.addWeight(transID, distScore);
          entity.addWeight(Proxistat.DIST, distScore);
        }
      }
    }
  }
  
  private static Set<Snippet> getSnippetWithIndex(int index, Set<Snippet> snippets) {
    Set<Snippet> set = new HashSet<Snippet>();
    for (Snippet snippet : snippets)
      if (snippet.getIndex() == index)
        set.add(snippet);
    return set;
  }
  
  public Proximator(String targetLangID) {
//    transPair = new TranslationPair();
    entityList = new EntityList();
    sourceSet = new HashSet<String>();
    snippetMap = new HashMap<Entity, Set<Snippet>>();
    total = new Entity(Proxistat.class.getName());
    fetcher = new WebFetcher();
    fetcher.setAnnotateQuery(true);
    
    setTargetLangID(targetLangID);
    //setEngine(DEFAULT_ENGINE);
    setEngine(gv.getUseEngine());
    setMinSnippetFreq(DEFAULT_MIN_SNIPPET_FREQ);
    setExtractionMode(DEFAULT_EXTRACTION_MODE);
    setNumTopTrans(DEFAULT_NUM_TOP_TRANS);
    setSimThreshold(DEFAULT_SIM_THRESHOLD);
    setLambda(DEFAULT_LAMBDA);
    setNumResults(DEFAULT_NUM_RESULTS);
    
    badWordFilterer = new BadWordFilterer();
    badWordFilterer.loadStopwords(gv.getStopwordsList());
  }
  
  public void addSource(String source) {
    addSources(Arrays.asList(source), true);
  }
  
  /**
   * Adds a set of source words
   * @param sources a collection of source words
   * @param asOneQuery true to use all words as one search query string, 
   * false to use each word as an individual query string
   */
  public void addSources(List<String> sources, boolean asOneQuery) {
    Lang targetLang = LangProvider.getLang(targetLangID);
    if (targetLang == null) return;
    contentPattern = targetLang.getPattern();

    // the snippets fetched are indexed by source word's index in the list
    fetcher.setNumSubSeeds(asOneQuery ? 0 : 1);
    Set<Snippet> snippets = fetcher.fetchSnippets(sources, null);

    int numSources = (int) total.getWeight(Proxistat.SOURCE);
    int numEntities = (int) total.getWeight(Proxistat.CHUNK);
    
    if (asOneQuery) {
      addSources(sources, snippets);
    } else {
      List<String> list = new LinkedList<String>();
      for (int i = 0; i < sources.size(); i++) {
        String source = sources.get(i);
        System.out.print("Proximating in " + LangProvider.getName(targetLangID) + " using query: " + source + Helper.repeat(' ', 20) + "\r");
        list.add(source);
        addSources(list, getSnippetWithIndex(i, snippets));
        list.clear();
      }
      System.out.println();
    }
    
    log.info("Fetched " + ((int) total.getWeight(Proxistat.SOURCE)-numSources) + " sources, " + 
             snippets.size() + " snippets, and " +
             ((int) total.getWeight(Proxistat.CHUNK)-numEntities) + " entities in total!");
  }
  
  public void addStopword(String stopword, boolean isUnigram) {
    badWordFilterer.add(stopword, isUnigram);
  }
  
  public void addStopwords(Collection<String> stopwords, boolean isUnigram) {
    badWordFilterer.add(stopwords, isUnigram);
  }
  
  public void clear() {
    entityList.clear();
    sourceSet.clear();
    snippetMap.clear();
    total.clear();
  }
  
  public boolean[] getEngine() {
    return fetcher.getUseEngine();
  }
  
  public int getExtractionMode() {
    return extractionMode;
  }

  public double getLambda() {
    return lambda;
  }
  
  public double getMinSnippetFreq() {
    return minSnippetFreq;
  }
  
  public int getNumResults() {
    return fetcher.getNumResults();
  }
  
  public int getNumSource() {
    return sourceSet.size();
  }

  public int getNumTopTrans() {
    return numTopTrans;
  }
  
  public Proximator getReverseProximator() {
    return reverseProximator;
  }

  public double getSimThreshold() {
    return simThreshold;
  }

  public Entity getTarget(String source) {
    if (reverseProximator == null) {
      log.fatal("Reverse translator for language " + targetLangID + " is missing!");
      return null;
    }
    Entity bestTarget = null;
    double maxWeight = Double.MIN_VALUE;
    
    // collapse numTopTrans of translations
    EntityList targetList = getTargets(source);
    targetList = targetList.subList(0, numTopTrans*2);
    // shrink completely by name length
    targetList.shrink(Proxistat.PROXISCORE, Double.POSITIVE_INFINITY);
    targetList = targetList.subList(0, numTopTrans);
    
    for (int i = 0; i < targetList.size(); i++) {
      Entity targetEnt = targetList.get(i);
      double targetWeight = targetEnt.getWeight(Proxistat.PROXISCORE);
      // kmr possible gotcha
      EntityList sourceList = reverseProximator.getTargets(targetEnt.getName().toString());
      
      for (int j = 0; j < Math.min(numTopTrans, sourceList.size()); j++) {
        Entity sourceEnt = sourceList.get(j);
        double sourceWeight = sourceEnt.getWeight(Proxistat.PROXISCORE);
        // kmr possible gotcha
        double similarity = StringEditDistance.levenshtein(sourceEnt.getName().toString(), source);
        if (similarity < simThreshold) continue;
        log.info(source + " --> " + targetEnt.getName() + "(" + Math.round(targetWeight*100) + 
                 "%) --> " + sourceEnt.getName() + "(" + Math.round(sourceWeight*100) + "%)");
        double weight = targetWeight * sourceWeight * similarity;
        if (weight > maxWeight) {
          maxWeight = weight;
          bestTarget = targetEnt;
        }
      }
    }
    return bestTarget;
  }

  public String getTargetLangID() {
    return targetLangID;
  }
  
  public EntityList getTargets(List<String> sources, boolean asOneQuery) {
    if (sources.isEmpty()) {
      log.error("Error: Missing input source words!");
      return new EntityList();
    }
    
    String transID = getTransID(Helper.toReadableString(sources));
    if (!sourceSet.contains(transID))
      addSources(sources, asOneQuery);
    
    // return immediately if not in translation mode
    if (extractionMode != 0)
      return entityList;
    
    // get a list of entities in this document (document of the input source)
    EntityList subList = entityList.getEntities(transID);
    
    // get the sum of wtf(x) for all entities x in this document
    double WTF_d = subList.getSumWeights(transID) + subList.size();
    
    // get the sum of wtf(x) for all entities x in the rest of the documents
    double WTF_c = entityList.size();
    for (String s : sourceSet)
      if (!s.equals(transID))
        WTF_c += entityList.getSumWeights(getTransID(s));
    
    // added 10/15/2009 for testing
    /*int numSnippets = (int) total.getWeight(Proxistat.SNIPPET);
    int numExcerpts = (int) total.getWeight(Proxistat.EXCERPT);
    int numChunks = (int) total.getWeight(Proxistat.CHUNK);*/
    
    for (Entity e : subList) {
      
      // get wtf(x) for the input x in this document only
      double wtf_d = e.getWeight(transID) + 1;
      
      // get the sum of wtf(x) for all input x in other documents
      double wtf_c = 1;
      for (String s : sourceSet)
        if (!s.equals(transID))
          wtf_c += e.getWeight(getTransID(s));
      
      // compute the score using linear interpolation of the probabilities
      double prob_wtf_d = wtf_d / WTF_d;
      double prob_wtf_c = wtf_c / WTF_c;
      double score = lambda*prob_wtf_d + (1-lambda)*(1-prob_wtf_c);
      
      // added 10/15/2009 for testing
      /*double pSnippet = (numSnippets == 0) ? 0 : e.getWeight(Proxistat.SNIPPET) / numSnippets;
      double pExcerpt = (numExcerpts == 0) ? 0 : e.getWeight(Proxistat.EXCERPT) / numExcerpts;
      score = lambda * ((double) 1/3 * pSnippet + (double) 1/3 * pExcerpt + (double) 1/3 * prob_wtf_d) + (1-lambda)*(1-prob_wtf_c);
      score = (double) 1/3 * pSnippet + (double) 1/3 * pExcerpt + (double) 1/3 * prob_wtf_d;*/

      e.setWeight(Proxistat.PROXISCORE, score);
    }
    // shrink partially
    subList.shrink(Proxistat.PROXISCORE, 1);
    return subList;
  }

  public EntityList getTargets(String source) {
    return getTargets(Arrays.asList(source), true);
  }

  public Entity getTotal() {
    return total;
  }

  public String getTransID(String source) {
      return source + "." + targetLangID;
  }

  /*public TranslationPair getTransPair() {
    return transPair;
  }*/

  public void setEngine(int engine) {
    fetcher.setUseEngine(engine);
  }

  public void setExtractionMode(int mode) {
    this.extractionMode = mode;
  }

  public void setLambda(double lambda) {
    this.lambda = lambda;
  }

  public void setMinSnippetFreq(int minExcerptFreq) {
    this.minSnippetFreq = minExcerptFreq;
  }

  public void setNumResults(int numResults) {
    fetcher.setNumResults(numResults);
  }

  public void setNumTopTrans(int numTopTrans) {
    this.numTopTrans = numTopTrans;
  }

  public void setReverseProximator(Proximator reverseTranslator) {
    this.reverseProximator = reverseTranslator;
  }
  
  public void setSimThreshold(double simThreshold) {
    this.simThreshold = simThreshold;
  }
  
  public void setTargetLangID(String targetLangID) {
    if (!LangProvider.supports(targetLangID)) {
      log.error("Unknown lang: " + targetLangID);
      return;
    }
    this.targetLangID = targetLangID;
    fetcher.setLangID(targetLangID);
  }

  private void addSources(List<String> sources, Set<Snippet> snippets) {
    Set<Excerpt> goodExcerpts = new HashSet<Excerpt>();
    EntityList entities = extract(snippets, goodExcerpts);
    
    // output excerpts for debugging purposes
    /*int i = 1;
    for (Excerpt excerpt : excerpts)
      log.debug((i++) + ". " + excerpt);*/
    
    // filter out entities not having enough snippet frequency
//    log.info(this.getClass().getSimpleName() + " is removing instances with snippet-freq < " + minSnippetFreq);
    entities.absoluteFilter(Proxistat.SNIPPET, minSnippetFreq);
    
    // inserts weighted term frequency (WTF) as a feature (keyed by 'transID')
    // this is very computational expensive, try to minimize entities 
    // as much as possible before reaching this step
    String transID = getTransID(Helper.toReadableString(sources));
    assignDistScore(transID, entities, goodExcerpts);
    
    sourceSet.add(transID);
  }

  private void extract(Excerpt excerpt, EntityList entities) {
    if (extractionMode == 1)
      excerpt.chopText();
    
    String excerptStr = excerpt.toString();
    Matcher m = contentPattern.matcher(excerpt.getText());
    while (m.find()) {
      Annotation ann = excerpt.new Annotation(m.start(), m.end(), Annotator.TARGET_TYPE);
      
      for (Annotation a : excerpt.fixOverlap(ann)) {
        String chunk = a.getText().trim();
        
        if (extractionMode == 0) {
          entities.add(extract(chunk, a.start(), excerptStr));
        } else {
          for (String subChunk : HyponymProvider.split(chunk, targetLangID))
            entities.add(extract(subChunk, a.start(), excerptStr));
        }
      }
    }
  }

  private EntityList extract(Set<Snippet> snippets, Set<Excerpt> goodExcerpts) {
    EntityList sourceEntities = new EntityList();
    EntityList snippetEntities = new EntityList();
    EntityList excerptEntities = new EntityList();
    Set<Excerpt> excerpts = new HashSet<Excerpt>();
    
    for (Snippet snippet : snippets) {
      snippetEntities.clear();
      
      // collect all bilingual excerpts (title + summary)
      excerpts.clear();
      excerpts.add(snippet.getTitle());
      excerpts.addAll(snippet.getExcerpts());
      
      for (Excerpt excerpt : excerpts) {
        // ignore excerpts that do not contain any source annotation
        if (excerpt.countSource() == 0) continue;

        // extract entities in target language from the excerpt
        excerptEntities.clear();
        extract(excerpt, excerptEntities);
        if (excerptEntities.isEmpty()) continue;
        
        goodExcerpts.add(excerpt);
        excerptEntities.addWeight(Proxistat.EXCERPT, 1);
        total.addWeight(Proxistat.EXCERPT, 1);
        snippetEntities.addAll(excerptEntities);
      }
      
      if (snippetEntities.isEmpty()) continue;
      snippetEntities.addWeight(Proxistat.SNIPPET, 1);
      total.addWeight(Proxistat.SNIPPET, 1);
      sourceEntities.addAll(snippetEntities);
    }
    
    sourceEntities.addWeight(Proxistat.SOURCE, 1);
    total.addWeight(Proxistat.SOURCE, 1);
    return sourceEntities;
  }

  private Entity extract(String content, int startOffset, String excerpt) {
    // tidy the extracted content, but not to its canonical form
    content = WrapperFactory.tidy(content, true);
    
    // verify the content
    if (!WrapperFactory.checkSimpleContent(content)) return null;
    if (badWordFilterer.isBad(content)) return null;
    // discard entities starting with a number (added 11/01/2008)
    if (content.matches("^\\d+.*$")) return null;
    
    // remove the beginning words (e.g., "the") (added 10/19/2008)
    if (extractionMode == 1 && startOffset == 0)
      content = HyponymProvider.removeBeginWord(content, targetLangID);

    // tidy the extracted content to its canonical form
    Entity entity = entityList.add(WrapperFactory.tidy(content, false));
    entity.addWeight(Proxistat.CHUNK, 1);
    total.addWeight(Proxistat.CHUNK, 1);
    
    // store the original surface string
    Object[] keys = new Object[] {excerpt, startOffset, targetLangID};
    EntityLiteral e = entity.getName();
    Originator.add(e.arg1, content, keys);
    if (e.arg2 != null)
        Originator.add(e.arg2, content, keys);
    
    return entity;
  }
}
