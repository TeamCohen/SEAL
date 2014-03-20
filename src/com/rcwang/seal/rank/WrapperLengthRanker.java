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
import com.rcwang.seal.util.GlobalVar;
import com.rcwang.seal.util.StringFactory;

public class WrapperLengthRanker extends Ranker {
  
  public static final String DESCRIPTION = "Wrapper Length";
  private GlobalVar gv = GlobalVar.getGlobalVar();
  
  public WrapperLengthRanker() {
    super();
    setDescription(DESCRIPTION);
  }
  
  private double getSeedsProb(Wrapper wrapper) {
    double seedsProb = 0;
    for (Entity entity : wrapper.getSeeds()) {
      Double p = seedDist.getProbability(StringFactory.toID(entity.getName()));
      seedsProb += (p == null) ? 0 : p;
    }
    return seedsProb;
  }
  
  public void load(EntityList entities, DocumentSet documentSet) {
    for (Document document : documentSet) {
      for (Wrapper wrapper : document.getWrappers()) {
        int contextLength = wrapper.getContextLength();
        
        // get the total probabilities of this wrapper's seeds
        double seedsProb = getSeedsProb(wrapper);
        double w = 1;//seedsProb;
        w *= Math.log(contextLength);
        w /= Math.log(document.length());
        
        for (EntityLiteral content : wrapper.getContents()) {
//          Entity entity = new Entity(content);
//          if (wrapper.isRelational()) {
//              entity = Entity.parseEntity(content);
//              if (entity == null) {
//                  log.debug("Null entity parsed from "+content);
//                  continue;
//              }
//          } else
//              entity = new Entity(content);
            // kmr 6 June 2012 converted for EntityLiteral
          Entity entity = entities.add(content);
          if (entity == null) {
              log.debug("Null entity from entity add of "+content);
              continue;
          }
//        double w = Math.log(contextLength) * wrapper.getNumCommonTypes();
          
//          entityDist.add(content, w);
          entity.addWeight(getRankerID(), w);
          if (gv.isStoreContentWeights()) wrapper.setContentWeight(content, w);
        }
      }
    }
    entities.reduceSize(getRankerID());
  }
}
