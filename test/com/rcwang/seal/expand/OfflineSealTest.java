package com.rcwang.seal.expand;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfflineSealTest {
    @Test 
    public void testCategoryExpansion() {
        OfflineSeal seal = new OfflineSeal();
        EntityList seeds = new EntityList();
        seeds.add("cat"); seeds.add("dog"); seeds.add("horse");
        seal.expand(seeds);
        EntityList results = seal.getEntityList();
        assertTrue("Expected >1 result",results.size() > 0);
        System.out.println(results.toDetails(100));
    }
    
    @Test
    public void testRelationExpansion() {
        OfflineSeal seal = new OfflineSeal();
        EntityList seeds = new EntityList();
        seeds.add(new Entity("hot","cold")); 
        seeds.add(new Entity("black","white")); 
        //seeds.add(new Entity("bird","worm"));
        seal.expand(seeds);
        EntityList results = seal.getEntityList();
        assertTrue("Expected >1 result",results.size() > 0);
        System.out.println(results.toDetails(10));
    }
}
