package com.rcwang.seal.util;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.LangProvider;
import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.WrapperFactory;
import com.rcwang.seal.fetch.Document;


public class ExampleTextMaker {
  
  public static Logger log = Logger.getLogger(ExampleTextMaker.class);
  private static Random random = new Random(0);
  
  public static char getRandomChar() {
    char c = (char) (97 + random.nextInt(26));
    if (random.nextInt(2) == 0)
      c = Character.toUpperCase(c);
    return c;
  }
  
  public static String getRandomString(int length) {
    StringBuffer buf = new StringBuffer();
    for (; length >= 0; length--)
      buf.append(getRandomChar());
    return buf.toString();
  }
  
  public static String getRandomStringAndLength(int n) {
    return getRandomString(random.nextInt(n));
  }
  
  public static void main(String args[]) throws MalformedURLException {
    
    String[] contents = new String[] {
        "ford", "nissan", "toyota", "acura", "audi"
    };
    
    for (int i = 0; i < contents.length; i++)
      contents[i] = randomCapitalize(contents[i]);
    
    String[] contexts = new String[] {
        "KxH" + Wrapper.SEPARATOR + "xjH",
        "xKH" + Wrapper.SEPARATOR + "xkr",
    };
    
    String text = makeText(contents, contexts);
    text = "GtpKxHnIsSaNxjHJgleAuDialcLBxKHforDxkrpWNaCMwAAHOFoRduohdEXocUvaGKxHaCuRAxjHjnOxoTOyOTazxKHAUdIxkrOyQKxHToYotAxjHCRdmLxaCuRAPprtqOVKxHfoRdxjHaJAScRFrlaFoRDofwNLWxKHtOYotaxkrHxQKlacXlGEKtxKHNisSanxkrEq";
    text = "GtpKxHnIsSaNoKpjaPaNxjHJglelcLBxKHforDEFcuSAxkrpWNaAAHOFoRdaUSauohdKxHaCuRAoKpJapANxjHnOxoTOyOTaVjApaNzxKHAUdIEFcgErmANyxkrOyQKxHToYotAoKpJApaNxjHCRdmtqOVKxHfoRdoKpusAxjHaJASfrlaFoRDpuSawNLWxKHtOYotaEFcjAPanxkrHxQKGEKtxKHNisSanEFcJApAnxkrEq";
    text = "GtpKxHnIsSaNoKpjaPaNxjHJgleTuoLpBlcLBxKHforDEFcuSAxkrpWNapnIkAAHOFoRdawHDaUSauohdeQsKxHaCuRAoKpJapANxjHdIjWnOxoTOyOTaVaqjApaNzxKHAUdIEFcgErmANyxkrOyQKxHToYotAoKpJApaNxjHCRdmtqOVKxHfoRdoKpusAxjHaJASzEinSfrlaFoRDLMmpuSaofwNLWxKHtOYotaEFcjAPanxkrHxQKzrHpoKdGEKtxKHNisSanEFcJApAnxkrEq";
    log.info("Text: " + text);
    Document doc = new Document(text, null);
    
    List<String> seeds = Arrays.asList(
        "ford::usa", "nissan::japan", "toyota::japan"
    );
    
    WrapperFactory wf = new WrapperFactory();
    wf.setLangID(LangProvider.ENG[LangProvider.ID]);
    wf.setSeeds(new EntityList(seeds));
    Set<Wrapper> wrappers = wf.build(doc);
    
    for (Wrapper wrapper : wrappers) {
      log.info(Helper.repeat('-', 80));
      log.info(wrapper.toDetails());
    }
  }
  
  public static String randomCapitalize(String s) {
    char[] charArray = s.toLowerCase().toCharArray();
    for (int i = 0; i < charArray.length; i++)
      if (random.nextInt(2) == 0)
        charArray[i] = Character.toUpperCase(charArray[i]);
    return new String(charArray);
  }
  
  public static String makeText(String[] seeds, String[] contexts) {
    final int N = 10;
    
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < contexts.length; i++) {
      String context = contexts[i];
      for (String seed : seeds) {
        if (i == 0 && seed.equalsIgnoreCase("audi")) continue;
        if (i == 1 && seed.equalsIgnoreCase("acura")) continue;
        String s = context.replace(Wrapper.SEPARATOR, seed);
        list.add(s);
      }
    }
    Collections.shuffle(list, random);
    
    StringBuffer buf = new StringBuffer();
    buf.append(getRandomStringAndLength(N));
    for (int i = 0; i < list.size(); i++) {
      String s = list.get(i);
      if (random.nextInt(2) == 0) {
        String randomSeed = seeds[random.nextInt(seeds.length)];
        buf.append(randomSeed);
        i--;
      } else buf.append(s);
      String randomStr = getRandomStringAndLength(N);
      buf.append(randomStr);
    }
    return buf.toString();
  }
}
