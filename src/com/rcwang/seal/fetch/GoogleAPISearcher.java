/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.util.Helper;

public class GoogleAPISearcher extends WebSearcher {
  
  /********************** Google AJAX Parameters ********************/
  public static final String BASE_URL = "http://ajax.googleapis.com/ajax/services/search/web?v=1.0&safe=off&";
  public static final String RESULTS_KEY = "rsz";
  public static final String RESULTS_SMALL = "small";
  public static final String RESULTS_LARGE = "large";
  public static final String START_KEY = "start";
  public static final String LANG_KEY = "lr";
  public static final String QUERY_KEY = "q";
  public static final String GOOGLE_LANG_PREFIX = "lang_";
  public static final int MAX_RESULTS_PER_PAGE = 8;
  public static final int MAX_PAGES = 8;
  /******************************************************************/
  
  public static final String HOST = Helper.toURL(BASE_URL).getHost();
  public static Logger log = Logger.getLogger(GoogleAPISearcher.class);
  
  public static void main(String args[]) {
    int numResults = 64;
    GoogleAPISearcher gs = new GoogleAPISearcher();
    gs.setLangID("en");
    gs.setCacheDir(new File("/www.cache/"));
    gs.setNumResults(numResults);
    gs.setTimeOutInMS(10*1000);
    gs.setMaxDocSizeInKB(512);
    gs.addQuery("(\"Richard C. Wang\" OR \"David C. Wang\")", false);
    gs.run();
    Set<Snippet> snippets = gs.getSnippets();
    for (Snippet snippet : snippets)
      log.info(snippet);
    if (numResults == snippets.size())
      log.info("Test succeeded!");
    else log.error("Test failed! Expecting: " + numResults + " Actual: " + snippets.size());
  }
  
  public GoogleAPISearcher() {
    super();
    setMaxResultPerPage(MAX_RESULTS_PER_PAGE);
  }

  public void setLangID(String langID) {
    if (langID == null || langID.equals(LangProvider.UNI[LangProvider.ID]))
      langCode = "";
    else langCode = GOOGLE_LANG_PREFIX + langID;
  }

  protected void buildSnippets(String resultPage) {
    if (resultPage == null || resultPage.length() == 0) return;
    final String responseDataStr = "responseData";
    
    try {
      JSONObject document = new JSONObject(resultPage);
      if (document.isNull(responseDataStr)) return;
      JSONObject responseData = document.getJSONObject(responseDataStr);
      JSONArray results = responseData.getJSONArray("results");

      for (int i = 0; i < results.length(); i++) {
        JSONObject result = results.getJSONObject(i);
        String pageURL = result.getString("unescapedUrl");
        String title = result.getString("titleNoFormatting");

        // a valid snippet must contain page URL and title
        if (pageURL == null || title == null) continue;

        String cacheURL = result.getString("cacheUrl");
        String summary = result.getString("content");

        Snippet snippet = new Snippet();
        snippet.setPageURL(pageURL);
        snippet.setTitle(title);
        snippet.setCacheURL(cacheURL);
        snippet.setSummary(summary);
        snippet.setRank(snippets.size()+1);
        snippets.add(snippet);
      }
    } catch (JSONException e) {
      log.error(e);
    }
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
    if (pageNum < 1 || pageNum > MAX_PAGES)
      throw new IllegalArgumentException("Page number must be between 1 and " + MAX_PAGES + " inclusively.");
    
    int startIndex = (pageNum-1) * maxResultsPerPage;
    return getURL(numResultsForThisPage, startIndex, langCode, query);
  }
  
  public static String getURL(int numResults, int start, String langID, String query) {
    String resultsSize = numResults > (MAX_RESULTS_PER_PAGE/2) ? RESULTS_LARGE : RESULTS_SMALL;
    
    StringBuffer url = new StringBuffer(BASE_URL);
    url.append(RESULTS_KEY).append("=").append(resultsSize).append("&");
    url.append(START_KEY).append("=").append(start).append("&");
    url.append(LANG_KEY).append("=").append(langID).append("&");
    url.append(QUERY_KEY).append("=").append(query);
    return url.toString();
  }
}
