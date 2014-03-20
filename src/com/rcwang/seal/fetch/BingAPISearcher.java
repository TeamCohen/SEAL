package com.rcwang.seal.fetch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rcwang.seal.util.GlobalVar;

public class BingAPISearcher extends WebSearcher {
    private static final String QUERY_BASEURL = "http://api.bing.net/json.aspx?Appid=%s&query=%s&sources=web&web.count=%d&web.offset=%d";
    private static final Logger log = Logger.getLogger(BingAPISearcher.class);
    
    private static final int ERROR_INVALIDPARAMETER = 1002;
    private static final int NPRINT_ON_ERROR = 500;

    private static final int MAX_RESULTS = 1000;
    private static final int MAX_PAGESIZE = 50;
    private String appId;

    public BingAPISearcher() {
        super();
        this.setMaxResultPerPage(MAX_PAGESIZE);
        appId = GlobalVar.getGlobalVar().getBingAPIKey();
    }

    @Override
    protected void buildSnippets(String resultPage) {
        if (resultPage == null || resultPage.length() == 0) return;
        if (!resultPage.startsWith("{")) {
            log.error("Bad result page for Bing. Bing results must be JSON formatted. Perhaps a bad cache file? Turn DEBUG on for WebManager to view cache locations.");
            log.debug("Result page text:\n"+resultPage);
            return;
        }
        final String responseDataStr = "SearchResponse";
        
        JSONObject responseData = null;
        try {
          JSONObject document = new JSONObject(resultPage);
          if (document.isNull(responseDataStr)) return;
          responseData = document.getJSONObject(responseDataStr);
          JSONArray results = responseData.getJSONObject("Web").getJSONArray("Results");
          log.debug("Response shows "+results.length()+" results.");

          for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            String pageURL = result.getString("Url");
            String title = result.getString("Title");

            // a valid snippet must contain page URL and title
            if (pageURL == null || title == null) continue;

            String cacheURL = "";
            if (!result.isNull("CacheUrl")) cacheURL = result.getString("CacheUrl");
            String summary = "";
            if (result.has("Description"))
                summary = result.getString("Description");

            Snippet snippet = new Snippet();
            snippet.setPageURL(pageURL);
            snippet.setTitle(title);
            if (cacheURL != "") snippet.setCacheURL(cacheURL);
            snippet.setSummary(summary);
            snippet.setRank(snippets.size()+1);
            snippets.add(snippet);
          }
        } catch (JSONException e) {
          log.error(e);
          if (responseData != null) {
              boolean has_unknown_errors = false;
              try {
                  JSONArray errors = responseData.getJSONArray("Errors");
                  for (int i=0; i < errors.length(); i++) { JSONObject error = errors.getJSONObject(i);
                      if (error.getInt("Code") == ERROR_INVALIDPARAMETER && error.getString("Parameter").equals("SearchRequest.AppId")) {
                          log.error("Bing API key is invalid. Obtain a valid key from http://www.bing.com/toolbox/bingdeveloper/ and set the bingAPIKey value in seal.properties.");
                      } else {
                          has_unknown_errors = true;
                          log.error("Unknown error "+error.getInt("Code")+": "+error.getString("Message")+error.getString("Parameter"));
                      }
                  }
              } catch (JSONException e2) { log.error(e2); log.debug("Document: "+resultPage.substring(0, NPRINT_ON_ERROR)); has_unknown_errors = false; }
              if (has_unknown_errors) log.debug("Document: "+resultPage.substring(0, NPRINT_ON_ERROR));
          } else 
              log.debug("Document: "+resultPage.substring(0, NPRINT_ON_ERROR));
        }
    }
    
    static long last_URL = 0;
    static int qps = 0;
    private void qpslimit() {
        qps++;
        if (last_URL == 0) { last_URL=System.currentTimeMillis(); return; }
        if (System.currentTimeMillis()-last_URL > 1000) { last_URL = 0; qps = 0; return; }
        if (qps >= 7) {
            last_URL=0;
            qps = 0;
            try {
                log.debug("Bing requires usage at less than 7 queries per second. Sleeping 1s...");
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
        }
    }
    
    @Override
    protected String getSearchURL(int numResultsForThisPage, int pageNum, String query) {
        if (numResultsForThisPage < 1 || numResultsForThisPage > maxResultsPerPage)
            throw new IllegalArgumentException("Number of results for this page must be between 1 and " + maxResultsPerPage + " inclusively.");
        if (pageNum*numResultsForThisPage > MAX_RESULTS)
            throw new IllegalArgumentException("Maximum results cannot exceed "+MAX_RESULTS+" (pageNum probably too big)");
        
        qpslimit();
        
        int offset = (pageNum - 1) * maxResultsPerPage;
        String url = String.format(QUERY_BASEURL, appId, query, numResultsForThisPage, offset);
        log.debug(url);
        return url;
    }

}
