package com.rcwang.seal.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.rcwang.seal.fetch.GoogleWebSearcher;

public class Timer {
  public static Logger log = Logger.getLogger(GoogleWebSearcher.class);
  
  private static Map<String, Long> startTimes = new HashMap<String, Long>();
  private static Map<String, Long> elapsedTimes = new TreeMap<String, Long>();
  
  public static void main(String args[]) throws InterruptedException {
    Timer.start("a");
    Thread.sleep(1000);
    Timer.end("a");
    Timer.start("a");
    Thread.sleep(2000);
    Timer.end("a");
    Timer.start("b");
    Thread.sleep(3000);
    Timer.end("b");
    log.info(Timer.getResults());
  }
  
  public static void reset() {
    startTimes.clear();
    elapsedTimes.clear();
  }
  
  public static void end(String type) {
    if (type == null) return;
    Long startTime = startTimes.get(type);
    if (startTime == null) {
      log.error("Timer \"" + type + "\" was not set!");
      return;
    }
    Long elapsedTime = elapsedTimes.get(type);
    if (elapsedTime == null)
      elapsedTime = (long) 0;
    elapsedTime += System.currentTimeMillis() - startTime;
    elapsedTimes.put(type, elapsedTime);
  }
  
  public static Set<String> getTypes() {
    return elapsedTimes.keySet();
  }
  
  public static void start(String type) {
    if (type == null) return;
    startTimes.put(type, System.currentTimeMillis());
  }
  
  public static String getResults() {
    StringBuffer buf = new StringBuffer("------------------------------------\n");
    for (Map.Entry<String, Long> entry : elapsedTimes.entrySet()) {
      String type = entry.getKey();
      long elapsedTime = entry.getValue();
      buf.append("Timer '").append(type).append("':\t").append(elapsedTime).append(" ms\n");
    }
    return buf.toString();
  }
}
