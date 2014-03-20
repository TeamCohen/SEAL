/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.qa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EphyraXML {
  
  private String question, focus, trecID;
  private Set<String> answerTypes;
  private List<ItemWeight> answers;
  private List<ItemWeight> keywords;
  
  public EphyraXML() {
    keywords = new ArrayList<ItemWeight>();
    answers = new ArrayList<ItemWeight>();
    answerTypes = new HashSet<String>();
  }
  
  public void setAnswers(List<String> inputs, double weight) {
    answers.clear();
    if (inputs == null)
      return;
    /*for (int i = 0; i < inputs.size(); i++) {
      double w = (1.0 - (double) i/inputs.size());
      addAnswer(inputs.get(i), w);
    }*/
    for (String input : inputs)
      addAnswer(input, weight);
  }
  
  public void addAnswer(String answer, Double weight) {
    if (answer != null && weight != null) {
      ItemWeight iw = new ItemWeight(answer, weight);
      if (!answers.contains(iw))
        answers.add(iw);
    }
  }
  
  public void addAnswerType(String ansType) {
    if (ansType != null)
      answerTypes.add(ansType);
  }
  
  public void addKeyword(String keyword, Double weight) {
    if (keyword != null && weight != null) {
      ItemWeight iw = new ItemWeight(keyword, weight);
      if (!keywords.contains(iw))
        keywords.add(iw);
    }
  }
  
  public List<ItemWeight> getAnswers() {
    return answers;
  }
  
  public List<String> getAnswersString() {
    return ItemWeight.toItemList(answers);
  }
  
  public String getFocus() {
    return focus;
  }
  
  public List<ItemWeight> getKeywords() {
    return keywords;
  }
  
  public List<String> getKeywordsString() {
    return ItemWeight.toItemList(keywords);
  }
  
  public int getNumAnswers() {
    return answers.size();
  }
  
  public int getNumKeywords() {
    return keywords.size();
  }
  
  public String getQuestion() {
    return question;
  }

  public List<ItemWeight> getTopAnswers(double threshold, boolean isAbsolute) {
    return getTopAnswers(ItemWeight.getRankAt(answers, threshold, isAbsolute));
  }
  
  public List<ItemWeight> getTopAnswers(int rank) {
    return answers.subList(0, rank);
  }

  public String getTrecID() {
    return trecID;
  }

  public void setFocus(String focus) {
    this.focus = focus;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public void setTrecID(String trecID) {
    this.trecID = trecID;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("[").append(trecID).append("] ").append(question).append("\n");
    if (focus != null)
      buf.append("Focus: ").append(focus).append("\n");
    buf.append("Keywords: ");
    for (ItemWeight iw : keywords)
      buf.append(iw.getItem()).append(", ");
    buf.append("\nAnswers: ");
    for (ItemWeight iw : answers)
      buf.append(iw.getItem()).append(", ");
    return buf.toString();
  }
}