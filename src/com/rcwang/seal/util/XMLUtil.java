/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * @author svoboda, rcwang
 * Created on Apr 20, 2005
 */
public class XMLUtil {
  static public Exception exception = null;
  static private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
  static private DocumentBuilder parser = null;
  
  /**
   *  XSL transform (identity) to create text from Document.
   */
  static private Transformer identityTransformer;
  
  private Document document;
  
  static {
    // XML initialization
    try {
      dbf = DocumentBuilderFactory.newInstance();
      parser = dbf.newDocumentBuilder();
    } catch (ParserConfigurationException x) {
      x.printStackTrace();
    }
  }

  static {
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    try {
      identityTransformer = transformerFactory.newTransformer();
    } catch (TransformerConfigurationException e) {
      e.printStackTrace();
    }
  }

  static public Document cloneDocument(Document document) {
    if (document == null) return null;
    Document result = newDocument();
    try {
      identityTransformer.transform(new DOMSource( document), new DOMResult( result));
    } catch (TransformerException e) {
      e.printStackTrace();
    }
    return result;
  }

  static public String document2String(Node document) {
    if (document == null) return null;

    StringWriter stringWriter = new StringWriter();
    try {
      identityTransformer.transform(new DOMSource( document), new StreamResult( stringWriter));
    } catch (TransformerException e) {
      e.printStackTrace();
    }
    return stringWriter.toString();
  }

  /**
   * Escapes special HTML entities
   * @param text
   * @return text with HTML entities escaped
   */
  public static String escapeXMLEntities(String text) {
    if (text == null)
      return null;
    text = text.replace("&", "&amp;");
    text = text.replace("\"", "&quot;");
    text = text.replace("<", "&lt;");
    text = text.replace(">", "&gt;");
    text = text.replace("'", "&#039;");
    return text;
  }
  
  /**
   *  Returns first node at the bottom of path from node.
   * If element begins with '@', indicates an attribute, eg "@id"
   * The '#text' element indicates that the node has a single text child.
   * @param node    Node to apply path to
   * @param path    Path to apply
   * @return        Node at bottom of path, or null
   */
  static public Node extractNode(Node node, String path) {
    if (node == null) return null;
    NodeList list = node.getChildNodes();
    if (path.equals("#text"))
      return  node.getFirstChild();
    else if (path.charAt(0) == '@')
      return node.getAttributes().getNamedItem( path.substring(1));
    else for (int j = 0; j < list.getLength(); j++)
      if (list.item(j).getNodeType() == Node.ELEMENT_NODE &&
          list.item(j).getNodeName().equals( path))
        return list.item(j);

    return null;
  }

  /**
   *  Returns all nodes at the bottom of path from node.
   * If element begins with '@', indicates an attribute, eg "@id"
   * The '#text' element indicates that the node has a single text child.
   * @param node    Node to apply path to
   * @param path    Path to apply
   * @return        All Nodes at bottom of path. List may be empty, but not null.
   */
  static public List<Node> extractNodes(Node node, String path) {
    if (node == null) return new ArrayList<Node>();
    List<Node> result = new ArrayList<Node>();
    NodeList list = node.getChildNodes();
    if (path.equals("#text"))
      result.add( node.getFirstChild());
    else if (path.charAt(0) == '@')
      result.add(node.getAttributes().getNamedItem( path.substring(1)));
    else for (int j = 0; j < list.getLength(); j++)
      if (list.item(j).getNodeType() == Node.ELEMENT_NODE &&
          list.item(j).getNodeName().equals( path))
        result.add( list.item(j));
    return result;
  }
  
  /**
   *  Returns first node at the bottom of path from node.
   * If element begins with '@', indicates an attribute, eg "@id"
   * The '#text' element indicates that the node has a single text child.
   * @param node    Node to apply path to
   * @param path    Path to apply
   * @return        Node at bottom of path, or null
   */
  static public Node extractPath(Node node, String[] path) {
    for (int i = 0; i < path.length; i++)
      node = extractNode( node, path[i]);
    return node;
  }

