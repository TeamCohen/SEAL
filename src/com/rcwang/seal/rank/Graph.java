/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.DocumentSet;
import com.rcwang.seal.util.Cell;
import com.rcwang.seal.util.Originator;
import com.rcwang.seal.util.SparseVector;
import com.rcwang.seal.util.StringFactory;
import com.rcwang.seal.util.StringFactory.SID;

public class Graph {

  public static class EdgeSet extends HashSet<String> {
    private static final long serialVersionUID = -6915613233385980502L;
  }
  
  public static class Node {
    public static final String SEPERATOR = ":";
    private SID id;
    private String type;
    private double weight;
    
    public Node(String name, String type) {
      id = StringFactory.toID(name);
      this.type = StringFactory.get(type);
    }    
    public Node(EntityLiteral name, String type) {
        id = StringFactory.toID(name);
        this.type = StringFactory.get(type);
      }
    
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final Node other = (Node) obj;
      if (id == null) {
        if (other.id != null) return false;
      } else if (!id.equals(other.id)) return false;
      return true;
    }
    
    public SID getID() {
      return id;
    }
    
    public String getName() {
      return StringFactory.toName(id);
    }
    
    public EntityLiteral getLiteral() {
      return id.toLiteral();
    }
    
    public String getType() {
      return type;
    }

    public double getWeight() {
      return weight;
    }

    @Override
    public int hashCode() {
      return 31 + ((id == null) ? 0 : id.hashCode());
    }

    public void setWeight(double weight) {
      this.weight = weight;
    }

    public String toString() {
      return getType() + SEPERATOR + getName();
    }
  }
  
  public static class NodeSet extends HashSet<Node> {
    private static final long serialVersionUID = -524577615563801077L;
  }
  
  public static final String CONTENT_NODE_NAME = "content";
  public static final String DOCUMENT_NODE_NAME = "document";
  public static final String WRAPPER_NODE_NAME = "wrapper";
  public static final String CONTAIN_EDGE_LABEL = "contain";
  public static final String EXTRACT_EDGE_LABEL = "extract";
  public static final String DERIVE_EDGE_LABEL = "derive";
  public static final String INVERSE_EDGE_LABEL_SUFFIX = "By";

  private Map<Node, EdgeSet> edgeMap; // node --> edges
  private Map<SID, NodeSet> nodeMap;  // edge --> nodes
  private Map<SID, Node> allNodeMap;  // id --> node
//  private NodeSet allNodes;
  
  public static String inverse(String edgeLabel) {
    return edgeLabel + INVERSE_EDGE_LABEL_SUFFIX;
  }
  
  public static void sortByWeight(List<Node> list, final boolean ascending) {
    Comparator<Node> c = new Comparator<Node>() {
      public int compare(Node e1, Node e2) {
        double w1 = e1.getWeight(), w2 = e2.getWeight();
        return ascending ? Double.compare(w1, w2) : Double.compare(w2, w1);
      }
    };
    Collections.sort(list, c);
  }
  
  public Graph() {
    edgeMap = new HashMap<Node, EdgeSet>();
    nodeMap = new HashMap<SID, NodeSet>();
//    allNodes = new NodeSet();
    allNodeMap = new HashMap<SID, Node>();
  }
  
  public void add(Node fromNode, String edgeLabel, Node toNode) {
    edgeLabel = StringFactory.get(edgeLabel);
    EdgeSet edgeSet = getEdges(fromNode);
    if (edgeSet == null) {
      edgeSet = new EdgeSet();
      edgeMap.put(fromNode, edgeSet);
    }
    edgeSet.add(edgeLabel);

    NodeSet nodeSet = followEdge(fromNode, edgeLabel);
    if (nodeSet == null) {
      nodeSet = new NodeSet();
      SID id = getFromEdgeID(fromNode, edgeLabel);
      nodeMap.put(id, nodeSet);
    }
    nodeSet.add(toNode);
//    allNodes.add(fromNode);
//    allNodes.add(toNode);
    allNodeMap.put(fromNode.getID(), fromNode);
    allNodeMap.put(toNode.getID(), toNode);
  }
  
  public void addBidirect(Node fromNode, String edgeLabel, Node toNode) {
    add(fromNode, edgeLabel, toNode);
    add(toNode, inverse(edgeLabel), fromNode);
  }
  
  public void assignWeights(SparseVector sv) {
    for (Node node : allNodeMap.values()) {
      Cell cell = sv.get(node.getID());
      if (cell == null) continue;
      node.setWeight(cell.value);
    }
  }
  
  public void clear() {
    edgeMap.clear();
    nodeMap.clear();
//    allNodes.clear();
    allNodeMap.clear();
  }
  
  public NodeSet followEdge(Node fromNode, String edgeLabel) {
    SID edgeID = getFromEdgeID(fromNode, edgeLabel);
    return nodeMap.get(edgeID);
  }
  
  public Collection<Node> getAllNodes() {
    return allNodeMap.values();
  }
  
  public EdgeSet getEdges(Node fromNode) {
    return edgeMap.get(fromNode);
  }
  
  public Node getNode(SID id) {
    return allNodeMap.get(id);
  }
  
  /*public NodeSet getAllNodes() {
    return allNodes;
  }*/
  
  public void load(DocumentSet documentSet) {
    for (Document document : documentSet) {
      String urlStr = document.getURL().toString();
      Node documentNode = new Node(urlStr, DOCUMENT_NODE_NAME);
      
      for (Wrapper wrapper : document.getWrappers()) {
        Node wrapperNode = new Node(wrapper.toString(), WRAPPER_NODE_NAME);
        addBidirect(documentNode, CONTAIN_EDGE_LABEL, wrapperNode);

        for (EntityLiteral content : wrapper.getContents()) {
          Node contentNode = new Node(content, CONTENT_NODE_NAME);
          addBidirect(wrapperNode, EXTRACT_EDGE_LABEL, contentNode);
          addBidirect(documentNode, CONTAIN_EDGE_LABEL, contentNode);
        }
      }
    }
  }

  public String toString() {
    List<Node> nodeList = new ArrayList<Node>(getAllNodes());
    sortByWeight(nodeList, false);
    StringBuffer buf = new StringBuffer();
    
    for (Node fromNode : nodeList) {
      double w = fromNode.getWeight();
      if (w != 0) buf.append(w).append("\t");
      
      String type = fromNode.getType();
      if (type.equals(Graph.CONTENT_NODE_NAME)) {
        String s = Originator.getOriginal(fromNode.getName());
        buf.append(type).append(Node.SEPERATOR).append(s);
      } else buf.append(fromNode);
      buf.append("\t");
      EdgeSet edgeSet = getEdges(fromNode);
      
      for (String edgeLabel : edgeSet) {
        NodeSet toNodes = followEdge(fromNode, edgeLabel);
        
        for (Node toNode : toNodes)
          buf.append(toNode).append("\t");
      }
      buf.setCharAt(buf.length()-1, '\n');
    }
    return buf.toString();
  }
  
  private SID getFromEdgeID(Node fromNode, String edgeLabel) {
    SID edgeID = new SID(fromNode.id);
    edgeID.append(StringFactory.get(edgeLabel));
    return edgeID;
  }
  
}

