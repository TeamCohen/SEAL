package com.rcwang.seal.asia;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.rcwang.seal.fetch.BingAPISearcher;
import com.rcwang.seal.util.GlobalVar;


public class WrapperSavingAsiaTest {

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
        prop.load(new FileInputStream("config/seal.properties.asia"));
        gv.load(prop);
        
        String[] input = {"countries"};
        WrapperSavingAsia.main(input);
    }
}
