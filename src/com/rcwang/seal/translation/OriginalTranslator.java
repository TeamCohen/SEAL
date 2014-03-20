/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.translation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.fetch.Excerpt;
import com.rcwang.seal.fetch.Snippet;
import com.rcwang.seal.fetch.WebFetcher;
import com.rcwang.seal.util.GlobalVar;

public class OriginalTranslator {
  
  // TODO: use Trie?
  public static Logger log = Logger.getLogger(OriginalTranslator.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  public static enum NEFeatures { CTF, CEF, CSF, SF, CF, DIST_SCORE, DIST_RANK }
  public static final int MIN_CHUNK_FREQ = 2;
  public static final int MIN_CONTENT_LENGTH = 2;
  public static final int NUM_TOP_DIST_CHUNK = 10;
  public static final int NUM_RESULTS = 100;
  
  private Pattern contentPattern;
  private EntityList entityList;
  private List<Annotator> annotators;
  
  public static void main(String args[]) {
    
    OriginalTranslator translator = new OriginalTranslator();
//    translator.setSource("約翰甘迺迪");
//    translator.setSource("侏儸紀公園");
//    translator.setSource("不可能的任務");
//    translator.setSource("George W. Bush");
//    translator.setSource("Doraemon");
//    translator.setSource("도라에몽");
//    translator.setSource("Wanted");
//    translator.setSource("The Dark Knight");
//    translator.setSource("senba");
    EntityList entities;
    entities = translator.translate("匹茲堡鋼人", "en");
//    entities = translator.translate("cinderella", "zh-TW");
    
    for (Entity entity : entities)
      log.info(entity);
  }
  
  public OriginalTranslator() {
    entityList = new EntityList();
    annotators = new ArrayList<Annotator>();
  }
  
  public EntityList translate(String source, String toLangID) {
    entityList.clear();
    annotators.clear();
    
    /*String contentPatternStr = WrapperFactory.getContentPattern(toLangID);
    if (contentPatternStr == null) return null;
    contentPattern = Pattern.compile(contentPatternStr);*/
    
    contentPattern = LangProvider.getLang(toLangID).getPattern();
    if (contentPattern == null) return null;

    // added 2008-09-09
    if (toLangID.equals("en")) toLangID = "un";
    Set<Snippet> snippets = fetchSnippets(source, toLangID);

    injectChunkFeatures(source, snippets);
    injectDistFeatures();
    
//    int distRank = 1;
    double numChunks = entityList.getSumOfLogWeights(NEFeatures.CF);
    double numSubChunks = entityList.getSumOfLogWeights(NEFeatures.SF);
    double sumDistScore = entityList.getSumOfLogWeights(NEFeatures.DIST_SCORE);

    for (Entity entity : entityList) {
//      entity.setWeight(NEFeatures.DIST_RANK, distRank++);
      double score = 0;
      score += Math.log((entity.getWeight(NEFeatures.CF)+1) / (numChunks+1));
      score += Math.log((entity.getWeight(NEFeatures.CSF)+1) / (snippets.size()+1));
      score += Math.log((entity.getWeight(NEFeatures.CTF)+1) / (snippets.size()+1));
      score += Math.log((entity.getWeight(NEFeatures.CEF)+1) / (annotators.size()+1));
      score += Math.log((entity.getWeight(NEFeatures.DIST_SCORE)) / sumDistScore);
      score -= Math.log((entity.getWeight(NEFeatures.SF)+1) / (numSubChunks+1));
      entity.setScore(score);
    }
    entityList.sort();
    collapseEntities();
    return entityList;
  }

  // inserts IN_TITLE, IN_EXCERPT, and IN_SNIPPET features
  private void injectChunkFeatures(String source, Set<Snippet> snippets) {
    EntityList snippetEntities = new EntityList();
    
    for (Snippet snippet : snippets) {
      snippetEntities.clear();
      EntityList subList = extractEntities(source, snippet.getTitle().toString());
      subList.addWeight(NEFeatures.CTF, 1);
      snippetEntities.addAll(subList);
      
      for (Excerpt excerpt : snippet.getExcerpts()) {
        subList = extractEntities(source, excerpt.toString());
        subList.addWeight(NEFeatures.CEF, 1);
        snippetEntities.addAll(subList);
      }
      snippetEntities.addWeight(NEFeatures.CSF, 1);
    }
  }

  private void collapseEntities() {
    boolean[] toRemove = new boolean[entityList.size()];
    for (int i = 0; i < entityList.size(); i++) {
      if (toRemove[i]) continue;
      Entity e1 = entityList.get(i);
      
      for (int j = i+1; j < entityList.size(); j++) {
        if (toRemove[j]) continue;
        Entity e2 = entityList.get(j);
        
        if (e1.getName().toString().indexOf(e2.getName().toString()) != -1)
          toRemove[j] = true;
      }
    }
    int offset = 0, size = entityList.size();
    for (int i = 0; i < size; i++) {
      if (toRemove[i]) {
        entityList.remove(i-offset);
        offset++;
      }
    }
  }
  
  // inserts DIST_SCORE feature; very costly!
  private void injectDistFeatures() {
    for (Iterator<Entity> i = entityList.iterator(); i.hasNext();) {
      Entity entity = i.next();
      double chunkFreq = entity.getWeight(NEFeatures.CF);
      if (entity.length() < MIN_CONTENT_LENGTH || chunkFreq < MIN_CHUNK_FREQ) {
        i.remove();
        continue;
      }
      double subChunkFreq = 0;

      for (Annotator annotator : annotators) {
        annotator.clearTarget();
        // kmr possible gotcha
        annotator.annotateTarget(entity.getName().toString());
        subChunkFreq += annotator.countTarget();
        entity.addWeight(NEFeatures.DIST_SCORE, annotator.getDistScore());
      }
      entity.setWeight(NEFeatures.SF, subChunkFreq - chunkFreq);
    }
  }
  
  private EntityList extractEntities(String source, String text) {
    boolean hasTarget = false;
    EntityList subList = new EntityList();
    Matcher m = contentPattern.matcher(text);
    while (m.find()) {
      String s = m.group().toLowerCase();
      s = s.replace("-", " ");  // e.g. chiang kai shek vs. chiang kai-shek
      if (!WrapperFactory.checkSimpleContent(s))
        continue;
      hasTarget = true;
      // kmr possible gotcha; hope these aren't relational?
      Entity entity = entityList.get(Entity.parseEntity(s));
      if (entity == null) {
        entity = new Entity(s);
        entityList.add(entity);
      }
      entity.addWeight(NEFeatures.CF, 1);
      subList.add(entity);
    }
    if (hasTarget) {
      Annotator ann = new Annotator(text);
      ann.annotateSource(source);
      annotators.add(ann);
    }
    return subList;
  }

  // fetch snippets
  private static Set<Snippet> fetchSnippets(String source, String toLangID) {
    List<String> seeds = new ArrayList<String>();
    seeds.add(source);
    WebFetcher fetcher = new WebFetcher();
    fetcher.setNumResults(NUM_RESULTS);
    fetcher.setLangID(toLangID);
    return fetcher.fetchSnippets(seeds, null);
  }
  
}
