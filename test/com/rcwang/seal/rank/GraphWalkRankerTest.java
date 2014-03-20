package com.rcwang.seal.rank;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;

import static org.junit.Assert.*;

public class GraphWalkRankerTest {

    @BeforeClass
    public static void initLog() throws FileNotFoundException, IOException {
        Properties logging = new Properties();
        logging.load(new FileInputStream(new File("config/log4j.properties")));
        PropertyConfigurator.configure(logging);
    }
    
    @Test
    public void testLowerCase() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        gv.load("config/seal.properties.clueweb");
        gv.setFeature(Feature.GWW);
        
        Seal s = new Seal();
        Set<String> seeds = new TreeSet<String>();
        Collections.addAll(seeds, "wean hall","doherty hall", "margaret morrison");
        s.expand(new EntityList(seeds));
        EntityList el = s.getEntityList();
        double topWeight = el.get(0).getWeight(Feature.GWW);
        assertTrue("Lower case: wanted nonzero got "+topWeight,topWeight > 0);
    }
    
    @Test
    public void testTitleCase() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        gv.load("config/seal.properties.clueweb");
        gv.setFeature(Feature.GWW);
        
        Seal s = new Seal();
        Set<String> seeds = new TreeSet<String>();
        Collections.addAll(seeds, "Wean Hall","Doherty Hall", "Margaret Morrison");
        s.expand(new EntityList(seeds));
        EntityList el = s.getEntityList();
        double topWeight = el.get(0).getWeight(Feature.GWW);
        assertTrue("Title case: wanted nonzero got "+topWeight,topWeight > 0);
    }
}
