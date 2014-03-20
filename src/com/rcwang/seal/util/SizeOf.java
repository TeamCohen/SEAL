package com.rcwang.seal.util;

import java.util.Random;


/**
 * Measure the size of an object by allocating a bunch of them and
 * seeing how much heap memory was consumed.
 * 
 * Created on Apr 8, 2003 3:59:14 PM
 * @author mroth
 */
public class SizeOf {

  public static final Random rand = new Random(0);
  
  public static void main(String[] argv) {
    System.out.println("SizeOf: " + estimateSize(100000) + " bytes");
  }

  public static String randomString(int i) {
    StringBuffer buf = new StringBuffer();
    for (; i > 0; i--)
      buf.append(rand.nextInt(10));
    return buf.toString();
  }
  
  public static Object makeObject(int i) {
//    return new Double(12345.12345);
//    return Arrays.asList(StringFactory.get(randomString(3)), StringFactory.get(randomString(3)), StringFactory.get(randomString(3)));
//    return randomString(3) + "." + randomString(3) + "." + randomString(3);
//    return new String[] {(i%10)+""};
//    return new SparseVector(Arrays.asList(randomString(1)));
    return new SparseVector(StringFactory.toID(randomString(1)));
  }
  
  public static long estimateSize(int n) {
    Object[] array = new Object[n];
    giveGarbageCollectionAChance();
    long before = getUsedMemory();
    for (int i = n - 1; i >= 0; i--)
      array[i] = makeObject(i);
    giveGarbageCollectionAChance();
    long after = getUsedMemory();
    return Math.round((double) (after - before) / (double) n);
  }

  private static void giveGarbageCollectionAChance() {
    System.gc();
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      // ignore - should not happen
    }
  }

  private static long getUsedMemory() {
    Runtime r = Runtime.getRuntime();
    return r.totalMemory() - r.freeMemory();
  }

}
