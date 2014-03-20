package com.rcwang.seal.fetch;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.util.GlobalVar;


public class BingAPISearcherTest {
    @BeforeClass
    public static void initLog() throws FileNotFoundException, IOException {
        Properties logging = new Properties();
        logging.load(new FileInputStream(new File("config/log4j.properties")));
        PropertyConfigurator.configure(logging);
        Logger.getLogger(BingAPISearcher.class).debug("Testing debug log");
    }
    
    @Test
    public void testOnline() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("config/seal.properties.bing"));
        gv.load(prop);
        
        Seal s = new Seal();
        List<String> el = new ArrayList<String>(); Collections.addAll(el,"birds","fish","bears","deer");
        s.expand(new EntityList(el));
        assertTrue(s.getEntityList().size() > 0);
    }
    
    @Test
    public void testQPS() throws FileNotFoundException, IOException, InterruptedException {
        BingAPISearcher s = new BingAPISearcher();
        for (int i=0;i<7;i++) {
            System.out.println(System.currentTimeMillis());
            s.getSearchURL(10, 1, "birds");
        }
        
        System.out.println("\nNow requesting at 170ms\n");
        
        for (int i=0;i<8;i++) {
            System.out.println(System.currentTimeMillis());
            Thread.sleep(170);
            s.getSearchURL(10, 1, "birds");
        }
    }
    
    @Test
    public void testRun() throws FileNotFoundException, IOException {
        
        GlobalVar gv = GlobalVar.getGlobalVar();
        Properties prop = new Properties();
        prop.load(new FileInputStream("config/seal.properties.bing"));
        gv.load(prop);
        
        BingAPISearcher s = new BingAPISearcher();
        s.addQuery("birds");
        s.run();
        assertEquals(100,s.getSnippets().size());
    }
    
    @Test
    public void testParse() throws FileNotFoundException, IOException {
        BingAPISearcher s = new BingAPISearcher();
        String response = getCachedOrLiveResult(s);
        s.buildSnippets(response);
        Set<Snippet> snippets = s.getSnippets(); 
        assertEquals(10,snippets.size());
        for (Snippet sn : snippets) {
            System.out.println(String.format("%71s\t%s",sn.getTitle().getText().trim(), sn.getPageURL().toString()));
        }
    }
    
    public String getCachedOrLiveResult(BingAPISearcher s) throws IOException {
        File bingcache = new File("bingcache.txt");
        
        if (!bingcache.exists()) return initializeBingFile(s);
        
        StringWriter writer = new StringWriter();
        FileReader reader = new FileReader(bingcache);
        for (int i; (i=reader.read()) != -1;) { writer.write(i); }
        reader.close();
        
        return writer.toString();
    }
    
    public String initializeBingFile(BingAPISearcher s) throws MalformedURLException {
        WebManager mng = new WebManager();
        return mng.get(new URL(s.getSearchURL(10, 1, "birds")));
    }
}
