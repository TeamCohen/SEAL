/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public class YahooBossSearcher extends WebSearcher {

  /********************** Yahoo! Parameters **************************/
  public static final String BASE_URL = "http://boss.yahooapis.com/ysearch/web/v1/";
  public static final String FIXED_KEY_VALUE = "format=xml&abstract=long&type=html,text,xl,msword";
  public static final String APPID_KEY = "appid";
  public static final String COUNT_KEY = "count";
  public static final String START_KEY = "start";
  public static final String LANG_KEY = "lang";
  public static final String CHT_LANG = "tzh";
  public static final String CHS_LANG = "szh";
  public static final int MAX_RESULTS_PER_PAGE = 50;
  /******************************************************************/

  public static final String HOST = Helper.toURL(BASE_URL).getHost();
  public static Logger log = Logger.getLogger(YahooBossSearcher.class);
  public static GlobalVar gv = GlobalVar.getGlobalVar();

  private static DateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd");
  private String yahooBossKey;
  
  public static void main(String args[]) {
    int numResults = 100;
    YahooBossSearcher ys = new YahooBossSearcher();
    ys.setLangID("zh-TW");
    ys.setCacheDir(new File("/www.cache/"));
    ys.setNumResults(numResults);
    ys.setTimeOutInMS(10*1000);
    ys.setMaxDocSizeInKB(512);
//    ys.setYahooBossKey("IcIH_zzV34HLRBtyqhb0NvHa42U7426aGC7pVZdXqYHA3g9bzAPAJG3KW0IRKCI-");
    ys.addQuery("Global Warming", true);
    ys.run();
    Set<Snippet> snippets = ys.getSnippets();
    for (Snippet snippet : snippets)
      log.info(snippet.toString());
    if (numResults == snippets.size())
      log.info("Test succeeded!");
    else log.error("Test failed! Expecting: " + numResults + " Actual: " + snippets.size());
  }
  
  public YahooBossSearcher() {
    super();
    setYahooBossKey(gv.getYahooBossKey());
    setMaxResultPerPage(MAX_RESULTS_PER_PAGE);
  }
  
  public String getYahooBossKey() {
    return yahooBossKey;
  }

  public void setLangID(String langID) {
    // equals(ENG_LANG) was added on 09/09/2008
    if (langID == null || 
        langID.equals(LangProvider.UNI[LangProvider.ID]) || 
        langID.equals(LangProvider.ENG[LangProvider.ID]))
      langCode = "";
    else if (langID.equals(LangProvider.CHT[LangProvider.ID]))
      langCode = CHT_LANG;
    else if (langID.equals(LangProvider.CHS[LangProvider.ID]))
      langCode = CHS_LANG;
    else langCode = langID;
  }

  public void setYahooBossKey(String yahooBossKey) {
    this.yahooBossKey = yahooBossKey;
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
    
    List<Node> resultNodes = XMLUtil.extractPaths(document, new String[] {"ysearchresponse", "resultset_web", "result"});

    for (Node resultNode : resultNodes) {
      Snippet snippet = new Snippet();
      snippet.setTitle(XMLUtil.extractNode(resultNode, "title").getTextContent());
      snippet.setSummary(XMLUtil.extractNode(resultNode, "abstract").getTextContent());
      snippet.setLastModified(toTimestamp(XMLUtil.extractNode(resultNode, "date").getTextContent()));
      snippet.setPageURL(XMLUtil.extractNode(resultNode, "url").getTextContent());
      snippet.setRank(snippets.size()+1);
      snippets.add(snippet);
    }
  }
  
  private long toTimestamp(String dateStr) {
    Date date;
    try {
      date = dateFormatter.parse(dateStr);
    } catch (ParseException e) {
      log.error("Failed parsing: " + dateStr);
      return 0;
    }
    return date.getTime();
  }

  /**
   * Returns a URL for Yahoo! search page
   * @param numResultsForThisPage number of results per page (between 1 and 100 inclusively)
   * @param pageNum page number (greater than 0)
   * @param query query terms
   * @return Google search page URL
   */
  protected String getSearchURL(int numResultsForThisPage, int pageNum, String query) {
    if (Helper.empty(yahooBossKey))
      throw new IllegalArgumentException("Yahoo! Boss Key is not specified!");
    if (numResultsForThisPage < 1 || numResultsForThisPage > maxResultsPerPage)
      throw new IllegalArgumentException("Number of results for this page must be between 1 and " + maxResultsPerPage + " inclusively.");
    if (pageNum < 1)
      throw new IllegalArgumentException("Page number must be at least 1.");
    
    int startIndex = (pageNum-1) * maxResultsPerPage;
    return getURL(yahooBossKey, numResultsForThisPage, startIndex, langCode, query);
  }
  
  public static String getURL(String appID, int numResults, int start, String langID, String query) {
    StringBuffer url = new StringBuffer(BASE_URL);
    url.append(query).append("?");
    url.append(APPID_KEY).append("=").append(appID).append("&");
    url.append(COUNT_KEY).append("=").append(numResults).append("&");
    url.append(START_KEY).append("=").append(start).append("&");
    url.append(LANG_KEY).append("=").append(langID).append("&");
    url.append(FIXED_KEY_VALUE);
    return url.toString();
  }
}
