/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.fetch.URLBlackLister;
import com.rcwang.seal.fetch.WebFetcher;
import com.rcwang.seal.rank.Graph;
import com.rcwang.seal.rank.GraphRanker;
import com.rcwang.seal.rank.PageRanker;
import com.rcwang.seal.rank.Ranker;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.PairedTrie;
import com.rcwang.seal.util.XMLUtil;

public class Seal extends Pinniped {
  
  /********************** Parameters **************************/
  public static boolean readSerializedDocs = true;
  public static boolean writeSerializedDocs = false;
  /************************************************************/

  public static Logger log = Logger.getLogger(Seal.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final String SERIAL_DIR = "cache";
  public static final String SERIAL_FILENAME_EXT = ".ser";

  private Graph graph;
  private Ranker ranker;
  private DocumentSet lastDocs;
  private WrapperFactory wrapperFactory;
  private String extractLangID;
  private String fetchLangID;
  private double logWrapperLength;
  private boolean disableWalk;
  private int numLastWrappers;
  private int numPastWrappers;
  private int numPastDocs;
  private int numResults;
  private int engine;

  public static DocumentSet fetch(EntityList seeds, String hint, String langID, 
                                  int numResults, int engine) {
    log.info("Retrieving webpages using " + seeds.size() + " queries: {" + seeds + "}");
    if (!Helper.empty(hint))
      log.info("\tand a hint: " + hint);
    
    WebFetcher webFetcher = new WebFetcher();
    webFetcher.setLangID(langID);
    webFetcher.setNumResults(numResults);
    webFetcher.setUseEngine(engine);
    DocumentSet documents = webFetcher.fetchDocuments(seeds, hint);
//    DocumentSet documents = webFetcher.fetchDocuments(seeds, hint);
    int numUrlsToFetch = webFetcher.getSnippets().size();
    int numUrlsFetched = documents.size();
    
    // output the percentage of URLs fetched
    double pctPageRetrieved = (numUrlsToFetch == 0) ? 0 : (double) numUrlsFetched / numUrlsToFetch;
    log.info("Number of webpages retrieved: " + numUrlsFetched + " out of " + numUrlsToFetch + 
             " (" + Helper.formatNumber(pctPageRetrieved*100, 1) + "%)");
    return documents;
  }
  
  public static void main(String args[]) {
    long startTime = System.currentTimeMillis();

    // parse the command line argument
    if (args.length == 0) {
      log.error("Incorrect arguments in the command line");
      log.info("Usage 1: java " + Seal.class.getName() + " seed_file [hint_file]");
      log.info("Usage 2: java " + Seal.class.getName() + " seed_1 seed_2 ...");
      return;
    }

    File seedFile = new File(args[0]);
    String[] seedArr;
    String hint = null;
    if (seedFile.exists()) {
      seedArr = Helper.readFile(seedFile).split("\n");
      if (args.length >= 2) {
        File hintFile = Helper.toFileOrDie(args[1]);
        hint = Helper.readFile(hintFile).replaceAll("[\r\n]+", " ");
      }
    } else {
      for (int i = 0; i < args.length; i++)
        args[i] = args[i].replace('_', ' ');
      seedArr = args;
    }
    EntityList seeds = new EntityList();
    for (String s : seedArr) seeds.add(Entity.parseEntity(s));
    
    Seal seal = new Seal();
    seal.expand(seeds, seeds, hint);
    seal.save();//Results();
    
    log.info(seal.getEntityList().toDetails(100, seal.getFeature()));
    Helper.printMemoryUsed();
    Helper.printElapsedTime(startTime);
  }
  
  private static File getSerialFile(EntityList seeds, String hint) {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((seeds == null) ? 0 : seeds.hashCode());
    result = prime * result + ((hint == null) ? 0 : hint.hashCode());
    String filename = Integer.toString(Math.abs(result)) + SERIAL_FILENAME_EXT;
    return new File(SERIAL_DIR, filename);
  }
  
  public Seal() {
    super();
    wrapperFactory = new WrapperFactory();
    loadStopwords(gv.getStopwordsList());
    
    // initialize some parameters
    setLangID(gv.getLangID());
    setFeature(gv.getFeature());
    setEngine(gv.getUseEngine());
    setNumResults(gv.getNumResults());
    setMinContextLength(gv.getMinContextLength());
    setMinSeedsBracketed(gv.getMinSeedsBracketed());
    URLBlackLister.setListFile(gv.getUrlBlackList());
  }
  
  public Seal(String langID) {
    this();
    setLangID(langID);
  }
  
  public void addStopword(String stopword, boolean isUnigram) {
    wrapperFactory.addStopword(stopword, isUnigram);
  }
  
  public void addStopwords(Collection<String> stopwords, boolean isUnigram) {
    wrapperFactory.addStopwords(stopwords, isUnigram);
  }
  
  /**
   * reset for a totally new round of expansion/bootstrapping
   */
  public void clear() {
    super.clear();
    ranker.clear();
    lastDocs = null;
  }
  
  public boolean expand(EntityList seeds) {
    return expand(seeds, seeds, null);
  }
  
  public boolean expand(EntityList seeds, DocumentSet documents) {
    numLastEntities = 0;
    numLastWrappers = 0;

    this.lastDocs = documents;
    
    if (seeds == null || seeds.isEmpty()) {
      log.error("Error: Need at least one wrapper seed!");
      return false;
    } else if (documents == null || documents.size() == 0) {
      log.error("Error: No webpages provided for set expansion!");
      return false;
    }
    
    // output expansion information
    String className = this.getClass().getSimpleName();
    log.info(className + " is bracketing " + PairedTrie.toMinTypeStr(getMinSeedsBracketed()) + " of the " + seeds.size() + " wrapper seeds:");
    log.info("{" + seeds + "}");
    
    setStartTime();
    // associate wrappers with documents
    extract(seeds, documents);
    
    if (numPastWrappers > 0)
        log.info("Estimated List Quality: " + Helper.formatNumber(getListQuality(), 3));
    else log.info("No results!");
    
    // load documents into the ranker
    rank(seeds, documents);
    
    entityList.assignScore(getFeature(), hasNoisySeeds());
    entityList.scoreByLength(); // break any ties
    entityList.sortByScore();

    setSeeds(seeds);  // must be after rank()
    setEndTime();
    return true;
  }
  
  public boolean expand(EntityList wrapperSeeds, EntityList pageSeeds, String hint) {
    return expand(wrapperSeeds, readDocuments(pageSeeds, hint));
  }
  
  public int getEngine() {
    return engine;
  }
  
  public String getExtractLangID() {
    return extractLangID;
  }
  
  public Feature getFeature() {
    return getRanker().getRankerID();
  }

  public String getFetchLangID() {
    return fetchLangID;
  }

  public Graph getGraph() {
    return graph;
  }

  public DocumentSet getLastDocs() {
    return lastDocs;
  }

  public double getListQuality() {
    return logWrapperLength / numPastWrappers;
  }

  public int getMinContextLength() {
    return wrapperFactory.getMinContextLength();
  }

  public int getMinSeedsBracketed() {
    return wrapperFactory.getMinSeedsBracketed();
  }

  public int getNumLastWrappers() {
    return numLastWrappers;
  }

  public int getNumPastDocs() {
    return numPastDocs;
  }

  public int getNumPastWrappers() {
    return numPastWrappers;
  }
  
  public int getNumResults() {
    return numResults;
  }

  public Ranker getRanker() {
    return ranker;
  }

  public boolean isDisableWalk() {
    return disableWalk;
  }

  public void loadStopwords(File stopwordsFile) {
    wrapperFactory.loadStopwords(stopwordsFile);
  }

  public void setDisableWalk(boolean disableWalk) {
    this.disableWalk = disableWalk;
  }

  public void setEngine(int useEngine) {
    this.engine = useEngine;
  }

  public void setExtractLangID(String extractLangID) {
    this.extractLangID = extractLangID;
  }
  
  public void setFeature(Feature feature) {
    this.ranker = Ranker.toRanker(feature);
  }
  
  public void setFetchLangID(String fetchLangID) {
    this.fetchLangID = fetchLangID;
  }

  public void setLangID(String langID) {
    extractLangID = langID;
    fetchLangID = langID;
  }

  public void setMinContextLength(int minContextLength) {
    wrapperFactory.setMinContextLength(minContextLength);
  }

  public void setMinSeedsBracketed(int minSeedsBracketed) {
    wrapperFactory.setMinSeedsBracketed(minSeedsBracketed);
  }

  public void setNumResults(int numResults) {
    this.numResults = numResults;
  }

  public Element toXMLElement() {
    return toXMLElement(null);
  }

  /**
   * @return expansion results in XML format
   */
  public Element toXMLElement(org.w3c.dom.Document document) {
    XMLUtil xml = new XMLUtil(document);

    // response node is the top node
    Element responseNode = xml.createElement("response", null);
    xml.createAttrsFor(responseNode, new Object[]{
        "elapsedTimeInMS", getElapsedTime()
    });

    // setting node
    Element settingNode = xml.createElement("setting", null);
    Element seedsNode = xml.createElementBelow(settingNode, "seeds", null);
    for (Entity seed : lastSeeds)
      xml.createElementBelow(seedsNode, "seed", seed.getOriginal());
//    xml.createElementBelow(settingNode, "hint", hint == null ? "" : hint);
    xml.createElementBelow(settingNode, "extract-language", extractLangID);
    xml.createElementBelow(settingNode, "fetch-language", fetchLangID);

    responseNode.appendChild(settingNode);
//    responseNode.appendChild(pastDocs.toXMLElement(xml.getDocument()));
    responseNode.appendChild(entityList.toXMLElement(xml.getDocument()));       
    if (gv.getWrapperSaving()==1) {
        // save the wrappers in this xml node  
        WrapperSaver wrapperSaver = new WrapperSaver();
        responseNode.appendChild(wrapperSaver.toXMLElement(xml.getDocument(),lastDocs,entityList,lastSeeds));
    }
    return responseNode;
  }

  private void addSeedsToRanker(EntityList seeds, Ranker ranker) {
    if (seeds == null || ranker == null) return;
    
    if (pastSeeds.isEmpty()) {
      // no seeds have been given before
      if (seeds.containsFeature(getFeature())) {
        log.info("Using seed weights from input seeds...");
        ranker.addSeeds(seeds, getFeature());
      } else {
        log.info("Seeds are missing " + getFeature() + " weights, using uniform weights...");
        ranker.addUniformSeeds(seeds);
      }
    } else {
      // there are some previously expanded seeds
      log.info("Using seed weights from previously expanded entities...");
      
      // constructs a union of current seeds and past seeds
      EntityList newSeeds = new EntityList();
      newSeeds.addAll(seeds);
      newSeeds.addAll(pastSeeds);
      
      // the weights of those seeds come from their corresponding entities in last expansion
      ranker.addSeeds(entityList.intersect(newSeeds), getFeature());
    }
  }

  private Set<EntityLiteral> extract(EntityList seeds, DocumentSet documents) {
    // configure wrapper factory
    wrapperFactory.setSeeds(seeds);
    wrapperFactory.setLangID(extractLangID);
    wrapperFactory.setFeature(getFeature());
    
    int prevPercent = -1;
    Set<EntityLiteral> contents = new HashSet<EntityLiteral>();
    
    for (int i = 0; i < documents.size(); i++) {
      Document document = documents.get(i);
      
      // progress bar
      int percent = (int) ((double)(i+1)/documents.size()*100);
      if (percent != prevPercent) {
        log.info("Extracting from " + (i+1) + "/" + documents.size() + " (" + percent + "%) documents...");
        prevPercent = percent;
      }

      if (document.isEmpty()) continue;
      Set<Wrapper> wrappers = wrapperFactory.build(document);
      if (wrappers.isEmpty()) continue;

      numLastWrappers += wrappers.size();
      numPastWrappers += wrappers.size();
      document.addWrappers(wrappers);

      for (Wrapper wrapper : wrappers) {
        logWrapperLength += wrapper.getNumCommonTypes() * Math.log(wrapper.getContextLength());
        contents.addAll(wrapper.getContents());
      }
    }
    numPastDocs += documents.size();
    numLastEntities = contents.size();
    return contents;
  }

  /**
   * assigns scores to each entity and document based on the scoring method
   */
  private boolean rank(EntityList seeds, DocumentSet documents) {
    if (ranker == null) {
      log.error("Error: Feature is not specified!");
      return false;
    }
    
    // get the graph (added 12/10/2008)
    if (ranker instanceof GraphRanker) {
      graph = ((GraphRanker) ranker).getGraph();
      if (disableWalk) {
        graph.load(documents);
        return true;
      }
    }
    
    // PageRank starts from all nodes with uniform weights, so no need seeds
    if (!(ranker instanceof PageRanker))
      addSeedsToRanker(seeds, ranker);

    log.info("Ranking entities by " + getFeature() + " (" + ranker.getDescription() + ")" + 
             " and " + ranker.getSeedDist().size() + " seeds...");
    
    ranker.load(entityList, documents);
    return true;
  }

  private DocumentSet readDocuments(EntityList seeds, String hint) {
    if (Helper.empty(seeds)) return null;
    
    File serialFile = getSerialFile(seeds, hint);
    DocumentSet documents = null;

    if (readSerializedDocs && serialFile.exists()) {
      // documents exist in cache as serialized object
      documents = Helper.loadSerialized(serialFile, lastDocs.getClass());
      log.info("Successfully loaded " + documents.size() + " webpages!");
    } else {
      // download documents from the web
      documents = fetch(seeds, hint, fetchLangID, numResults, engine);
      if (writeSerializedDocs)
        Helper.saveSerialized(documents, serialFile, true);
    }
    return documents;
  }
}
