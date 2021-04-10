package org.hypergraphql.schemaextraction;

import net.bytebuddy.implementation.bind.MethodDelegationBinder;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA_NAMESPACE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA_NAMESPACE_PREFIX;

class PrefixServiceTest {

    private PrefixService prefixService;

    @BeforeEach
    void setUp() {
        Map<String, String> testMappings = new HashMap<>();
        testMappings.put("http://www.example.org/", "ex");
        prefixService = new PrefixService(testMappings);
    }

    @Test
    void getNamespaceMapping() {

        Map<String, String>  namespace = prefixService.getNamespaceMapping();
        assertTrue(namespace.containsKey("http://www.example.org/"));
        assertEquals("ex", namespace.get("http://www.example.org/"));
        // check if the internal prefixes are added as well
        assertTrue(namespace.size() == 2);
        assertTrue(namespace.containsKey(HGQL_SCHEMA_NAMESPACE));
        assertEquals(HGQL_SCHEMA_NAMESPACE_PREFIX, namespace.get(HGQL_SCHEMA_NAMESPACE));
    }

    @Test
    void getId() {
        Resource resource = new ResourceImpl("http://www.example.org/Person");
        assertEquals("ex_Person", prefixService.getId(resource));
    }

    @Test
    void getPrefix() {
        Resource resource = new ResourceImpl("http://www.example.org/Person");
        assertEquals("ex", prefixService.getPrefix(resource));
    }

    @Test
    void randomString() {
    }
}