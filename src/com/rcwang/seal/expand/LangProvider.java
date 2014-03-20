/**************************************************************************
 * Developed by Carnegie Mellon Language Technologies Institute
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.expand;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class LangProvider {

  public static class Lang {
    String id, name;
    Pattern pattern;
    
    public Lang() {}
    
    public Lang(String[] array) {
      setId(array[ID]);
      setName(array[NAME]);
      String pattern = array[ALPHABET];
      String type = array[TYPE];
      if (type.equals(NON_SPACE_TYPE))
        pattern = toNonSpacePattern(pattern);
      else if (type.equals(HAS_SPACE_TYPE))
        pattern = toHasSpacePattern(pattern);
      setPattern(pattern);
    }
    
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final Lang other = (Lang) obj;
      if (id == null) {
        if (other.id != null) return false;
      } else if (!id.equals(other.id)) return false;
      return true;
    }
    
    public String getId() {
      return id;
    }
    
    public String getName() {
      return name;
    }
    
    public Pattern getPattern() {
      return pattern;
    }
    
    @Override
    public int hashCode() {
      return 31 + ((id == null) ? 0 : id.hashCode());
    }
    
    public void setId(String id) {
      this.id = id;
    }
    
    public void setName(String name) {
      this.name = name;
    }
    
    public void setPattern(Pattern pattern) {
      this.pattern = pattern;
    }
    
    public void setPattern(String pattern) {
      this.pattern = Pattern.compile(pattern);
    }
  }
  
  public static final int ID = 0;
  public static final int NAME = 1;
  public static final int ALPHABET = 2;
  public static final int TYPE = 3;
  
  public static final String NON_SPACE_TYPE = "0";
  public static final String HAS_SPACE_TYPE = "1";
  public static final String NEITHER_TYPE = "2";
  
  // languages with spaces
  public static final String[] ENG = new String[] {
    "en", "English", "[A-Za-z]", HAS_SPACE_TYPE
  };
  public static final String[] POR = new String[] {
    "pt", "Portuguese", "[A-Za-zÁÂÃÀÇÉÊÍÓÔÕÚáâãàçéêíóôõú]", HAS_SPACE_TYPE
  };
  public static final String[] FRE = new String[] {
    "fr", "French", ENG[ALPHABET], HAS_SPACE_TYPE
  };
  public static final String[] ITA = new String[] {
    "it", "Italian", "[A-IL-VZa-il-vz]", HAS_SPACE_TYPE
  };
  public static final String[] SPA = new String[] {
    "es", "Spanish", "[A-Za-zÑñ]", HAS_SPACE_TYPE
  };
  public static final String[] GER = new String[] {
    "de", "German", "[A-Za-zÄÖÜäöüß]", HAS_SPACE_TYPE
  };
  
  // languages without spaces
  public static final String[] CHT = new String[] {
    "zh-TW", "Traditional Chinese", "[\\p{InCJKUnifiedIdeographs}\\p{InBopomofo}\u02C7\u02CA\u02CB\u30FB]", NON_SPACE_TYPE
  };
  public static final String[] CHS = new String[] {
    "zh-CN", "Simplified Chinese", "[\\p{InCJKUnifiedIdeographs}]", NON_SPACE_TYPE
  };
  public static final String[] JAP = new String[] {
    "ja", "Japanese", "[\\p{InCJKUnifiedIdeographs}\\p{InKatakana}\\p{InHiragana}]", NON_SPACE_TYPE
  };
  public static final String[] KOR = new String[] {
    "ko", "Korean", "[\\p{InHangulSyllables}]", NON_SPACE_TYPE
  };
  
  // universal language
  public static final String[] UNI = new String[] {
    "un", "Universal", "[^\\x22-\\x25\\x28-\\x2C\\x3B-\\x40\\x5B-\\x5E\\x7B-\\x7E\\x2F\\x60]{2,}", NEITHER_TYPE
  };
  
  private static Set<Lang> langList = init();
  
  public static boolean supports(String id) {
    return getLang(id) != null;
  }
  
  /**
   * Auto-detect the language of the input collection of strings
   * @param ss input collection of strings
   * @return a Lang object, or null if they are not all in the same language
   */
  public static Set<Lang> detect(EntityList entities) {
    StringBuffer buf = new StringBuffer();
    for (Entity entity : entities)
      for (String name : entity.getNames())
        buf.append(name);
    return detect(buf.toString());
  }
  
  /**
   * Auto-detect the language of the input string
   * in the same order as in the Lang list
   * @param s input string
   * @return a Lang object, or null if the language is unrecognizable
   */
  public static Set<Lang> detect(String s) {
    Set<Lang> langSet = new LinkedHashSet<Lang>();
    for (Lang lang : langList)
      if (lang.getPattern().matcher(s.trim()).matches())
         langSet.add(lang);
    return langSet;
  }
  
  public static Set<Lang> getAllLangs() {
    return langList;
  }

  public static Lang getLang(String id) {
    if (id == null) return null;
    for (Lang lang : langList)
      if (lang.getId().equals(id))
        return lang;
    return null;
  }
  
  /**
   * Creates a Lang object using id1's ID and id2's pattern
   * @param id1
   * @param id2
   * @return
   */
  public static Lang getLang(String id1, String id2) {
    Lang lang1 = getLang(id1);
    if (lang1 == null) return null;
    Lang lang2 = getLang(id2);
    if (lang2 == null) return null;

    Lang lang = new Lang();
    lang.setName(lang1.getName() + " + " + lang2.getName());
    lang.setId(lang1.getId()); // web page retrieval langId
    lang.setPattern(lang2.getPattern()); // text extraction langId
    return lang;
  }
  
  public static Lang getLang(String[] array) {
    return getLang(array[ID]);
  }
  
  public static String getName(String id) {
    Lang lang = getLang(id);
    if (lang == null) return null;
    return lang.getName();
  }
  
  public static void main(String args[]) {
    String s = "Richard's office is in NSH-4622";
    Set<Lang> langSet = detect(s);
    
    System.out.println(s);
    if (!langSet.isEmpty())
      for (Lang lang : langSet)
        System.out.println(lang.getName());
    
    s = "波ㄆㄛ羅ㄌㄨㄛˊ蜜ㄇㄧˋ";
    System.out.println(LangProvider.getLang("zh-TW").pattern.matcher(s).matches());
  }
  
  private static Set<Lang> init() {
    Set<Lang> list = new LinkedHashSet<Lang>();
    list.add(new Lang(ENG));
    list.add(new Lang(CHT));
    list.add(new Lang(CHS));
    list.add(new Lang(JAP));
    list.add(new Lang(KOR));
    list.add(new Lang(POR));
    list.add(new Lang(FRE));
    list.add(new Lang(ITA));
    list.add(new Lang(SPA));
    list.add(new Lang(GER));
    list.add(new Lang(UNI));  // Universal (must be last)
    return list;
  }
  
  private static String toHasSpacePattern(String alphabets) {
    String puncts = "[ \\.:'&-]";
    String digitAlpha = "[\\d" + alphabets + "]";
    return alphabets + digitAlpha + "*(?:" + puncts + "[ ]?" + digitAlpha + "+)*";
  }
  
  private static String toNonSpacePattern(String alphabets) {
    return "[\\d" + alphabets + "]+";
  }
  
}
