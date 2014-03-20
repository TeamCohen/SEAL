/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.StringFactory;

public class Wrapper {
    private static final Logger log = Logger.getLogger(Wrapper.class);
  
  public static class MiddleContext {
    private String text;
    private boolean isReversed;
    
    public MiddleContext(String text, boolean isReversed) {
      this.text = text;
      this.isReversed = isReversed;
    }
    
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final MiddleContext other = (MiddleContext) obj;
      if (isReversed != other.isReversed) return false;
      if (text == null) {
        if (other.text != null) return false;
      } else if (!text.equals(other.text)) return false;
      return true;
    }
    
    public String getText() {
      return text;
    }
    
    @Override
    public int hashCode() {
      final int PRIME = 31;
      int result = 1;
      result = PRIME * result + (isReversed ? 1231 : 1237);
      result = PRIME * result + ((text == null) ? 0 : text.hashCode());
      return result;
    }
    
    public boolean isReversed() {
      return isReversed;
    }
    
    @Override
    public String toString() {
      return (isReversed() ? "[-] " : "[+] ") + noSpace(text);
    }
  }
  
  public static final String SEPARATOR = "[...]";
  public static final String SEPARATOR1 = "[.1.]";
  public static final String SEPARATOR2 = "[.2.]";
  
  public static class EntityLiteral implements Comparable<EntityLiteral> {
      public String arg1;
      public String arg2;
      public EntityLiteral(String arg1) { this.arg1=StringFactory.get(arg1); }
      public EntityLiteral(String arg1, String arg2) { this(arg1); this.arg2 = StringFactory.get(arg2); }
      public EntityLiteral(String ... names) {
          if (names.length > 0) this.arg1=StringFactory.get(names[0]);
          if (names.length > 1) this.arg2 = StringFactory.get(names[1]);
          if (names.length > 2) log.warn("Attempt to create entity literal with more than two strings:" + names);
      }
    public String toString() { 
          StringBuilder sb = new StringBuilder(arg1);
          if (arg2!= null) sb.append(Entity.RELATION_SEPARATOR).append(arg2);
          return sb.toString();
      }
    final public boolean equals(Object o) {
        if (! (o instanceof EntityLiteral)) return false;
        EntityLiteral e = (EntityLiteral) o;
        return arg1 == e.arg1 && arg2 == e.arg2; 
    }
    final public int hashCode() { 
        int a1 = arg1.hashCode();
        if (arg2!=null) return a1 ^ arg2.hashCode();
        return a1;
    }
    final public int compareTo(EntityLiteral e) {
        return this.toString().compareTo(e.toString());
    }
  }
  private Map<EntityLiteral, Integer> contentTF;
  private Map<EntityLiteral, Double> contentWeight;
  private EntityList seeds;
  private MiddleContext middle;
  private URL url;
  protected String left, right;

  private static String noSpace(String s) {
    return s.replaceAll("\\s", " ");
  }
  
  public Wrapper(String left, MiddleContext middle, String right) {
    this(left, right);
    this.middle = middle;
  }

  public Wrapper(String left, String right) {
    this.left = left;
    this.right = right;
    // mapping from content to its extracted term frequency
    this.contentTF = new HashMap<EntityLiteral, Integer>();
  }
  
  public void addContent(EntityLiteral content) {
    if (content == null) return;
//    content = StringFactory.get(content);
    contentTF.put(content, getContentTF(content) + 1);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (getClass() != obj.getClass()) return false;
    final Wrapper other = (Wrapper) obj;
    if (left == null) {
      if (other.left != null) return false;
    } else if (!left.equals(other.left))
      return false;
    if (middle == null) {
      if (other.middle != null) return false;
    } else if (!middle.equals(other.middle))
      return false;
    if (right == null) {
      if (other.right != null) return false;
    } else if (!right.equals(other.right))
      return false;
    if (url == null) {
      if (other.url != null) return false;
    } else if (!url.toString().equals(other.url.toString()))
      return false;
    return true;
  }
  
  public Set<EntityLiteral> getContents() {
    return contentTF.keySet();
  }
  
  public int getContentSize() {
    return getContents().size();
  }
  
  public int getContentTF(EntityLiteral content) {
    Integer counts = contentTF.get(content);
    return counts == null ? 0 : counts;
  }
  
  public int getContextLength() {
    return getLeft().length() + (middle == null ? 0 : getMiddle().length()) + getRight().length();
  }
  
  public String getLeft() {
    return left;
  }

  public String getMiddle() {
    return middle.getText();
  }
  
  public MiddleContext getMiddleContext() {
    return middle;
  }
  
  public int getNumCommonTypes() {
    return seeds.size();
  }
  
  public String getRight() {
    return right;
  }
  
  public EntityList getSeeds() {
    return seeds;
  }
  
  public int getSeedSize() {
    return getSeeds().size();
  }
  
  public URL getURL() {
    return url;
  }
  
  public double getWeight(Object feature) {
    return seeds.getSumWeights(feature);
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((middle == null) ? 0 : middle.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + ((url == null) ? 0 : url.toString().hashCode());
    return result;
  }

  public boolean isRelational() {
    return middle != null;
  }

  public boolean isReversed() {
    return middle.isReversed();
  }
  
  public void setSeeds(Set<Entity> entities) {
    this.seeds = new EntityList(entities);
  }

  public void setURL(URL url) {
    this.url = url;
  }

  public void setContentWeight(EntityLiteral c,double wt) {
      if (this.contentWeight == null) this.contentWeight = new HashMap<EntityLiteral,Double>();
      this.contentWeight.put(c, wt);
  }
  
  public double getContentWeight(EntityLiteral c) { return this.contentWeight.get(c); }
  
  public String toDetails() {
    StringBuffer buf = new StringBuffer();
    if (url != null)
      buf.append("URL: ").append(url.toString()).append("\n");
    buf.append(" Seeds : [" + getSeedSize() + "] ").append(this.getSeeds()).append("\n");
    buf.append("Context: ").append(this).append("\n");
    buf.append("Content: ").append(Helper.toReadableString(this.getContents()));
    return buf.toString();
  }
  
  public String toString() {
    StringBuffer buf = new StringBuffer("{");
    buf.append(noSpace(getLeft()));
    if (isRelational()) {
      buf.append(middle.isReversed() ? SEPARATOR2 : SEPARATOR1);
      buf.append(noSpace(middle.getText()));
      buf.append(middle.isReversed() ? SEPARATOR1 : SEPARATOR2);
    } else {
      buf.append(SEPARATOR);
    }
    buf.append(noSpace(getRight())).append("}");;
    return buf.toString();
  }
}

