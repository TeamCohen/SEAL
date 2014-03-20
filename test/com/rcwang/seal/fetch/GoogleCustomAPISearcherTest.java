package com.rcwang.seal.fetch;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Seal;
import com.rcwang.seal.util.GlobalVar;

public class GoogleCustomAPISearcherTest {
    @BeforeClass
    public static void initLog() throws FileNotFoundException, IOException {
        Properties logging = new Properties();
        logging.load(new FileInputStream(new File("config/log4j.properties")));
        PropertyConfigurator.configure(logging);
        Logger.getLogger(GoogleCustomAPISearcher.class).debug("Testing debug log");
    }

    @Test
    public void testOnline() throws FileNotFoundException, IOException {
        GlobalVar gv = GlobalVar.getGlobalVar();
        gv.load("config/seal.properties.googlecustom");
        
        Seal s = new Seal();
        List<String> el = new ArrayList<String>(); Collections.addAll(el,"birds","fish","bears","deer");
        s.expand(new EntityList(el));
        System.out.println(s.getEntityList().toDetails(20));
        assertTrue(s.getEntityList().size() > 0);
    }

}
