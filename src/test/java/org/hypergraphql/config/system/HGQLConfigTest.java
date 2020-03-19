package org.hypergraphql.config.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class HGQLConfigTest {

    private static ObjectMapper mapper;
    private static HGQLConfig config;
    private static HGQLConfig configMapping;

    @BeforeEach
    void setUp() throws IOException {
        mapper = new ObjectMapper();
        try(InputStream inputStream = new FileInputStream("./src/test/resources/test_config.json")){
            config = mapper.readValue(inputStream, HGQLConfig.class);
        }
        try(InputStream inputStream = new FileInputStream("./src/test/resources/test_config_with_mapping.json")) {
            configMapping = mapper.readValue(inputStream, HGQLConfig.class);
        }
    }

    @Test
    void getName() {
        assertEquals("mydemo", config.getName());
        assertEquals("hgql-example-with-sparql", configMapping.getName());
    }

    @Test
    void setName() {
        config.setName("1");
        configMapping.setName("2");
        assertEquals("1", config.getName());
        assertEquals("2", configMapping.getName());
    }

    @Test
    void getSchema() {

    }

    @Test
    void setSchema() {
    }

    @Test
    void setGraphQLSchema() {
    }

    @Test
    void getHgqlSchema() {
    }

    @Test
    void setHgqlSchema() {
    }

    @Test
    void getGraphqlConfig() {
        assertNotNull(config.getGraphqlConfig().port());  //If NO port is provided a random port is generated
        assertEquals("/graphql", config.getGraphqlConfig().graphQLPath());
        assertEquals("/graphiql", config.getGraphqlConfig().graphiQLPath());
        assertEquals(Integer.valueOf(8080), configMapping.getGraphqlConfig().port());
        assertEquals("/graphql", configMapping.getGraphqlConfig().graphQLPath());
        assertEquals("/graphiql", configMapping.getGraphqlConfig().graphiQLPath());
    }

    @Test
    void getSchemaFile() {
        assertEquals("test_schema.graphql", config.getSchemaFile());
        assertEquals("gql/schema.graphql", configMapping.getSchemaFile());
    }

    @Test
    void getServiceConfigs() {
        assertEquals("dbpedia",config.getServiceConfigs().get(0).getId());
        assertEquals("SPARQLEndpointService",config.getServiceConfigs().get(0).getType());
        assertEquals("http://live.dbpedia.org/sparql/",config.getServiceConfigs().get(1).getUrl());
        assertEquals("http://dbpedia.org",config.getServiceConfigs().get(0).getGraph());
        assertEquals("test",config.getServiceConfigs().get(3).getUser());
        assertEquals("pwd123",config.getServiceConfigs().get(3).getPassword());

        assertEquals("dbpedia-sparql",configMapping.getServiceConfigs().get(0).getId());
        assertEquals("SPARQLEndpointService",configMapping.getServiceConfigs().get(0).getType());
        assertEquals("http://dbpedia.org/sparql/",configMapping.getServiceConfigs().get(0).getUrl());
        assertEquals("http://dbpedia.org",configMapping.getServiceConfigs().get(0).getGraph());
        assertEquals("",configMapping.getServiceConfigs().get(0).getUser());
        assertEquals("",configMapping.getServiceConfigs().get(0).getPassword());
    }

    @Test
    void getExtraction() {
        assertTrue(config.getExtraction() == null || !config.getExtraction());
        assertTrue(configMapping.getExtraction());
    }

    @Test
    void getMappingFile() {
        assertNull(config.getMappingFile());
        assertEquals("mapping.ttl", configMapping.getMappingFile());
    }
}