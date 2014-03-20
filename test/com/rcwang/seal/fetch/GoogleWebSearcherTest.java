package com.rcwang.seal.fetch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;
import org.junit.BeforeClass;

import com.rcwang.seal.fetch.GoogleWebSearcher;
import com.rcwang.seal.fetch.WebSearcher;
import com.rcwang.seal.util.GlobalVar;



public class GoogleWebSearcherTest {
    @BeforeClass
    public static void setupLogging() throws FileNotFoundException, IOException {
        Properties logging = new Properties();
        logging.load(new FileInputStream(new File("config/log4j.properties")));
        PropertyConfigurator.configure(logging);
    }
    
    @Test
    public void testRun() throws FileNotFoundException, IOException {
        WebSearcher s = new GoogleWebSearcher();
        s.addQuery("bees");
        GlobalVar.getGlobalVar().setTimeOutInMS(1000 * 60 * 10); // 10 minutes
        s.run();
        System.out.println(s.getSnippets().size()+" snippets found");
        assertTrue(s.getSnippets().size() > 0);
    }
    
    /**
     * Extractions: Last updated 9 March 2012 kmr
     */
    @Test
    public void testExtractions() {
        String snippet = "<li class=\"g\"><h3 class=\"r\"><a href=\"%s\">Authorities f"+
            "oil planned suicide <b>bombing attack</b> on Capitol building <b>...</b></a></h3><div class=\"s"+
            "\">Feb 17, 2012 <b>...</b> authorities arrested a suspect on his way to the Capitol. A potentia"+
            "l suicide <br>  <b>bombing attack</b> of Congress was thwarted Friday when authorities <b>...</"+
            "b><br><div><cite>thehill.com/.../211447-authorities-foil-planned-suicide-<b>bombing</b>-<b>atta"+
            "ck</b>-on-<wbr>capitol-building</cite><span class=\"flc\"> - <a href=\"//webcache.googleusercon"+
            "tent.com/search?hl=en&amp;safe=off&amp;filter=1&amp;num=100&amp;lr=&amp;q=cache:0V7H9wTUapcJ:ht"+
            "tp://thehill.com/homenews/news/211447-authorities-foil-planned-suicide-bombing-attack-on-capito"+
            "l-building+%%22bombing%%22+%%22attack%%22&amp;ct=clnk\">Cached</a> - <a href=\"/search?hl=en&amp;sa"+
            "fe=off&amp;filter=1&amp;num=100&amp;lr=&amp;tbo=1&amp;q=related:http://thehill.com/homenews/new"+
            "s/211447-authorities-foil-planned-suicide-bombing-attack-on-capitol-building+%%22bombing%%22+%%22a"+
            "ttack%%22&amp;sa=X\">Similar</a></span></div></div></li>";
        String[] urls = {
            "/url?q=http://thehill.com/homenews/news/"+
            "211447-authorities-foil-planned-suicide-bombing-attack-on-capitol-building&amp;sa=U&amp;ei=sFxa"+
            "T_q8Hsq40QHavt3DDw&amp;ved=0CB4QFjAA&amp;usg=AFQjCNEQBqIRUVBUaods7SI3zt9bgHeNIg",
            "http://www.movietheater.com/videos/video/pz72kLw7oSc/The-Life-Before-Her-Eyes-87=L-?5@54-55-3;070<8.html",
            "http://www.tjoos.com/Coupon/112576/Pied-Piper-Quilt-Shop-&amp;-Lady-Slipper-Needle-Craft",
            "http://www.johnsbargainstores.com/Girls%20&%20Teen%20Girls.htm"
        };
        String[] endsWith = {
                "-building",
                ".html",
                "-Craft",
                ".htm"
        };
        for (int i=0; i<urls.length; i++) {
            GoogleWebSearcher searcher = new GoogleWebSearcher();
            searcher.buildSnippets(String.format(snippet,urls[i]));
            assertEquals("Couldn't parse URL "+i,1,searcher.snippets.size());
            URL url = searcher.snippets.iterator().next().getPageURL();
            assertNotNull("Null URL",url);
            String surl = url.toExternalForm();
            System.out.println(surl);
            assertTrue("Must end with "+endsWith[i],surl.endsWith(endsWith[i]));
        }
    }
}
