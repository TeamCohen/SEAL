/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;

public class Helper {
  
  public static Logger log = Logger.getLogger(Helper.class);
  
  public static final String UNICODE = "UTF-8";
  public static final String COMMON_ENCODING = "ISO-8859-1";
  public static final String DATE_FORMAT = "yyyy-MM-dd";  // must match below
  public static final String DATE_PATTERN = "\\d+{4}-\\d+{2}-\\d+{2}";  // must match above
  public static final String TIME_FORMAT = "h:mm:ss aa";
  public static final String DATE_TIME_FORMAT = DATE_FORMAT + " " + TIME_FORMAT;
  public static final NumberFormat NUM_FORMAT = NumberFormat.getInstance();
  
  public static String repeat(char c, int n) {
    StringBuffer buf = new StringBuffer();
    for (; n > 0; n--)
      buf.append(c);
    return buf.toString();
  }
  
  public static String center(String s, char filler, int width) {
    if (s.length() >= width) return s;
    int numFillers = (width - s.length()) / 2;
    return repeat(filler, numFillers) + s + repeat(filler, numFillers);
  }
  
  public static String addQuote(String s) {
    s = s.trim();
    if (!s.startsWith("\""))
      s = "\"" + s;
    if (!s.endsWith("\""))
      s += "\"";
    return s;
  }
  
  public static double getPDF(double x, double avg, double std) {
    double diff = x - avg;
    double exp = Math.exp(-0.5 * Math.pow(diff / std, 2));
    return exp / (std * Math.sqrt(2 * Math.PI));
  }
  
  public static String addQuote(String[] ss) {
    StringBuffer buf = new StringBuffer();
    for (String s : ss)
      buf.append(buf.length() > 0 ? " " : "").append(addQuote(s));
    return buf.toString();
  }
  
  public static File createDir(File dir) {
    if (dir != null && !dir.isDirectory()) {
      log.info("Creating directory: " + dir.getAbsolutePath());
      if (!dir.mkdirs())
          log.error("Could not create requested directories for "+dir.getAbsolutePath());
    }
    return dir;
  }
  
  public static File createTempFile (String content) {
    File temp = null;
    try {
      temp = File.createTempFile("tmp", null);
    } catch (IOException ioe) {
      log.error("Error creating temp file: " + ioe);
      return null;
    }
    temp.deleteOnExit();
    writeToFile(temp, content, "UTF-8", false);
    return temp;
  }
  
  public static String decodeURLString(String s) {
    if (s == null) return null;
    try {
      return URLDecoder.decode(s, UNICODE);
    } catch (UnsupportedEncodingException e) {
      log.error(e.toString());
      return null;
    }
  }
  
  public static void die(String s) {
    System.err.println("[FATAL] " + s);
    System.exit(1);
  }
  
  public static boolean empty(EntityList entityList) {
    return entityList == null || entityList.isEmpty();
  }
  
  public static boolean empty(String s) {
    return s == null || s.trim().length() == 0;
  }
  
  // encode the query and formulate the query URL
  public static String encodeURLString(String s) {
    if (s == null) return null;
    try {
      return URLEncoder.encode(s, UNICODE);
    } catch (UnsupportedEncodingException e) {
      log.error(e.toString());
      return null;
    }
  }
  
  /**
   * Returns the String bounded by "left" and "after" in String s
   * @param s
   * @param left
   * @param right
   * @return the bounded String
   */
  public static String extract(String s, String left, String right) {
    int leftOffset, rightOffset;
    if (left == null) {
      leftOffset = 0;
      left = "";
    } else {
      leftOffset = s.indexOf(left);
      if (leftOffset == -1)
        return null;
    }
    if (right == null) {
      rightOffset = s.length();
    } else {
      rightOffset = s.indexOf(right, leftOffset + left.length());
      if (rightOffset == -1)
        return null;
    }
    return s.substring(leftOffset + left.length(), rightOffset);
  }

  public static String formatNumber(double number, int decimal) {
    NUM_FORMAT.setMaximumFractionDigits(decimal);
    NUM_FORMAT.setMinimumFractionDigits(decimal);
    return NUM_FORMAT.format(number).replace(",", "");
  }
  
