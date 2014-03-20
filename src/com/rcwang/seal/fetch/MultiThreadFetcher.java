/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.ByteBuffer;
import com.rcwang.seal.util.Helper;

public class MultiThreadFetcher {
  
  /********************** Parameters **************************/
  // encoding used for extracting charset from the <meta> tag of web documents
  public static final String DEFAULT_ENCODING = "UTF-8";
  // regex pattern to extract charset value from <meta> tag
  public static final Pattern CHARSET_PAT = Pattern.compile("(?i)content=[^<>]+?charset=\"?([\\w-]+)");
  public static final List<String> ILLEGAL_PROTOCOLS = Arrays.asList( "ftp" );
  public static final int DEFAULT_TIMEOUT_IN_MS = 15 * 1000; // 15 seconds
  /************************************************************/
  
  public static Logger log = Logger.getLogger(MultiThreadFetcher.class);

  /**
   * Downlaods a list of URLs with auto timeout and no document size limits
   * @param urls a list of URLs
   * @return a list of URL contents
   */
  public static List<String> fetch(List<URL> urls) {
    return fetch(urls, 0, 0);
  }
  
  /**
   * Downloads a list of URLs with time and document size constraints
   * @param urls a list of URLs
   * @param timeOutInMS duration to time out in milliseconds
   * @param maxDocSizeInKB maximum document size in kilobytes
   * @return a list of URL contents
   */
  public static List<String> fetch(List<URL> urls, int timeOutInMS, int maxDocSizeInKB) {
    if (urls.isEmpty()) return Collections.EMPTY_LIST;
    int numRemain = urls.size();
    boolean[] isDone = new boolean[urls.size()];  // default to false
    WebFetchingThread[] wfts = new WebFetchingThread[urls.size()];
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < urls.size(); i++) {
      URL url = urls.get(i);
      if (url == null || 
          ILLEGAL_PROTOCOLS.contains(url.getProtocol()) || 
          URLBlackLister.isListed(url)) {
        isDone[i] = true;
        numRemain--;
        continue;
      }
      wfts[i] = new WebFetchingThread(url, maxDocSizeInKB, timeOutInMS, timeOutInMS);
      Thread thread = new Thread(wfts[i], Integer.toString(i));
      thread.setDaemon(true); // JVM will exit if all threads running are daemon threads
      thread.start(); // start running the thread
    }
    
    // initialize timeout mechanism
    int prevSecLeft = -1;
    boolean isAutoTimeOut = false;
    if (timeOutInMS <= 0) {
      timeOutInMS = DEFAULT_TIMEOUT_IN_MS;
      isAutoTimeOut = true;
    }

