package org.hypergraphql.schemaextraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.assembler.assemblers.ReasonerFactoryAssembler;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.log4j.Logger;
import org.hypergraphql.config.system.ServiceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;



import static org.junit.jupiter.api.Assertions.*;

class ExtractionControllerTest {

    private static final String NAME_DATASET_SUBCLASS = "/subclass";
    static Logger log = Logger.getLogger(ExtractionControllerTest.class.getName());
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
    Model model_1;
    Model model_2;
    Model mapping;

    private FusekiServer server1;
    private FusekiServer server2;
    private String template_query_file_path = "./src/test/resources/test_mapping/queries/extraction_query_template.sparql";

    @BeforeEach
    void setUp() throws FileNotFoundException {
        //server_1_setup();
        server_2_setup();
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        mapping = ModelFactory.createDefaultModel();
        mapping.read(new FileInputStream(inputFileName),null,"TTL");
    }

    @AfterEach
    void tearDown() {
        server_2_tearDown();
    }

    @Test
    void getHGQLSchema() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ServiceConfig config_2 = mapper.readValue(new FileInputStream("./src/test/resources/test_mapping/service_2.json"), ServiceConfig.class);
        final ServiceConfig config_3 = mapper.readValue(new FileInputStream("./src/test/resources/test_mapping/service_3.json"), ServiceConfig.class);
        List<ServiceConfig> services = new ArrayList<ServiceConfig>();
        services.add(config_2);
        services.add(config_3);
        ExtractionController controller = new ExtractionController(services,mapping,readFile(template_query_file_path));
        String schema = controller.getHGQLSchema();
        log.info(schema);
    }

    @Test
    /*
    Test if services that are configured as excluded from extraction are not called during the extraction process
     */
    void testServiceExclusion() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ServiceConfig config_1 = mapper.readValue(new FileInputStream("./src/test/resources/test_mapping/service_1.json"), ServiceConfig.class);
        final ServiceConfig config_2 = mapper.readValue(new FileInputStream("./src/test/resources/test_mapping/service_2.json"), ServiceConfig.class);
        List<ServiceConfig> services = new ArrayList<ServiceConfig>();
        services.add(config_1);
        services.add(config_2);
        ExtractionController controller = new ExtractionController(services,mapping,readFile(template_query_file_path));
        String schema = controller.getHGQLSchema();
        log.debug(schema);
        assertFalse(schema.contains("Local-dataset-extraction-excluded"));
    }


    private void server_2_setup() throws FileNotFoundException {
        model_2 = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/data/dataset_2.ttl";
        model_2.read(new FileInputStream(inputFileName),null,"TTL");
        ds2 = DatasetFactory.create(model_2);
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