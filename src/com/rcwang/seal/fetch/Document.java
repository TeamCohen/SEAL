/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.Serializable;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;

public class Document implements Comparable<Document>, Serializable {
  public static Logger log = Logger.getLogger(Document.class);
  private static final long serialVersionUID = -1407914809895275301L;
  
  private Set<Wrapper> wrappers;
  private Set<EntityLiteral> extractions;
  private Snippet snippet;
  private String text;
  private URL url;
  private double weight = 0;
  
  public Document(String text, URL url) {
    setText(text);
    setURL(url);
    wrappers = new HashSet<Wrapper>();
    extractions = new HashSet<EntityLiteral>();
  }
  
  public void addWrapper(Wrapper wrapper) {
    if (wrapper == null || wrapper.getContents().isEmpty()) return;
    wrapper.setURL(url);
    wrappers.add(wrapper);
    extractions.addAll(wrapper.getContents());
  }
  
  public void addWrappers(Set<Wrapper> wrappers) {
    for (Wrapper wrapper : wrappers)
      addWrapper(wrapper);
  }
  
  public void clearExtractions() {
    extractions.clear();
  }
  
  public void clearWrappers() {
    wrappers.clear();
  }
  
  public int compareTo(Document doc2) {
    return Double.compare(doc2.weight, weight);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final Document other = (Document) obj;
    if (url == null) {
      if (other.url != null) return false;
    } else if (!url.toString().equals(other.url.toString()))
      return false;
    return true;
  }
  
  public Set<EntityLiteral> getExtractions() {
    return extractions;
  }

  public Snippet getSnippet() {
    return snippet;
  }

  public String getText() {
    return text;
  }

  public URL getURL() {
    return url;
  }

  public double getWeight() {
    return weight;
  }
  
  public Set<Wrapper> getWrappers() {
    return wrappers;
  }

  @Override
  public int hashCode() {
    return 31 + (url == null ? 0 : url.toString().hashCode());  // added 01/27/2009
  }

  public boolean isEmpty() {
    return text == null || text.trim().length() == 0;
  }

  public int length() {
    return getText().length();
  }

  protected void merge(Document document) {
    if (document == null) return;
    if (!this.equals(document)) {
      log.warn("Documents cannot be merged since they are not equal!");
      return;
    }
    addWrappers(document.getWrappers());
    setSnippet(document.getSnippet());
    setURL(document.getURL());
  }

  public void setSnippet(Snippet snippet) {
    if (this.snippet == null)
      this.snippet = snippet;
    else if (snippet != null)
      this.snippet.merge(snippet);
  }
  
  public void setText(String text) {
    this.text = text;
  }

  public void setURL(URL url) {
    if (url == null) return;
    
    if (this.url == null) {
      this.url = url;
    } else if (this.url.toString().length() > url.toString().length()) {
      // prefer shorter URL
      this.url = url;
    }
    
    /*if (this.url == null || 
        url != null && url.toString().length() < this.url.toString().length())
      this.url = url;*/
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }
}