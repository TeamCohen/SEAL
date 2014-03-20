/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.rank.Ranker;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public abstract class SetExpander {

  public static final String XML_EXT = ".xml";
  public static Logger log = Logger.getLogger(SetExpander.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  private File resultDir;
  
  protected EntityList lastSeeds;
  protected EntityList pastSeeds;
  protected EntityList entityList;
//  protected int numNewEntities = 0;
  protected int numLastEntities = 0;
  protected long startTime = 0;
  protected long endTime = 0;
  protected boolean hasNoisySeeds;
//  protected String langID;
//  protected Feature feature;

  public SetExpander() {
    lastSeeds = new EntityList();
    pastSeeds = new EntityList();
    entityList = new EntityList();
    setHasNoisySeeds(gv.hasNoisySeeds());
    setResultDir(gv.getResultDir());
//    setLangID(gv.getLangID());
//    setFeature(gv.getFeature());
  }
  
  /**
   * A dummy method
   * @param feature
   * @return true
   */
  public boolean assignWeights(Feature feature) {
    return true;
  }

  public void clear() {
    lastSeeds.clear();
    pastSeeds.clear();
    entityList.clear();
  }
  
  /*public void clearPastSeeds() {
    pastSeeds.clear();
  }*/

  public abstract boolean expand(EntityList seeds);
  
  public long getElapsedTime() {
    return (startTime > endTime) ? 0 : endTime - startTime; // in milliseconds
  }
  
  public List<Entity> getEntities() {
    return getEntityList().getEntities();
  }

//  public Entity getEntity(String name) {
//    return entityList.get(name);
//  }
  
  public EntityList getEntityList() {
    /*if (entityList.size() > 1 && entityList.getSortByFeature() == null) {
      log.warn("Entities have not been assigned weights! They are randomly ordered.");
      log.warn("Please use SetExpander.assignWeights(ScoringMethod feature)");
    }*/
    return entityList;
  }
  
  /*public Feature getFeature() {
    return feature;
  }*/
  
  public EntityList getPastSeeds() {
    return pastSeeds;
  }
  
  /*public String getLangID() {
    return langID;
  }*/
  
  public EntityList getLastSeeds() {
    return lastSeeds;
  }
  
  public int getNumEntities() {
    return entityList.size();
  }
  
  public int getNumLastEntities() {
    return numLastEntities;
  }
  
  /*public int getNumNewEntities() {
    return numNewEntities;
  }*/

  public File getResultDir() {
    return resultDir;
  }
  
  public boolean hasNoisySeeds() {
    return hasNoisySeeds;
  }

  public boolean isEmpty() {
    return entityList.isEmpty();
  }
  
  /**
   * Constructs a file name, allowing user to specify what should be included in the name
   * @param anything any String
   * @param useNumEntities true to include number of extracted entities
   * @param useSeeds true to include current seeds only (not seeds used in past rounds)
   * @return a file name String
   */
  public String makeFileName(String anything, boolean useNumEntities, boolean useSeeds) {
    StringBuffer buf = new StringBuffer();
//    Object feature = entityList.getSortByFeature();
//    if (feature != null)
//      buf.append(feature.toString().toLowerCase()).append(".");
    if (anything != null)
      buf.append(anything).append(".");
    if (useNumEntities)
      buf.append(entityList.size()).append(".");
    if (useSeeds)
      buf.append(Math.abs(lastSeeds.hashCode())).append(".");
    if (buf.length() == 0)
      return null;
    return buf.substring(0, buf.length()-1);
  }

  
  public void save() {
      saveResults();
      saveWrappers();
  }
  /**
   * Saves results in XML format using an automatic generated file name
   * @return saved file
   */
  public File saveResults() {
    return saveResults(makeFileName(null, true, true));
  }
  
  public File saveResults(File file) {
    if (file == null) return null;
    String xmlResult = toXML();
    if (xmlResult == null) return null;
    Helper.createDir(file.getParentFile());
    log.info("Saving results XML to: " + file);
    file.delete();
    Helper.writeToFile(file, xmlResult);
    return file;
  }
  

  /**
   * Saves results in XML format using a user specified file name
   * @param filename Name of the output file (without extension)
   * @return saved file
   */
  public File saveResults(String filename) {
    if (filename == null) return null;
    if (resultDir == null) return null;
    File resultFile = new File(resultDir, filename + XML_EXT);
    return saveResults(resultFile);
  }
  

  public void saveWrappers() {
      if (gv.getWrapperSaving()==2) {
          WrapperSaver wrapperSaver = new WrapperSaver();
          wrapperSaver.saveAsDocuments(gv.getSavedWrapperDir(),this.getLastDocs(),entityList,lastSeeds);
      }
  }

  /*public void setFeature(Feature feature) {
    this.feature = feature;
  }*/

  public void setHasNoisySeeds(boolean hasNoisySeeds) {
    this.hasNoisySeeds = hasNoisySeeds;
  }

  /*public void setLangID(String langID) {
    this.langID = langID;
  }*/

  public void setResultDir(File resultDir) {
    this.resultDir = resultDir;
  }

  public String toXML() {
    return XMLUtil.document2String(toXMLElement());
  }

  protected void setEndTime() {
    endTime = System.currentTimeMillis();
  }

  /**
   * Set the seeds for immediate expansion
   * @param seeds
   * @return
   */
  protected EntityList setSeeds(EntityList seeds) {
    if (seeds == null) return null;
    
    // set the 'isSeed' status of all seeds
    for (Entity entity : seeds)
      entity.setSeed(true);
    
    // add seeds to last seeds
    lastSeeds.clear();
    lastSeeds.addAll(seeds);
    
    // add seeds to global seeds
    pastSeeds.addAll(seeds);
    
    return lastSeeds;
  }

  protected void setStartTime() {
    startTime = System.currentTimeMillis();
  }

  protected abstract Element toXMLElement();
  
  public boolean expand(EntityList seeds, DocumentSet documents) {
      throw new UnsupportedOperationException("Not implemented in "+this.getClass().getCanonicalName());
  }
  public DocumentSet getLastDocs() {
      throw new UnsupportedOperationException("Not implemented in "+this.getClass().getCanonicalName());
  }
  public Ranker getRanker() { log.warn(this.getClass().getCanonicalName()+" does not have a ranker."); return null; }
}
