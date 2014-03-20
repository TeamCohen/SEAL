package com.rcwang.seal.rank;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.fetch.BingAPISearcher;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.Helper;


public class WrapperLengthRankerTest {

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
        
        Set<String> seeds = new TreeSet<String>();
        Collections.addAll(seeds, "bears","bees","birds","dogs");
        Seal s;
        
        for (Feature f : Feature.values()) {
            File outfile = new File(String.format("%s.%s.txt",f.name(),Helper.getUniqueID()));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outfile));
            
            s = new Seal();
            s.setFeature(f);
            s.expand(new EntityList(seeds));
            
            writer.write(s.getEntityList().toDetails(100));
            writer.close();
        }
    }
}
