package org.hypergraphql;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.hypergraphql.datafetching.services.SPARQLService;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_GQL_NAME;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_VALUE_GQL_NAME;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {

    long SOCKET_CLOSING = 500;
// This method can be used for testing with the GraphiQL interface
//    @Test
//    void main() throws Exception {
//        System.out.print(System.getProperty("user.dir"));
//        String config = "config.json";
//        Application.main(new String[]{"--classpath","-config", config});
//        while(true){
//
//        }
//    }

    /**
     * This test is documented
     * @throws Exception
     */
    @Test
    void multipleServiceTest() throws Exception {
        String config = "build/resources/test/evaluation/multiple_service/test_config_multiple_services.json";
        String query = "{City{label(lang:\\\"de\\\")_id}}";

        JSONObject json_response = sendPost(config, query);
        if(json_response == null) {
            fail("No Response from the HGQL instance received");
        }
        // the City Cologne is only in service_1
        boolean cologne_label = false;
        boolean cologne_id = false;
        // the city Corfu is only in service_2
        boolean corfu_label = false;
        boolean corfu_id = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("City")) {
            if(((JSONObject) o).get("_id").equals("http://dbpedia.org/resource/Corfu")){
                corfu_id = true;
                if (((JSONObject) o).getJSONArray("label").get(0).equals("Korfu")) {
                    corfu_label = true;
                }
            }else  if (((JSONObject) o).get("_id").equals("http://dbpedia.org/resource/koeln")) {
                cologne_id = true;
                if (((JSONObject) o).getJSONArray("label").get(0).equals("Köln")) {
                    cologne_label = true;
                }
            }
        }
        assertTrue(cologne_label && corfu_label && cologne_id && corfu_id);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void multipleServiceTestExtended() throws Exception {
        String config = "build/resources/test/evaluation/multiple_service_extended/test_config_multiple_services_extended.json";
        String query = "{City{label(lang:\\\"de\\\")_id}}";

        JSONObject json_response = sendPost(config, query);
        if(json_response == null) {
            fail("No Response from the HGQL instance received");
        }
        // the City Cologne is only in service_1
        boolean cologne_label = false;
        boolean cologne_id = false;
        // the city Corfu is only in service_2
        boolean corfu_label = false;
        boolean corfu_id = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("City")) {
            if(((JSONObject) o).get("_id").equals("http://dbpedia.org/resource/Corfu")){
                corfu_id = true;
                if (((JSONObject) o).getJSONArray("label").get(0).equals("Korfu")) {
                    corfu_label = true;
                }
            }else  if (((JSONObject) o).get("_id").equals("http://dbpedia.org/resource/koeln")) {
                cologne_id = true;
                if (((JSONObject) o).getJSONArray("label").get(0).equals("Köln")) {
                    cologne_label = true;
                }
            }
        }
        assertTrue(cologne_label && corfu_label && cologne_id && corfu_id);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void combinedServices() throws Exception {
        String config = "build/resources/test/evaluation/combined_services/config.json";
        String query = "{Person{firstName address{street city{ _id label(lang:\\\"de\\\")}}}}";
        JSONObject json_response = sendPost(config, query);
        System.out.println(json_response);
        String alice_firstName = "Alice";
        String alice_residence = "Aachen";
        boolean correctly_nested = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("Person")) {
            JSONObject person = (JSONObject) o;
            if(person.getJSONArray("firstName").get(0).equals(alice_firstName)){
                JSONObject address = (JSONObject) person.getJSONArray("address").get(0);
                JSONObject city = (JSONObject) address.getJSONArray("city").get(0);
                if(city.getJSONArray("label").get(0).equals(alice_residence)){
                    correctly_nested = true;
                }
            }
        }
        assertTrue(correctly_nested);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void combinedServicesWithExtraction() throws Exception {
        String config = "build/resources/test/evaluation/combined_services_with_extraction/config.json";
        String query = "{eg_Person{eg_firstName eg_address{eg_street eg_city{ _id rdfs_label(lang:\\\"de\\\")}}}}";
        JSONObject json_response = sendPost(config, query);
        System.out.println(json_response);
        String alice_firstName = "Alice";
        String alice_residence = "Aachen";
        boolean correctly_nested = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("eg_Person")) {
            JSONObject person = (JSONObject) o;
            if(person.getJSONArray("eg_firstName").get(0).equals(alice_firstName)){
                JSONObject address = (JSONObject) person.getJSONArray("eg_address").get(0);
                JSONObject city = (JSONObject) address.getJSONArray("eg_city").get(0);
                if(city.getJSONArray("rdfs_label").get(0).equals(alice_residence)){
                    correctly_nested = true;
                }
            }
        }
        assertTrue(correctly_nested);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void sameAsTest() throws Exception {
        String config = "build/resources/test/evaluation/sameAs/config.json";
        String query = "{eg_Person{_id rdfs_label }}";
        JSONObject json_response = sendPost(config, query);
        boolean bob = false;
        boolean alice = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("eg_Person")) {
            JSONObject person = (JSONObject) o;
            if(person.getJSONArray("rdfs_label").get(0).equals("Alice")){
                alice = true;
            }else if(person.getJSONArray("rdfs_label").get(0).equals("Bob")){
                bob = true;
            }
        }
        assertTrue(alice && bob);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void unionTest() throws Exception {
        String config = "build/resources/test/evaluation/union/config.json";
        String query = "{eg_Person{_id rdfs_label eg_address{... on eg_Address{_id eg_street}  ... on dbo_Address{dbo_street_name  dbo_street_number} }}}";
        JSONObject json_response = sendPost(config, query);
        boolean bob = false;
        boolean alice = false;
        for (Object o : json_response.getJSONObject("data").getJSONArray("eg_Person")) {
            JSONObject person = (JSONObject) o;
            if(person.getJSONArray("rdfs_label").get(0).equals("Alice")){
                final JSONArray addressObjects = person.getJSONArray("eg_address");
                for (Object object : addressObjects) {
                    JSONObject addressObject = (JSONObject) object;
                    if (addressObject.keySet().contains("eg_street")) {
                        assertTrue(alice = ((JSONArray) addressObject.get("eg_street")).get(0).equals("123 Fake Street"));
                    }
                }
            }else if(person.getJSONArray("rdfs_label").get(0).equals("Bob")){
                final JSONArray addressObjects = person.getJSONArray("eg_address");
                for (Object object : addressObjects) {
                    JSONObject addressObject = (JSONObject) object;
                    if(addressObject.keySet().contains("dbo_street_name")){
                        assertTrue(bob = bob || ((JSONArray) addressObject.get("dbo_street_name")).get(0).equals("Evergreen Terrace"));
                    }
                    if(addressObject.keySet().contains("dbo_street_name")){
                        assertTrue((bob = bob || (((JSONArray) addressObject.get("dbo_street_number")).get(0).equals("742"))));
                    }
                }
            }
        }
        assertTrue(alice && bob);
        Thread.sleep(SOCKET_CLOSING);
    }

    @Test
    void interfaceTest() throws Exception {
        String config = "build/resources/test/evaluation/interface/config.json";
        String query = "{Person{firstName pet{_id name...on dog{color}}}}";
        JSONObject json_response = sendPost(config, query);
        boolean has_type_1 = false;
        boolean has_type_2 = false;
        boolean has_typeSpecificField_of_type_1 = false;
        System.out.println(json_response);
        for (Object o : json_response.getJSONObject("data").getJSONArray("Person")) {
            JSONObject person = (JSONObject) o;
            if(person.getJSONArray("firstName").get(0).equals("Alice")){
                JSONArray pets = person.getJSONArray("pet");
                for(Object o1 : pets){
                    JSONObject pet = (JSONObject) o1;
                    if(pet.get("_id").equals("http://www.example.org/dog_a")){
                        has_type_1 = true;
                        if(pet.getJSONArray("color").get(0).equals("brown")){
                            has_typeSpecificField_of_type_1 = true;
                        }
                    }else if(pet.get("_id").equals("http://www.example.org/cat_a")){
                        has_type_2 = true;
                    }
                }
            }
        }
        Thread.sleep(SOCKET_CLOSING);
        assertTrue(has_type_1 && has_type_2 && has_typeSpecificField_of_type_1);
        //while true loop only for live testing
//        while (true){
//
//        }
    }

    @Test
    void limitAndOffsetTest() throws Exception {
        String config = "build/resources/test/evaluation/limit_and_offset/config.json";
        String query = "{Person(" + SPARQLServiceConverter.ID + ": [\\\"http://www.example.org/alice\\\"]" + " ){" + SPARQLServiceConverter.ID + " label(" + SPARQLServiceConverter.ORDER + ": " + SPARQLServiceConverter.ORDER_DESC + ", " + SPARQLServiceConverter.LIMIT + ": 1, " + SPARQLServiceConverter.OFFSET + ": 1) }}";
        JSONObject json_response = sendPost(config, query);
        System.out.println(json_response);
        Thread.sleep(SOCKET_CLOSING);
        for (Object o : json_response.getJSONObject("data").getJSONArray("Person")) {
            JSONObject person = (JSONObject) o;
            if(person.has(SPARQLServiceConverter.ID)){
                if(person.getString("_id").equals("http://www.example.org/alice")){
                    if(person.has("label")){
                        JSONArray label = person.getJSONArray("label");
                        assertTrue(label.length() == 1);
                        assertEquals("Alice2", label.get(0));
                    }

                }
            }
        }
    }

    //ToDo: Finish this test
    //@Test
    void foreignEndpointTest() throws Exception {
        String config = "build/resources/test/evaluation/foreignEndpoint/config_extraction.json";
        Application.main(new String[]{"-config", config});
        Thread.sleep(SOCKET_CLOSING);
        //ToDo: Write test cases -> while true loop only for live testing
//        while(true){
//
//        }
    }


    @Test
    void literalHandlingTest() throws Exception{
        String config = "build/resources/test/evaluation/literal_handling/config.json";
        String query = "{ex_Person{ex_name ex_address{...on " + HGQL_SCALAR_LITERAL_GQL_NAME +
                "{" + HGQL_SCALAR_LITERAL_VALUE_GQL_NAME + "} ...on ex_Address{ex_house_number}}}}";
        JSONObject json_response = sendPost(config, query);
        System.out.println(json_response);
        JSONObject data = (JSONObject) json_response.get("data");
        Boolean hasLiteral = false;
        Boolean hasObject = false;
        if(data != null){
            JSONObject person = (JSONObject) data.getJSONArray("ex_Person").get(0);
            if(person != null){
                JSONArray addr = person.getJSONArray("ex_address");
                for(int i = 0; i< addr.length(); i++){
                    JSONObject obj = (JSONObject) addr.get(i);
                    if(obj.has(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME)){
                        assertTrue(obj.getJSONArray(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME).get(0).equals("742 Evergreen Terrace"));
                        hasLiteral = true;
                    }
                    if(obj.has("ex_house_number")){
                        assertTrue(obj.getJSONArray("ex_house_number").get(0).equals("742"));
                        hasObject = true;
                    }
                };
            }
        }
        assertTrue(hasLiteral && hasObject);
    }

    @Test
    void testTrigDataHandling() throws Exception{
        String config = "build/resources/test/evaluation/trig/config.json";
        String query = "{City{_id label located_in{_id label consists_of{_id label}}}}";
        JSONObject json_response = sendPost(config, query);
        System.out.println(json_response.toString(3));
        assertTrue(json_response != null);
        assertTrue(json_response.has("data"));
        assertTrue(json_response.getJSONObject("data").has("City"));
        for (Object res_item : json_response.getJSONObject("data").getJSONArray("City")){
            JSONObject res = (JSONObject) res_item;
            assertTrue(res.has("_id"));
            String[] match_id = {"http://www.example.org/#Berlin", "http://www.example.org/#Aachen"};
            String match = res.getString("_id");
            assertTrue(Arrays.stream(match_id).anyMatch(s -> s.equals(match)));
            assertTrue(res.has("located_in"));
            assertEquals("http://www.example.org/#Germany", res.getJSONObject("located_in").getString("_id"));
            assertTrue( res.getJSONObject("located_in").has("consists_of"));
            assertTrue( res.getJSONObject("located_in").getJSONArray("consists_of").length() == 2);
        }
    }



    private JSONObject sendPost(String config, String query) throws Exception {
        Application.main(new String[]{"-config", config});
        Thread.sleep(SOCKET_CLOSING);
        int port = getPort(config);
        HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead

        try {

            HttpPost request = new HttpPost("http://localhost:"+port+"/graphql");
            StringEntity params =new StringEntity("{\"query\":\""+query+"\",\"variables\":null,\"operationName\":null}");
            request.addHeader("content-type", "application/json+turtle");
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);

            //handle response here...
            JSONObject json_response = new JSONObject(EntityUtils.toString(response.getEntity()));
            return json_response;
        }catch (Exception ex) {
            System.out.println(ex);
            //handle exception here

        } finally {
            //Deprecated
            //httpClient.getConnectionManager().shutdown();
        }
        return null;

    }

    @Test
    void start() {
    }

    @Test
    void testStart() {
    }

    private int getPort(String configFile) throws IOException {
        return getConfig(configFile).getJSONObject("server").getInt("port");
    }

    private JSONObject getConfig(String configfile) throws IOException {
        String file = new String(Files.readAllBytes(Paths.get(configfile)));
        final JSONObject jsonObject = new JSONObject(file);
        return jsonObject;
    }
}