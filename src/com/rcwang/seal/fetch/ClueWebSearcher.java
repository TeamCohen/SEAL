package com.rcwang.seal.fetch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;

public class ClueWebSearcher extends WebSearcher {
    private static final Logger log = Logger.getLogger(ClueWebSearcher.class);

    private static final Pattern TITLE_PATTERN=Pattern.compile("<TITLE>(.+)</TITLE>", Pattern.CASE_INSENSITIVE);
    private static final String WIKIPEDIA_BASEURL="http://boston.lti.cs.cmu.edu/NELL/search/clueweb09_wikipedia/";
    public static final String BATCH_CATB_BASEURL="http://boston.lti.cs.cmu.edu/NELL/batchquery/upload.cgi";
    public static final String CATB_CACHE_BASEURL="http://boston.lti.cs.cmu.edu:8085/clueweb09/render/renderpage.cgi?id=%s";

    public static final String FORMAT_INDRI="0";
    public static final String FORMAT_TREC_EVAL="1";

    private static ClueWebSearcher lastRun=null;
    /** Get a ClueWebSearcher object, returning a persisted one if it exists.
     * This is necessary because SEAL refreshes its WebSearcher object for each
     * query, but a single ClueWebSearcher object collects results for multiple
     * queries at a time.
     * @return a new ClueWebSearcher object, or if we are in multiquery mode and
     * a persisted ClueWebSearcher object exists, the old object.
     */
    public static ClueWebSearcher getSearcher() { 
        if (gv.isMultiquery()) {
            if (lastRun == null) lastRun = new ClueWebSearcher();
            return lastRun;
        }
        return new ClueWebSearcher();
    }
    /** Get the persisted ClueWebSearcher object.
     * @return the old ClueWebSearcher object, or null if in single-query mode.
     */
    public static ClueWebSearcher getLastRun() { return lastRun; }

    private boolean memoryManagement=false;
    private long lastTimeMemoryWasOk=0;
    /** Enable memory management (default false) (turn on for memory-impoverished machines or large batches) **/
    public void setMemoryManagement(boolean s) { memoryManagement = s; }

    private File tmpDir;
    /** Set directory where the raw uploaded batch request file and raw 
     * downloaded batch response files should be stored. Directory is
     * created if it doesn't already exist. 
     * @param d
     */
    public void setTmpDir(File d) { this.tmpDir=d;
    if (!d.exists()) d.mkdir(); }

    private boolean keepResponseFile=false;
    /** Keep raw batch response file (default: delete once file is parsed into the SEAL cache) **/
    public void setKeepResponseFile(boolean k) { keepResponseFile = k; }

    private boolean keepQuery=false;
    /** Keep raw batch request file (default: delete once response is parsed into the SEAL cache) **/
    public void setKeepQuery(boolean k) { keepQuery = k; }

    private List<DocumentSet> documents;
    private List<Set<Snippet>> snippetsByQuery;

    private String format=FORMAT_TREC_EVAL;
    private boolean fulltext=true;
    /** Request fulltext (default:on) **/
    public void setFulltext(boolean f) { fulltext = f; }
    /** Get whether fulltext is being requested **/
    public boolean getFulltext() { return fulltext; }

    private boolean encodeQueries;
    private boolean diskMode;
    /** Set whether to store fulltext document sets on disk or in memory (default:none).
     * If memory management has been turned on, the system monitors memory usage and
     * automatically turns on disk mode once space becomes tight. **/
    public void setDiskMode(boolean s) { diskMode = s; }

    /** Default constructor (encodeQueries on) **/
    public ClueWebSearcher() {
        this(true);
    }

