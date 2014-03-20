package com.rcwang.seal.expand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class EntityListTest {
    @Test
    public void testAdd() {
        ArrayList<String> entities = new ArrayList<String>();
        Collections.addAll(entities, "cat","dog","mouse");
        TestableEntityList el = new TestableEntityList(entities);
        
        Entity cat = new Entity("cat");
        Entity e = el.get(cat);
        assertEquals("Should have cat but doesn't",cat,e);
        
        Entity bird = new Entity("bird");
        e = el.get(bird);
        assertNull("Shouldn't have bird but does",e);
        
        Entity evilR = new Entity("cat","bird");
        Entity evilE = new Entity("cat::bird::skunk");
        assertNull("Shouldn't have relation "+evilR.getName(),el.get(evilR));
        assertNull("Shouldn't have entity "+evilE.getName(),el.get(evilE));
        assertFalse("Should be different on ==",evilR == (evilE));
        assertFalse("Should be different on equals()",evilR.equals(evilE));
        assertNotSame("Should have different name()",evilR.getName(),evilE.getName());

        el.add(evilE);
        assertTrue("added entity should show up in entity map as itself",el.get(evilE.getName()) == evilE);
        e = el.get(evilR.getName());
        assertFalse("unadded relation should not show up in entity map as previous added entity", e == evilE);
        assertEquals("added entity "+evilE.getName()+" should have index >= 0",3,el.getEntityList().indexOf(evilE));
        assertEquals("unadded relation "+evilR.getName()+" should have index -1",-1,el.getEntityList().indexOf(evilR));
    }
    
    public static class TestableEntityList extends EntityList {
        public TestableEntityList(Collection<String> ents) {
            super(ents);
        }
        public List<Entity> getEntityList() { return entityList; }
    }
}
