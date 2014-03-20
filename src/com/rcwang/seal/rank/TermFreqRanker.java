/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.DocumentSet;

public class TermFreqRanker extends Ranker {
  
  public static final String DESCRIPTION = "Extracted Term Frequency";
  
  public TermFreqRanker() {
    super();
    setDescription(DESCRIPTION);
  }
  
  public void load(EntityList entities, DocumentSet documentSet) {
    for (Document document : documentSet)
      for (Wrapper wrapper : document.getWrappers())
        for (EntityLiteral content : wrapper.getContents()) {
//          entityDist.add(content, wrapper.getContentTF(content));
            // kmr 6 June 2012 converted for EntityLiteral
          Entity entity = entities.add(content);
          if (entity == null) continue;
          int tf = wrapper.getContentTF(content);
          entity.addWeight(getRankerID(), tf);
        }
    entities.reduceSize(getRankerID());
  }
}