    String[] docs = new String[urls.size()];  // default to null
    for (int i = 0; numRemain > 0; i = (i+1) % urls.size()) {
      if (isDone[i]) continue;
      if (wfts[i].success) {
        String backupEncoding = identifyEncoding(wfts[i].contentType);
        docs[i] = htmlEncode(i, wfts[i].buffer.getBuffer(), backupEncoding);
      }
      if (wfts[i].success || wfts[i].done) {
        isDone[i] = true;
        numRemain--;
      }
      
      // calculate amount of time left
      long timeLeft = timeOutInMS - (System.currentTimeMillis() - startTime);
      if (isAutoTimeOut) {
        long autoTimeLeft = getAutoTimeLeft(numRemain);
        if (autoTimeLeft < timeLeft) {
          // decrease timeout
          timeOutInMS -= (timeLeft - autoTimeLeft);
          timeLeft = autoTimeLeft;
        }
      }
      int currSecLeft = (int) Math.round(timeLeft/1000);
      if (currSecLeft != prevSecLeft) {
        System.out.print("[" + currSecLeft + "s left] Remaining " + numRemain + " webpages...\r");
        prevSecLeft = currSecLeft;
      }

      if (timeLeft < 0) {
        System.out.println();
        log.warn("TIMED OUT! Time exceeds " + timeOutInMS / 1000.0 + " seconds.");
        // signal all threads that they have timed out!
        for (WebFetchingThread wft : wfts) {
          if (wft != null)
            wft.hasTimedOut = true;
        }
        break;
      }
    }
    URLBlackLister.saveList();
    return Arrays.asList(docs);
  }
  
  private static long getAutoTimeLeft(int numActiveFetcher) {
    return (long) (Math.log10(numActiveFetcher)*5+3)*1000;
  }
  
  public static void main(String[] args){
    long startTime = System.currentTimeMillis();
    List<URL> urls = new ArrayList<URL>();
    try {
      for (int i = 0; i < 1; i++) {
        urls.add(new URL("http://www.cornwallonlineholidays.co.uk/attractions-blog/2005_12_01_cornwall-attractions_archive.html"));
        urls.add(new URL("http://wiki.tianwang.grids.cn/pub/Main/QAofLiu/141.2"));
        urls.add(new URL("http://www.frank-sinatra.de/filmlis.htm"));
        urls.add(new URL("http://www.bali-travelnews.com/Batrav/Batrav170/people.htm"));
        urls.add(new URL("http://www.bali-travelnews.com/Batrav/Batrav168/guide_1.htm"));
        urls.add(new URL("http://www.ettoday.com/"));
        urls.add(new URL("http://www.newegg.com/"));
        urls.add(new URL("http://www.abc.com/"));
      }
    } catch (MalformedURLException e1) {
      e1.printStackTrace();
    }
    int timeout = 10;
    int maxDocSize = 512;
    URLBlackLister.setListFile(new File("filteredURLs.txt"));
    List<String> docs = MultiThreadFetcher.fetch(urls, timeout*1000, maxDocSize);
    for (int i = 0; i < docs.size(); i++) {
      String s = docs.get(i);
      if (s == null) s = "";
      else s = s.toLowerCase();
      if (s.indexOf("<html>") != -1 && s.indexOf("</html>") != -1)
        log.info("[" + i + "] Succeeded!");
      else log.info("[" + i + "] Failed!");
      Helper.writeToFile(new File(i + ".html"), docs.get(i));
    }

    Helper.printElapsedTime(startTime);
    Helper.printMemoryUsed();
  }
  
  private static String htmlEncode(int id, byte[] bytes, String backupEncoding) {
    String doc = null;
    try {
      doc = new String(bytes, DEFAULT_ENCODING);
      // identify encoding by looking into the <meta> tag
      Matcher m = CHARSET_PAT.matcher(doc);
      if (m.find()) {
        String encoding = m.group(1).toUpperCase();
        log.debug("[" + id + "] Encoding identified as: " + encoding);
        if (!encoding.equals(DEFAULT_ENCODING))
          doc = new String(bytes, encoding);
      } else if (backupEncoding != null) {
        log.debug("[" + id + "] Encoding could not be identified! Using the backup: " + backupEncoding);
        doc = new String(bytes, backupEncoding);
      } else {
        log.debug("[" + id + "] Encoding could not be identified! Using the default: " + DEFAULT_ENCODING);
      }
    } catch (UnsupportedEncodingException e) {
      log.debug("[" + id + "] " + e.toString());
    }
    return doc.trim();
  }
  
  private static String identifyEncoding(String contentType) {
    if (contentType == null)
      return null;
    Matcher m = CHARSET_PAT.matcher(contentType);
    if (m.find())
      return m.group(1).toUpperCase();
    return null;
  }
}
  
class WebFetchingThread implements Runnable {
  
  /********************** Parameters **************************/
  // buffer size for each read from remote server
  public static final int BUFFER_SIZE = 1024;
  // request method: GET, POST, etc.
  public static final String REQUEST_METHOD = "GET";
  // HTTP request properties in the format of: key, value, key, value, etc.
  public static final String[] HTTP_REQUEST = new String[] {
    "Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5",
    "Accept-Language", "zh-tw,zh-cn;q=0.8,en-us;q=0.6,en;q=0.4,ja;q=0.2",
    "Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7",
    "User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1) Gecko/20061010 Firefox/2.0",
  };
  public static final List<String> HOST_LEVEL_EXCEPTIONS = Arrays.asList(
      "java.net.UnknownHostException",
      "java.net.NoRouteToHostException"
  );
  /************************************************************/
  
