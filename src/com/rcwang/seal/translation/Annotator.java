/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.WrapperFactory;

public class Annotator {

  public class Annotation implements Comparable<Annotation> {
    private int startOffset;
    private int endOffset;
    private String type;

    public Annotation(int startOffset, int endOffset, String type) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.type = type;
      trim();
    }
    
    public int compareTo(Annotation ann) {
      return Double.compare(startOffset, ann.start());
    }
    
    public int end() {
      return endOffset;
    }

    public String getText() {
      return text.substring(startOffset, endOffset);
    }

    public String getType() {
      return type;
    }

    public boolean isSourceType() {
      return type.equals(SOURCE_TYPE);
    }
    
    public boolean isTargetType() {
      return type.equals(TARGET_TYPE);
    }
    
    public void shift(int offset) {
      startOffset += offset;
      endOffset += offset;
    }
    
    public int start() {
      return startOffset;
    }
    
    @Override
    public String toString() {
      return "\"" + text.substring(startOffset, endOffset) + "@[" + startOffset + "," + (endOffset-1) + "]\"";
    }
    
    public void trim() {
      for (; startOffset < endOffset; startOffset++)
        if (!WrapperFactory.isPunct(text.charAt(startOffset))) break;

      for (; endOffset > startOffset; endOffset--)
        if (!WrapperFactory.isPunct(text.charAt(endOffset-1))) break;
    }
  }
  
  public static Logger log = Logger.getLogger(Annotator.class);

  public static final String QUERY_SYMBOL = "+";
  public static final String SOURCE_TYPE = "source";
  public static final String TARGET_TYPE = "target";
  public static final String START_SOURCE_MARKER = "<";
  public static final String END_SOURCE_MARKER = ">";
  public static final String START_TARGET_MARKER = "{";
  public static final String END_TARGET_MARKER = "}";
  public static final int LAPLACIAN_SMOOTHING = 1;
  
  protected List<Annotation> annotations;
  protected String text;
  protected int queryType;
  
  public static void main(String args[]) {
//    String s = "Richard Wang studies at Carnegie Mellon University!";
    String s = "|a          a            a|";
    Annotator annotator = new Annotator(s);
    annotator.annotateSource("|");
    annotator.annotateTarget("a");
    log.info(annotator);
    log.info("Score: " + annotator.getDistScore());
    log.info("# Sources: " + annotator.countSource());
    log.info("# Targets: " + annotator.countTarget());
  }
  
  private static int determineQueryType(String s) {
    int queryType = 0;
    for (; ; queryType++)
      if (!s.substring(queryType, queryType+1).equals(QUERY_SYMBOL))
        break;
    return queryType;
  }
  
  private static boolean hasOverlap(Annotation ann1, Annotation ann2) {
    return ann1.end() > ann2.start() && ann2.end() > ann1.start();
  }
  
  public Annotator(String text) {
    annotations = new ArrayList<Annotation>();
    this.text = text;
    queryType = 0;
  }
  
  public void annotateSource(String s) {
    annotate(s, SOURCE_TYPE);
  }
  
  public void annotateTarget(String s) {
    annotate(s, TARGET_TYPE);
  }
  
  public void clearSource() {
    clearAnnotations(SOURCE_TYPE);
  }
  
  public void clearTarget() {
    clearAnnotations(TARGET_TYPE);
  }
  
  public int countSource() {
    return countType(SOURCE_TYPE);
  }
  
  public int countTarget() {
    return countType(TARGET_TYPE);
  }
  
  public boolean existSource() {
    return countSource() > 0;
  }
  
  public boolean existTarget() {
    return countTarget() > 0;
  }
  
  /**
   * Check input annotation newAnn against existing annotations and 
   * suggest new annotations that do not overlap with the existing ones
   * @param newAnn
   * @return a list of new suggested annotations; if no overlap, then the original annotation is returned.
   */
  public List<Annotation> fixOverlap(Annotation newAnn) {
    List<Annotation> anns = new ArrayList<Annotation>();
    String type = newAnn.getType();
    boolean hasOverlap = false;
    
    for (Annotation oldAnn : annotations) {
      if (!hasOverlap(newAnn, oldAnn)) continue;
      hasOverlap = true;
      if (newAnn.start() < oldAnn.start() && newAnn.end() > oldAnn.end()) {
        // "those vintage Disney movies e.g. Aladdin@[39,78]" overlaps with existing annotation "Disney movies e.g.@[53,70]"
        anns.add(new Annotation(newAnn.start(), oldAnn.start(), type));
        anns.add(new Annotation(oldAnn.end(), newAnn.end(), type));
      } else if (newAnn.end() > oldAnn.end()) {
        // "e.g. Aladdin@[67,78]" overlaps with existing annotation "Disney movies e.g.@[53,70]"
        anns.add(new Annotation(oldAnn.end(), newAnn.end(), type));
      } else if (newAnn.start() < oldAnn.start()) {
        // "the CLASSIC disney movies@[16,40]" overlaps with existing annotation "disney movies i.e @[28,45]"
        anns.add(new Annotation(newAnn.start(), oldAnn.start(), type));
      }
    }
    if (!hasOverlap)
      anns.add(newAnn);
    return anns;
  }
  
  public List<Annotation> getAnnotations() {
    return annotations;
  }
  
  public double getDistScore() {
    Annotation prevSourceAnn = null;
    Annotation nextSourceAnn = null;
    double score = 0;
    Collections.sort(annotations);
    
    for (int i = 0; i < annotations.size(); i++) {
      Annotation currAnn = annotations.get(i);
      if (currAnn.isSourceType()) {
        prevSourceAnn = currAnn;
        continue;
      }
      // current annotation is 'target' type from this point on...
      int minCharDist = Integer.MAX_VALUE;
      if (prevSourceAnn != null)
        minCharDist = currAnn.startOffset - prevSourceAnn.end();

      // find the next 'source' annotation
      if (nextSourceAnn == null || currAnn.end() > nextSourceAnn.start()) {
        nextSourceAnn = null;
        for (int j = i+1; j < annotations.size(); j++) {
          Annotation ann = annotations.get(j);
          if (ann.isSourceType()) {
            nextSourceAnn = ann;
            break;
          }
        }
      }
      if (nextSourceAnn != null) {
        int charDist = nextSourceAnn.start() - currAnn.endOffset;
        minCharDist = Math.min(charDist, minCharDist);
      }
      if (minCharDist == Integer.MAX_VALUE) break;
      score += 1.0 / ((minCharDist+1) + LAPLACIAN_SMOOTHING);
    }
    return score;
  }
  
  public int getQueryType() {
    return queryType;
  }

  public String getText() {
    return text;
  }
  
  public String toString() {
    if (text == null) return null;
    Collections.sort(annotations);
    StringBuffer buf = new StringBuffer();
    int prevEndOffset = 0;
    
    for (Annotation ann : annotations) {
      if (prevEndOffset > ann.start())
        continue;
      buf.append(text.substring(prevEndOffset, ann.start()));
      buf.append(ann.isTargetType() ? START_TARGET_MARKER : START_SOURCE_MARKER);
      buf.append(text.substring(ann.start(), ann.end()));
      buf.append(ann.isTargetType() ? END_TARGET_MARKER : END_SOURCE_MARKER);
      prevEndOffset = ann.end();
    }
    buf.append(text.substring(prevEndOffset));
    return buf.toString();
  }

  private void annotate(String s, String type) {
    if (s == null || type == null) return;
    s.trim();
    
    queryType = determineQueryType(s);
    String patternStr;
    if (queryType > 0) {
      s = s.substring(queryType);
      patternStr = WrapperFactory.toPhraseRE(s);
    } else {
      patternStr = WrapperFactory.toEntityRE(s);
    }
    patternStr = "(?is:" + patternStr + ")";
    Matcher m = Pattern.compile(patternStr).matcher(text);

    while (m.find()) {
      Annotation newAnn = new Annotation(m.start(), m.end(), type);
      boolean hasOverlap = false;
      
      for (Annotation oldAnn : annotations) {
        hasOverlap = hasOverlap(newAnn, oldAnn);
        if (hasOverlap) {
//          log.debug("New annotation " + newAnn + " overlaps with existing annotation " + oldAnn);
          break;
        }
      }
      if (hasOverlap) continue;
//      log.info("-->" + text);
//      log.info("!!!" + newAnn);
      annotations.add(newAnn);
    }
  }

  private void clearAnnotations(String type) {
    if (type == null) return;
    for (Iterator<Annotation> i = annotations.iterator(); i.hasNext();) {
      Annotation ann = i.next();
      if (ann.getType().equals(type))
        i.remove();
    }
  }

  private int countType(String type) {
    int count = 0;
    if (type == null)
      return count;
    for (Annotation ann : annotations)
      if (ann.getType().equals(type))
        count++;
    return count;
  }
}
