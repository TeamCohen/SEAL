/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.Helper;

public class NonBlockingFetcher {
  private static Logger log = Logger.getLogger(NonBlockingFetcher.class); 
  
  // this represents one web page
  private static class Work {
    public URL url;
    public ByteBuffer httpRequest;
    public InetSocketAddress isa;
    public boolean success = false;
    public final ByteBuffer buffer = ByteBuffer.allocateDirect(DOC_BUFFER_SIZE);
  }
  
  // extracts charset value from <meta> tag
  public static final Pattern CHARSET_PAT = Pattern.compile("(?i)charset=\"?([\\w-]+)");
  public static final String DEFAULT_ENCODING = "US-ASCII";
  public static final long DEFAULT_MIN_TIMEOUT = 5 * 1000; // milliseconds

  public static final int DOC_BUFFER_SIZE = 1024 * 1024;  // allocate 1MB for each web page
  public static final int DIRECT_BUFFER_SIZE = 1024;  // allocate 1KB for direct buffers
  public static final int HTTP_PORT = 80;

  private static ByteBuffer buffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);;
  private static Charset charset = Charset.forName(DEFAULT_ENCODING);
  private static Selector selector;
  private static URL[] urls;
  private static Work[] works;
  private static List<String> documents;
  private static long timeout;
  private static long startTime;
  private static int numUnfinished;
  
  public static List<String> download(List<URL> urlList) {
    int numURLs = urlList.size();
    numUnfinished = numURLs;
    timeout = estimateTimeOut(numURLs);
    urls = urlList.toArray(new URL[numURLs]);
    works = new Work[numURLs];
    documents = new ArrayList<String>(numURLs);
    
    log.info("Connecting to servers (this may take a while)... ");
    startTime = System.currentTimeMillis();
    resolveHostNames();
    try {
      selector = Selector.open();
    } catch (IOException e) {
      log.error("IO error: " + e);
    }
    for (Work work : works) {
      if (work == null)
        numUnfinished--;
      else add(work);
    }
    Helper.printElapsedTime(startTime);
    
    log.info("Retrieving webpages... (will time out in " + timeout / 1000.0 + " seconds)");
    startTime = System.currentTimeMillis();
    retrieveWebPages();
    log.info("Number of unfinished URL(s): " + numUnfinished);
    Helper.printElapsedTime(startTime);
    
    log.info("Post processing each retrieved webpage...");
    startTime = System.currentTimeMillis();
    processDocuments();
    Helper.printElapsedTime(startTime);
    return documents;
  }
  
  public static void main(String args[]) throws Exception {
    List<URL> urls = new ArrayList<URL>();
    urls.add(new URL("http://www.google.com.tw"));
    urls.add(new URL("http://www.rcwang.com"));
    urls.add(new URL("http://www.msn.com.tw"));
    urls.add(new URL("http://www.ettoday.com"));
    
    List<String> docs = NonBlockingFetcher.download(urls);
    for (String doc : docs)
      Helper.writeToFile(new File(doc.hashCode() + ".html"), doc);
  }

  private static boolean add(Work work) {
    SocketChannel channel = null;
    try {
      channel = SocketChannel.open();
      channel.configureBlocking(false);
      channel.connect(work.isa);
      channel.register(selector, SelectionKey.OP_CONNECT, work);
    } catch (Exception ioe) {
      if (channel != null) {
        try {
          channel.close();
        } catch (IOException ioe2) {
          log.error("Unable to close channel: " + ioe2);
        }
      }
      log.error("Channel creation or registration failed: " + ioe);
      numUnfinished--;
      return false;
    }
    return true;
  }

  private static ByteBuffer buildHTTPRequest(URL url, CharsetEncoder encoder) {
    String file = url.getFile();
    if (file.length() == 0)
      file = "/";
    
    StringBuffer request = new StringBuffer();
    request.append("GET ").append(file).append(" HTTP/1.0\r\n");
    request.append("Host: ").append(url.getHost()).append("\r\n");
    request.append("Connection: close\r\n");
    request.append("Accept: text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\r\n");
    request.append("Accept-Language: zh-tw,zh-cn;q=0.8,en-us;q=0.6,en;q=0.4,ja;q=0.2\r\n");
    request.append("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\n");
    request.append("User-Agent: Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1) Gecko/20061010 Firefox/2.0\r\n");
    request.append("\r\n");

    try {
      return encoder.encode(CharBuffer.wrap(request));
    } catch (CharacterCodingException e) {
      log.error("Character coding error");
    }
    return null;
  }
  
  private static boolean doRead(SocketChannel channel, Work work) throws IOException {
    buffer.clear();
    boolean done = false;
    int numBytesRead = channel.read(buffer);
    if (numBytesRead == -1) {
      work.success = true;
      done = true;
    } else if (numBytesRead > 0) {
      buffer.flip();
      int bufSizeRemaining = work.buffer.remaining();
      // see if the bytes read can fit into the buffer
      if (bufSizeRemaining >= numBytesRead) {
        // buffer has enough space left
        work.buffer.put(buffer);
      } else if (bufSizeRemaining > 0) {
        // buffer doesn't have enough space, will discard bytes that don't fit
        buffer.limit(bufSizeRemaining);
        work.buffer.put(buffer);
      }
    }
    return done;
  }
  
  private static boolean doWrite(SocketChannel channel, Work work) throws IOException {
    int rem = work.httpRequest.remaining();
    int num = channel.write(work.httpRequest);
    // Continue writing until everything has been written
    return (num == rem);
  }
  
  private static long estimateTimeOut(int numURLs) {
    return (long) Math.max(Math.sqrt(numURLs) * 1000, DEFAULT_MIN_TIMEOUT);
  }
  
  private static void finished(SocketChannel channel, SelectionKey key, Work work) {
    key.cancel();
    try {
      channel.close();
    } catch (IOException ioe) {
      log.error("Failed to close socket: " + ioe.toString());
    } 
    String status = work.success ? "Success" : "Failed ";
    log.debug("[" + (urls.length - numUnfinished + 1) + "/" + urls.length + "] " + status + ": " + work.url);
    numUnfinished--;
  }

  private static void processDocuments() {
    CharsetDecoder decoder = charset.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.IGNORE);
    decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
    
    // perform 1 to 2-pass decoding on every document
    for (int i = 0; i < works.length; i++) {
      documents.add(null);
      if (works[i] == null)
        continue;
      
      URL url = works[i].url;
      log.debug("[" + (i + 1) + "/" + urls.length + "] Processing: " + url);
      String encoding = DEFAULT_ENCODING;
      String doc = null;
      ByteBuffer buffer = works[i].buffer;
      buffer.flip();
      
      try {
        // try to use default encoding to decode the document
        doc = decoder.decode(buffer).toString();
        
        // identify encoding by looking into the <meta> tag
        Matcher m = CHARSET_PAT.matcher(doc);
        if (m.find()) {
          encoding = m.group(1).toUpperCase();
          log.debug("Encoding identified as: " + encoding);
        } else {
          log.debug("Encoding could not be identified! Using the default: " + DEFAULT_ENCODING);
        }

        // if the identified encoding is different from the default encoding
        if (!encoding.equals(DEFAULT_ENCODING)) {
          // decode again using the identified encoding 
          CharsetDecoder d = Charset.forName(encoding).newDecoder();
          d.onUnmappableCharacter(CodingErrorAction.IGNORE);
          d.onMalformedInput(CodingErrorAction.IGNORE);
          buffer.flip();
          doc = d.decode(buffer).toString();
        }
      } catch (Exception e) {
        log.error("Character coding error: " + e);
        continue;
      }
      documents.set(i, removeHTTPHeader(doc));
//      doc = removeHTTPHeader(doc);
//      doc = CacheRecoverer.recover(url, doc);
//      documents.set(i, doc);
    }
  }

  private static void processKeys() {
    Set<SelectionKey> keys = selector.selectedKeys();
    for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
      SelectionKey key = iter.next();
      iter.remove();
      Work work = (Work) key.attachment();
      SocketChannel channel = (SocketChannel) key.channel();
      try {
        // If timed out
        if (System.currentTimeMillis() - startTime > timeout) {
          finished(channel, key, work);
          continue;
        }
        if (key.isConnectable() && channel.finishConnect()) {
          // If the Channel is connected, setup the Channel to
          // write the HTTP message to the remote server
          key.interestOps(SelectionKey.OP_WRITE);
        } else if (key.isWritable()) {
          // If the Channel is finished writing, setup the
          // Channel to read the HTTP response
          if (doWrite(channel, work))
            key.interestOps(SelectionKey.OP_READ);
        } else if (key.isReadable()) {
          // If the Channel is finished reading, call finished
          // to complete the work
          if (doRead(channel, work))
            finished(channel, key, work);
        }
      } catch (IOException ioe) {
        log.error("Failure during IO operation");
        finished(channel, key, work);
      }
    }
  }

  private static String removeHTTPHeader(String s) {
    String headerBoundary = "\r\n\r\n";
    int boundaryOffset = s.indexOf(headerBoundary);
    if (boundaryOffset > -1)
      return s.substring(boundaryOffset + headerBoundary.length());
    return s;
  }

  private static void resolveHostNames() {
    CharsetEncoder encoder = charset.newEncoder();
    for (int i = 0; i < urls.length; i++) {
      // this is a blocking operation, may take a long time
      works[i] = new Work();
      works[i].url = urls[i];
      works[i].httpRequest = buildHTTPRequest(urls[i], encoder);
      works[i].isa = new InetSocketAddress(urls[i].getHost(), HTTP_PORT);
    }
  }

  private static void retrieveWebPages() {
    try {
      while (numUnfinished > 0) {
        int num = selector.selectNow();
        if (num > 0) processKeys();
        if (System.currentTimeMillis() - startTime > timeout) {
          log.error("TIMED OUT! Time exceeds " + timeout / 1000.0 + " sec.");
          break;
        }
      }
      selector.close();
    } catch (IOException ioe) {
      log.error("IO Error: " + ioe.toString());
    }
  }
}
