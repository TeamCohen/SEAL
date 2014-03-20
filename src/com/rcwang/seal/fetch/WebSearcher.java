/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public abstract class WebSearcher implements Runnable {

    public static Logger log = Logger.getLogger(WebSearcher.class);
    public static GlobalVar gv = GlobalVar.getGlobalVar();

    public static final int DEFAULT_MAX_RESULTS_PER_PAGE = 100;
    public static final boolean DEFAULT_ANNOTATE_QUERY = false;

    private List<String> resultPages;
    private List<String> queries;
    private File cacheDir;
    private int numResults;
    private int maxDocSizeInKB;
    private int timeOutInMS;
    private int index = 0;
    private boolean annotateQuery;
    protected int maxResultsPerPage;

    protected String langCode;
    protected Set<Snippet> snippets;

    public WebSearcher() {
        queries = new ArrayList<String>();
        resultPages = new ArrayList<String>();
        snippets = new LinkedHashSet<Snippet>();

        setMaxResultPerPage(DEFAULT_MAX_RESULTS_PER_PAGE);
        setAnnotateQuery(DEFAULT_ANNOTATE_QUERY);
        setLangID(gv.getLangID());
        setNumResults(gv.getNumResults());
        setMaxDocSizeInKB(gv.getMaxDocSizeInKB());
        setTimeOutInMS(gv.getTimeOutInMS());
        setCacheDir(gv.getCacheDir());
    }

    public void addQueries(Collection<String> queries, boolean addQuote) {
        if (queries == null) return;
        for (String query : queries)
            addQuery(query, addQuote);
    }

    public void addQuery(String query) {
        addQuery(query, false);
    }

    public void addQuery(String query, boolean addQuote) {
        if (query == null) return;
        query = query.trim();
        if (query.length() == 0) return;
        query = addQuote ? Helper.addQuote(query) : query;
        queries.add(query);
    }

    public File getCacheDir() {
        return cacheDir;
    }

    protected List<String> getQueries() {
        return this.queries;
    }
    
    // encode the query and formulate the query URL
    public String getEncodedQuery() {
        if (queries.size() == 0) {
            log.error("No queries to search for!");
            return null;
        }
        StringBuffer buf = new StringBuffer();
        for (String query : queries) {
            if (buf.length() > 0)
                buf.append(" ");
            buf.append(query);
        }
        return Helper.encodeURLString(buf.toString());
    }

    public String getLangCode() {
        return langCode;
    }

    public int getMaxDocSizeInKB() {
        return maxDocSizeInKB;
    }

    public int getMaxResultPerPage() {
        return maxResultsPerPage;
    }

    public int getNumResults() {
        return numResults;
    }

    public List<String> getResultPages() {
        return resultPages;
    }

    /**
     * Returns the URLs for retrieving a list of search results from Google 
     * @return URLs for retrieving a list of search results from Google
     */
    public List<URL> getSearchURLs() {
        List<URL> urls = new ArrayList<URL>();
        if (numResults < 1) {
            log.error("Number of results to retrieve must be at least 1!");
            return urls;
        }
        for (int pageNum = 1; pageNum <= ((numResults - 1) / maxResultsPerPage) + 1; pageNum++) {
            int numResultsForThisPage = Math.min(numResults - maxResultsPerPage * (pageNum - 1), maxResultsPerPage);
            String encodedQuery = getEncodedQuery();
            if (encodedQuery == null)
                return null;
            String urlStr = getSearchURL(numResultsForThisPage, pageNum, encodedQuery);
            if (urlStr == null) continue;
            try {
                URL url = new URL(urlStr);
                urls.add(url);
            } catch (MalformedURLException e) {
                log.error(e.toString());
                continue;
            }
        }
        return urls;
    }

    /**
     * Returns the snippets from the previous search
     * @return snippets from the previous search
     */
    public Set<Snippet> getSnippets() {
        return snippets;
    }

    public int getTimeOutInMS() {
        return timeOutInMS;
    }

    public boolean isAnnotateQuery() {
        return annotateQuery;
    }

    public void reset() {
        queries.clear();
    }

    public List<String> generateResultPages() {
        WebManager webManager = new WebManager();
        List<URL> searchURLs = getSearchURLs();
        List<String> documents = webManager.get(searchURLs);
        return documents;
    }

    /**
     * Sends queries to search engines
     */
    public void run() {
        String className = this.getClass().getSimpleName();
        log.debug("Querying " + className + " for " + Helper.toReadableString(queries) + "...");

        resultPages.clear();
        resultPages.addAll(generateResultPages());
        snippets.clear();

        for (String resultPage : resultPages)
            if (resultPage != null)
                // updates snippets
                buildSnippets(resultPage);

        // assign index of query string to each snippet
        for (Snippet snippet : snippets)
            snippet.setIndex(index);

        // true to annotate query string in snippets
        if (annotateQuery) annotateQuery();

        log.debug(className + " retrieved a total of " + snippets.size() + " URLs for " + Helper.toReadableString(queries) + "!");
    }

    public void setAnnotateQuery(boolean annotateQuery) {
        this.annotateQuery = annotateQuery;
    }

    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public void setMaxDocSizeInKB(int maxDocSizeInKB) {
        this.maxDocSizeInKB = maxDocSizeInKB;
    }

    public void setMaxResultPerPage(int maxResultsPerPage) {
        this.maxResultsPerPage = maxResultsPerPage;
    }

    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }

    public void setTimeOutInMS(int timeOutInMS) {
        this.timeOutInMS = timeOutInMS;
    }

    protected void annotateQuery() {
        for (String query : queries) {
            query = Helper.removeQuote(query);

            for (Snippet snippet : snippets) {
                snippet.getTitle().annotateSource(query);
                for (Excerpt excerpt : snippet.getExcerpts())
                    excerpt.annotateSource(query);
            }
        }
    }

    protected abstract void buildSnippets(String resultPage);

    protected abstract String getSearchURL(int numResultsForThisPage, int pageNum, String query);

    protected void setLangID(String langCode) { this.langCode = langCode; }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}