/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.util.Helper;

public class GoogleWebSearcher extends WebSearcher {
  
  /********************** Google Web Parameters **************************/
  public static final String BASE_URL = "http://www.google.com/search?hl=en&safe=off&filter=1&";
  public static final String RESULTS_KEY = "num";
  public static final String START_KEY = "start";
  public static final String LANG_KEY = "lr";
  public static final String QUERY_KEY = "q";
  public static final String GOOGLE_LANG_PREFIX = "lang_";
  public static final String PAGE_FORBIDDEN_ERROR = "<title>403 ";
  /******************************************************************/
  
  public static final String HOST = Helper.toURL(BASE_URL).getHost();
  public static Logger log = Logger.getLogger(GoogleWebSearcher.class);
  
  public static boolean containsGoogleURL(List<URL> urls) {
    if (urls == null) return false;
    for (URL url : urls)
      if (isGoogleURL(url))
        return true;
    return false;
  }
  
  public static boolean isBlockedByGoogle(String doc, URL url) {
    if (doc == null || url == null) return false;
    if (isGoogleURL(url) && doc.contains(PAGE_FORBIDDEN_ERROR)) {
      log.fatal("Congratulations! Google has blocked you on: " + new Date());
      return true;
    }
    return false;
  }
  
  public static boolean isGoogleURL(URL url) {
    if (url == null) return false;
    return url.getHost().equals(HOST);
  }
  
  public static void main(String args[]) {
    int numResults = 100;
    GoogleWebSearcher gs = new GoogleWebSearcher();
    gs.setLangID("en");
    gs.setCacheDir(new File("/www.cache/"));
    gs.setNumResults(numResults);
    gs.setTimeOutInMS(10*1000);
    gs.setMaxDocSizeInKB(512);
    gs.addQuery("george w. bush", true);
    gs.run();
    Set<Snippet> snippets = gs.getSnippets();
    for (Snippet snippet : snippets)
      log.info(snippet.getPageURL());
    if (numResults == snippets.size())
      log.info("Test succeeded!");
    else log.error("Test failed! Expecting: " + numResults + " Actual: " + snippets.size());
  }
  
  public GoogleWebSearcher() {
    super();
  }

  public void setLangID(String langID) {
    if (langID == null || langID.equals(LangProvider.UNI[LangProvider.ID]))
      langCode = "";
    else langCode = GOOGLE_LANG_PREFIX + langID;
  }

  protected void buildSnippets(String resultPage) {
//    Pattern snippetPat = Pattern.compile("(?s)<div class=g(?: [^<>]+)?>(.+?)</table></div>");
//    Pattern snippetPat = Pattern.compile("(?s)<!--m-->(.+?)<!--n-->");
      Pattern snippetPat = Pattern.compile("(?s)<li class=\"g\">(.+?)</li>"); // - wwc update 6/15
    Pattern[] patterns = new Pattern[] {
        //Pattern.compile("<h(?:2|3) class=r><a href=\"(?:/interstitial\\?url=|/url\\?q=)?([^\"]+)\""),  // 0: page URL
        //Pattern.compile("<h(?:2|3) class=\"r\"><a href=\"([^\"]+)\""),  // 0: page URL  - wwc update 6/15
        Pattern.compile("<h(?:2|3) class=\"r\"><a href=\"(?:[^\"]*?)(http://[^\"]+?)(?:\"|&amp;\\w+=)"),  // 0: page URL  - kmr update 3/09/2012
        Pattern.compile("(?s)\">(.+?)</a></h(?:2|3)>"),  // 1: title
        Pattern.compile("<a href=\"([^\"]+)\"[^<>]*>View as HTML"), // 2: cached URL #1
        Pattern.compile("(?s)(?:<div class=std>|</a><br>)(.+?)<br>"), // 3: summary
        Pattern.compile("<a class=fl href=\"([^\"]+)\"[^<>]*>Cached</a>"),  // 4: cached URL #2
    };

    List<String> extractions = new ArrayList<String>();
    Matcher matcher = snippetPat.matcher(resultPage);
    while (matcher.find()) {
        buildSingleSnippet(matcher.group(1), extractions, patterns);
    }
    //log.debug("Page:\n"+resultPage);
  }
  
  protected void buildSingleSnippet(String rawSnippet, List<String> extractions, Pattern[] patterns) {
      extractions.clear();
      for (Pattern p : patterns) {
        Matcher m = p.matcher(rawSnippet);
        if (m.find()) {
          extractions.add(m.group(1));
          rawSnippet = rawSnippet.substring(m.end(1));
        } else extractions.add(null);
      }
      
      String pageURL = extractions.get(0);
      String title = extractions.get(1);
      String cacheURL1 = extractions.get(2);
      String summary = extractions.get(3);
      String cacheURL2 = extractions.get(4);
      //log.debug("Title:"+title);
      //log.debug("pageURL:"+pageURL);
      
      // a valid snippet must contain page URL and title
      if (pageURL == null || title == null) return;
      
      Snippet snippet = new Snippet();
      snippet.setPageURL(pageURL);
      snippet.setTitle(title);
      snippet.setCacheURL(cacheURL1 == null ? cacheURL2 : cacheURL1);
      snippet.setSummary(summary);
      snippet.setRank(snippets.size()+1);
      snippets.add(snippet);
  }
  
  /**
   * Returns a URL for Google search page
   * @param numResultsForThisPage number of results per page (between 1 and 100 inclusively)
   * @param pageNum page number (greater than 0)
   * @param query query terms
   * @return Google search page URL
   */
  protected String getSearchURL(int numResultsForThisPage, int pageNum, String query) {
    if (numResultsForThisPage < 1 || numResultsForThisPage > maxResultsPerPage)
      throw new IllegalArgumentException("Number of results for this page must be between 1 and " + maxResultsPerPage + " inclusively.");
    if (pageNum < 1)
      throw new IllegalArgumentException("Page number must be at least 1.");
    
    int startIndex = (pageNum-1) * maxResultsPerPage;
    return getURL(numResultsForThisPage, startIndex, langCode, query);
  }
  
  public static String getURL(int numResults, int start, String langID, String query) {
    StringBuffer url = new StringBuffer(BASE_URL);
    url.append(RESULTS_KEY).append("=").append(numResults).append("&");
    url.append(START_KEY).append("=").append(start).append("&");
    url.append(LANG_KEY).append("=").append(langID).append("&");
    url.append(QUERY_KEY).append("=").append(query);
    return url.toString();
  }
}
