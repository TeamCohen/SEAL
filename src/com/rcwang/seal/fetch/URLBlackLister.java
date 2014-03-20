/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

public class URLBlackLister {
  
  // these are good host names and should never be blocked
  private static final List<String> GOOD_HOSTS = Arrays.asList(new String[] {
      YahooAPISearcher.HOST, GoogleWebSearcher.HOST, GoogleAPISearcher.HOST
  });
  private static Set<String> oldURLs = new HashSet<String>();  // URLs in the initial file
  private static Set<String> newURLs = new HashSet<String>();  // URLs that will be saved later
  private static Logger log = Logger.getLogger(URLBlackLister.class);
  private static File listFile = null;
  
  /**
   * Adds a URL to the black list
   * @param url
   */
  public static void addToList(URL url) {
    addToList(url, false);
  }
  
  /**
   * Adds a URL (or its host) to the black list
   * @param url the url to be added
   * @param addHost true to add only the host of this url
   */
  public static synchronized void addToList(URL url, boolean addHost) {
    if (url == null || !addHost && isListed(url))
      return;
    if (GOOD_HOSTS.contains(url.getHost()))
      return;
    String urlStr;
    if (addHost) {
      urlStr = toHostPattern(url);
      if (oldURLs.contains(urlStr) || newURLs.contains(urlStr))
        return;
      // remove all new URLs that is under this host
      String host = url.getProtocol() + "://" + url.getHost();
      for (Iterator<String> i = newURLs.iterator(); i.hasNext();)
        if (i.next().startsWith(host))
          i.remove();
    } else {
      urlStr = url.toString();
    }
//    log.debug("Black listing: " + urlStr);
    newURLs.add(urlStr);
  }
  
  public static File getListFile() {
    return listFile;
  }
  
  /**
   * Checks to see if an URL (or its host) is black listed already
   * @param url
   * @return true if an URL (or its host) is black listed
   */
  public static boolean isListed(URL url) {
    if (url == null) return false;
    String urlStr = url.toString();
    if (oldURLs.contains(urlStr) || newURLs.contains(urlStr)) {
      log.debug("Found bad URL: " + url);
      return true;
    }
    String host = toHostPattern(url);
    if (oldURLs.contains(host) || newURLs.contains(host)) {
      log.debug("Found bad host: " + url.getHost());
      return true;
    }
    return false;
  }
  
  public static void main(String args[]) throws MalformedURLException {
    URLBlackLister.setListFile(new File("filteredURLs.txt"));
    URL url = new URL("http://www.aspacepragmatism.com/2006/12/why-nasa-why-private-spaces.html");
    URLBlackLister.addToList(url, false);
    URLBlackLister.isListed(url);
    URLBlackLister.saveList();
    url = new URL("http://www.aspacepragmatism.com/abcd.html");
    URLBlackLister.addToList(url, true);
    URLBlackLister.isListed(url);
    URLBlackLister.saveList();
  }
  
  public static void saveList() {
    if (listFile != null)
      saveList(listFile);
  }
  
  /**
   * Saves black listed URLs to file by appending to the end of initial list file
   * @param listFile
   */
  public static synchronized void saveList(File listFile) {
    if (newURLs.isEmpty()) return;
    BufferedWriter out = null;
    try {
      out = new BufferedWriter(new FileWriter(listFile, true));
      for (String urlStr : newURLs) {
        out.write(urlStr);
        out.newLine();
      }
      log.debug("Saved " + newURLs.size() + " bad URLs to: " + listFile);
      oldURLs.addAll(newURLs);
      newURLs.clear();
    } catch (IOException e) {
      log.error(e.toString());
    } finally {
      try {
        if (out != null) {
          out.close();
          out = null;
        }
      } catch (IOException e) {}
    }
  }
  
  /**
   * Loads a list of URLs into the black list
   * Caution: Only ONE list can be loaded for the lifetime of this class!
   * @param file
   */
  public static void setListFile(File file) {
    if (file == null) {
//      log.error("Cannot use black list: list file is null!");
      return;
    }
    if (file.equals(listFile)) {
      return;
    } else if (listFile != null) {
      log.error("A black list has already been set: " + listFile);
      return;
    }
    log.info("Using black list: " + file);
    if (file.exists()) {
      Set<String> urls = new HashSet<String>();
      BufferedReader bReader = null;
      try {
        String line;
        bReader = new BufferedReader(new FileReader(file));
        while ((line = bReader.readLine()) != null)
          urls.add(line.trim());
      } catch (IOException e) {
        log.error(e.toString());
      } finally {
        try {
          if (bReader != null) {
            bReader.close();
            bReader = null;
          }
        } catch (IOException e) {}
      }
      oldURLs.addAll(urls);
    }
    listFile = file;
  }
  
  private static String toHostPattern(URL url) {
    return url.getProtocol() + "://" + url.getHost() + "/**";
  }
}