  public static Logger log = Logger.getLogger(WebFetchingThread.class);
  // successfully retrieved a document
  public boolean success = false;
  // thread has done running (could be either successful or unsuccessful)
  public boolean done = false;
  // timeout signals by the MultiThreadFetcher
  public boolean hasTimedOut = false;
  public String contentType;
  public ByteBuffer buffer;
  public URL url;
  private int maxDocSizeInKB;
  private int connectionTimeout;
  private int readTimeout;
  
  // set request header
  private static void setRequestHeader(HttpURLConnection conn) {
    try {
      conn.setRequestMethod(REQUEST_METHOD);
    } catch (ProtocolException e) {
       e.printStackTrace();
    }
    for (int i = 1; i < HTTP_REQUEST.length; i+=2)
      conn.setRequestProperty(HTTP_REQUEST[i-1], HTTP_REQUEST[i]);
  }
  
  public WebFetchingThread(URL url) {
    this(url, 0, 0, 0);
  }
  
  public WebFetchingThread(URL url, int maxDocSizeInKB, int connectionTimeout, int readTimeout) {
    if (maxDocSizeInKB < 0 || connectionTimeout < 0 || readTimeout < 0)
      throw new IllegalArgumentException("Parameters must be greater than zero!");
    if (url == null)
      throw new IllegalArgumentException("URL cannot be null!");
    this.maxDocSizeInKB = maxDocSizeInKB;
    this.connectionTimeout = connectionTimeout;
    this.readTimeout = readTimeout;
    this.url = url;
    buffer = new ByteBuffer();
  }
  
  public void run() {
    log.debug("[" + Thread.currentThread().getName() + "] Fetching: " + url);
    BufferedInputStream input = null;

    try {
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(connectionTimeout);
      conn.setReadTimeout(readTimeout);
      setRequestHeader(conn);

      // initiate connection + open an input stream
      if (conn.getResponseCode() == 400) 
          input = new BufferedInputStream(conn.getErrorStream());
      else
          input = new BufferedInputStream(conn.getInputStream());
      contentType = conn.getContentType();
      int contentLength = conn.getContentLength();
      if (contentLength > -1 && maxDocSizeInKB < contentLength/1024)
        throw new IOException("Document size (" + contentLength/1024 + "KB) is larger than " + maxDocSizeInKB + "KB");

      // start reading from the stream
      byte[] bytes = new byte[BUFFER_SIZE];
      for (int numBytesRead; (numBytesRead = input.read(bytes)) != -1;) {
        buffer.append(bytes, 0, numBytesRead);
        if (hasTimedOut) {
          // timing out on read means the remote server is slow (perhaps due to high network latency)
          // we will not block this URL due to this reason
          log.debug("[" + Thread.currentThread().getName() + "] Overall Read Timed Out: " + url);
          return;
        }
        // throw exception if document size (KB) is too large
        if (maxDocSizeInKB > 0 && maxDocSizeInKB < buffer.length()/1024)
          throw new IOException("Document size (" + buffer.length()/1024 + "KB) is larger than " + maxDocSizeInKB + "KB");
      }
      // success only if all data is retrieved
      log.debug("[" + Thread.currentThread().getName() + "] Successfully Done: " + url);
      success = true;
    } catch (IOException e) {
      // if any IO error occurs, block the URL
      blockURL(url, e);
    } catch (IllegalArgumentException e) {
      // catch illegal argument exception
    } catch (Exception e) {
      // catch any other exception
    } finally {
      try { // close the opened stream
        if (input != null) {
          input.close();
          input = null;
        }
      } catch (IOException e) {}
      done = true;
    }
  }
  
  private void blockURL(URL url, IOException e) {
    log.debug("[" + Thread.currentThread().getName() + "] " + e.toString());
    boolean blockHost = HOST_LEVEL_EXCEPTIONS.contains(e.getClass().getName());
    URLBlackLister.addToList(url, blockHost);
    // if has timed out, then save this URL to the black list
    if (hasTimedOut)
      URLBlackLister.saveList();
  }
}
