package com.rcwang.seal.expand;

import com.rcwang.seal.rank.Ranker.Feature;

/** 
 * Pinniped defines the shared methods of Seal-like classes, including Seal and OfflineSeal.
 * Putting these methods in a common interface allows us to use better logic in code
 * which can operate with either class.
 * 
 * @author krivard
 *
 */
public abstract class Pinniped extends SetExpander {
    public abstract void setEngine(int useEngine);
    public abstract void setFeature(Feature feature);
    public abstract boolean expand(EntityList wrapperSeeds, EntityList pageSeeds, String hint);
}
