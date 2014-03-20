/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.rank.Ranker.Feature;
import com.rcwang.seal.util.XMLUtil;

public class DocumentSet implements Iterable<Document>, Serializable {
  
  public static final double DELTA = 1e-10;
  public static final int DEFAULT_MAX_DOCS_IN_XML = 5;
  
  private static final long serialVersionUID = 7211782411429869181L;
  private Map<Document, Document> docMap;
  private List<Document> docList;
  private Set<EntityLiteral> extractions;
  private Set<Wrapper> wrappers;
  private Feature scoredByFeature;
  private int maxDocsInXML;
  
  public DocumentSet() {
    setMaxDocsInXML(DEFAULT_MAX_DOCS_IN_XML);
    docMap = new HashMap<Document, Document>();
    docList = new ArrayList<Document>();
    extractions = new HashSet<EntityLiteral>();
    wrappers = new HashSet<Wrapper>();
  }
  
  public DocumentSet(DocumentSet documents) {
    this();
    addAll(documents);
  }
  
  public void add(Document document) {
    if (document == null) return;
    Document doc = docMap.get(document);
    if (doc == document) return;
    if (doc == null) {
      docMap.put(document, document);
      docList.add(document);
    } else {
      doc.merge(document);
    }
    wrappers.addAll(document.getWrappers());
    extractions.addAll(document.getExtractions());
  }
  
  public void addAll(DocumentSet documents) {
    if (documents == null) return;
    for (Document document : documents)
      add(document);
  }
  
  public void assignScore(Feature feature) {
    scoredByFeature = feature;
    if (docList.size() == 0) return;
    sort();
    double maxScore = docList.get(0).getWeight() + DELTA;
    double minScore = docList.get(docList.size()-1).getWeight() - DELTA;
    for (Document document : docList) {
      double weight = (document.getWeight() - minScore) / (maxScore - minScore);
      document.setWeight(weight);
    }
  }
  
  public void clear() {
    docMap.clear();
    docList.clear();
    extractions.clear();
    wrappers.clear();
  }
  
  public void clearExtractions() {
    for (Document document : this)
      document.clearExtractions();
    extractions.clear();
  }
  
  public void clearStats() {
    clearWrappers();
    clearExtractions();
  }
  
  /*public double getConfidence() {
    double w = 0;
    for (Document document : this) {
      if (document.length() == 0) continue;
      int sum = 0;
      for (Wrapper wrapper : document.getWrappers())
        sum += wrapper.length() * wrapper.getNumCommonTypes();
      w += (double) sum / document.length();
    }
    w = w / this.size();
    return w;
  }*/
  
  public void clearWrappers() {
    for (Document document : this)
      document.clearWrappers();
    wrappers.clear();
  }
  
  public Document get(int i) {
    return docList.get(i);
  }
  
  public double getContentQuality() {
    int numWrappers = getNumWrappers();
    if (numWrappers == 0) return 0;
    return getSumWrapperLength() / numWrappers;
  }
  
  public Set<EntityLiteral> getExtractions() {
    return extractions;
  }
  
  public int getMaxDocsInXML() {
    return maxDocsInXML;
  }
  
  public int getNumExtractions() {
    return getExtractions().size();
  }
  
  public int getNumWrappers() {
    return getWrappers().size();
  }
  
  public int getSumDocLength() {
    int sum = 0;
    for (Document document : this)
      sum += document.length();
    return sum;
  }
  
  public double getSumWrapperLength() {
    double sum = 0;
    for (Document document : this)
      for (Wrapper wrapper : document.getWrappers())
        sum += Math.log(wrapper.getContextLength()) * wrapper.getNumCommonTypes();
    return sum;
  }
  
  public Set<Wrapper> getWrappers() {
    return wrappers;
  }

  public Iterator<Document> iterator() {
    return docList.iterator();
  }
  
  public void setMaxDocsInXML(int numTopDocsInXML) {
    this.maxDocsInXML = numTopDocsInXML;
  }
  
  public int size() {
    return docList.size();
  }
  
  public void sort() {
    Collections.sort(docList);
  }
  
  public DocumentSet subList(int fromIndex, int toIndex) {
    DocumentSet docSet = new DocumentSet();
    docSet.docList = docList.subList(fromIndex, toIndex);
    for (Document document : docList)
      docSet.docMap.put(document, document);
    return docSet;
  }

  public String toXML() {
    return XMLUtil.document2String(toXMLElement(null));
  }

  public Element toXMLElement(org.w3c.dom.Document document) {
    XMLUtil xml = new XMLUtil(document);
    Element documentsNode = xml.createElement("documents", null);
    xml.createAttrsFor(documentsNode, new Object[]{
        "numDocuments", docList.size(),
        "scoredBy", scoredByFeature,
    });

    for (int i = 0; i < Math.min(docList.size(), maxDocsInXML); i++) {
      Document doc = docList.get(i);
      if (doc.getWeight() == 0) continue;
      Element documentNode = xml.createElementBelow(documentsNode, "document", null);
      xml.createAttrsFor(documentNode, new Object[]{
          "rank", i+1,
          "weight", doc.getWeight(),
      });
      String title = doc.getSnippet().getTitle().toString();
      String urlStr = doc.getSnippet().getPageURL().toString();
      xml.createElementBelow(documentNode, "title", title);
      xml.createElementBelow(documentNode, "url", urlStr);
    }
    return documentsNode;
  }

  protected Feature getScoredByFeature() {
    return scoredByFeature;
  }
}