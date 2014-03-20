/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class BadWordFilterer {

  public static Logger log = Logger.getLogger(BadWordFilterer.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  private Set<String> unigramSet;
  private Set<String> multigramSet;
  
  public BadWordFilterer() {
    unigramSet = new HashSet<String>();
    multigramSet = new HashSet<String>();
  }
  
  public void add(Collection<String> entities, boolean isUnigram) {
    if (entities == null) return;
    for (String badEntity : entities)
      add(badEntity, isUnigram);
  }
  
  public void add(String entity, boolean isUnigram) {
    if (entity == null) return;
    entity = entity.trim();
    if (entity.length() == 0) return;
    entity = entity.toLowerCase();
    if (isUnigram)
      unigramSet.add(entity);
    else multigramSet.add(entity);
  }
  
  public void addMultigram(String entity) {
    add(entity, false);
  }
  
  public void addMultigrams(Collection<String> entities) {
    add(entities, false);
  }
  
  public void addUnigram(String entity) {
    add(entity, true);
  }
  
  public void addUnigrams(Collection<String> entities) {
    add(entities, true);
  }
  
  public void clear() {
    unigramSet.clear();
    multigramSet.clear();
  }
  
  public boolean isBad(String entity) {
    if (entity == null) return true;
    entity = entity.trim().toLowerCase();
    if (unigramSet.contains(entity))
      return true;
    for (String multigram : multigramSet)
      if (multigram.contains(entity))
        return true;
    return false;
  }
  
  public void loadStopwords(File listFile) {
    if (listFile == null) return;
    log.debug("Loading stopwords file: " + listFile);
    String[] stopwords = Helper.readFile(listFile).split("\n");
    for (String stopword : stopwords)
      addUnigram(stopword);
  }
  
}