  /**
   *  Returns all nodes at the bottom of path from node.
   * If element begins with '@', indicates an attribute, eg "@id"
   * The '#text' element indicates that the node has a single text child.
   * @param node    Node to apply path to
   * @param path    Path to apply
   * @return        All nodes at bottom of path. List may be empty but not null.
   */
  static public List<Node> extractPaths(Node node, String[] path) {
    List<Node> result = new ArrayList<Node>();
    result.add( node);
    for (int i = 0; i < path.length; i++) {
      List<Node> children = new ArrayList<Node>();
      for (int j = 0; j < result.size(); j++)
        children.addAll( extractNodes((Node) result.get(j), path[i]));
      result = children;
    }
    return result;
  }

  static public Document newDocument() {
    return parser.newDocument();
  }

  static public Document parse(String text) {
    Document document = null;
    exception = null;
    try {
      document = riskyParse( text);
    } catch (Exception e) {
      exception = e;
    }
    return document;
  }

  public static String removeXMLTags(String in) {
    return in.replaceAll("<[^<>]+>", "");
  }

  public static synchronized Document riskyParse(String text) throws Exception {
    InputSource is = new InputSource(new StringReader( text));
    return parser.parse(is);
  }

  /**
   * Unescapes special HTML entities
   * @param s
   * @return text with HTML entities escaped
   */
  public static String unescapeXMLEntities(String s) {
    if (s == null) return null;
    // handles common HTML entities
    s = s.replaceAll("(?i)&amp;", "&");
    s = s.replaceAll("(?i)&gt;", ">");
    s = s.replaceAll("(?i)&lt;", "<");
    s = s.replaceAll("(?i)&quot;", "\"");
    s = s.replaceAll("(?i)&nbsp;", " ");
    s = s.replaceAll("(?i)&apos;", "'");
    s = s.replaceAll("(?i)&middot;", "·");

    // handles characters represented by (hexa)decimals
    StringBuffer sb = new StringBuffer();
    Matcher m = Pattern.compile("(?i)&#x?(\\d+);").matcher(s);
    while(m.find()) {
      int radix = m.group(0).toLowerCase().contains("x") ? 16 : 10;
      char c = (char)Integer.valueOf(m.group(1), radix).intValue();
      String rep = Character.toString(c);
      switch(c) {
      case '\\': rep = "\\\\"; break;
      case '$': rep = "\\$"; break;
      }
      m.appendReplacement(sb, rep);
    }
    m.appendTail(sb);
    return sb.toString();
  }

  public XMLUtil() {
    this(null);
  }
  
  public XMLUtil(Document document) {
    this.document = (document == null) ? newDocument() : document;
  }
  
  public Attr[] createAttrsFor(Element element, Object[] keyValuePairs) {
    int numAttrs = keyValuePairs.length / 2;
    Attr[] attrs = new Attr[numAttrs];
    for (int i = 0; i < numAttrs; i++) {
      Object keyObj = keyValuePairs[i*2];
      Object valueObj = keyValuePairs[i*2+1];
      if (keyObj == null || valueObj == null)
        continue;
      attrs[i] = document.createAttribute(keyObj.toString());
      attrs[i].setNodeValue(valueObj.toString());
    }
    for (Attr attr : attrs)
      if (attr != null)
        element.setAttributeNode(attr);
    return attrs;
  }

  /**
   *  Creates a new element with given text: <element>text</element>
   * @param element  Name of new element tag
   * @param text     Text that element tag should contain
   * @return new element node. (not actually added to document yet)
   */
  public Element createElement(String element, String text) {
    Element node = document.createElement( element);
    if (text != null) {
      Node textNode = document.createTextNode( text);
      node.insertBefore( textNode, null);
    }
    return node;
  }
  
  public Element createElementBelow(Element parentNode, String element, String text) {
    Element childNode = createElement(element, text);
    parentNode.insertBefore(childNode, null);
    return childNode;
  }

  /**
   *  Creates a new element path with given text: <element1><element2>text</element2></element1>
   * @param path     Path of new element tags
   * @param text     Text that element tag should contain
   * @return Array of new element nodes. First node is top node, last is text node.
   */
  public Element[] createPath(String[] path, String text) {
    Element[] elements = new Element[ path.length];
    for (int i = 0; i < path.length; i++)
      elements[i] = document.createElement( path[i]);
    Node textNode = document.createTextNode( text);
    for (int i = 0; i < path.length - 1; i++)
      elements[i].insertBefore( elements[i+1], null);
    elements[ path.length - 1].insertBefore( textNode, null);
    return elements;
  }
  
  public Document getDocument() {
    return document;
  }
}
