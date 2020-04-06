package org.hypergraphql.schemaextraction;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.web.HttpOp;
import org.apache.jena.sparql.engine.http.Service;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SPARQLExtractionTest {

    private static final String NAME_DATASET_SUBCLASS = "/subclass";
    static Logger log = Logger.getLogger(SPARQLExtractionTest.class.getName());
    private Dataset ds1;
    private Dataset ds2;
    private Dataset da_subclass;
    private static final String SERVICE_NAMESPACE = "http://localhost:";
    private static final Integer SERVER_1_PORT= 8001;
    private static final Integer SERVER_2_PORT= 8002;
    private static final String SERVICE_1_URL = SERVICE_NAMESPACE + SERVER_1_PORT;
    private static final String SERVICE_2_URL = SERVICE_NAMESPACE + SERVER_2_PORT;
    private static final String NAME_DATASET = "/dataset";
    private static final String DS1_URL = SERVICE_1_URL + NAME_DATASET;
    private static final String DS2_URL = SERVICE_2_URL + NAME_DATASET;
    private static final String DS1_SUBCLASS_URL = SERVICE_1_URL + NAME_DATASET_SUBCLASS;

    private FusekiServer server1;
    private FusekiServer server2;
    private String template_query_file_path = "./src/test/resources/test_mapping/queries/extraction_query_template.sparql";

    @BeforeEach
    void setUp() throws FileNotFoundException {
        server_1_setup();
        server_2_setup();
    }

    @AfterEach
    void tearDown() {
        server_1_tearDown();
        server_2_tearDown();
    }
    @Test
    void extractSchema() throws FileNotFoundException {
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        Model mapping = ModelFactory.createDefaultModel();
        mapping.read(new FileInputStream(inputFileName),null,"TTL");
        mapping.write(System.out);
        MappingConfig conf = new MappingConfig(mapping);
        SPARQLExtraction extractor = new SPARQLExtraction(conf,readFile(template_query_file_path));
        log.debug("Start to query server_1");
        Model res_1 = extractor.extractSchema(DS1_URL,"", "");
        res_1.write(System.out, "Turtle");
        log.info("Test 1: Check if Property is extracted");
        ResIterator resIterator = res_1.listSubjectsWithProperty(RDF.type, res_1.getResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"));
        if(resIterator.hasNext()){
            assertEquals("has",resIterator.next().getLocalName());
        }else{
            fail("Schema MUST have a Property");
        }
        NodeIterator nodeIterator = res_1.listObjectsOfProperty(RDF.type, RDF.type);
        if(nodeIterator.hasNext()){
            assertEquals("Property",nodeIterator.next().asResource().getLocalName());
        }else{
            fail("Schema MUST have a Class");
        }

        log.info("Test 2: Check if Class is extracted");
        resIterator = res_1.listSubjectsWithProperty(RDF.type, res_1.getResource("http://www.w3.org/2000/01/rdf-schema#Class"));
        if(resIterator.hasNext()){
            assertEquals("vehicle",resIterator.next().getLocalName());
        }else{
            fail("Schema MUST have a Class");
        }

        log.info("Test 3: Check if domain and range are extracted");
        nodeIterator = res_1.listObjectsOfProperty(RDF.type, res_1.getProperty("http://www.w3.org/2000/01/rdf-schema#domain"));
        if(nodeIterator.hasNext()){
            assertEquals("vehicle",nodeIterator.next().asResource().getLocalName());
            assertEquals("Class",nodeIterator.next().asResource().getLocalName());
        }else{
            fail("Schema MUST have a doamin");
        }
        nodeIterator = res_1.listObjectsOfProperty(RDF.type, res_1.getProperty("http://www.w3.org/2000/01/rdf-schema#range"));
        if(nodeIterator.hasNext()){
            assertEquals("Class",nodeIterator.next().asResource().getLocalName());
        }else{
            fail("Schema MUST have a doamin");
        }

    }

    @Test
    void subClassTest() throws FileNotFoundException {
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        Model mapping = ModelFactory.createDefaultModel();
        mapping.read(new FileInputStream(inputFileName),null,"TTL");
        mapping.write(System.out);
        MappingConfig conf = new MappingConfig(mapping);
        SPARQLExtraction extractor = new SPARQLExtraction(conf,readFile(template_query_file_path));
        log.debug("Start to query server_1");
        Model res_1 = extractor.extractSchema(DS1_SUBCLASS_URL,"", "", null);
        System.out.print("=================================");
        res_1.write(System.out, "Turtle");
        ResIterator resIterator = res_1.listSubjectsWithProperty(RDF.type, res_1.getResource("http://www.w3.org/2000/01/rdf-schema#Class"));
        String classes = resIterator.toSet().stream().map(r->r.getLocalName()).collect(Collectors.joining(","));
        //assertEquals("vehicle,car",classes);

    }

    @Test
    void extractSchemaFromLocalRDFFile() throws FileNotFoundException {
        // setup
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        Model mapping = ModelFactory.createDefaultModel();
        mapping.read(new FileInputStream(inputFileName),null,"TTL");
        mapping.write(System.out);
        MappingConfig conf = new MappingConfig(mapping);
        SPARQLExtraction extractor = new SPARQLExtraction(conf,readFile(template_query_file_path));
        log.info("Local RDF file test");
        String filemame = "./src/test/resources/test_mapping/data/dataset_1.ttl";
        String type = "TTL";
        Model res = extractor.extractSchemaFromLocalRDFFile(filemame, type);
        res.write(System.out, "Turtle");
    }

    @Test
    void query(){
        log.debug("Start to query server_1");
        String updateQuery = "INSERT {?s ?p ?o.} WHERE{ SERVICE <%s>{SELECT ?s ?p ?o WHERE{ ?s ?p ?o.}}}";
        Model upModel = ModelFactory.createDefaultModel();
        UpdateAction.parseExecute(String.format(updateQuery, DS1_URL), upModel);

        //Auth
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        Credentials credentials = new UsernamePasswordCredentials("user1", "pwd123");
        credsProvider.setCredentials(AuthScope.ANY, credentials);
        HttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
        HttpOp.setDefaultHttpClient(httpclient);
        UpdateAction.parseExecute(String.format(updateQuery, DS2_URL), upModel);
        log.info(upModel.listObjects().toList());
        log.info(upModel.listSubjects().toList());

    }

    @Test
    void schemExtractionOneEndpoint(){
        //ToDo:
    }

    @Test
    void schemaExtractionMultipleEndpoints(){
        //ToDo:
    }

    @Test
    void schemaExtractionOneEndpointWithAuth(){
        //ToDo:
    }

    @Test
    void schemaExtractionMultipleEndpointsWithAuth(){
        //ToDo:
    }


    private void server_1_setup() throws FileNotFoundException {
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/data/dataset_1.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        ds1 = DatasetFactory.create(model);
        Model model_subClass = ModelFactory.createDefaultModel();
        String inputFileName_subclass = "./src/test/resources/test_mapping/data/dataset_subclass.ttl";
        model_subClass.read(new FileInputStream(inputFileName_subclass),null,"TTL");
        ds1 = DatasetFactory.create(model);
        da_subclass = DatasetFactory.create(model_subClass);
        server1 = FusekiServer.create()
                .add(NAME_DATASET, ds1)
                .add(NAME_DATASET_SUBCLASS, da_subclass)
                .setPort(8001)
                .build();
        server1.start();
    }

    private void server_2_setup() throws FileNotFoundException {
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/data/dataset_2.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        ds2 = DatasetFactory.create(model);
        server2 = FusekiServer.create()
                .parseConfigFile("./src/test/resources/test_mapping/server_2_conf.ttl")
                .port(8002)
                .build();
        server2.start();
    }

    private void server_1_tearDown(){
        server1.stop();
    }

    private void server_2_tearDown(){
        server2.stop();
    }

    private static String readFile(String filePath)
    {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines( Paths.get(filePath), StandardCharsets.UTF_8))
        {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }
}