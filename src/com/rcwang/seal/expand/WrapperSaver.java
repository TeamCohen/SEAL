/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 *
 * ... and William Cohen (wcohen@cs.cmu.edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.io.File;
import java.net.URL;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.rcwang.seal.util.XMLUtil;
import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.StringFactory;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.WebManager;
import com.rcwang.seal.util.StringEncoder;


public class WrapperSaver {
    public static Logger log = Logger.getLogger(WrapperSaver.class);

    private static StringEncoder encoder = new StringEncoder('%',"\"\t\n\r");

    public static StringEncoder getEncoder() { return encoder; }

    private double minWrapperScore = 0.5;        // only save wrappers if score of all extractions is at least this tall
    private int minWrapperContextLength = 3;     // only save wrappers if |left+right| is at least this tall
    private double minExtractionScore = 0.01;    // only save extractions if score is at least this tall

    public double totalScore(Wrapper wrapper,EntityList entityList,EntityList lastSeeds) {
        double tot = 0.0;
        for (EntityLiteral content : wrapper.getContents()) {                
            Entity e = entityList.get(content);
            if (e!=null) tot += e.getScore();
        }
        tot -= lastSeeds.size();
        return tot;
    }

    public boolean isWorthy(Wrapper wrapper,double totalScore)  {
        return totalScore>=minWrapperScore && wrapper.getLeft().length()+wrapper.getRight().length()>=minWrapperContextLength;
    }

    /* note: EntityList should be scored before doing this, otherwise totalScore can't be computed
     */
    public void saveAsDocuments(File wrapperCache, DocumentSet lastDocs, EntityList entityList, EntityList lastSeeds) {
        for (Document document : lastDocs) {
            for (Wrapper wrapper : document.getWrappers()) {
                double totalScore = totalScore(wrapper,entityList,lastSeeds);
                if (isWorthy(wrapper,totalScore)) {
                    StringBuffer buf = new StringBuffer("");
                    buf.append(wrapper.getURL().toString());
                    buf.append("\t");
                    buf.append(safe(wrapper.toString()));
                    buf.append("\n");
                    for (EntityLiteral content : wrapper.getContents()) {
                        Entity e = entityList.get(content);                        
                        if (e!=null && e.getScore()>=minExtractionScore) {
                            buf.append(safe(wrapper.getLeft()));
                            buf.append("\t");
                            buf.append(safe(content.toString()));
                            buf.append("\t");
                            buf.append(safe(wrapper.getRight()));
                            buf.append("\n");
                        }
                    }
                    WebManager.writeToCache(uniqueURLFor(wrapper),buf.toString(),wrapperCache);
                }
            }
        }
    }

    public URL uniqueURLFor(Wrapper wrapper) {
        try {
            return new URL(wrapper.getURL() + "?wrapper=" + Integer.toString(wrapper.hashCode()));
        } catch (java.net.MalformedURLException ex) {
            log.error("can't create url for "+wrapper+" with url "+wrapper.getURL());
            return wrapper.getURL();
        }
    }

    private String safe(String s) {
        return encoder.encode(s);
    }

    /* note: EntityList should be scored before doing this, otherwise totalScore can't be computed
     */
    public Element toXMLElement(org.w3c.dom.Document xmlDocument, DocumentSet lastDocs, EntityList entityList, EntityList lastSeeds) {

        XMLUtil xml = new XMLUtil(xmlDocument);

        Element wrapperListNode = xml.createElement("wrapperList", null);
        for (Document document : lastDocs) {
            for (Wrapper wrapper : document.getWrappers()) {
                double totalScore = totalScore(wrapper,entityList,lastSeeds);
                if (isWorthy(wrapper,totalScore)) {
                    Element wrapperNode = xml.createElementBelow(wrapperListNode, "wrapper", null);
                    List<Object> pairs = new ArrayList<Object>();
                    pairs.add("left");
                    pairs.add(wrapper.getLeft());
                    pairs.add("right");
                    pairs.add(wrapper.getRight());
                    pairs.add("URL");
                    pairs.add(wrapper.getURL());
                    pairs.add("extractionWeight");
                    pairs.add(Double.toString(totalScore));
                    xml.createAttrsFor(wrapperNode, pairs.toArray(new Object[pairs.size()]));
                    for (EntityLiteral content : wrapper.getContents()) {
                        Entity e = entityList.get(content);                        
                        if (e!=null && e.getScore()>=minExtractionScore) {
                            xml.createElementBelow(wrapperNode,"extraction",content.toString());
                        }
                    }
                }
            }
        }
        return wrapperListNode;
    }
}

