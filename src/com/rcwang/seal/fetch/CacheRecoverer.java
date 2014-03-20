/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.fetch;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.Helper;

public class CacheRecoverer {
  // removes HTML tags that highlight keywords
  public static final Pattern GOOGLE_HIGHLIGHT_PAT =
    Pattern.compile("<b style=\"color:(?:black|white);background-color:#(?:ffff66|a0ffff|99ff99|ff9999|ff66ff|880000|00aa00|886800|004699|990099)\">(.+?)</b>");
  public static final Pattern YAHOO_HIGHLIGHT_PAT =
    Pattern.compile("<font style=\"color:(?:black|white);background-color:(?:ffff66|A0ffff|99ff99|ff9999|ff66ff|880000|00aa00|886800|004699|990099)\">(.+?)</font>");

  // identifies weather a URL points to a cached page or not
  public static final String GOOGLE_CACHE_URL_KEY = "/search?q=cache:";
  public static final String GOOGLE_NO_CACHE_KEY = " - did not match any documents.  <br><br>Suggestions:";
  public static final String YAHOO_CACHE_URL_KEY = "/search/cache%3f";

  // identifies header boundary in a cached page
  public static final String GOOGLE_HEADER_DELIM = "</td></tr></table></td></tr></table>\r\n<hr>\r\n<div style=\"position:relative\">";
  public static final String YAHOO_HEADER_DELIM = "<td bgcolor=black height=1 colspan=2><spacer type=block width=1 height=1></td></tr></table>";
  public static final String YAHOO_FOOTER_DELIM = "<script language=javascript>\r\nif(window.yzq_p==null)";
  
  private static Logger log = Logger.getLogger(CacheRecoverer.class); 

  public static void main(String args[]) throws MalformedURLException {
//    String yahoo = Helper.readFile(new File("google1.html"));
//    String recovered = recover(new URL("http://www.yahoo.com" + GOOGLE_CACHE_URL_KEY), yahoo);
//    Helper.writeToFile(new File("_google1.html"), recovered);
    
    URL yURL = new URL("http://rds.yahoo.com/_ylt=A0geu6dV5rZGrQABASNXNyoA/SIG=16t67jcp2/EXP=1186478037/**http%3a//216.109.125.130/search/cache%3fei=UTF-8%26p=rcwang%26fr=yfp-t-471%26u=www.pcdvd.com.tw/showthread.php%253Ft%253D420299%2526page%253D6%2526pp%253D10%26w=rcwang%26d=FLFgLuljO9R-%26icp=1%26.intl=us");
    System.out.println(recover(yURL));
  }
  
  public static String recover(URL url) {
    if (url == null)
      return null;
    String urlStr = url.getFile();
    if (isGoogleCache(url)) {
      return urlStr.substring(29, urlStr.indexOf('+', 30));
    } else if (isYahooCache(url)) {
      int start_pos = urlStr.indexOf("%26u=");
      int end_pos = urlStr.indexOf("%26w=");
      if (start_pos > -1 && end_pos > -1)
        return Helper.decodeURLString(Helper.decodeURLString(urlStr.substring(start_pos+5, end_pos)));
    }
    return url.toString();
  }
  
  public static String recover(URL url, String s) {
    if (url == null || s == null)
      return null;
    String recovered;
    if (isGoogleCache(url)) {
      recovered = recoverGoogleCache(s);
      if (recovered == null) {
        log.error("Could not parse the following Google cached webpage, please check the parser!\n" + url);
        return null;
      }
      return recovered;
    } else if (isYahooCache(url)) {
      recovered = recoverYahooCache(s);
      if (recovered == null) {
        log.error("Could not parse the following Yahoo! cached webpage, please check the parser!\n" + url);
        return null;
      }
      return recovered;
    }
    return s;
  }

  private static boolean isGoogleCache(URL url) {
    return url != null && url.toString().indexOf(GOOGLE_CACHE_URL_KEY) > -1;
  }

  private static boolean isYahooCache(URL url) {
    return url != null && url.toString().indexOf(YAHOO_CACHE_URL_KEY) > -1;
  }

  private static String recoverGoogleCache(String s) {
    if (s == null)
      return null;
    int pos;
    if ((pos = s.indexOf(GOOGLE_HEADER_DELIM)) > -1) {
      s = s.substring(pos + GOOGLE_HEADER_DELIM.length());
      s = GOOGLE_HIGHLIGHT_PAT.matcher(s).replaceAll("$1");
      return s;
    } else if (s.indexOf(GOOGLE_NO_CACHE_KEY) > -1) {
      return s;
    }
    return null;
  }

  private static String recoverYahooCache(String s) {
    if (s == null)
      return null;
    int start_pos = s.indexOf(YAHOO_HEADER_DELIM);
    int end_pos = s.indexOf(YAHOO_FOOTER_DELIM);
    if (start_pos > -1 && end_pos > -1) {
      s = s.substring(start_pos + YAHOO_HEADER_DELIM.length(), end_pos);
      s = YAHOO_HIGHLIGHT_PAT.matcher(s).replaceAll("$1");
      return s;
    }
    return null;
  }
}