  public static int getStringSize(String s) {
    int docSize = -1;
    try {
      docSize = s.getBytes("US-ASCII").length;
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return docSize;
  }
  
  /***
   * Creates a unique ID based on the current date and time
   * @return a unique ID
   */
  public static String getUniqueID () {
    Calendar cal = Calendar.getInstance(TimeZone.getDefault());
    SimpleDateFormat sdf = new SimpleDateFormat("MMdd-HHmmss");
    sdf.setTimeZone(TimeZone.getDefault());
    return sdf.format(cal.getTime());
  }
  
  public static Properties loadPropertiesFile(File propFile) {
    if (propFile == null)
      return null;
    Properties props = new Properties();
    InputStream in;
    try {
      in = readFileToStream(propFile);
      if (in != null) {
        props.load(in);
        in.close();
      }
    } catch (FileNotFoundException e) {
      log.error("Properties file not found: " + e);
      return null;
    } catch (IOException e) {
      log.error("Read properties file error: " + e);
      return null;
    }
    return props;
  }
  
  public static <T> T loadSerialized(File file, Class<? extends T> c) {
    log.info("Loading serialized object from: " + file);
    FileInputStream fis = null;
    ObjectInputStream in = null;
    T object = null;
    try {
      fis = new FileInputStream(file);
      in = new ObjectInputStream(fis);
      object = c.cast(in.readObject());
      in.close();
    } catch(IOException ex) {
      ex.printStackTrace();
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    return object;
  }

  public static double log2(double d) {
    return Math.log(d) / Math.log(2);
  }
  
  public static String merge(String[] strList, String delimiter) {
    StringBuffer buf = new StringBuffer();
    for (String s : strList) {
      if (buf.length() > 0)
        buf.append(delimiter);
      buf.append(s);
    }
    return buf.toString();
  }
  
  public static void printElapsedTime(long startTime) {
    log.info("Elapsed time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
  }
  
  public static void printDebugInfo() {
    log.info("Originator Size: " + Originator.size() + " (" + Originator.getNumStrings() + ")");
    log.info("String     Size: " + StringFactory.getStrSize());
    log.info("String ID  Size: " + StringFactory.getIDSize());
    Helper.printMemoryUsed();
  }
  
  public static void printMemoryUsed() {
    for (int i = 0; i < 3; i++) System.gc();
    long memSize = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    String memSizeStr = Helper.formatNumber((double) memSize / 1024 / 1024, 2);
    log.info(" Memory used: " + memSizeStr + "MB");
  }
  
  public static String readFile(File f) {
    return readFile(f, "UTF-8", false);
  }

  /**
   * Reads in a file, if not found, search on the class path
   * @param file the input file
   * @param charset the character set
   * @param binary is the file binary or text (non-binary)?
   * @return the content of the file
   */
  public static String readFile(File file, String charset, boolean binary) {
    if (file == null || charset == null)
      return null;
    InputStream in = readFileToStream(file);
    if (in == null)
      return null;
    StringBuffer buf = new StringBuffer();
    try {
      BufferedReader bReader = new BufferedReader(new InputStreamReader(in, charset));
      if (!binary) {
        String line;
        while ((line = bReader.readLine()) != null)
          buf.append(line).append("\n");
      } else {
        char[] c = new char[(int) file.length()];
        bReader.read(c);
        buf.append(c);
      }
      bReader.close();
    } catch (Exception e) {
      log.error("Could not read \"" + file + "\": " + e);
      return null;
    }
    String result = buf.toString();
    // get rid of BOM (byte order marker) from UTF-8 file
    if (result.length() > 0 && result.charAt(0) == 65279)
      result = result.substring(1);
    return result;
  }
  
  public static InputStream readFileToStream(File in) {
    if (in == null) return null;
    InputStream s = null;
    try {
      if (in.exists()) // if file exist locally
        s = new FileInputStream(in);
      if (s == null) // if file exist somewhere on the class path
        s = ClassLoader.getSystemResourceAsStream(in.getPath());
      if (s == null) { // if file still could not be found
        log.error("Could not find \"" + in + "\" locally ("+in.getAbsolutePath()+") or on classpath");
        return null;
      }
    } catch (Exception e) {
      log.error("Could not read \"" + in + "\": " + e);
      return null;
    }
    return s;
  }
  
  public static void recursivelyRemove(File f) {
    if (f == null || !f.exists()) return;
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      for (File file : files)
        recursivelyRemove(file);
    }
    String type = f.isDirectory() ? "directory" : "file";
    boolean deleted = f.delete();
    if (!deleted)
      log.error("Cannot delete the " + type + ": " + f);
    else log.debug("Successfully deleted the " + type + ": " + f);
  }
  
  public static String removeQuote(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.startsWith("\"") && s.endsWith("\""))
      s = s.substring(1, s.length()-1);
    return s;
  }
  
  public static void saveSerialized(Object object, File file, boolean overwrite) {
    if (!overwrite && file.exists()) {
      log.info("Serialized file " + file + " already exists! Skip saving...");
      return;
    }
    log.info("Saving serialized object to: " + file);
    if (file.getParentFile() != null)
      Helper.createDir(file.getParentFile());
    FileOutputStream fos = null;
    ObjectOutputStream out = null;
    try {
      fos = new FileOutputStream(file);
      out = new ObjectOutputStream(fos);
      out.writeObject(object);
      out.close();
    } catch(IOException ex) {
      ex.printStackTrace();
    }
  }

  public static void sortByLength(List<String> list, final boolean ascending) {
    Comparator<String> c = new Comparator<String>() {
      public int compare(String e1, String e2) {
        int length1 = e1.length();
        int length2 = e2.length();
        if (ascending)
          return Double.compare(length1, length2);
        else return Double.compare(length2, length1);
      }
    };
    Collections.sort(list, c);
  }
  
  /**
   * Converts an integer to a binary array with a specified max size
   * input: 11, output: [1011]
   * @param integer
   * @param size
   * @return
   */
  public static boolean[] toBinaryArray(int integer, int size) {
    boolean[] b = new boolean[size];
    String s = Integer.toBinaryString(integer);
    if (s.length() > b.length)
      s = s.substring(s.length()-b.length);
    // algorithm bug fixed kmr jan 2012
    // the 1s place is at the end of the string, so start there
    for (int i = s.length()-1; i >=0; i--)
      // the 1s element is at b[0] so start there
      b[s.length()-1-i] = (s.charAt(i) == '1');
    return b;
  }

  public static String toFileName(String s) {
    return s.replaceAll("[\\/:\\*?\"<>|\\s]+", "_");
  }

  public static File toFileOrDie(String filename) {
    if (filename == null)
      die("Could not find the file: " + filename);
    File file = new File(filename);
    if (!file.exists())
      die("Could not find the file: " + filename);
    return file;
  }

  public static String toReadableString(Collection<String> list) {
    Set<String> seeds = new TreeSet<String>();
    if (list != null)
      seeds.addAll(list);
    StringBuffer buf = new StringBuffer();
    buf.append("{");
    for (String seed : seeds) {
      if (buf.length() > 1)
        buf.append(", ");
      buf.append(seed);
    }
    buf.append("}");
    return buf.toString();
  }

  public static String toReadableDouble(Collection<Double> doubles) {
    StringBuffer buf = new StringBuffer();
    buf.append("{");
    for (Double d : doubles) {
      if (buf.length() > 1)
        buf.append(", ");
      buf.append(Helper.formatNumber(d, 5));
    }
    buf.append("}");
    return buf.toString();
  }
  
  public static String toReadableTime(Long timestamp, String format) {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    sdf.setTimeZone(TimeZone.getDefault());
    if (timestamp == null)
      timestamp = System.currentTimeMillis();
    return sdf.format(new Date(timestamp));
  }

  public static String toSeedsFileName(Collection<String> seedList) {
    Set<String> seeds = new TreeSet<String>();
    if (seedList != null)
      seeds.addAll(seedList);
    StringBuffer buf = new StringBuffer();
    for (String seed : seeds) {
      if (buf.length() > 0)
        buf.append("+");
      buf.append(seed);
    }
    return toFileName(buf.toString());
  }
  
  /**
   * Converts a String array to a Collection of Unicode
   * @param strs
   * @return
   */
  public static Set<String> toUnicode(Collection<String> strs) {
    Set<String> set = new HashSet<String>();
    if (strs == null) return set;
    for (String s : strs) {
      s = toUnicode(s);
      if (!empty(s))
        set.add(s.toLowerCase());  // added 04/21/2008
    }
    return set;
  }

  /**
   * Converts a string to unicode
   * @param s
   * @return
   */
  public static String toUnicode(String s) {
    if (s == null) return null;
    try {
      s = new String(s.getBytes(COMMON_ENCODING), UNICODE);
    } catch (UnsupportedEncodingException e) {
      log.error(e);
    }
    return s.trim();
  }

  public static URL toURL(String s) {
    try {
      return new URL(s);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  public static void writeToFile(File out, String content) {
    writeToFile(out, content, UNICODE, false);
  }

  public static void writeToFile(File out, String content, boolean append) {
    writeToFile(out, content, UNICODE, append);
  }
  
  public static void writeToFile(File out, String content, String charset, boolean append) {
    if (out == null || content == null) return;
    try {
      BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out, append), charset));
      bWriter.write(content);
      bWriter.close();
    } catch (IOException e) {
      log.error("Writing to " + out + ": " + e);
    }
  }

public static Object toReadableString(Set<EntityLiteral> contents) {
        Set<EntityLiteral> seeds = new TreeSet<EntityLiteral>();
        if (contents != null)
          seeds.addAll(contents);
        StringBuffer buf = new StringBuffer();
        buf.append("{");
        for (EntityLiteral seed : seeds) {
          if (buf.length() > 1)
            buf.append(", ");
          buf.append(seed.toString());
        }
        buf.append("}");
        return buf.toString();
      }
}
