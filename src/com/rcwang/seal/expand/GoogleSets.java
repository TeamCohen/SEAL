/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.WebManager;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public class GoogleSets extends SetExpander {
  
  public static final Pattern EXTRACTION_PAT = Pattern.compile("\">([^<>]+)</a></font></center></td></tr>");
  public static final String GOOGLE_SET_URL = "http://labs.google.com/sets?hl=en&btn=";
  public static final String LARGE_SET = "Large+Set"; // returns about 50 entities
  public static final String SMALL_SET = "Small"; // returns about 15 entities
//  public static final Feature FEATURE = Feature.GSW;
  public static final String FEATURE = "GSW";
  public static final int NUM_MAX_RETRY = 3;
  public static final long WAIT_TIME = 3000;
  public static Logger log = Logger.getLogger(GoogleSets.class);
  
  private static final long serialVersionUID = -3457290506126642298L;
  private boolean isLargeSet;
  
  public GoogleSets() {
    super();
    setLargeSet(true);
  }
  
  public boolean expand(EntityList seeds) {
//    numNewEntities = 0;
    numLastEntities = 0;

    if (seeds.isEmpty()) {
      log.error("Need at least one seed!");
      return false;
    }
    setStartTime();
    log.info(this.getClass().getSimpleName() + " is expanding " + seeds.toString() + "...");
    String document = getResultPage(seeds.getEntityLiterals());
    if (document == null) {
      setEndTime();
      return false;
    }
//    numNewEntities = getNumEntities();
    extractEntities(document);
//    numNewEntities = getNumEntities() - numNewEntities;
    setSeeds(seeds);
    entityList.assignScore(FEATURE, hasNoisySeeds());
    entityList.sortByScore();
    setEndTime();
    return true;
  }
  
  public static URL getQueryURL(Collection<String> seeds, boolean isLargeSet) {
    String base = GOOGLE_SET_URL + (isLargeSet ? LARGE_SET : SMALL_SET);
    StringBuffer buf = new StringBuffer(base);
    if (seeds.size() == 0) {
      log.error("No seeds to expand!");
      return null;
    }

    int i = 1;
    for (String seed : seeds)
      buf.append("&q").append(i++).append("=").append(Helper.encodeURLString(seed));
    URL url = null;
    try {
      url = new URL(buf.toString());
    } catch (MalformedURLException e) {
      log.error("Malformed URL: " + buf.toString());
      return null;
    }
    return url;
  }

  public boolean isLargeSet() {
    return isLargeSet;
  }

  public void setLargeSet(boolean useLargeSet) {
    this.isLargeSet = useLargeSet;
  }
  
  /**
   * @return expansion results in XML format
   */
  public Element toXMLElement() {
    XMLUtil xml = new XMLUtil();

    // response node is the top node
    Element responseNode = xml.createElement("response", null);
    xml.createAttrsFor(responseNode, new Object[]{
        "elapsedTimeInMS", getElapsedTime()
    });

    // setting node
    Element settingNode = xml.createElement("setting", null);
    Element seedsNode = xml.createElementBelow(settingNode, "seeds", null);
    for (Entity seed : lastSeeds)
      xml.createElementBelow(seedsNode, "seed", seed.getName().toString());

    responseNode.appendChild(settingNode);
    responseNode.appendChild(entityList.toXMLElement(xml.getDocument()));
    return responseNode;
  }

  private void extractEntities(String document) {
    Matcher m = EXTRACTION_PAT.matcher(document);
    List<String> contentList = new ArrayList<String>();
    while (m.find()) {
      String content = m.group(1).toLowerCase().trim();
      if (content.length() > 0)
        contentList.add(content);
    }
    for (int i = 0; i < contentList.size(); i++) {
      EntityLiteral content = new EntityLiteral(contentList.get(i));
      Entity entity = entityList.get(content);
      if (entity == null)
        entity = entityList.add(content);
      double weight = 1 - (double) i / contentList.size();
      entity.addWeight(FEATURE, weight);
      if (!hasNoisySeeds() && lastSeeds.contains(content))
        entity.setSeed(true);
    }
    numLastEntities = new HashSet<String>(contentList).size();
//    entityList.addAssignedFeature(FEATURE);
  }

  private String getResultPage(Collection<EntityLiteral> eseeds) {
      Collection<String> seeds = new TreeSet<String>();
      for (EntityLiteral e : eseeds) seeds.add(e.toString());
//  }
//  
//  private String getResultPage(Collection<String> seeds) {
    URL url = getQueryURL(seeds, isLargeSet());
    if (url == null) return null;
    String className = this.getClass().getSimpleName();
    log.debug(className + " is fetching: " + url);
    String document = null;
    WebManager webManager = new WebManager();
    for (int numRetries = 0; numRetries < NUM_MAX_RETRY; numRetries++) {
      document = webManager.get(url);
      if (document == null) {
        log.error("[Attempt: " + (numRetries+1) + "/" + NUM_MAX_RETRY + "] " + 
                  className + " failed to connect to Google Sets!");
        log.error("Sleeping for " + WAIT_TIME + " milliseconds...");
        try {
          Thread.sleep(WAIT_TIME);
        } catch (InterruptedException e) {}
      } else break;
    }
    return document;
  }
}
