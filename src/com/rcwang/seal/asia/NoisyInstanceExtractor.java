/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.asia;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.asia.HyponymProvider.Hyponym;
import com.rcwang.seal.eval.EvalResult;
import com.rcwang.seal.eval.Evaluator;
import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.translation.Annotator;
import com.rcwang.seal.translation.Proximator;
import com.rcwang.seal.translation.Proximator.Proxistat;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class NoisyInstanceExtractor {

  public static Logger log = Logger.getLogger(NoisyInstanceExtractor.class);
  
  public static final double SHRINK_POWER = 3; // [3] 1: shrink mildly, 5: shrink strongly
//  public static final int MAX_WRAPPER_SEEDS = 1000;  // [1000] consider only these top extracted instances
  public static final int NUM_RESULTS = 200;  // number of webpages per hyponym pattern (>100 requires multiple queries)
  public static final int MIN_ENTITIES = 2;  // [10] use back-off method if # of extracted instances is lower than this
//  public static final int MIN_SOURCE = 2; // filter entities having hyponym freq lower than this
  public static final int MAX_TO_PRINT = 20;  // number of extracted instances to print
  
  public static final int DEFAULT_ENGINE = -1; // 4: use Yahoo! only (due to mass amount of queries)
  public static final int DEFAULT_MIN_SNIPPET_FREQ = 2; // 2

  public static final String HIRAGANA_PATTERN = ".*\\p{InHiragana}+.*"; // reg-exp for hiragana
  public static final String DEFAULT_OUTPUT_FEATURE = "EG_PATTERN";
  
  // coefficient for each statistics
  public static final double HYPONYM_WEIGHT = (double) 1/4;
  public static final double SNIPPET_WEIGHT = (double) 1/4;
  public static final double EXCERPT_WEIGHT = (double) 1/4;
  public static final double CHUNK_DIST_WEIGHT = (double) 1/4;
  
  private int engine;
  private int confidence = 0;
  private int minSnippetFreq;
  private Evaluator evaluator;
  private EvalResult evalResult;
  private Object outputFeature;
  private EntityList entities;
  
  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println("Usage: java MemberExtractor langID category");
      return;
    }
    
    String targetLangID = args[0];
    String category = args[1].replace("_", " ");
    
    NoisyInstanceExtractor extractor = new NoisyInstanceExtractor();
    EntityList members = extractor.extract(category, targetLangID, true);
    
    System.out.println();
    for (int i = 0; i < Math.min(members.size(), 50); i++) {
      Entity entity = members.get(i);
      log.info((i+1) + ". " + entity.getName());
    }
  }
  
  public static List<String> makeGeneralQueries(String category) {
    final String regex = "\".+?\"";
    List<String> patterns = new ArrayList<String>();
    Matcher m = Pattern.compile(regex).matcher(category);
    while (m.find()) {
      String s = m.group(0).substring(1, m.group(0).length()-1);
      patterns.add(s.toLowerCase().trim());
    }
    category = category.replaceAll(regex, " ");
    String[] ss = category.split("\\s+");
    for (String s : ss) {
      s = s.trim();
      if (s.length() > 0)
        patterns.add(s.toLowerCase());
    }
    return patterns;
  }
  
  private static List<String> makeEgQueries(String category, String langID) {
    Hyponym hyponym = HyponymProvider.getHyponym(langID);
    if (hyponym == null) {
      log.fatal("Error: Could not find hyponym patterns for language: " + langID);
      return null;
    }
    List<String> phrases = hyponym.getHyponymPhrases();
    List<String> patterns = new ArrayList<String>();
    for (String s : phrases) {
      s = s.trim();
      int queryType = s.endsWith(HyponymProvider.C) ? 2 : 1;
      s = s.replace(HyponymProvider.C, category);
      for (int i = 0; i < queryType; i++)
        s = Annotator.QUERY_SYMBOL + s;
      patterns.add(s.trim());
    }
    return patterns;
  }
  
  private static void removeHiragana(EntityList entities) {
    boolean[] toRemove = new boolean[entities.size()];
    for (int i = 0; i < entities.size(); i++) {
      Entity entity = entities.get(i);
      String name = entity.getName().toString();
      if (name.matches(HIRAGANA_PATTERN)) 
        toRemove[i] = true;
    }
    entities.remove(toRemove);
  }
  
  public NoisyInstanceExtractor() {
    //setEngine(DEFAULT_ENGINE);
    setEngine(GlobalVar.getGlobalVar().getUseEngine());
    setOutputFeature(DEFAULT_OUTPUT_FEATURE);
    setMinSnippetFreq(DEFAULT_MIN_SNIPPET_FREQ);
  }
  
  public EntityList extract(String category, String langID) {
    boolean useBackOff = false;
    entities = new EntityList();
    evalResult = null;
    
    final boolean[] booleans = new boolean[] {false, true};
    for (int j = 0; j < booleans.length; j++) {
      useBackOff = booleans[j];
      EntityList extracted = extract(category, langID, useBackOff);
      if (extracted == null) continue;
      entities.addAll(extracted);
      if (entities.size() >= MIN_ENTITIES) break;
      log.info("Number of extracted entities (" + entities.size() + ") is < " + MIN_ENTITIES + 
               ((j == 0) ? ", using back-off strategy..." : ""));
    }
    if (entities.isEmpty()) return null;
    entities.assignScore(outputFeature, true);
    
    // set the confidence score
    confidence = (useBackOff ? 1 : 2);
    
    if (evaluator != null)
      evalResult = evaluator.evaluate(entities);
    log.info(this.getClass().getSimpleName() + " extracted the following " + entities.size() + " instances for category: " + category);
    log.info(entities.toDetails(MAX_TO_PRINT, outputFeature));
    return entities;
  }
  
  public int getConfidence() {
    return confidence;
  }
  
  public int getEngine() {
    return engine;
  }

  public EntityList getEntities() {
    return entities;
  }

  public EvalResult getEvalResult() {
    return evalResult;
  }

  public Evaluator getEvaluator() {
    return evaluator;
  }

  public int getMinSnippetFreq() {
    return minSnippetFreq;
  }

  public Object getOutputFeature() {
    return outputFeature;
  }

  public void setEngine(int useEngine) {
    this.engine = useEngine;
  }

  public void setEvaluator(Evaluator evaluator) {
    this.evaluator = evaluator;
  }

  public void setMinSnippetFreq(int minSnippetFreq) {
    this.minSnippetFreq = minSnippetFreq;
  }

  public void setOutputFeature(Object outputFeature) {
    this.outputFeature = outputFeature;
  }
  
  private EntityList extract(String category, String langID, boolean useBackOff) {
    if (Helper.empty(category)) {
      throw new IllegalArgumentException("Category name must be specified!");
    } else if (minSnippetFreq < 0) {
      throw new IllegalArgumentException("Minimum Snippet Frequency must be greater than zero!");
    }
    
    // setup up the proximator
    Proximator proximator = new Proximator(langID);
    proximator.setNumResults(NUM_RESULTS);
    proximator.setMinSnippetFreq(minSnippetFreq);
    proximator.setEngine(engine);
    proximator.addStopword(category, false);
    Hyponym hyponym = HyponymProvider.getHyponym(langID);
    if (hyponym == null) return null;
    proximator.addStopwords(hyponym.getSplitWords(), true);

    List<String> queries;
    EntityList entities;
    String msg = "Extracting instances for \"" + category + "\" using %1 patterns " +
                 "with min-snippet-freq of " + minSnippetFreq + "...";
    
    if (!useBackOff) {
      log.info(msg.replace("%1", "eg"));
      proximator.setExtractionMode(1);
      queries = makeEgQueries(category, langID);
      entities = proximator.getTargets(queries, false);
      
      // filter out entities not having at least MIN_SOURCE
      /*if (entities.count(Proxistat.SOURCE, MIN_SOURCE) > MIN_ENTITIES)
        entities.absoluteFilter(Proxistat.SOURCE, MIN_SOURCE);*/
    } else {
      log.info(msg.replace("%1", "no"));
      proximator.setExtractionMode(2);
      queries = makeGeneralQueries(category);
      entities = proximator.getTargets(queries, true);
    }
    
    // Japanese instances composed of all hiragana chars are unhelpful
    if (langID.equals(LangProvider.JAP[LangProvider.ID]))
      removeHiragana(entities);
    
    // assigns weights to 'outputFeature', and sort by the weights
    rank(entities, category, proximator.getTotal());
    
    // consider only some top instances
//    entities = entities.subList(0, MAX_WRAPPER_SEEDS);
    
    // collapse instances when one is a substring of another
    // sorts the list by 'outputFeature'
    entities.shrink(outputFeature, SHRINK_POWER);
//    entities.normalize(outputFeature);
    return entities;
  }

  private void rank(EntityList entities, String category, Entity stat) {
    int numHyponyms = (int) stat.getWeight(Proxistat.SOURCE);
    int numSnippets = (int) stat.getWeight(Proxistat.SNIPPET);
    int numExcerpts = (int) stat.getWeight(Proxistat.EXCERPT);
    int numChunks = (int) stat.getWeight(Proxistat.CHUNK);
    
    for (Entity e : entities) {
      double pHyponym = (numHyponyms == 0) ? 0 : e.getWeight(Proxistat.SOURCE) / numHyponyms;
      double pSnippet = (numSnippets == 0) ? 0 : e.getWeight(Proxistat.SNIPPET) / numSnippets;
      double pExcerpt = (numExcerpts == 0) ? 0 : e.getWeight(Proxistat.EXCERPT) / numExcerpts;
      double pWTF = (numChunks == 0) ? 0 : e.getWeight(Proxistat.DIST) / numChunks;
      
//    double w = Math.log(prob_snippet) + Math.log(prob_excerpt) + Math.log(prob_wtf);
//    double w = prob_snippet * prob_excerpt * prob_wtf;
      double w = HYPONYM_WEIGHT * pHyponym + 
                 SNIPPET_WEIGHT * pSnippet + 
                 EXCERPT_WEIGHT * pExcerpt + 
                 CHUNK_DIST_WEIGHT * pWTF;
      e.setWeight(outputFeature, w);
    }
    entities.sortBy(outputFeature, false);
  }
}
