/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.XMLUtil;

public class Snippet {

  public static Logger log = Logger.getLogger(Snippet.class);
  public static final String EXCERPT_DELIMITER = "...";
  
  private Set<Excerpt> excerpts;
  private Excerpt title;
  private String mimeType;
  private URL pageURL;
  private URL cacheURL;
  private long lastModified;
  private int rank = Integer.MAX_VALUE;
  private int cacheSize = 0;
  private int hashCode = 0;
  private int index = 0;
  
  /**
   * Removes "www" from the host name
   * @param urlStr
   * @return
   */
  private static String normalizeHost(String urlStr) {
    if (urlStr == null) return null;
    final String k = "://www";
    int p = urlStr.toLowerCase().indexOf(k);
    if (p != -1) {
      int q = urlStr.indexOf(".", p+k.length());
      if (q != -1)
        return urlStr.substring(0, p+3) + urlStr.substring(q+1);
    }
    return urlStr;
  }
  
  public Snippet() {
    excerpts = new HashSet<Excerpt>();
  }
  
  public void addExcerpt(Excerpt excerpt) {
    excerpts.add(excerpt);
    excerpt.setSnippet(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final Snippet other = (Snippet) obj;
    if (hashCode != other.hashCode)
      return false;
    return true;
  }

  public int getCacheSize() {
    return cacheSize;
  }

  public URL getCacheURL() {
    return cacheURL;
  }

  public Set<Excerpt> getExcerpts() {
    return excerpts;
  }

  public long getLastModified() {
    return lastModified;
  }

  public String getMimeType() {
    return mimeType;
  }

  public URL getPageURL() {
    return pageURL;
  }

  public int getRank() {
    return rank;
  }

  public Excerpt getTitle() {
    return title;
  }
  
  @Override
  public int hashCode() {
    return hashCode;
  }
  
  public void merge(Snippet snippet) {
    if (snippet == null) return;
    setRank(snippet.getRank());
    setTitle(snippet.getTitle().toString());
    setPageURL(snippet.getPageURL().toString());
    URL cacheURL = snippet.getCacheURL();
    if (cacheURL != null)
      setCacheURL(cacheURL.toString());
    excerpts.addAll(snippet.getExcerpts());
  }

  public void setCacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
  }

  public void setCacheURL(String s) {
    if (s == null || s.length() == 0) return;
    // prefer shorter URL
    if (cacheURL != null && cacheURL.toString().length() <= s.length())
      return;
    try {
      cacheURL = new URL(s);
    } catch (MalformedURLException e) {
      log.error(e);
    }
  }

  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public void setPageURL(String s) {
    if (s == null || s.length() == 0) return;
    // prefer shorter URL
    if (pageURL != null && pageURL.toString().length() <= s.length())
      return;
    try {
      pageURL = new URL(s);
    } catch (MalformedURLException e) {
      log.error(e);
    }
    hashCode = normalizeHost(s).hashCode();
  }

  public void setRank(int rank) {
    this.rank = Math.min(rank, this.rank);
  }
  
  public void setSummary(String summary) {
    if (summary == null || summary.length() == 0) return;
    summary = XMLUtil.removeXMLTags(summary);
    summary = XMLUtil.unescapeXMLEntities(summary);
    summary = summary.replaceAll("[ ]+", " ").trim();
    String[] excerpts = summary.split(Pattern.quote(EXCERPT_DELIMITER));
    for (String excerptStr : excerpts)
      if (excerptStr.trim().length() > 0)
        addExcerpt(new Excerpt(excerptStr.trim()));
  }

  public void setTitle(String title) {
    if (title == null || title.length() == 0) return;
    title = XMLUtil.removeXMLTags(title);
    title = XMLUtil.unescapeXMLEntities(title);
    title = title.replaceAll("[ ]+", " ").trim();
    if (title.endsWith(EXCERPT_DELIMITER))
      title = title.substring(0, title.length()-EXCERPT_DELIMITER.length());
    // prefer longer titles
    if (this.title != null && this.title.getText().length() >= title.length())
      return;
    this.title = new Excerpt(title);
    this.title.setSnippet(this);
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("[").append(rank).append("] Title: ").append(title).append("\n");
    if (excerpts.size() > 0) {
      buf.append("\tBody:\n");
      for (Excerpt excerpt : excerpts)
        buf.append("\t\t").append(excerpt).append("\n");
    }
    buf.append("\tPage URL: ").append(pageURL).append("\n");
    if (cacheURL != null)
      buf.append("\tCache URL: ").append(cacheURL).append("");
    return buf.toString();
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }
}