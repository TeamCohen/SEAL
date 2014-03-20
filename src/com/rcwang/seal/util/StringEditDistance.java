package com.rcwang.seal.util;

import java.util.Arrays;

public class StringEditDistance {

  public static final int JWINKLER_PREFIX_SIZE = 4;
  public static final double JWINKLER_WEIGHT_THRESHOLD = 0.7;
  
  public static double jaroWinkler(CharSequence cSeq1, CharSequence cSeq2) {
      return jaroWinklerDistance(cSeq1, cSeq2, JWINKLER_WEIGHT_THRESHOLD, JWINKLER_PREFIX_SIZE);
  }
  
  public static double levenshtein(String s1, String s2) {
    return 1.0 - (double) levenshteinDistance(s1, s2) / Math.max(s1.length(), s2.length());
  }
  
  private static double jaroWinklerDistance(CharSequence cSeq1, CharSequence cSeq2, double mWeightThreshold, int mNumChars) {
      int len1 = cSeq1.length();
      int len2 = cSeq2.length();
      if (len1 == 0)
          return len2 == 0 ? 1.0 : 0.0;

      int  searchRange = Math.max(0,Math.max(len1,len2)/2 - 1);

      boolean[] matched1 = new boolean[len1];
      Arrays.fill(matched1,false);
      boolean[] matched2 = new boolean[len2];
      Arrays.fill(matched2,false);

      int numCommon = 0;
      for (int i = 0; i < len1; ++i) {
          int start = Math.max(0,i-searchRange);
          int end = Math.min(i+searchRange+1,len2);
          for (int j = start; j < end; ++j) {
              if (matched2[j]) continue;
              if (cSeq1.charAt(i) != cSeq2.charAt(j))
                  continue;
              matched1[i] = true;
              matched2[j] = true;
              ++numCommon;
              break;
          }
      }
      if (numCommon == 0) return 0.0;

      int numHalfTransposed = 0;
      int j = 0;
      for (int i = 0; i < len1; ++i) {
          if (!matched1[i]) continue;
          while (!matched2[j]) ++j;
          if (cSeq1.charAt(i) != cSeq2.charAt(j))
              ++numHalfTransposed;
          ++j;
      }
      int numTransposed = numHalfTransposed/2;

      double numCommonD = numCommon;
      double weight = (numCommonD/len1
                       + numCommonD/len2
                       + (numCommon - numTransposed)/numCommonD)/3.0;

      if (weight <= mWeightThreshold) return weight;
      int max = Math.min(mNumChars, Math.min(cSeq1.length(),cSeq2.length()));
      int pos = 0;
      while (pos < max && cSeq1.charAt(pos) == cSeq2.charAt(pos))
          ++pos;
      if (pos == 0) return weight;
      return weight + 0.1 * pos * (1.0 - weight);
  }

  private static int levenshteinDistance(String s, String t) {
    int n = s.length(), m = t.length(), v, i, j;
    if (n == 0) return m;
    if (m == 0) return n;

    int[][] d = new int[n + 1][m + 1];
    for (i = 0; i <= n; d[i][0] = i++);
    for (j = 1; j <= m; d[0][j] = j++);

    char sc;
    for (i = 1; i <= n; i++) {
      sc = s.charAt( i-1 );
      for (j = 1; j <= m; j++) {
        v = d[i-1][j-1];
        if (t.charAt( j-1 ) != sc) v++;
        d[i][j] = Math.min(Math.min( d[i-1][j] + 1, d[i][j-1] + 1 ), v);
      }
    }
    return d[n][m];
  }
  
}
