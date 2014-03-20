/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import com.rcwang.seal.util.StringFactory.SID;


public class SparseMatrix {
  public static Logger log = Logger.getLogger(SparseMatrix.class);
  
  private Map<SID, SparseVector> rowMap;
  private Map<SID, SparseVector> columnMap;
  
  public SparseMatrix() {
    rowMap = new LinkedHashMap<SID, SparseVector>();
    columnMap = new LinkedHashMap<SID, SparseVector>();
  }
  
  public void add(SID columnID, SID rowID, double value) {
    // ensures the row exists
    SparseVector row = rowMap.get(rowID);
    if (row == null) {
      row = new SparseVector(rowID);
      rowMap.put(rowID, row);
    }

    // ensures the column exists
    SparseVector column = columnMap.get(columnID);
    if (column == null) {
      column = new SparseVector(columnID);
      columnMap.put(columnID, column);
    }
    
    // ensures the cell exists
    Cell cell = row.get(columnID);
    if (cell == null) {
      cell = new Cell();
      row.put(columnID, cell);
      column.put(rowID, cell);
    }
    
    // modifies the entry
    cell.value += value;
  }
  
  public void add(String columnIDStr, String rowIDStr, double value) {
    SID rowID = StringFactory.toID(rowIDStr);
    SID columnID = StringFactory.toID(columnIDStr);
    add(columnID, rowID, value);
  }
  
  public void addColumn(SparseVector column) {
    columnMap.put(column.id, column);
    for (Entry<SID, Cell> entry : column.entrySet()) {
      SID rowID = entry.getKey();
      SparseVector row = getRow(rowID);
      if (row == null) {
        row = new SparseVector(rowID);
        rowMap.put(rowID, row);
      }
      row.put(rowID, entry.getValue());
    }
  }
  
  public void addRow(SparseVector row) {
    rowMap.put(row.id, row);
    for (Entry<SID, Cell> entry : row.entrySet()) {
      SID columnID = entry.getKey();
      SparseVector column = getColumn(columnID);
      if (column == null) {
        column = new SparseVector(columnID);
        columnMap.put(columnID, column);
      }
      column.put(columnID, entry.getValue());
    }
  }
  
  public void clear() {
    rowMap.clear();
    columnMap.clear();
  }
  
  public Iterator<SparseVector> columnIterator() {
    return columnMap.values().iterator();
  }
  
  public Double getCellValue(SID rowID, SID columnID) {
    SparseVector row = getRow(rowID);
    return (row == null) ? null : row.getValue(columnID);
  }

  public SparseVector getColumn(SID id) {
    return columnMap.get(id);
  }
  
  public SparseVector getColumn(String name) {
    return getColumn(StringFactory.toID(name));
  }
  
  public Set<SID> getColumnIDs() {
    return columnMap.keySet();
  }

  public Set<String> getColumnNames() {
    return StringFactory.toNames(getColumnIDs());
  }
  
  public Collection<SparseVector> getColumns() {
    return columnMap.values();
  }
  
  public Set<SID> getColumnsWithRow(SID id) {
    SparseVector row = getRow(id);
    return (row == null) ? null : row.keySet();
  }
  
  public Set<String> getColumnsWithRow(String name) {
    Set<SID> columnIDs = getColumnsWithRow(StringFactory.toID(name));
    return (columnIDs == null) ? null : StringFactory.toNames(columnIDs);
  }

  public int getNumColumns() {
    return columnMap.size();
  }
  
  public int getNumRows() {
    return rowMap.size();
  }
  
  public SparseVector getRow(SID id) {
    return rowMap.get(id);
  }

  public SparseVector getRow(String name) {
    return getRow(StringFactory.toID(name));
  }
  
  public Set<SID> getRowIDs() {
    return rowMap.keySet();
  }
  
  public Set<String> getRowNames() {
    return StringFactory.toNames(getRowIDs());
  }
  
  public Collection<SparseVector> getRows() {
    return rowMap.values();
  }
  
  public Set<SID> getRowsWithColumn(SID id) {
    SparseVector column = getColumn(id);
    return (column == null) ? null : column.keySet();
  }
  
  public Set<String> getRowsWithColumn(String name) {
    Set<SID> rowIDs = getRowsWithColumn(StringFactory.toID(name));
    return (rowIDs == null) ? null : StringFactory.toNames(rowIDs);
  }
  
  public Iterator<SparseVector> rowIterator() {
    return rowMap.values().iterator();
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();
    for (SparseVector row : getRows())
      buf.append(StringFactory.toName(row.id)).append(" <-- ").append(row).append("\n");
    return buf.toString();
  }
}
