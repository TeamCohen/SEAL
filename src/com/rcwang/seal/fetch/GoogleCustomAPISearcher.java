package com.rcwang.seal.fetch;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rcwang.seal.util.GlobalVar;

public class GoogleCustomAPISearcher extends WebSearcher {
    private static final String QUERY_BASEURL = "https://www.googleapis.com/customsearch/v1?key=%s&%s&start=%s&q=%s";
    private static final Logger log = Logger.getLogger(GoogleCustomAPISearcher.class);
    private static final String DEFAULT_CSE = "cx=002444431219174564167:9oc1_-dfbf0";
    private static final Object ERROR_KEY_INVALID = "keyInvalid";
    private static final int NCHARS_ON_ERROR = 500;
    private static final int MAX_PAGESIZE=10;
    
    private String apikey;
    private String cse;
    
    public GoogleCustomAPISearcher() {
        super();
        this.setMaxResultPerPage(MAX_PAGESIZE);
        GlobalVar gv = GlobalVar.getGlobalVar();
        this.apikey = gv.getGoogleCustomAPIKey();
        this.cse = DEFAULT_CSE;//gv.getGoogleCSE();
    }
    
    @Override
    protected void buildSnippets(String resultPage) {
        if (resultPage == null || resultPage.length() == 0) return;
        if (!resultPage.startsWith("{")) {
            log.error("Bad result page for Google Custom Search. Google Custom Search results must be JSON formatted. Perhaps a bad cache file? Turn DEBUG on for WebManager to view cache locations.");
            log.debug("Result page text:\n"+resultPage);
            return;
        }
        
        final String responseDataField = "items";
        JSONObject document = null;
        JSONArray results = null;
        try {
            document = new JSONObject(resultPage);
            if (document.isNull(responseDataField)) return;
            results = document.getJSONArray(responseDataField);
            log.debug("Response shows "+results.length()+" results.");
            
            for (int i=0;i<results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                String pageURL = result.getString("link");
                String title = result.getString("title");
                
                //if either is missing; the result is invalid
                if (pageURL == null || title == null) continue;
                
                String cacheURL = ""; //TODO: wrong:
                if (!result.isNull("cacheId")) cacheURL = result.getString("cacheId");
                String summary = "";
                if (result.has("snippet")) summary = result.getString("snippet");
                
                Snippet snippet = new Snippet();
                snippet.setPageURL(pageURL);
                snippet.setTitle(title);
                //if (cacheURL != "") snippet.setCacheURL(cacheURL);
                snippet.setSummary(summary);
                snippet.setRank(snippets.size()+1);
                snippets.add(snippet);
            } 
        } catch(JSONException e) {
            log.error(e);
            if (results != null) {
                boolean has_unknown_errors = false;
                try {
                    JSONArray errors = document.getJSONObject("error").getJSONArray("errors");
                    for (int i=0; i<errors.length(); i++) {
                        JSONObject error = errors.getJSONObject(i);
                        if (error.getString("reason").equals(ERROR_KEY_INVALID)) {
                            log.error("Google Custom Search API key is invalid. Obtain a valid key from https://code.google.com/apis/console and set the googleCustomAPIKey value in seal.properties.");
                        } else {
                            has_unknown_errors = true;
                            log.error("Unknown error "+error.getString("message")+": ("+error.getString("domain")+") "+error.getString("reason")+error.getString("Parameter"));
                        }
                    }
                } catch (JSONException e2) { }
                if (has_unknown_errors) log.debug("Document: "+resultPage.substring(0,NCHARS_ON_ERROR));
            } else log.debug("Document: "+resultPage.substring(0,NCHARS_ON_ERROR));
        }
    }

    @Override
    protected String getSearchURL(int numResultsForThisPage, int pageNum, String query) {
        String url = String.format(QUERY_BASEURL, this.apikey, this.cse, (pageNum-1)*numResultsForThisPage+1, query);
        log.debug(url);
        return url;
    }

}