    /** Create a new ClueWebSearcher and set whether queries should be encoded or not
     * 
     * @param encodeQueries - true to use seed-style queries (single-query 
     * mode), false for batch-style queries (multi-query mode). Seed-style 
     * queries are provided for command-line calls and for backwards 
     * compatibility for other SEAL searchers, for which "addQuery(s:String)"
     * adds a seed, and the list of queries is the list of seeds; thus the 
     * encoded queries form the single search string sent to the search engine.
     * For ClueWebSearcher in multi-query/batch mode, addQuery(s:String) adds 
     * an indri-style query which is already formed of multiple seeds, and the 
     * list of queries is the same as the batch.
     */
    public ClueWebSearcher(boolean encodeQueries) {
        documents = new ArrayList<DocumentSet>();//new DocumentSet();
        snippetsByQuery = new ArrayList<Set<Snippet>>();
        this.encodeQueries = encodeQueries;

        GlobalVar gv = GlobalVar.getGlobalVar();
        this.setKeepQuery(gv.getClueWebKeepQueryFile());
        this.setKeepResponseFile(gv.getClueWebKeepResponseFile());
        this.setMemoryManagement(gv.getClueWebMemoryManagement());
        String tdirname = gv.getProperties().getProperty(GlobalVar.CLUEWEB_TMP_DIR);
        if (tdirname != null) this.setTmpDir(new File(tdirname));
    }
    @Override
    /**
     * Sends queries to search service.
     * 
     * 1. Build query set
     * 2. Send batch query and retrieve response
     * 3. Build snippets from the response
     */
    public void run() { 
        lastRun = this;
        System.gc();
        String className = this.getClass().getSimpleName();

        Collection<String> querySet = new TreeSet<String>();
        if (this.encodeQueries) querySet.add(this.getEncodedQuery());
        else querySet = this.getQueries();

        log.info("Querying " + className + " for " + Helper.toReadableString(querySet) + "...");

        snippets.clear(); 
        snippetsByQuery.clear();
        //snippetsByQuery.add(snippets);
        for (int i=0;i<querySet.size();i++) this.snippetsByQuery.add(new HashSet<Snippet>());

        File result = sendBatchQuery(querySet);
        if (result != null) buildSnippets(result);
        else log.error("Problem while sending query; no results recorded.");

        // assign index of query string to each snippet
        //        for (Snippet snippet : snippets)
        //            snippet.setIndex(this.getIndex());

        // true to annotate query string in snippets
        if (isAnnotateQuery()) annotateQuery();

        log.info(className + " retrieved a total of " + snippets.size() + " URLs and documents for " + Helper.toReadableString(this.getQueries()) + "!");

    }

