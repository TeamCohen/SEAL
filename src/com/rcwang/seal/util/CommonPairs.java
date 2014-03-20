/**
 * 
 */
package com.rcwang.seal.util;

import java.util.Set;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper.MiddleContext;

public class CommonPairs {
  
    public static final String SEPARATOR = "[...]";
    private EntityList commonTypes = null;
    protected String left, right;
    protected MiddleContext middle = null;
    
    public CommonPairs(String left, MiddleContext middle, String right, EntityList commonTypes) {
      this.left = left;
      this.middle = middle;
      this.right = right;
      this.commonTypes = commonTypes;
    }
    
    public CommonPairs(String left, MiddleContext middle, String right, Set<Entity> commonTypes) {
      this(left, middle, right, new EntityList(commonTypes));
    }
    
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((left == null) ? 0 : left.hashCode());
      result = prime * result + ((middle == null) ? 0 : middle.hashCode());
      result = prime * result + ((right == null) ? 0 : right.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      final CommonPairs other = (CommonPairs) obj;
      if (left == null) {
        if (other.left != null)
          return false;
      } else if (!left.equals(other.left))
        return false;
      if (middle == null) {
        if (other.middle != null)
          return false;
      } else if (!middle.equals(other.middle))
        return false;
      if (right == null) {
        if (other.right != null)
          return false;
      } else if (!right.equals(other.right))
        return false;
      return true;
    }

    /*@Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final CommonPairs other = (CommonPairs) obj;
      if (left == null) {
        if (other.left != null) return false;
      } else if (!left.equals(other.left)) return false;
      if (right == null) {
        if (other.right != null) return false;
      } else if (!right.equals(other.right)) return false;
      return true;
    }*/
    
    public EntityList getCommonTypes() {
      return commonTypes;
    }
    
    public String getLeft() {
      return left;
    }

    public int getNumCommonTypes() {
      return commonTypes.size();
    }
    
    public String getRight() {
      return right;
    }
    
    public double getWeight(Object feature) {
      return commonTypes.getSumWeights(feature);
    }
    
    /*@Override
    public int hashCode() {
      final int PRIME = 31;
      int result = PRIME + ((left == null) ? 0 : left.hashCode());
      return PRIME * result + ((right == null) ? 0 : right.hashCode());
    }*/
    
    public int length() {
      return getLeft().length() + (middle == null ? 0 : middle.getText().length()) + getRight().length();
    }
    
    public String toString() {
      return "{" + (getLeft() + SEPARATOR + getRight()).replaceAll("\\s", " ") + "}";
//      return "{" + (getLeft() + SEPARATOR + getRight()).replaceAll("\n", "N").replaceAll("\r", "R").replaceAll("\t", "T") + "}";
//      return "[" + getCommonTypes().size() + "] \"" + (getLeft() + SEPARATOR + getRight()).replaceAll("\\s", " ") + "\"";
    }
  }