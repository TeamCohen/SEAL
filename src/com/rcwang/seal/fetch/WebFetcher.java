/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.util.ComboMaker;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class WebFetcher {
    public static Logger log = Logger.getLogger(WebFetcher.class);
    public static GlobalVar gv = GlobalVar.getGlobalVar();

    // true to add quotation marks around queries
    public static final boolean DEFAULT_QUOTE_SEED = true;
    // true to fetch search engine's cache
    // CAUTION: IP address *will* be blocked by Google after a few queries
    public static final boolean DEFAULT_FETCH_ENGINE_CACHE = false;
    // true to remove duplicate documents (slower)
    public static final boolean DEFAULT_REMOVE_DUPLICATE_DOCS = false;
    // true to annotate the query in snippet's title and summary (slower)
    public static final boolean DEFAULT_ANNOTATE_QUERY = false;
    // total number of searchers (update getSearcher() if more engines are added)
    public static final int NUM_SEARCHERS = 5;
    public static final int ENGINE_BING_API = 4;
    public static final int ENGINE_CLUEWEB = 3;
    public static final int ENGINE_GOOGLE_WEB = 2;
    public static final int ENGINE_GOOGLE_API = 1;
    public static final int ENGINE_YAHOO_API = 0;

    private Set<Snippet> snippets;
    private String langID;
    private int numSubSeeds;
    private int numResults;
    private boolean[] useEngine;
    private boolean annotateQuery;
    private boolean fetchSearchEngineCache;
    private boolean removeDuplicateDocument;
    private boolean quoteSeed;

    public WebFetcher() {
        snippets = new HashSet<Snippet>();
        setQuoteSeed(DEFAULT_QUOTE_SEED);
        setAnnotateQuery(DEFAULT_ANNOTATE_QUERY);
        setFetchSearchEngineCache(DEFAULT_FETCH_ENGINE_CACHE);
        setRemoveDuplicateDocument(DEFAULT_REMOVE_DUPLICATE_DOCS);

        setUseEngine(gv.getUseEngine());
        setLangID(gv.getLangID());
        setNumSubSeeds(gv.getNumSubSeeds());
        setNumResults(gv.getNumResults());
    }


    public DocumentSet fetchDocuments(EntityList seeds, String hint) {
        // retrieve snippets
        DocumentSet documents = new DocumentSet();
        if (seeds == null || seeds.isEmpty())
            return documents;
        Set<Snippet> snippets = fetchSnippets(seeds, hint);
        return fetchDocuments(snippets,documents);
    }
    


    /**
     * Queries search engines and crawls the web
     * @param seeds
     * @param hint
     * @return crawled web pages
     */
    public DocumentSet fetchDocuments(Collection<String> seeds, String hint) {
        // retrieve snippets
        DocumentSet documents = new DocumentSet();
        if (seeds == null || seeds.isEmpty())
            return documents;
        Set<Snippet> snippets = fetchSnippets(seeds, hint);
        return fetchDocuments(snippets,documents);
    }
    public DocumentSet fetchDocuments(Set<Snippet> snippets,DocumentSet documents) {
        if (this.useEngine[ENGINE_CLUEWEB]) {
            documents.addAll(ClueWebSearcher.getLastRun().getDocuments());
        }
        List<URL> urls = new ArrayList<URL>();
        List<Snippet> snippetList = new ArrayList<Snippet>(snippets);
        for (Snippet snippet : snippetList) {
            if (fetchSearchEngineCache && snippet.getCacheURL() != null)
                urls.add(snippet.getCacheURL());
            else urls.add(snippet.getPageURL());
        }
        WebManager webManager = new WebManager();
        webManager.setTimeOutInMS(0);  // use auto timeout
        List<String> webpages = webManager.get(urls);
        Set<Integer> docHashSet = new HashSet<Integer>();

        for (int i = 0; i < urls.size(); i++) {
            String webpage = webpages.get(i);
            URL url = urls.get(i);
            if (webpage == null || url == null)
                continue;

            if (removeDuplicateDocument) {
                int fingerPrint = webpage.toLowerCase().replaceAll("\\W+", "").hashCode();
                if (docHashSet.contains(fingerPrint)) {
                    log.info("Found a duplicate document: " + url);
                    continue;
                }
                docHashSet.add(fingerPrint);
            }

            Document document = new Document(webpage, url);
            document.setSnippet(snippetList.get(i));
            documents.add(document);
        }
        return documents;
    }


    public Set<Snippet> fetchSnippets(EntityList seeds, String hint) {
        snippets.clear();
        if (seeds == null || seeds.isEmpty())
            return snippets;

        int numSeeds = (numSubSeeds <= 0) ? seeds.size() : Math.min(numSubSeeds, seeds.size());
        ComboMaker<Entity> comboMaker = new ComboMaker<Entity>();
        List<List<Entity>> subSeedsList = comboMaker.make(seeds.getEntities(), numSeeds);

        List<Thread> threadList = new ArrayList<Thread>();
        List<WebSearcher> searcherList = new ArrayList<WebSearcher>();
        for (int i = 0; i < subSeedsList.size(); i++)
            fetchSnippetsInThread(new EntityList(subSeedsList.get(i)).getEntityNames(), i, hint, threadList, searcherList);

        joinSnippetThreads(threadList,searcherList);
        return snippets;
    }
    /**
     * Aggressively fetch snippets
     * @param seeds
     * @param hint
     */
    public Set<Snippet> fetchSnippets(Collection<String> seeds, String hint) {
        return fetchSnippets(new EntityList(seeds),hint);
//        snippets.clear();
//        if (seeds == null || seeds.isEmpty())
//            return snippets;
//
//        int numSeeds = (numSubSeeds <= 0) ? seeds.size() : Math.min(numSubSeeds, seeds.size());
//        ComboMaker<String> comboMaker = new ComboMaker<String>();
//        List<List<String>> subSeedsList = comboMaker.make(seeds, numSeeds);
//
//        List<Thread> threadList = new ArrayList<Thread>();
//        List<WebSearcher> searcherList = new ArrayList<WebSearcher>();
//        for (int i = 0; i < subSeedsList.size(); i++)
//            fetchSnippetsInThread(new EntityList(subSeedsList.get(i)).getEntityNames(), i, hint, threadList, searcherList);
//
//        joinSnippetThreads(threadList,searcherList);
//        return snippets;
    }
    
    private void joinSnippetThreads(List<Thread> threadList, List<WebSearcher> searcherList) {
        try {
            // wait for the threads to finish
            for (int i = 0; i < threadList.size(); i++) {
                Thread searchThread = threadList.get(i);
                if (searchThread == null) continue;
                searchThread.join();
                WebSearcher searcher = searcherList.get(i);
                snippets.addAll(searcher.getSnippets());
            }
        } catch (InterruptedException ie) {}
    }

    public String getLangID() {
        return langID;
    }

    public int getNumResults() {
        return numResults;
    }

    public int getNumSubSeeds() {
        return numSubSeeds;
    }

    public Set<Snippet> getSnippets() {
        return snippets;
    }

    public boolean[] getUseEngine() {
        return useEngine;
    }

    public boolean isAnnotateQuery() {
        return annotateQuery;
    }

    public boolean isFetchSearchEngineCache() {
        return fetchSearchEngineCache;
    }

    public boolean isQuoteSeed() {
        return quoteSeed;
    }

    public boolean isRemoveDuplicateDocument() {
        return removeDuplicateDocument;
    }

    public void setAnnotateQuery(boolean annotateQuery) {
        this.annotateQuery = annotateQuery;
    }

    public void setFetchSearchEngineCache(boolean fetchSearchEngineCache) {
        this.fetchSearchEngineCache = fetchSearchEngineCache;
    }

    public void setLangID(String langID) {
        this.langID = langID;
    }

    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }

    public void setNumSubSeeds(int numSubSeeds) {
        this.numSubSeeds = numSubSeeds;
    }

    public void setQuoteSeed(boolean addQuote) {
        this.quoteSeed = addQuote;
    }

    public void setRemoveDuplicateDocument(boolean removeDuplicateDocument) {
        this.removeDuplicateDocument = removeDuplicateDocument;
    }

    public void setUseEngine(int engine) {
        this.useEngine = Helper.toBinaryArray(engine, NUM_SEARCHERS);
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("Using engines: ");
            StringBuilder sb2 = new StringBuilder("(others: ");
            String[] engines = new String[] {"Yahoo API","Google API","Google Web","Clue Web","Bing API"};
            for (int i=0; i<this.useEngine.length; i++) {
                if (this.useEngine[i]) sb.append(engines[i]).append(" ");
                else sb2.append(engines[i]).append(" ");
            }
            log.debug(sb.toString()+sb2.toString()+")");
        }
    }

    private void fetchSnippetsInThread(List<String[]> seeds, int index, String hint, 
            List<Thread> threadList, 
            List<WebSearcher> searcherList) {
        // create searcher threads
        for (int i = 0; i < NUM_SEARCHERS; i++) {
            WebSearcher searcher = getSearcher(i);
            if (searcher == null) continue;

            // add queries into the searcher
            for (String[] seed : seeds) {
                for (String s : seed)
                    searcher.addQuery(s, quoteSeed);
            }
            // configures the searcher
            searcher.addQuery(hint);
            searcher.setLangID(langID);
            searcher.setNumResults(numResults);
            searcher.setAnnotateQuery(annotateQuery);
            searcher.setIndex(index);

            Thread searchThread = new Thread(searcher);
            searchThread.start();
            threadList.add(searchThread);
            searcherList.add(searcher);
        }
    }

    // update NUM_SEARCHERS if more engines are added
    private WebSearcher getSearcher(int i) {
        if (!useEngine[i]) return null;
        switch (i) {
        case ENGINE_YAHOO_API: return new YahooAPISearcher();
        case ENGINE_GOOGLE_API: return new GoogleCustomAPISearcher();//GoogleAPISearcher();
        case ENGINE_GOOGLE_WEB: return new GoogleWebSearcher();
        case ENGINE_CLUEWEB: return ClueWebSearcher.getSearcher();
        case ENGINE_BING_API: return new BingAPISearcher();
        default: return null;
        }
    }
}
