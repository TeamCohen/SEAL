/**************************************************************************
 * Developed by Language Technologies Institute, Carnegie Mellon University
 * Written by Richard Wang (rcwang#cs,cmu,edu)
 **************************************************************************/
package com.rcwang.seal.rank;

import java.util.HashSet;
import java.util.Set;

import com.rcwang.seal.expand.Entity;
import com.rcwang.seal.expand.EntityList;
import com.rcwang.seal.expand.Wrapper.EntityLiteral;
import com.rcwang.seal.fetch.Document;
import com.rcwang.seal.fetch.DocumentSet;

public class DocFreqRanker extends Ranker {
  
  public static final String DESCRIPTION = "Extracted Document Frequency";
  
  private Set<Integer> contentHashSet;
  
  public DocFreqRanker() {
    super();
    setDescription(DESCRIPTION);
    contentHashSet = new HashSet<Integer>();
  }
  
  public void load(EntityList entities, DocumentSet documentSet) {
    for (Document document : documentSet) {
      for (EntityLiteral content : document.getExtractions()) {
        int hashCode = getHashCode(document, content, null);
        if (contentHashSet.add(hashCode)) {
//          entityDist.add(content, 1);
          Entity entity = entities.add(content);
          if (entity == null) continue;
          entity.addWeight(getRankerID(), 1);
        }
      }
    }
    entities.reduceSize(getRankerID());
  }
}
