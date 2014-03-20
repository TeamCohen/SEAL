/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class WebManager {

  public static Logger log = Logger.getLogger(WebManager.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();
  
  public static final String URL_VAR = "%URL%";
  public static final String CACHE_HEADER = "<!-- Downloaded from: " + URL_VAR + " -->\n";
  public static final String CACHE_FILE_EXT = ".html";
  public static final int NUM_CACHE_BINS = 1024;
  
  // the unix timestamp when www.google.com was last accessed
  private static long lastHitGoogle = 0;
  // true to enable downloading web documents; false otherwise
    private static boolean isFetchFromWeb = true;
  // true to enable reading from the cache; false otherwise
  private static boolean isReadFromCache = true;
  // true to enable writing to the cache; false otherwise
  private static boolean isWriteToCache = true;
  
  private File cacheDir;
  private int numUrlFromCache = 0;
  private int numUrlFromWeb = 0;
  private int timeOutInMS;
  private int maxDocSizeInKB;
  
  public static boolean isFetchFromWeb() {
    return isFetchFromWeb;
  }
  
  public static boolean isReadFromCache() {
    return isReadFromCache;
  }
  
  public static boolean isWriteToCache() {
    return isWriteToCache;
  }
  
  /**
   * Read cache from the input URL
   * @param url
   * @return cached document
   */
  public static String readFromCache(URL url, File cacheDir) {
    if (url == null || !isReadFromCache())
      return null;
    String document = null;
    File cacheFile = loadCachedFile(url, cacheDir, false);
    if (cacheFile != null && cacheFile.isFile()) {
      document = Helper.readFile(cacheFile);
      // no permission to read the document?
      if (document == null) return null;
      // remove empty cache file
      if (document.length() == 0) {
        cacheFile.delete();
        return null;
      }
      // remove error Yahoo! page
      if (document.contains("service temporarily unavailable [C:28]")) {
        cacheFile.delete();
        return null;
      }
      log.debug("Found Cache: " + cacheFile);
      // get rid of "page source" on the top of every cached document
      document = removeCacheHeader(document);
    }
    return document;
  }
  
  public static void setFetchFromWeb(boolean isFetchFromWeb) {
      WebManager.isFetchFromWeb = isFetchFromWeb;
      log.info("isFetchFromWeb set to " + WebManager.isFetchFromWeb);
  }
  
  public static void setReadFromCache(boolean isReadFromCache) {
    WebManager.isReadFromCache = isReadFromCache;
  }

  public static void setWriteToCache(boolean isWriteToCache) {
    WebManager.isWriteToCache = isWriteToCache;
  }
  
  public static void writeToCache(URL url, String document, File cacheDir) {
    if (document == null || url == null || !isWriteToCache())
      return;
    File cacheFile = loadCachedFile(url, cacheDir, true);
    if (cacheFile == null)
      return;
    long documentSize = Math.round(Helper.getStringSize(document) / 1024.0);
    log.debug("To Cache: " + cacheFile + " (" + documentSize + "KB)");
    // add a "page source" on the top of every cached document
    document = CACHE_HEADER.replace(URL_VAR, url.toString()) + document;
    Helper.writeToFile(cacheFile, document);
  }
  
  /**
   * Finds the corresponding cached file for the input "url".
   * @param url
   * @param makeDir True to create cache dir if it does not exist
   * @return cached file
   */
  private static File loadCachedFile(URL url, File cacheDir, boolean makeDir) {
    if (url == null || cacheDir == null)
      return null;
    int urlHash = Math.abs(url.toString().hashCode());
    String cacheDirName = String.valueOf(urlHash % NUM_CACHE_BINS);
    File newCacheDir = new File(cacheDir, cacheDirName);
    if (makeDir)
      Helper.createDir(newCacheDir);
    String urlFileName = cacheDirName + "." + urlHash + CACHE_FILE_EXT;
    return new File(newCacheDir, urlFileName);
  }
  
  /**
   * Tries to read "urls" from cache. If an URL is not found in cache, it will be inserted into "uncachedURLs". 
   * @param urls List of URLs to retrieve from cache
   * @param uncachedURLs List of URLs not found in cache
   * @return corresponding cached documents of the input "urls"
   */
  private static List<String> readFromCache(List<URL> urls, List<URL> uncachedURLs, File cacheDir) {
    if (urls == null || uncachedURLs == null)
      return null;
    List<String> documents = new ArrayList<String>(urls.size());
    if (urls.size() == 0)
      return documents;
//    log.debug("Reading " + urls.size() + " URLs from cache...");
    for (URL url : urls) {
      String document = readFromCache(url, cacheDir);
      /*final String userDirName0 = "/usr0/", userDirName1 = "/usr1/";
      if (document == null && cacheDir.toString().contains(userDirName0)) {
        File alterFile = new File(cacheDir.toString().replace(userDirName0, userDirName1));
        document = readFromCache(url, alterFile);
      }*/
      documents.add(document);
      if (document == null)
        uncachedURLs.add(url);
    }
    return documents;
  }

    // wwc: made public
  public static String removeCacheHeader(String document) {
    final String prefix = CACHE_HEADER.substring(0, CACHE_HEADER.indexOf(URL_VAR));
    if (document.startsWith(prefix)) {
      int endIndex = document.indexOf("\n");
      if (endIndex != -1)
        return document.substring(endIndex+1);
    }
    return document;
  }

  private static void sleep(List<URL> uncachedURLs) {
    if (!GoogleWebSearcher.containsGoogleURL(uncachedURLs))
      return;
    long elapsedTime;
    while ((elapsedTime = System.currentTimeMillis() - lastHitGoogle) < gv.getGoogleHitGapInMS()) {
      long sleepTime = gv.getGoogleHitGapInMS() - elapsedTime;
      String sleepTimeStr = Helper.formatNumber(sleepTime/1000.0, 2);
      log.info("Just hit Google, sleeping for " + sleepTimeStr + " seconds to prevent being blocked...");
      try { Thread.sleep(sleepTime); }
      catch (InterruptedException e) {}
    }
    lastHitGoogle = System.currentTimeMillis();
  }

  private static void writeToCache(List<URL> urls, List<String> documents, File cacheDir) {
    if (urls == null || documents == null || !isWriteToCache())
      return;
    int size = Math.min(urls.size(), documents.size());
    if (size == 0)
      return;
    log.debug("Writing " + size + " URLs to cache...");
    for (int i = 0; i < size; i++)
      writeToCache(urls.get(i), documents.get(i), cacheDir);
  }

  public WebManager() {
    setCacheDir(gv.getCacheDir());
    setTimeOutInMS(gv.getTimeOutInMS());
    setMaxDocSizeInKB(gv.getMaxDocSizeInKB());
    setFetchFromWeb(gv.getIsFetchFromWeb());
  }

  /**
   * Attempts to retrieve documents from the cache. If not found in cache, 
   * it will download the document from the Internet.
   * @param urls
   * @return retrieved documents
   */
  public List<String> get(List<URL> urls) {
    if (urls == null) return null;
    List<URL> uncachedURLs = new ArrayList<URL>();
    List<String> documents = readFromCache(urls, uncachedURLs, cacheDir);
    numUrlFromCache = urls.size()-uncachedURLs.size();
    numUrlFromWeb = uncachedURLs.size();
    log.info("Fetching " + numUrlFromCache + " webpages from cache and up to " + numUrlFromWeb + " webpages from the Internet");
    
    List<String> uncachedDocs;
    if (!isFetchFromWeb()) {
      log.warn("Not downloading from the Web: Fetching has been disabled!");
      uncachedDocs = Arrays.asList(new String[uncachedURLs.size()]);
    } else {
      log.info("Downloading URLs from Web");
      sleep(uncachedURLs);  // prevent blocking by Google
      uncachedDocs = MultiThreadFetcher.fetch(uncachedURLs, timeOutInMS, maxDocSizeInKB);
    }
    
    // converts search engine's cached pages back to their original format 
    for (int i = 0; i < uncachedDocs.size(); i++) {
      URL url = uncachedURLs.get(i);
      String doc = uncachedDocs.get(i);
      if (GoogleWebSearcher.isBlockedByGoogle(doc, url))
        System.exit(1);
      doc = CacheRecoverer.recover(url, doc);
      uncachedDocs.set(i, doc);
    }

    writeToCache(uncachedURLs, uncachedDocs, cacheDir);
    int j = 0;
    for (int i = 0; i < documents.size(); i++)
      if (documents.get(i) == null)
        documents.set(i, uncachedDocs.get(j++));
    return documents;
  }

  /**
   * Attempts to retrieve a document from the cache. If not found in cache, 
   * it will download the document from the Internet.
   * @param url
   * @return retrieved documents
   */
  public String get(URL url) {
    if (url == null) return null;
    List<URL> urls = new ArrayList<URL>();
    urls.add(url);
    return get(urls).get(0);
  }

  public File getCacheDir() {
    return cacheDir;
  }

  public int getMaxDocSizeInKB() {
    return maxDocSizeInKB;
  }

  public int getNumUrlFromCache() {
    return numUrlFromCache;
  }

  public int getNumUrlFromWeb() {
    return numUrlFromWeb;
  }

  public int getTimeOutInMS() {
    return timeOutInMS;
  }

  public void setCacheDir(File cacheDir) {
    this.cacheDir = cacheDir;
  }

  public void setMaxDocSizeInKB(int maxDocSizeInKB) {
    this.maxDocSizeInKB = maxDocSizeInKB;
  }

  public void setTimeOutInMS(int timeOutInMS) {
    this.timeOutInMS = timeOutInMS;
  }
}
