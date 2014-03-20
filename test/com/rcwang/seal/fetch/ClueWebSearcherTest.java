package com.rcwang.seal.fetch;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.expand.SetExpander;
import com.rcwang.seal.fetch.ClueWebSearcher;
import com.rcwang.seal.fetch.WebSearcher;
import com.rcwang.seal.rank.Graph;
import com.rcwang.seal.rank.GraphRanker;
import com.rcwang.seal.rank.Graph.Node;
import com.rcwang.seal.rank.Graph.NodeSet;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.StringFactory;


import static org.junit.Assert.*;

public class ClueWebSearcherTest {
    private static final Logger log = Logger.getLogger(ClueWebSearcherTest.class);
    
    @BeforeClass
    public static void setupLogging() throws FileNotFoundException, IOException {
        Properties logging = new Properties();
        logging.load(new FileInputStream(new File("config/log4j.properties")));
        PropertyConfigurator.configure(logging);
        Logger.getLogger(ClueWebSearcher.class).setLevel(Level.DEBUG);
        log.setLevel(Level.DEBUG);
        Logger.getLogger(ClueWebSearcher.class).debug("Testing debug log");
    }
    
    @Test
    public void testNPE() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("../OntologyLearner/conf/seal.properties"));
        gv.load(prop);
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TMP_DIR,"/tmp/");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_HEADER_TIMEOUT_MS,"20000");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TIMEOUT_REPORTING_MS,"5000");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TIMEOUT_NUMTRIALS,"10");
        gv.setStopwordsList(new File("lib/stopwords.txt"));
        gv.setClueWebKeepQueryFile(true);
        gv.setClueWebKeepResponseFile(true);
        
        Seal s = new Seal();
        EntityList el = new EntityList();
        String[] seeds =  ("Gm, Bmw, KIA, TVR, kia, Benz, ACURA, Acura, DODGE, E-M-F, Lexus, VOLVO, Vashon, jaguar, suzuki, AvtoVAZ, AvtoVaz, HYUNDAI, PEUGEOT, "+
"Porsche, Renault, Sanchis, avtovaz, citroen, peugeot, CORVETTE, Motobloc, cadillac, motobloc, CHEVROLET, Land rover, volkswagen, mercedes clc, nissan motor,"+ 
" Austro Daimler, AMERICAN MOTORS, Mercedes SLR-Class, Mitsubishi Evolander, mercedes used canada, mercedes_benz_cars_usa, mitsubishi galant lambda, MITSUBISHI"+
" FTO Fan Clutch, Mitsubishi Motors Company, mercedes e class 2010 new, mercedes_benz_sprinter_3_t, Mercedes R63 AMG and ML63 AMG, mitsubishi fto petrol fuel"+ 
" tank, MITSUBISHI FTO Front Bumper Guard").split(", ");
        for (String seed : seeds) { el.add(seed); }
        s.expand(el);
    }
    
    @Test
    public void testCachedOLRelations() throws FileNotFoundException, IOException {
        GlobalVar gv = ontologyLearnerProperties();
        ClueWebSearcher s = new ClueWebSearcher();
        s.buildSnippets(new File("/tmp/clueweb-resp_30299.txt"));
        
        EntityList el = new EntityList();
        el.add(new Entity("Steelers","Pittsburgh"));
        el.add(new Entity("Seahawks","Seattle"));
        el.add(new Entity("Bears","Chicago"));
        
        for (Feature f : Feature.values()) {
            gv.setFeature(f);
            Seal seal = new Seal();
            seal.expand(el, s.getDocuments(0));
            EntityList newEntities = seal.getEntityList();
            if (seal.getRanker() instanceof GraphRanker) 
                ontologyLearnerScoring(newEntities, ((GraphRanker) seal.getRanker()).getGraph());
            System.out.println(newEntities.toDetails(50));
            assertTrue(f+" Nonrelational! :(",newEntities.iterator().next().isRelational());
        }
        System.out.println("Complete!");
    }
    
    public GlobalVar ontologyLearnerProperties() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("../OntologyLearner/conf/seal.properties"));
        gv.load(prop);
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TMP_DIR,"/tmp/");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_HEADER_TIMEOUT_MS,"60000");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TIMEOUT_REPORTING_MS,"5000");
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TIMEOUT_NUMTRIALS,"10");
        gv.setStopwordsList(new File("lib/stopwords.txt"));
        gv.setClueWebKeepQueryFile(true);
        gv.setClueWebKeepResponseFile(true);
        return gv;
    }
    
    public void ontologyLearnerSeal(EntityList seeds) throws FileNotFoundException, IOException {
        ontologyLearnerProperties();
        
        Seal seal = new Seal();
        seal.expand(seeds);
        
        EntityList newEntities = seal.getEntityList();
        Graph graph = ((GraphRanker) seal.getRanker()).getGraph();

        ontologyLearnerScoring(newEntities,graph);

        System.out.println(newEntities.toDetails(50));
        System.out.println("Complete!");
    }
    
    public void ontologyLearnerScoring(EntityList newEntities, Graph graph) {
        for (Entity en : newEntities) {
            Node n = graph.getNode(StringFactory.toID(en.getName()));//content
            if (n == null) {
                continue;
            }
            for (String edgelabel : graph.getEdges(n)) {
                NodeSet ds = graph.followEdge(n, edgelabel);
                for (Node d : ds) {
                    if (!d.getType().equals(Graph.DOCUMENT_NODE_NAME)) continue;
                    System.out.print(".");
                    //System.out.println(en+": "+d.getName());
//                    Instance in = instanceFromEntity(
//                            entityInstances.get(en), 
//                            en, 
//                            d.getName(), 
//                            1.,
//                            "");
//                    if (!entityInstances.containsKey(en)) entityInstances.put(en,in);
                }
            }
        }
    }
    
    @Test
    public void testNPEAgain() throws FileNotFoundException, IOException {

        EntityList el = new EntityList();
        
        LineNumberReader reader = new LineNumberReader(new FileReader("../OntologyLearner/endless.troubleshooting/npe-query.txt"));
        String line = "";
        while (!line.startsWith("<text>")) assertNotNull(line = reader.readLine());
        line = line.substring(7, line.length()-8);
        String[] seeds = line.split("\\) #");
        for (String seed : seeds) { 
            String s = seed.replaceAll("1\\(","");
            log.debug(s); 
            el.add(s); 
        }
        
        ontologyLearnerSeal(el);
    }
    
    @Test
    public void testOLRelations() throws FileNotFoundException, IOException {
        EntityList el = new EntityList();
        el.add(new Entity("Steelers","Pittsburgh"));
        el.add(new Entity("Seahawks","Seattle"));
        el.add(new Entity("Bears","Chicago"));
        ontologyLearnerSeal(el);
    }
    
    @Test
    public void testPostTimeout() {
        ArrayList<String> querySet = new ArrayList<String>();
        querySet.add("#1(iguanas) #1(tortoises) #1(alligators)");
        querySet.add("#1(scarves) #1(mittens) #1(ear muffs)");
        querySet.add(" #1(Washington State University) #1(University of Oxford) #1(University of Pittsburgh)");
//        querySet.add("#1(robin) #1(chickadee) #1(finch)");
//        querySet.add("#1(red) #1(blue) #1(green)");
//        querySet.add("#1(boston) #1(new york) #1(san francisco) #1(seattle)");
        
        GlobalVar gv = GlobalVar.getGlobalVar();
        gv.getProperties().setProperty("clueweb.headerTimeout", "11500");
        ClueWebSearcher s = new ClueWebSearcher();
        File resp = s.sendBatchQuery(querySet);
//        resp = s.sendBatchQuery(querySet);
//        resp = s.sendBatchQuery(querySet);
    }
    
    @Test @Ignore
    public void testRun() throws FileNotFoundException, IOException {

        ClueWebSearcher s = new ClueWebSearcher();
        s.addQuery("birds");
        s.run();
        assertEquals(100,s.getSnippets().size());
    }

    @Test
    public void testCachedSearch() throws FileNotFoundException, IOException {


        ClueWebSearcher s = new ClueWebSearcher();
        s.setFulltext(FULLTEXT_ON);
        s.setDiskMode(true);
        s.setMemoryManagement(true);
        s.setKeepResponseFile(true);
        s.setFormat(ClueWebSearcher.FORMAT_TREC_EVAL);
        
        s.buildSnippets(new File("/tmp/clueweb-resp_30299.txt"));
        File cachedQuery = new File("/tmp/clueweb-query_30298.txt");

        //      File cachedQuery = new File("../OntologyLearner/bostonPerformanceStudy/categoryQuery.txt");
        List<EntityList> queryseeds = new ArrayList<EntityList>();
        BufferedReader reader = new BufferedReader(new FileReader(cachedQuery));
        for (String line; (line = reader.readLine()) != null;) {
            if (line.startsWith("<text>")) {
                String[] seeds = line.substring(6,line.length()-7).split(" ");
                EntityList query = new EntityList();
                for (String seed : seeds) { if (seed.length() < 4) continue;
                    query.add(new Entity(seed.substring(3,seed.length()-1)));
                }
                queryseeds.add(query);
            }
        } reader.close();

        int i=0;
        for (String q : s.getQueries()) {
            Seal seal = new Seal();
            DocumentSet ds = s.getDocuments(i);
            assertTrue("DocuemntSet size "+ds.size()+"exceeds limit of 100",ds.size() <= 100);
            seal.expand(queryseeds.get(i), ds);
            i++;
        }
        System.out.println("Stopping for heap dump");
    }

    @Test
    public void testBuildSnippets() throws FileNotFoundException, IOException {

        ClueWebSearcher s = new ClueWebSearcher();
        s.setFulltext(FULLTEXT_ON);
        s.setMemoryManagement(true);
        s.setKeepResponseFile(true);
        s.setFormat(ClueWebSearcher.FORMAT_INDRI);
        s.buildSnippets(new File("cluewebTestQueryResponse.txt"));
        assertEquals(100,s.getSnippets().size());
    }

    @Test
    public void testBuildSnippets_trec() throws FileNotFoundException, IOException {
        ClueWebSearcher s = new ClueWebSearcher();
        s.setFormat(ClueWebSearcher.FORMAT_TREC_EVAL);
        s.setMemoryManagement(true);

        //        s.buildSnippets(new File("cluewebTestQueryResponse_trec.txt"));
        //        assertEquals(100,s.getSnippets().size());


        s.buildSnippets(new File("/tmp/clueweb-resp_47622txt"));
        assertEquals(7017,s.getSnippets().size());

    }

    @Test
    public void investigate_skipped_documents() throws FileNotFoundException, IOException {
        ClueWebSearcher s = new ClueWebSearcher();
        s.setFormat(ClueWebSearcher.FORMAT_TREC_EVAL);
        s.setMemoryManagement(true);

        s.buildSnippets(new File("cluewebTestQueryResponse_trec_failure.txt"));
        for (int i=0;i<97;i++) {
            System.out.println("Analyzing query "+i);
            DocumentSet q = s.getDocuments(i);
            if (q.size() == 0) continue;
            if (q.size() != 100) {
                int[] ranks = new int[100];
                for (int d=0;d<q.size();d++) { ranks[d] = q.get(d).getSnippet().getRank(); }
                Arrays.sort(ranks);
                int j=1;
                for (int d=0;d<q.size() && j<ranks.length;d++) {
                    while (ranks[d] != j) {
                        System.out.println("\tMissing "+j+" (seeking "+ranks[d]+")");
                        j=ranks[d];
                    }
                    j++;
                }
                break;
            }
            assertEquals(100,q.size());
        }
        assertEquals(100,s.getSnippets().size());
    }

    @Test
    public void testOnlineSeal() throws FileNotFoundException, IOException {

        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("config/seal.properties.online"));
        gv.load(prop);
        gv.setUseEngine(8); gv.setFeature(Feature.GWW);
        gv.setIsFetchFromWeb(false);
        gv.setClueWebMemoryManagement(false);
        gv.setClueWebKeepResponseFile(true);
        gv.setClueWebKeepQueryFile(true);
        gv.getProperties().setProperty(GlobalVar.CLUEWEB_TMP_DIR, "tmp");

        SetExpander seal = new Seal();
        EntityList seeds = new EntityList();
        seeds.add("Steelers"); seeds.add("Ravens"); seeds.add("Patriots");
        seal.expand(seeds);

        List<Entity> results = seal.getEntities();
        assertTrue("No results!",results.size() > 0);
        System.out.println(seal.getEntityList().toDetails(10));
    }

    static final boolean FULLTEXT_ON=true;
    static final boolean FULLTEXT_OFF=false;

    @Test
    public void testPiecewiseOnlineSeal() throws FileNotFoundException, IOException {

        ArrayList<String[]> categories = new ArrayList<String[]>();
        categories.add(new String[] {"Steelers","Ravens","Buccaneers"});
        categories.add(new String[] {"bracelet","ring","necklace"});
        categories.add(new String[] {"Pennsylvania","Virginia","Ohio"});

        ClueWebSearcher s = runOnlineTest(categories, FULLTEXT_ON);

        for (int i=0;i<categories.size();i++) {
            assertTrue("Expected at least 1 document for query '"+s.getQuery(i)+"'",s.getDocuments(i).size()>0);
        }
    }

    @Test
    public void testPathologicalSeedsOnline() throws FileNotFoundException, IOException {
        ArrayList<String[]> categories = new ArrayList<String[]>();
        categories.add(new String[] {"Baccarat","Battleship","Blackjack","Blind Man ' s Bluff"});
        categories.add(new String[] {"Avril Lavigne","Dan Aykroyd","Jim Carrey","Michael J . Fox","Bernardo O'Higgins"});
        categories.add(new String[] {"pop","punk","r&b","rap","reggae"});
        categories.add(new String[] {"U.S. Senate Majority Leader","U.S. Supreme Court Justice","Defence Secretary"});
        categories.add(new String[] {"C++","COBOL","FORTRAN","LISP"});
        categories.add(new String[] {"4th of July, Asbury Park","America the Beautiful","Being for the Benefit of Mr. Kite!"});

        ClueWebSearcher s = runOnlineTest(categories, FULLTEXT_ON);

        for (int i=0;i<categories.size();i++) {
            assertTrue("Expected at least 1 document for query '"+s.getQuery(i)+"'",s.getDocuments(i).size()>0);
        }

    }

    protected ClueWebSearcher runOnlineTest(List<String[]> categories, boolean usefulltext) throws FileNotFoundException, IOException {
        
        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("config/seal.properties.online"));
        gv.load(prop);
        gv.setUseEngine(8);
        gv.setIsFetchFromWeb(false);

        long start = System.currentTimeMillis();

        ClueWebSearcher searcher = new ClueWebSearcher(false);
        searcher.setFulltext(usefulltext);


        for (String[] cat : categories) {
            searcher.addQuery(cat);
        }
        searcher.run();

        long retrieve = System.currentTimeMillis();
        System.out.println("Time: "+((double)(retrieve - start))/1000+"s");

        for (int i=0; i<categories.size(); i++) {
            String[] cat = categories.get(i);
            EntityList seeds = new EntityList();
            for (String s : cat) seeds.add(s);
            Seal seal = new Seal();
            seal.expand(seeds, searcher.getDocuments(i));

            EntityList results = seal.getEntityList();
            //            assertTrue("No results for query "+searcher.getQueries().get(i),results.size() > 0);
            System.out.println(results.toDetails(10));
        }

        long end = System.currentTimeMillis();
        System.out.println("Retrieval: "+((double)(retrieve - start))/1000+"s");
        System.out.println("Expansion: "+((double)(end - retrieve))/1000+"s");
        System.out.println("Total:     "+((double)(end - start))/1000+"s");

        return searcher;
    }
}