    /** Formulate the set of queries as a batch request, send it, and retrieve the 
     * response from the server as a file.
     * @param querySet - a set of Indri-formatted queries
     * @return
     */
    public File sendBatchQuery(Collection<String> querySet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<parameters>");
        if (fulltext) sb.append("<printDocuments>true</printDocuments>");
        int i=0;
        for (String q : querySet) {
            sb.append("<query>\n");
            sb.append("<number>").append(i++).append("</number>\n");
            sb.append("<text>").append(q).append("</text>\n");
            sb.append("</query>\n");
            if (!diskMode) documents.add(new DocumentSet());
        }
        sb.append("</parameters>");
        if (log.isDebugEnabled()) log.debug("Query parameters file:\n"+sb.toString());
        if (keepQuery) {
            try {
                BufferedWriter ow = new BufferedWriter(new FileWriter(File.createTempFile("clueweb-query_", ".txt",this.tmpDir)));
                ow.write(sb.toString());
                ow.close();
            } catch (FileNotFoundException e) {
                log.error("Problem saving query file:",e);
            } catch (IOException e) {
                log.error("Problem saving query file:",e);
            }
        }

        HttpClient httpclient = new DefaultHttpClient();
        try {                    
            int numTrials = Integer.parseInt(gv.getProperties().getProperty(GlobalVar.CLUEWEB_TIMEOUT_NUMTRIALS,"2"));

            String headerTimeout =gv.getProperties().getProperty(GlobalVar.CLUEWEB_HEADER_TIMEOUT_MS,"-1"); 
            int timeoutms = Integer.parseInt(headerTimeout);
            long reportingPeriod = Long.parseLong(gv.getProperties().getProperty(GlobalVar.CLUEWEB_TIMEOUT_REPORTING_MS,"1000"));
            HttpEntity resentity=null;
            File result=null;
            boolean timedOut = true;

            trials: // multiple trials only relevant when timeoutms is set >0
                for (int trial = 0; trial < numTrials; trial++) {
                    if (trial > 0) { 
                        if (timeoutms < Integer.MAX_VALUE/2) timeoutms *= 2;
                        EntityUtils.consume(resentity);
                    }
                    HttpPost post = makePost(sb.toString());
                    HttpResponse response = httpclient.execute(post);
                    resentity = response.getEntity();
                    StatusLine sline = response.getStatusLine();
                    if (log.isDebugEnabled()) {
                        for (Header h : response.getAllHeaders()) {
                            log.debug(h.getName()+": "+h.getValue());
                        }
                    }
                    if (sline.getStatusCode() != 200) {
                        log.error("Bad status code "+sline.getStatusCode()+": "+sline.getReasonPhrase());
                        return null;
                    }
                    if (resentity == null) {
                        log.error("Null result entity?");
                        return null;
                    }
                    
                    result = File.createTempFile("clueweb-resp_", ".txt", this.tmpDir);
                    FileOutputStream os = new FileOutputStream(result);
                    long starttime=System.currentTimeMillis();
                    if (timeoutms < 0) {
                        log.info("Saving file directly...");
                        resentity.writeTo(os);
                        log.info((System.currentTimeMillis() - starttime)+" ms");
                        timedOut=false;
                    } else {
                        log.info("[trial "+(trial+1)+" of "+numTrials+"] "+timeoutms/1000+" seconds to read header.");
                        ResponseReaderThread r = new ResponseReaderThread(resentity.getContent(), os);
                        Thread readerthread = new Thread(r,result.getName());
                        readerthread.start();
                        long then = starttime;
                        while(!r.hasTimedOut) {
                            try {
                                if(r.hasFinishedHeader) {
                                    log.info("Header complete at "+(System.currentTimeMillis() - starttime)+"ms. Saving results...");
                                    readerthread.join();
                                    timedOut = false;
                                    break trials; // outer loop
                                }
                                long now = System.currentTimeMillis();
                                long timeElapsed = now - starttime;
                                long timeLeft = timeoutms - timeElapsed;
                                if (timeLeft > 0) {
                                    if (now - then > reportingPeriod || timeLeft < 6000) { 
                                        double byterate = (double) 1000 * r.bytes / (now-starttime);
                                        log.info(timeLeft/1000+" seconds remaining; "+byterate+" bytes/s");
                                        then += reportingPeriod;
                                    }
                                    Thread.sleep(Math.min(timeLeft,1000));
                                } else { // then we've timed out
                                    log.warn("Timed out reading header! Waiting to finish...");
                                    r.hasTimedOut = true;
                                    readerthread.join();
                                }
                            } catch (InterruptedException e) {
                                log.error(e);
                            }
                        } // timer poll loop
                    } // timeout case
                    log.info("File complete at "+(System.currentTimeMillis() - starttime)+" ms");
                } // trials loop
            
            // not sure whether these are strictly necessary but we'll go with it
            EntityUtils.consume(resentity);
            
            if (timedOut) return null;
            
            return result;
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        } catch (ClientProtocolException e) {
            log.error(e);
        } catch (IOException e) {
            log.error(e);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return null;
    }

    /** Utility method:
     * Formulate an HTTP POST request to upload the batch query file
     * @param queryBody
     * @return
     * @throws UnsupportedEncodingException
     */
    private HttpPost makePost(String queryBody) throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(ClueWebSearcher.BATCH_CATB_BASEURL);
        InputStreamBody qparams = 
            new InputStreamBody(
                    new ReaderInputStream(new StringReader(queryBody)),
                    "text/plain",
            "query.txt");

        MultipartEntity entity = new MultipartEntity();
        entity.addPart("viewstatus", new StringBody("1")); 
        entity.addPart("indextype",  new StringBody("catbparams"));
        entity.addPart("countmax",   new StringBody("100"));
        entity.addPart("formattype", new StringBody(format));
        entity.addPart("infile",     qparams);

        post.setEntity(entity);
        return post;
    }
    /** 
     * Detect Indri or trec_eval formatted results line, which delimits WARC records in full-text responses.
     * @param line
     * @return
     */
    private boolean isResultsLine(String line) {
        String[] header;
        if (format == FORMAT_INDRI)
            return (line.length() > 23) && ((header = line.split("\t")).length == 4) && header[1].startsWith("clueweb");
        // then we have something like
        // -2.18963        clueweb09-en0009-21-22503       0       2558
        // and we're done with this document
        else if (format == FORMAT_TREC_EVAL)
            return (line.length() > 25) && ((header = line.split(" ")).length == 6) && header[2].startsWith("clueweb");
        log.error("Invalid format setting '"+format+"' for indri-clueweb response");
        return false;
    }
    /** Consumes, records, and caches a single document from the WARC file returned by the batch service **/
    protected void parseWARC(BufferedReader reader, Snippet snippet, int queryNumber) throws IOException {
        if (snippet == null) {
            log.warn("No snippet provided");
            return;
        }
        // consume WARC headers
        String line = "";
        URL url = null;
        int doclen = 0;
        while (!line.startsWith("HTTP")) {
            line = reader.readLine();
            if (line == null) {
                log.warn("Unexpected end of results file while reading WARC headers for document "+snippet.getTitle().getText());
                return;
            }
            if (line.startsWith("WARC-Target-URI")) {
                url = new URL(line.substring(line.indexOf(":")+2));
                snippet.setPageURL(url.toString());
            }
        }
        // now 'line' contains the first line of the HTTP header for this document
        // consume the HTTP headers
        while(!line.startsWith("Content-Length")) {
            line = reader.readLine();
            if (line == null) {
                log.warn("Unexpected end of results file while reading HTTP headers for document "+snippet.getTitle().getText());
                return;
            }
        }
        // now 'line' contains the (incorrect) content length
        doclen = Integer.parseInt(line.substring(line.indexOf(':')+2));
        reader.readLine(); // toss the newline

        // read the file
        StringBuilder sb = new StringBuilder(); 
        while ( (line = reader.readLine()) != null) {
            //gross: no delimiters, must detect header of next file
            if ( isResultsLine(line) ) {
                reader.reset();
                break;
            }
            reader.mark(128);
            sb.append(line).append("\n");
        }
        if (doclen - sb.length() > 5) 
            log.debug("Content-length "+doclen+" for document length "+sb.length()+"; header off by "+(doclen - sb.length()));


        Document document = new Document(sb.toString(), url);
        Matcher m = TITLE_PATTERN.matcher(document.getText());
        if (m.find()) {
            snippet.setTitle(m.group(1));
            //            snippet.setTitle(String.copyValueOf(m.group(1).toCharArray()));
        }
        document.setSnippet(snippet);

        if (!diskMode) {
            DocumentSet d = null;
            if (documents.size()>queryNumber) d = documents.get(queryNumber);
            else { 
                log.warn("query number "+queryNumber+" out of known range; adding a new one");
                d = new DocumentSet();
                while (queryNumber > documents.size()) documents.add(new DocumentSet());
                documents.add(queryNumber,d);
            }
            log.debug("Added document to documentset "+d+" @query "+queryNumber);
            d.add(document);
        }

        //log.debug("Added document to query "+queryNumber);

        WebManager.writeToCache(url, document.getText(), getCacheDir());
        if (memoryManagement) {
            Runtime runtime = Runtime.getRuntime();
            if (runtime.freeMemory() > 1e7) lastTimeMemoryWasOk = System.currentTimeMillis();
            long dt =System.currentTimeMillis() - lastTimeMemoryWasOk; 
            log.debug("Last good: "+dt+ (diskMode ? " (disk)" : ""));
            if (!diskMode & (dt > 1000)) {
                log.info("Memory getting tight. Caching documentsets on disk instead...");
                cacheOut();
                diskMode=true;
            }
        }
    }

