/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.util.Iterator;

import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.translation.Annotator;
import com.rcwang.seal.util.XMLUtil;

public class Excerpt extends Annotator {

  private int hashCode;
  private Snippet snippet = null;
  
  private static int toHashCode(String s) {
    s = XMLUtil.removeXMLTags(s);
    s = s.replace("[\\s\\d\\p{Punct}]+", "");
    return s.hashCode();
  }
  
  public Excerpt(String excerpt) {
    super(excerpt);
    hashCode = toHashCode(excerpt);
  }
  
  public void chopText() {
    if (queryType == 1) chopInitialText();
    else if (queryType == 2) chopFinalText();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final Excerpt other = (Excerpt) obj;
    return hashCode == other.hashCode;
  }

  public Snippet getSnippet() {
    return snippet;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
  
  /**
   * Remove text from end of 'base' up to the start of last existing annotation
   */
  private void chopFinalText() {
    int lastStart = Integer.MIN_VALUE;
    for (Annotation ann : annotations)
      lastStart = Math.max(ann.start(), lastStart);
    if (lastStart == Integer.MIN_VALUE) return;
    
    // remove any punctuation starting from character at firstEnd (added 03/12/2009)
    for (; lastStart > 0; lastStart--)
      if (!WrapperFactory.isPunct(text.charAt(lastStart-1))) break;
    text = text.substring(0, lastStart);
    
    for (Iterator<Annotation> i = annotations.iterator(); i.hasNext();) {
      Annotation ann = i.next();
      if (ann.end() > lastStart)
        i.remove();
    }
    Annotation dummyAnn = new Annotation(lastStart, lastStart, SOURCE_TYPE);
    annotations.add(dummyAnn);
  }
  
  /**
   * Remove text from beginning of 'base' up to the end of first existing annotation
   */
  private void chopInitialText() {
    int firstEnd = Integer.MAX_VALUE;
    for (Annotation ann : annotations)
      firstEnd = Math.min(ann.end(), firstEnd);
    if (firstEnd == Integer.MAX_VALUE) return;
    
    // remove any punctuation starting from character at firstEnd (added 03/12/2009)
    for (; firstEnd < text.length(); firstEnd++)
      if (!WrapperFactory.isPunct(text.charAt(firstEnd))) break;
    text = text.substring(firstEnd);
    
    for (Iterator<Annotation> i = annotations.iterator(); i.hasNext();) {
      Annotation ann = i.next();
      if (ann.start() < firstEnd)
        i.remove();
      else ann.shift(-firstEnd);
    }
    Annotation dummyAnn = new Annotation(0, 0, SOURCE_TYPE);
    annotations.add(dummyAnn);
  }
  
  protected void setSnippet(Snippet snippet) {
    this.snippet = snippet;
  }
}