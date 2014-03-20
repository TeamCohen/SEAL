/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.io.File;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.rcwang.seal.util.Helper;
import com.rcwang.seal.util.XMLUtil;

public class EphyraLoader {

  public static Logger log = Logger.getLogger(EphyraLoader.class);
  
  public static EphyraXML load(File xmlFile) {
    String xml = Helper.readFile(xmlFile);
    Document document;
    try {
      document = XMLUtil.riskyParse(xml);
    } catch (Exception e) {
      log.error(e.toString());
      return null;
    }
    
    // load question
    EphyraXML ephyraXML = new EphyraXML();
    loadQuestion(document, ephyraXML);
    if (ephyraXML.getTrecID() == null) {
      log.error(xmlFile + " is not a list question!!!");
      return null;
    }

    loadFocus(document, ephyraXML);
    loadAnswerType(document, ephyraXML);
    loadKeywords(document, ephyraXML);
    loadAnswers(document, ephyraXML);
    
    // sort the keywords and answers
    Collections.sort(ephyraXML.getKeywords());
    Collections.sort(ephyraXML.getAnswers());    
    return ephyraXML;
  }
  
  private static void loadAnswers(Document document, EphyraXML ephyraXML) {
    String[] requestFillPath = new String[] {"AnswerGenerator", "RequestFillSet", "RequestFill"};
    for (Node requestFillNode : XMLUtil.extractPaths(document, requestFillPath)) {
      Node ansNode = XMLUtil.extractNode(requestFillNode, "Answer");
      String confidenceStr = XMLUtil.extractNode(ansNode, "@confidence").getNodeValue();
      String ans = ansNode.getTextContent();
      ephyraXML.addAnswer(ans, Double.parseDouble(confidenceStr));
    }
  }
  
  private static void loadAnswerType(Document document, EphyraXML ephyraXML) {
    String[] ansTypePath = new String[] {"AnswerGenerator", "RequestObject", "AnswerTypeInfo", "AnswerType"};
    for (Node ansType: XMLUtil.extractPaths(document, ansTypePath)) {
      String[] types = ansType.getTextContent().split("->");
      for (String type : types)
        ephyraXML.addAnswerType(type);
    }
  }
  
  private static void loadFocus(Document document, EphyraXML ephyraXML) {
    String[] focusPath = new String[] {"AnswerGenerator", "RequestObject", "AnswerTypeInfo", "Focus"};
    Node focus = XMLUtil.extractPath(document, focusPath);
    if (focus != null)
      ephyraXML.setFocus(focus.getTextContent());
  }
  
  private static void loadKeywords(Document document, EphyraXML ephyraXML) {
    String[] keywordPath = new String[] {"AnswerGenerator", "RequestObject", "Keywords", "Keyword"};
    for (Node keyNode : XMLUtil.extractPaths(document, keywordPath)) {
      Node termNode = XMLUtil.extractNode(keyNode, "Term");
      String relFreqStr = XMLUtil.extractNode(termNode, "@relFrequency").getNodeValue();
      String key = termNode.getTextContent();
      if (!ephyraXML.getKeywords().contains(key))
        ephyraXML.addKeyword(key, Double.parseDouble(relFreqStr));
    }
  }
  
  private static void loadQuestion(Document document, EphyraXML ephyraXML) {
    String[] questionPath = new String[] {"AnswerGenerator", "RequestObject", "Question"};
    Node question = XMLUtil.extractPath(document, questionPath);
    String questionType = XMLUtil.extractNode(question, "@type").getNodeValue();
    if (questionType == null || !questionType.equals("list"))
      return;
    ephyraXML.setQuestion(question.getTextContent());
    String trecID = XMLUtil.extractNode(question, "@trecID").getNodeValue();
    ephyraXML.setTrecID(trecID);
  }
}
