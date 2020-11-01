package org.hypergraphql.datafetching.services.resultmodel;

import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StringResultTest {

    final String ALICE = "Alice";
    final String BOB = "Bob";
    final String EVE = "Eve";
    final String ERROR = "Error";

    @Test
    void addString() {
        StringResult res = new StringResult("?x_1_1", "name");
        res.addString(ALICE);
        assertTrue(res.values.stream().anyMatch(s -> s.equals(ALICE)));
        res.addString(BOB);
        assertTrue(res.values.size() == 2);
        assertTrue(res.values.stream().allMatch(s -> s.equals(ALICE) || s.equals(BOB)));
        res.addString(ALICE);
        // It is expected that the insertion of the same value again does not result in duplicate values
        assertTrue(res.values.size() == 2);
    }

    @Test
    void generateJSON() {
        StringResult res = new StringResult("?x_1_1", "name");
        res.addString(ALICE);

        assertEquals(ALICE, res.generateJSON());   // isList is false single string should be returned

        //Testing safe fallback | isList=false and |values|>1 => return list and add error
        res.addString(BOB);
        Object obj = res.generateJSON();
        if(obj instanceof Collection){
            assertTrue(((Collection<?>) obj).size() == 2);
            assertNotEquals("", res.errors);
        }else{
            fail("Two values were added the safe fallback should result in returning a set containing both values.");
        }

        // Testing string resutls defined as list
        StringResult res_list = new StringResult("?x_1_1", "name");
        res_list.isList(true);
        res_list.addString(EVE);
        res_list.addString(BOB);
        res_list.addString(ALICE);

        Object obj_list = res_list.generateJSON();
        if(obj_list instanceof Collection){
            assertTrue(((Collection<?>) obj_list).size() == 3);
            assertEquals("", res_list.errors);
        }else{
            fail("Result is not a Collection but is defined as List.");
        }

        // Test ordering
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(SPARQLServiceConverter.ORDER,SPARQLServiceConverter.ORDER_DESC);
        res_list.args = arguments;
        Collection<String> res_ordered = (Collection<String>) res_list.generateJSON();
        Iterator iterator = res_ordered.iterator();
        if(iterator.hasNext()){
            assertEquals(EVE, iterator.next());
        }else{
            fail("Result should have at least three result since three were added");
        }
        if(iterator.hasNext()){
            assertEquals(BOB, iterator.next());
        }else{
            fail("Result should have at least three result since three were added");
        }
        if(iterator.hasNext()){
            assertEquals(ALICE, iterator.next());
        }else{
            fail("Result should have at least three result since three were added");
        }

        // Test limit and offset
        arguments.put(SPARQLServiceConverter.LIMIT,1);
        arguments.put(SPARQLServiceConverter.OFFSET,1);
        Collection<String> obj_filtered = (Collection<String>) res_list.generateJSON();
        assertTrue(obj_filtered.size() == 1);
        assertEquals(BOB, obj_filtered.iterator().next());

        // language tag has not to be tested as it is only applied in the SPARQL query
    }

    @Test
    void merge() {
        StringResult res_a = new StringResult("?x_1_1", "name");
        StringResult res_b = new StringResult("?x_1_1", "name");

        res_a.addString(BOB);
        res_a.addString(ALICE);
        res_b.addString(BOB);
        res_b.addString(EVE);

        res_a.merge(res_b);

        assertTrue(res_a.values.size() == 3);
        assertTrue(res_a.values.contains(ALICE));
        assertTrue(res_a.values.contains(BOB));
        assertTrue(res_a.values.contains(EVE));

        StringResult res_c = new StringResult("?x_1_2", "address");
        res_c.addString(ERROR);

        res_a.merge(res_c);

        assertFalse(res_a.values.contains(ERROR));
    }
}