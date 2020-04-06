package org.hypergraphql.schemaextraction;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class QueryTemplatingEngineTest {

    static Logger log = Logger.getLogger(QueryTemplatingEngine.class.getName());
    private MappingConfig mapping;
    private MappingConfig mapping_multiple;
    private String template_query_file_path = "./src/test/resources/test_mapping/queries/extraction_query_template.sparql";
    private String query_file_path = "./src/test/resources/test_mapping/queries/extraction_query.sparql";
    private String expected_template_query_multiple_file_path = "./src/test/resources/test_mapping/queries/expected_result_extraction_query_template_multiple_mappings.sparql";
    private String expected_template_query_file_path = "./src/test/resources/test_mapping/queries/expected_result_extraction_query_template.sparql";
    private String expected_query_file_path = "./src/test/resources/test_mapping/queries/expected_result_extraction_query.sparql";

    @BeforeEach
    void setUp() throws FileNotFoundException {
        String inputFileName = "./src/test/resources/test_mapping/mapping.ttl";
        String inputFileName_mapping = "./src/test/resources/test_mapping/mapping_extended_sameAs.ttl";
        Model model = ModelFactory.createDefaultModel();
        this.mapping = new MappingConfig(model.read(new FileInputStream(inputFileName),null,"TTL"));
        Model model_mapping = ModelFactory.createDefaultModel();
        this.mapping_multiple = new MappingConfig(model_mapping.read(new FileInputStream(inputFileName_mapping),null,"TTL"));
    }

    @Test
    void buildQuery() {
        log.info("Test 1: query with only the service variable");
        String query_template = readFile(template_query_file_path);
        String query_only_service_var = readFile(query_file_path);
        QueryTemplatingEngine engine = new QueryTemplatingEngine(query_only_service_var,mapping);
        String res = engine.buildQuery("TESTService_1", null);
        assertEquals(readFile(expected_query_file_path),res);
        log.info("Test 1: successful");
        log.info("Test 2: query with schema syntax from the mapping file but only ONE mapping per feature");
        QueryTemplatingEngine engine2 = new QueryTemplatingEngine(query_template,mapping);
        String res2 = engine2.buildQuery("TESTService_2", null);
        assertEquals(readFile(expected_template_query_file_path),res2);
        log.info("Test 2: successful");
        log.info("Test 3: query with schema syntax from the mapping file with multiple mapping for some features");
        QueryTemplatingEngine engine3 = new QueryTemplatingEngine(query_template,mapping_multiple);
        String res3 = engine3.buildQuery("TESTService_2", null);
        assertEquals(readFile(expected_template_query_multiple_file_path),res3);
        log.info("Test 3: successful");
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