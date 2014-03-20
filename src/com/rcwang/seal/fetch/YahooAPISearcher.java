/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public class YahooAPISearcher extends WebSearcher {

  /********************** Yahoo! Parameters **************************/
  public static final String BASE_URL = "http://search.yahooapis.com/WebSearchService/V1/webSearch?format=html&";
  public static final String APPID_KEY = "appid";
  public static final String QUERY_KEY = "query";
  public static final String RESULTS_KEY = "results";
  public static final String START_KEY = "start";
  public static final String LANG_KEY = "language";
  public static final String CHT_LANG = "tzh";
  public static final String CHS_LANG = "szh";
  /******************************************************************/

  public static final String HOST = Helper.toURL(BASE_URL).getHost();
  public static Logger log = Logger.getLogger(YahooAPISearcher.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();

  private String yahooAPIKey;
  
  public static void main(String args[]) {
    int numResults = 100;
    YahooAPISearcher ys = new YahooAPISearcher();
    ys.setLangID("zh-TW");
    ys.setCacheDir(new File("/www.cache/"));
    ys.setNumResults(numResults);
    ys.setTimeOutInMS(10*1000);
    ys.setMaxDocSizeInKB(512);
    //ys.setYahooAPIKey("vTfDpxTV34H2rYAWCgCwINCrCiM3y3DSGz.r2HUuLz.I8ozwXd5741wnTsvQpQA-");
    ys.setYahooAPIKey("GyJlmi3V34GZPzUf6sQd9ihb2PslaIffGHY4ucGxTvECdsLMX03hqBDklDKpEg--");
    ys.addQuery("Global Warming", true);
    ys.run();
    Set<Snippet> snippets = ys.getSnippets();
    for (Snippet snippet : snippets)
      log.info(snippet.toString());
    if (numResults == snippets.size())
      log.info("Test succeeded!");
    else log.error("Test failed! Expecting: " + numResults + " Actual: " + snippets.size());
  }
  
  public YahooAPISearcher() {
    super();
    setYahooAPIKey(gv.getYahooAPIKey());
    log.warn("The web service used by YahooAPISearcher was shut down in August 2010. This searcher will generate no URLs. See http://developer.yahoo.com/blogs/ydn/posts/2010/08/api_updates_and_changes for details.");
  }
  
  public String getYahooAPIKey() {
    return yahooAPIKey;
  }

  public void setLangID(String langID) {
    if (langID == null || 
        langID.equals(LangProvider.UNI[LangProvider.ID]) || 
        langID.equals(LangProvider.ENG[LangProvider.ID])) // equals(ENG_LANG) was added on 09/09/2008
      langCode = "";
    else if (langID.equals(LangProvider.CHT[LangProvider.ID]))
      langCode = CHT_LANG;
    else if (langID.equals(LangProvider.CHS[LangProvider.ID]))
      langCode = CHS_LANG;
    else langCode = langID;
  }

  public void setYahooAPIKey(String yahooAPIKey) {
    this.yahooAPIKey = yahooAPIKey;
  }

  protected void buildSnippets(String resultPage) {
    Document document = null;
    try {
      document = XMLUtil.riskyParse(resultPage);
    } catch (Exception e) {
      log.error(e.toString());
      return;
    }
    
    Node errorNode = XMLUtil.extractNode(document, "Error");
    if (errorNode != null) {
      log.info(errorNode.getTextContent());
      return;
    }
    
    List<Node> resultNodes = XMLUtil.extractPaths(document, new String[] {"ResultSet", "Result"});

    for (Node resultNode : resultNodes) {
      Snippet snippet = new Snippet();
      snippet.setTitle(XMLUtil.extractNode(resultNode, "Title").getTextContent());
      snippet.setSummary(XMLUtil.extractNode(resultNode, "Summary").getTextContent());
      snippet.setMimeType(XMLUtil.extractNode(resultNode, "MimeType").getTextContent());
      snippet.setLastModified(Long.parseLong(XMLUtil.extractNode(resultNode, "ModificationDate").getTextContent()));
      snippet.setPageURL(XMLUtil.extractNode(resultNode, "Url").getTextContent());
      Node cacheNode = XMLUtil.extractNode(resultNode, "Cache");
      if (cacheNode != null) {
        snippet.setCacheURL(XMLUtil.extractNode(cacheNode, "Url").getTextContent());
        snippet.setCacheSize(Integer.parseInt(XMLUtil.extractNode(cacheNode, "Size").getTextContent()));
      }
      snippet.setRank(snippets.size()+1);
      snippets.add(snippet);
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
    if (Helper.empty(yahooAPIKey))
      throw new IllegalArgumentException("Yahoo! API Key is not specified!");
    if (numResultsForThisPage < 1 || numResultsForThisPage > maxResultsPerPage)
      throw new IllegalArgumentException("Number of results for this page must be between 1 and " + maxResultsPerPage + " inclusively.");
    if (pageNum < 1)
      throw new IllegalArgumentException("Page number must be at least 1.");
    
    int startIndex = (pageNum-1) * maxResultsPerPage + 1;
    return null;//getURL(yahooAPIKey, numResultsForThisPage, startIndex, langCode, query);
  }
  
  public static String getURL(String appID, int numResults, int start, String langID, String query) {
    StringBuffer url = new StringBuffer(BASE_URL);
    url.append(APPID_KEY).append("=").append(appID).append("&");
    url.append(RESULTS_KEY).append("=").append(numResults).append("&");
    url.append(START_KEY).append("=").append(start).append("&");
    url.append(LANG_KEY).append("=").append(langID).append("&");
    url.append(QUERY_KEY).append("=").append(query);
    log.debug(url.toString());
    return url.toString();
  }
}