    private void cacheOut() {
        this.documents.clear();
    }

    /** Parses the batch response file from the search service into a set of 
     * snippet collections for each query in the batch.
     * @param resultsFile Batch query response
     */
    protected void buildSnippets(File resultsFile) {
        LineNumberReader reader = null;
        int queryNumber=-1,docnumber=-1;
        try {
            reader = new LineNumberReader(new FileReader(resultsFile));
            for (int i=0;i<4;i++) {
                String line = reader.readLine();
                log.debug(line);
                if (line.startsWith("Approximately")) i--;
            }

            for (String line; (line = reader.readLine()) != null;) {
                String docid = "";
                if (format == FORMAT_INDRI) {
                    String[] parts = line.split("\t");
                    if (parts.length != 4) {
                        log.debug("Missed a boundary. Skipping this line...");
                        continue;
                    }
                    docid = parts[1];
                } else if (format == FORMAT_TREC_EVAL) {
                    String parts[] = line.split(" ");
                    if (parts.length != 6) {
                        log.debug("Missed a boundary. Skipping this line...");
                        continue;
                    }
                    docid = parts[2];
                    int newQueryNumber = Integer.parseInt(parts[0]);
                    if (newQueryNumber - queryNumber > 1) {
                        if (log.isInfoEnabled()) {
                            for (int i=queryNumber+1; i<newQueryNumber; i++) {
                                log.info("Likely malformed query: "+ (i < this.getQueries().size() ? this.getQuery(i) : i));
                            }
                        }
                    }
                    queryNumber = newQueryNumber;
                    docnumber = Integer.parseInt(parts[3]);
                }

                Snippet snippet = new Snippet();
                snippet.setCacheURL(String.format(CATB_CACHE_BASEURL,docid));
                snippet.setTitle(docid);
                if (fulltext) {
                    log.debug("Parsing document "+docid+"; #"+docnumber+" for query "+queryNumber);
                    parseWARC(reader,snippet,queryNumber);
                } else {
                    snippet.setPageURL(String.format(CATB_CACHE_BASEURL,docid));
                }
                if (snippet.getTitle().getText() == docid)
                    snippet.setTitle("(unknown; fetch from local cache)");
                snippet.setRank(docnumber);
                snippet.setIndex(queryNumber);

                snippets.add(snippet);

                Set<Snippet> qsnippets=null;
                if (queryNumber < snippetsByQuery.size())
                    qsnippets = snippetsByQuery.get(queryNumber);
                else {
                    log.debug("Snippet groups not initialized? Adding query group for query "+queryNumber);
                    while (snippetsByQuery.size() < queryNumber) snippetsByQuery.add(new HashSet<Snippet>());
                    qsnippets = new HashSet<Snippet>();
                    snippetsByQuery.add(queryNumber,qsnippets);
                }
                qsnippets.add(snippet);
            }
        } catch (IOException e) {
            log.error(e);
        }

        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("Added snippets:");
            for (int i=0;i<snippetsByQuery.size();i++) {
                sb.append("\n\t(").append(i).append(", ").append(snippetsByQuery.get(i).size()).append(")");
            }
            sb.append("\nTotal: ").append(snippets.size());
            log.debug(sb.toString());
        } 

