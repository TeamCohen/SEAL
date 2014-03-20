/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.rcwang.seal.expand.Wrapper.EntityLiteral;

public class StringFactory {

  public static class SID implements Iterable<String> {
    
    String[] array;
    
    public SID(List<String> strs) {
      array = strs.toArray(new String[strs.size()]);
    }

    public SID(SID id) {
      this(id.array);
    }

    public SID(String[] array) {
      this.array = array.clone();
    }
    
    public void append(SID sid) {
      append(sid.array);
    }
    
    public void append(String s) {
      append(StringFactory.toID(s));
    }
    
    public void append(String[] array) {
      String[] array2 = new String[this.array.length+array.length];
      System.arraycopy(this.array, 0, array2, 0, this.array.length);
      System.arraycopy(array, 0, array2, this.array.length, array.length);
      this.array = array2;
    }
    
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final SID other = (SID) obj;
      if (!Arrays.equals(array, other.array))
        return false;
      return true;
    }
    
    @Override
    public int hashCode() {
      return 31 + Arrays.hashCode(array);
    }
    
    public Iterator<String> iterator() {
      return Arrays.asList(array).iterator();
    }
    
    public int length() {
      int length = 0;
      for (String s : this)
        length += s.length();
      return length;
    }

    @Override
    public String toString() {
      return StringFactory.toName(this);
    }
    
    public EntityLiteral toLiteral() { return new EntityLiteral(StringFactory.toName(this)); }
    
    public SID toLower() {
    	return StringFactory.toID(new EntityLiteral(StringFactory.toName(this).toLowerCase()));
    }
  }
  public static class RSID extends SID {
      int boundary;

      public RSID(List<String> strs) { super(strs); }
      public RSID(SID id) { super(id); }
      public RSID(String[] array) { super(array); }
      
      public RSID(SID a1, SID a2) {
          super(a1); 
          boundary = a1.array.length;
          super.append(a2);
      }
      
      public EntityLiteral toLiteral() {
          return new EntityLiteral(
                  StringFactory.toName(Arrays.copyOfRange(this.array, 0, boundary)),
                  StringFactory.toName(Arrays.copyOfRange(this.array, boundary, this.array.length))
          );
      }
  }
  
  /*public static class SID extends ArrayList<String> {
    private static final long serialVersionUID = -746676179936368932L;
    
    public SID() {
      super();
    }
    
    public SID(SID id) {
      this();
      this.addAll(id);
    }
    
    public void append(SID id) {
      this.addAll(id);
    }
    
    public int length() {
      int length = 0;
      for (String s : this)
        length += s.length();
      return length;
    }
    
    @Override
    public String toString() {
      return StringFactory.toName(this);
    }
  }*/
  
  public static final boolean DISABLE_CACHE = false; // reduces memory for small runs
  private static Map<String, String> strMap = new Cache<String, String>();
  private static Map<SID, SID> sidMap = new Cache<SID, SID>();
  private static StringBuffer buf = new StringBuffer();
  private static String delimiter = " ";  // space
  
  public static void clear() {
    strMap.clear();
    sidMap.clear();
  }
  
  public static SID get(SID sid) {
    if (DISABLE_CACHE) return sid;
    SID sid2 = sidMap.get(sid);
    if (sid2 == null) {
      sidMap.put(sid, sid);
      return sid;
    } else return sid2;
  }
  
  public static String get(String s) {
    if (DISABLE_CACHE) return s;
    String s2 = strMap.get(s);
    if (s2 == null) {
      strMap.put(s, s);
      return s;
    } else return s2;
  }
  
  public static String getDelimiter() {
    return delimiter;
  }
  
  public static int getIDSize() {
    return sidMap.size();
  }
  
  public static int getStrSize() {
    return strMap.size();
  }
  
  public static void main(String args[]) {
    String[] tests = new String[] {
        "",
        " ",
        "  ",
        ".",
        ". ",
        " .",
        "..",
    };
    
    boolean failed = false;
    for (String test : tests) {
      SID id = StringFactory.toID(test);
      String s = StringFactory.toName(id);
      if (!test.equals(s)) {
        failed = true;
        break;
      }
    }
    
    if (failed)
      System.out.println("FAILURE!!!");
    else System.out.println("SUCCESS!!!");
  }
  
  public static void setDelimiter(String delimiter) {
    StringFactory.delimiter = delimiter;
  }
  
  /**
   * Sort by alphabetical order
   * @param idSet
   * @return
   */
  public static List<SID> sortIDs(Set<SID> idSet) {
    Map<String, SID> strIDMap = new HashMap<String, SID>();
    for (SID id : idSet)
      strIDMap.put(toName(id), id);
    List<String> strList = new ArrayList<String>(strIDMap.keySet());
    Collections.sort(strList);
    List<SID> resultList = new ArrayList<SID>();
    for (String str : strList)
      resultList.add(get(strIDMap.get(str)));
    return resultList;
  }
  
  /**
   * Splits a String
   * @param name
   * @return a new List
   */
  public static SID toID(String name) {
    if (name == null) return null;
    List<String> ids = new ArrayList<String>();
    int index = 0, prevIndex = 0;
    if (name.length() > 0) {
      while ((index = name.indexOf(delimiter, prevIndex)) != -1) {
        ids.add(get(name.substring(prevIndex, index)));
        prevIndex = index + delimiter.length();
        if (prevIndex == name.length())
          break;
      }
    }
    if (prevIndex > name.length())
      System.err.println("Something is wrong!!!");
    else if (prevIndex == name.length())
      ids.add(get(""));
    else if (index == -1)
      ids.add(get(name.substring(prevIndex)));
    return get(new SID(ids));
  }
  
  public static SID toID(EntityLiteral name) {
      if (name == null) return null;
      if (name.arg2 == null) return toID(name.arg1);
      SID id1 = toID(name.arg1);
      SID id2 = toID(name.arg2);
      return new RSID(id1,id2);
  }
  
  
  public static String toName(SID id) {
      if (id == null) return null;
      return toName(id.array);
  }
  /**
   * Combines a list of Strings
   * @param id
   * @return a new String
   */
  public static String toName(String ... id) {
    if (id == null) return null;
    buf.setLength(0);
    for (Object o : id) {
      if (buf.length() > 0)
        buf.append(delimiter);
      buf.append(o);
    }
    return buf.toString();
  }

  public static Set<String> toNames(Set<SID> idSet) {
    Set<String> names = new HashSet<String>();
    for (SID id : idSet)
      names.add(toName(id));
    return names;
  }
}
