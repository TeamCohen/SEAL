package com.rcwang.seal.fetch;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Set;
import java.net.URL;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
//import org.apache.lucene.document.Document; // can't use directly due to conflict
import org.apache.lucene.search.Searcher;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Collector;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.util.Version;
import org.apache.lucene.store.FSDirectory;

import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.GlobalVar;


public class OfflineSearcher {
    public static Logger log = Logger.getLogger(OfflineSearcher.class);

    private Set<String> queries = new TreeSet<String>();
    private Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_30);

    private int numResults = 100;
    private Searcher searcher;
    private File rootOfIndexedFiles;


    public OfflineSearcher(File indexDir,File rootOfIndexedFiles) {
        try {
            this.rootOfIndexedFiles = rootOfIndexedFiles;
            log.info("indexDir is '" + indexDir + "'");
            if (rootOfIndexedFiles!=null) {
                log.info("paths to file names in the index are relative to '" + rootOfIndexedFiles + "'");
            } else {
                log.info("paths to files in index are absolute");
            }
            IndexReader reader = IndexReader.open(FSDirectory.open(indexDir), true); // only searching, so read-only=true
            searcher = new IndexSearcher(reader);
            if (searcher==null) {
                log.error("can't open indexSearcher for "+indexDir);
            }
        } catch (IOException ex) {
            log.error(" caught IOException opening indexDir "+indexDir+" with message: " + ex.getMessage());
        }
    } 
        
    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }
    public void setLangID(String langID) {
        log.warn("Language id '"+langID+"' ignored, assuming English");
    }
    public void setUseEngine(int engine) {
        log.warn("Web search engine "+engine+" ignored, will search cache");
    }

    public DocumentSet fetchDocuments(Collection<String>seeds, String hint) {
        DocumentSet documents = new DocumentSet();
        try {
            // build the query string
            StringBuffer line = new StringBuffer("");
            for (String seed : seeds) {
                if (line.length()>0) line.append(" ");
                line.append("\"" + seed + "\"");
            }
            log.info("query line: '"+line+"'");
            if (hint!=null && hint.length()>0) {
                log.warn("ignored hint "+hint);
            }

            // submit query to lucene
            QueryParser parser = new QueryParser(Version.LUCENE_30, "contents", analyzer);
            Query query = parser.parse(line.toString());
            TopScoreDocCollector collector = TopScoreDocCollector.create(numResults, false);
            searcher.search(query, collector);
            log.info(collector.getTotalHits() + " matching documents");
            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            // retrieve the matching documents
            for (int i = 0; i < hits.length && i<numResults; i++) {
                org.apache.lucene.document.Document doc = searcher.doc(hits[i].doc);
                String docLoc = doc.get("url");
                log.debug("page is at docloc: "+docLoc);
                File cacheFile;
                URL cacheFileURL;
                if (rootOfIndexedFiles!=null) {
                    cacheFile = new File(rootOfIndexedFiles,docLoc);
                    cacheFileURL = new URL("file://" + rootOfIndexedFiles + "/" + docLoc);
                } else {
                    cacheFile = new File(docLoc);                    
                    cacheFileURL = new URL("file://" + docLoc);
                }
                log.debug("found page in cache at "+docLoc + " file "+cacheFile);
                String cachedPage = Helper.readFile(cacheFile);
                if (cachedPage!=null) {
                    cachedPage = WebManager.removeCacheHeader(cachedPage);
                    Document d = new Document(cachedPage,cacheFileURL);
                    d.setWeight(hits[i].score);
                    documents.add(d);
                } else {
                    log.error("can't uncache file "+cacheFile+" from loc "+docLoc+" root "+rootOfIndexedFiles);
                }
            }
        } catch (IOException ex) {
            log.error(" caught IOException in OfflineSearcher with message: " + ex.getMessage());
        } catch (ParseException ex) {
            log.error(" caught query parse exception in OfflineSearcher with message: " + ex.getMessage());
        }
        return documents;
    }

    public static void main(String args[])  {
        String[] seedArr;
        for (int i = 0; i < args.length; i++)
            args[i] = args[i].replace('_', ' ');
        seedArr = args;
        GlobalVar gv = GlobalVar.getGlobalVar();
        OfflineSearcher searcher = new OfflineSearcher(gv.getIndexDir(), gv.getLocalRoot());
        DocumentSet documents = searcher.fetchDocuments(Arrays.asList(seedArr), null);
        System.out.println("retrieved: "+documents.size()+" documents");
        for (Document doc : documents) {
            System.out.println(".\t"+doc.getWeight()+"\t"+doc.getURL());
        }
    }
}