        if (!keepResponseFile)
            resultsFile.delete();
    }

    public String getQuery(int queryNumber) {
        return this.getQueries().get(queryNumber);
    }

    /** Get all the documents returned by this request, regardless of query **/
    public DocumentSet getDocuments() {
        if (this.snippetsByQuery.size() > 1)
            log.warn("WARNING: Returning documents from "+this.snippetsByQuery.size()+" different queries as if they were a single query.");
        return loadFromDisk(snippets); 
    }
    /** Get the documents returned by a particular query inside the batch **/
    public DocumentSet getDocuments(int queryNumber) {
        if (diskMode) return loadFromDisk(queryNumber);
        return documents.get(queryNumber);
    }

    /** (Disk mode) Retrieve from disk the set of documents returned by a particular query **/
    private DocumentSet loadFromDisk(int queryNumber) {
        return loadFromDisk(this.snippetsByQuery.get(queryNumber));
    }
    /** (Disk mode) Retrieve from disk an arbitrary set of documents represented by a set of snippets **/
    private DocumentSet loadFromDisk(Set<Snippet> snippets) {
        DocumentSet result = new DocumentSet();
        for (Snippet s: snippets) {
            Document d = new Document(WebManager.readFromCache(s.getPageURL(), getCacheDir()),s.getPageURL());
            d.setSnippet(s);
            result.add(d);
        }
        return result;
    }

    /** Set the format of the batch results; use FORMAT_INDRI or FORMAT_TREC_EVAL. **/
    public void setFormat(String format) {
        this.format = format;
    }

    public void setEncodeQueries(boolean eq) {
        this.encodeQueries = eq;
    }

    @Override
    public String getEncodedQuery() {
        StringBuilder sb = new StringBuilder();
        for (String q : this.getQueries()) {
            if (sb.length()>0) sb.append(" ");
            sb.append(q);
        }
        return sb.toString();
    }

    protected static String sanitize(String keyword) {
        return keyword.replaceAll("['.!()]", "").replaceAll("[/,&+:]", " ").trim();
    }

    @Override
    /** ClueWeb query keywords are never enclosed in quotes. **/
    public void addQuery(String query, boolean addQuote) {
        if (query == null) return;
        super.addQuery(String.format(" #1(%s)",sanitize(query)),false);
    }

    /** Add a batch-style query (made of a list of seed strings) **/
    public void addQuery(String ... query) {
        if (query.length == 0) 
            addQuery("",false);
        StringBuilder sb = new StringBuilder();
        for (String q : query) {
            sb.append(String.format(" #1(%s)",sanitize(q)));
        }
        super.addQuery(sb.substring(1),false);
    }
    public void addQuery(EntityList query) {
        if (query.size() == 0) 
            addQuery("",false);
        StringBuilder sb = new StringBuilder();
        for (Entity e : query) {
            for (String q : e.getNames()) {
                sb.append(String.format(" #1(%s)",sanitize(q)));
            }
        }
        super.addQuery(sb.substring(1),false);
    }

    @Override
    /**
     * Not in use -- ClueWeb should be used with fetchFromWeb=false to prevent the webfetcher
     * from using URL-based fetching & search.
     */
    protected String getSearchURL(int numResultsForThisPage, int pageNum, String query) {
        StringBuilder url = new StringBuilder(WIKIPEDIA_BASEURL);
        url.append("lemur.cgi?d=0");
        url.append("&s=").append(pageNum*numResultsForThisPage);
        url.append("&n=").append(numResultsForThisPage);
        url.append("&q=").append(query);
        return url.toString();
    }

    @Override
    protected void buildSnippets(String resultPage) {
        throw new UnsupportedOperationException("ClueWebSearcher does not use the method signature buildSnippets(String):void; something has gone horribly wrong");
    }

    /** 
     * Utility class to monitor response retrieval, so that we can
     * retry the request if the response hangs for too long.
     * @author krivard
     *
     */
    class ResponseReaderThread implements Runnable {
        InputStream fromServer;
        OutputStream toFile;
        boolean hasTimedOut = false;
        boolean hasFinishedHeader = false;
        int bytes=0;
        public ResponseReaderThread(InputStream in, OutputStream out) {
            fromServer = in;
            toFile = out;
        }
        @Override
        public void run() {
            try {
                for (int ch; (ch=fromServer.read()) != -1; bytes++) {
                    if ((char)ch == '!') hasFinishedHeader = true;
                    if (!hasFinishedHeader && hasTimedOut) {
                        fromServer.close(); toFile.close();
                        log.debug("Timed out! I/O streams closed.");
                        return;
                    }
                    toFile.write(ch);
                }
                log.debug("Finished writing file");
            } catch (IOException e) {
                log.error("Couldn't reach clueweb-resp file.");
            }
        }
    }
}
