package com.rcwang.seal.util;
import static org.junit.Assert.*;

import org.junit.Test;

import com.rcwang.seal.fetch.WebFetcher;

public class HelperTest {
    @Test
    public void testToBinaryArray() {
        //   public static boolean[] toBinaryArray(int integer, int size) {
        boolean[] result = null;
        try {
            result = Helper.toBinaryArray(5,3);
            assertTrue("5: Yahoo",result[WebFetcher.ENGINE_YAHOO_API]);
            assertTrue("5: Google Web",result[WebFetcher.ENGINE_GOOGLE_WEB]);
            result = Helper.toBinaryArray(1,3); // should be yahoo 
            assertTrue("1: Yahoo",result[WebFetcher.ENGINE_YAHOO_API]);
            result = Helper.toBinaryArray(2,3); // should be google api
            assertTrue("2: Google API",result[WebFetcher.ENGINE_GOOGLE_API]);
            result = Helper.toBinaryArray(4,3); // should be google web
            assertTrue("4: Google Web",result[WebFetcher.ENGINE_GOOGLE_WEB]);
            result = Helper.toBinaryArray(5,4); // results shouldn't change just because you add more binary places
            assertTrue("5: Yahoo",result[WebFetcher.ENGINE_YAHOO_API]);
            assertTrue("5: Google Web",result[WebFetcher.ENGINE_GOOGLE_WEB]);
            result = Helper.toBinaryArray(16, 5);
            assertTrue("16: Bing",result[WebFetcher.ENGINE_BING_API]);
            assertFalse("16: !Yahoo",result[WebFetcher.ENGINE_YAHOO_API]);
            assertFalse("16: !Google Web",result[WebFetcher.ENGINE_GOOGLE_WEB]);
        } catch(AssertionError e) {
            for (int i=0; i<result.length; i++) {
                System.out.println(i+": "+result[i]);
            }
            throw e;
        }
    }
}
