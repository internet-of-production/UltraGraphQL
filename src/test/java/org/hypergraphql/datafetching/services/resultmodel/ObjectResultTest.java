package org.hypergraphql.datafetching.services.resultmodel;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObjectResultTest {

    final String ALICE = "Alice";
    final String BOB = "Bob";
    final String BOB_SECOND = "Bob_b";
    final String EVE = "Eve";
    final String ERROR = "Error";
    final String STREET_A = "Evergreen Terrace";
    final String STREET_B = "Main Street";
    final String IRI = "http://example.org/";
    final String IRI_BOB = IRI + BOB;
    final String IRI_ALICE = IRI + ALICE;
    final String IRI_ADDR_A = IRI + "addr_a";
    final String IRI_ADDR_B = IRI + "addr_b";

    @Test
    void getSubfiedldsOfObject() {
        ObjectResult obj = new ObjectResult("?x_1", "ex_Person");
        StringResult name = new StringResult("?x_1_1", "name");
        name.addString(BOB);
        HashMap<String, Result> bob_fields =  new HashMap<String, Result>();
        bob_fields.put("name", name);
        obj.addObject(IRI_BOB, bob_fields);
        assertEquals(bob_fields, obj.getSubfiedldsOfObject(IRI_BOB));
    }

    @Test
    void addObject() {
        // Testing empty insertion of new object entity
        ObjectResult obj = new ObjectResult("?x_1", "ex_Person");
        StringResult name = new StringResult("?x_1_1", "name");
        name.addString(BOB);
        obj.addObject(IRI_BOB);
        assertTrue(obj.subfields.size() == 1);
        assertTrue(obj.subfields.get(IRI_BOB).isEmpty());

        // Testing insertion of object data to existing entity
        HashMap<String, Result> bob_fields =  new HashMap<String, Result>();
        bob_fields.put("name", name);
        obj.addObject(IRI_BOB, bob_fields);
        assertTrue(obj.subfields.size() == 1);
        assertFalse(obj.subfields.get(IRI_BOB).isEmpty());
        assertTrue(obj.subfields.get(IRI_BOB).containsKey("name"));

        // Testing the merging capabilities - insertion of duplicate data should not result in duplicate data in the object
        obj.addObject(IRI_BOB, bob_fields);
        assertTrue(obj.subfields.size() == 1);
        if(obj.subfields.containsKey(IRI_BOB)){
            assertTrue(obj.subfields.get(IRI_BOB).size() == 1);
        }else{
            fail("The IRI " + IRI_BOB + " should be in the ResultObject");
        }

        // Testing the merging of different subfields for the same entity/IRI
        StringResult address = new StringResult("?x_1_2", "address");
        address.addString("Evergreen Terrace");
        HashMap<String, Result> bob_fields_2 =  new HashMap<String, Result>();
        bob_fields_2.put("address", address);
        obj.addObject(IRI_BOB, bob_fields_2);
        assertTrue(obj.subfields.size() == 1);
        if(obj.subfields.containsKey(IRI_BOB)){
            assertTrue(obj.subfields.get(IRI_BOB).size() == 2);
            assertTrue(obj.subfields.get(IRI_BOB).containsKey("address"));
            assertTrue(obj.subfields.get(IRI_BOB).containsKey("name"));
        }else{
            fail("The IRI " + IRI_BOB + " should be in the ResultObject");
        }

        // Testing the insertion of a new entity
        StringResult alice_name = new StringResult("?x_1_1", "name");
        alice_name.addString("Alice");
        StringResult alice_surname = new StringResult("?x_1_2", "surname");
        alice_surname.addString("Rivest");
        HashMap<String, Result> alice_fields =  new HashMap<String, Result>();
        alice_fields.put("name", alice_name);
        alice_fields.put("surname", alice_surname);
        obj.addObject(IRI_ALICE, alice_fields);
        assertTrue(obj.subfields.size() == 2);
        if(obj.subfields.containsKey(IRI_BOB) && obj.subfields.containsKey(IRI_ALICE)){
            // Test existence of the subfields of alice
            assertTrue(obj.subfields.get(IRI_ALICE).size() == 2);
            assertTrue(obj.subfields.get(IRI_ALICE).containsKey("surname"));
            assertTrue(obj.subfields.get(IRI_ALICE).containsKey("name"));

            //Test the existence of the subfields of bob
            assertTrue(obj.subfields.get(IRI_BOB).size() == 2);
            assertTrue(obj.subfields.get(IRI_BOB).containsKey("address"));
            assertTrue(obj.subfields.get(IRI_BOB).containsKey("name"));
        }else{
            fail("The IRI " + IRI_BOB + " should be in the ResultObject");
        }
    }

    @Test
    void generateJSON() {
        ObjectResult obj_a = new ObjectResult("?x_1", "ex_Person");
        StringResult name = new StringResult("?x_1_1", "name");
        name.addString(BOB);
        name.isList(true);
        HashMap<String, Result> bob_fields =  new HashMap<String, Result>();
        bob_fields.put("name", name);

        obj_a.addObject(IRI_BOB, bob_fields);

        // Check if the JSON object is generated correctly from one object with one string field
        // The object ex_Person should by default be NOT a List and therefore not return a list
        final Map<String, Object> objectMap = obj_a.generateJSON();
        assertTrue(objectMap.size() == 1);
        assertTrue(objectMap.containsKey("ex_Person"));
        assertTrue(((Map) objectMap.get("ex_Person")).size() == 1);
        assertTrue(((Map) objectMap.get("ex_Person")).containsKey("name"));
        assertTrue(((List)((Map) objectMap.get("ex_Person")).get("name")).contains(BOB));

        //  Set the object to List and test if the list is generated correctly
        obj_a.isList(true);
        final Map<String, Object> objectMap_list = obj_a.generateJSON();
        assertTrue(objectMap_list.size() == 1);
        assertTrue(objectMap_list.containsKey("ex_Person"));
        assertTrue(((List) objectMap_list.get("ex_Person")).size() == 1);
        assertTrue(((Map) ((List) objectMap_list.get("ex_Person")).iterator().next()).containsKey("name"));
        assertTrue(((List)((Map) ((List) objectMap_list.get("ex_Person")).iterator().next()).get("name")).contains(BOB));

        // Add field with object as output
        ObjectResult address = new ObjectResult("?x_1_1", "ex_address");
        address.addObject(IRI_ADDR_B);
        HashMap<String, Result> fields =  new HashMap<String, Result>();
        fields.put("ex_address", address);
        obj_a.addObject(IRI_BOB, fields);

        StringResult street_b = new StringResult("?x_1_1_1", "street");
        street_b.addString(STREET_B);
        HashMap<String, Result> subfields_b =  new HashMap<String, Result>();
        subfields_b.put("street", street_b);
        address.addObject(IRI_ADDR_B, subfields_b);

        final Map<String, Object> objectMap_obj_field = obj_a.generateJSON();
        assertTrue(objectMap_obj_field.size() == 1);
        assertTrue(objectMap_obj_field.containsKey("ex_Person"));
        assertTrue(((List) objectMap_obj_field.get("ex_Person")).size() == 1);
        assertTrue(((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).size() == 2);
        assertTrue(((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).containsKey("name"));
        assertTrue(((List)((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).get("name")).contains(BOB));
        assertTrue(((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).containsKey("ex_address"));
        assertTrue(((Map)((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).get("ex_address")).size() == 1);
        assertTrue(((Map)((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).get("ex_address")).containsKey("street"));
        assertEquals(STREET_B, ((Map)((Map) ((List) objectMap_obj_field.get("ex_Person")).iterator().next()).get("ex_address")).get("street"));

    }

    @Test
    void merge() {
        ObjectResult obj_a = new ObjectResult("?x_1", "ex_Person");
        StringResult name = new StringResult("?x_1_1", "name");
        name.addString(BOB);
        HashMap<String, Result> bob_fields =  new HashMap<String, Result>();
        bob_fields.put("name", name);

        obj_a.addObject(IRI_BOB, bob_fields);

        // Testing merging of ObjectResult wit StringResult -> ObjectResult should not change
        obj_a.merge(name);
        assertTrue(obj_a.subfields.size() == 1 && obj_a.subfields.containsKey(IRI_BOB));

        //Create a second ObjectResult object but with one entity more and one extended entity
        ObjectResult obj_b = new ObjectResult("?x_1", "ex_Person");
        StringResult name_b = new StringResult("?x_1_1", "name");
        name_b.addString(BOB_SECOND);
        StringResult address_b = new StringResult("?x_1_2", "address");
        address_b.addString("Evergreen Terrace");
        HashMap<String, Result> bob_fields_b =  new HashMap<String, Result>();
        bob_fields_b.put("name", name_b);
        bob_fields_b.put("address", address_b);

        obj_b.addObject(IRI_BOB, bob_fields_b);

        StringResult alice_name = new StringResult("?x_1_1", "name");
        alice_name.addString("Alice");
        StringResult alice_surname = new StringResult("?x_1_2", "surname");
        alice_surname.addString("Rivest");
        HashMap<String, Result> alice_fields =  new HashMap<String, Result>();
        alice_fields.put("name", alice_name);
        alice_fields.put("surname", alice_surname);

        obj_b.addObject(IRI_ALICE, alice_fields);

        obj_a.merge(obj_b);
        assertTrue(obj_a.subfields.size() == 2);
        if(obj_a.subfields.containsKey(IRI_BOB) && obj_a.subfields.containsKey(IRI_ALICE)){
            // Test if the entity alice has all its subfields
            assertTrue(obj_a.subfields.get(IRI_ALICE).containsKey("name"));
            assertTrue(obj_a.subfields.get(IRI_ALICE).containsKey("surname"));
            assertEquals(ALICE, obj_a.subfields.get(IRI_ALICE).get("name").generateJSON());

            // Test if the entity of bob was  merged correctly
            assertTrue(obj_a.subfields.get(IRI_BOB).size() == 2);
            assertTrue(obj_a.subfields.get(IRI_BOB).containsKey("name"));
            assertTrue(obj_a.subfields.get(IRI_BOB).containsKey("address"));
            assertTrue(((StringResult) obj_a.subfields.get(IRI_BOB).get("name")).getValues().size() == 2);
            assertTrue(((StringResult) obj_a.subfields.get(IRI_BOB).get("name")).getValues().contains(BOB) && ((StringResult) obj_a.subfields.get(IRI_BOB).get("name")).getValues().contains(BOB_SECOND));
            assertTrue(((StringResult) obj_a.subfields.get(IRI_BOB).get("address")).getValues().size() == 1);
        }else{
            fail("Merging error: object should contain both entities");
        }

        // Test if only ObjectResults with the same name are merged
        ObjectResult obj_error = new ObjectResult("?x_1", "ex_Error");
        StringResult error = new StringResult("?x_1_1", "error");
        error.addString(ERROR);
        HashMap<String, Result> error_fields =  new HashMap<String, Result>();
        error_fields.put("name", error);

        obj_error.addObject(IRI_BOB, error_fields);

        obj_a.merge(obj_error);
        assertFalse(obj_a.subfields.containsKey("error"));

    }

    @Test
    void deepSubfieldMerge() {
        ObjectResult obj_a = new ObjectResult("?x_1", "ex_Person");
        ObjectResult address = new ObjectResult("?x_1_1", "ex_address");
        address.addObject(IRI_ADDR_A);
        HashMap<String, Result> fields =  new HashMap<String, Result>();
        fields.put("ex_address", address);
        obj_a.addObject(IRI_BOB, fields);

        ObjectResult address_b = new ObjectResult("?x_1_1", "ex_address");
        StringResult street_b = new StringResult("?x_1_1_1", "street");
        street_b.addString(STREET_B);
        HashMap<String, Result> subfields_b =  new HashMap<String, Result>();
        subfields_b.put("street", street_b);
        address_b.addObject(IRI_ADDR_B, subfields_b);

        StringResult street = new StringResult("?x_1_1_1", "street");
        street.addString(STREET_A);
        HashMap<String, Result> subfields =  new HashMap<String, Result>();
        subfields.put("street", street);
        address_b.addObject(IRI_ADDR_A, subfields);


        obj_a.deepSubfieldMerge(address_b);

        assertTrue(obj_a.subfields.size() == 1);
        assertTrue(obj_a.subfields.containsKey(IRI_BOB));
        assertTrue(obj_a.subfields.get(IRI_BOB).size() == 1 && obj_a.subfields.get(IRI_BOB).containsKey("ex_address"));
        assertTrue(((ObjectResult) obj_a.subfields.get(IRI_BOB).get("ex_address")).subfields.size() == 1);
        assertTrue(((ObjectResult) obj_a.subfields.get(IRI_BOB).get("ex_address")).subfields.containsKey(IRI_ADDR_A));
        assertFalse(((ObjectResult) obj_a.subfields.get(IRI_BOB).get("ex_address")).subfields.containsKey(IRI_ADDR_B));
        assertTrue(((StringResult)((ObjectResult) obj_a.subfields.get(IRI_BOB).get("ex_address")).subfields.get(IRI_ADDR_A).get("street")).values.contains(STREET_A));
    }

}