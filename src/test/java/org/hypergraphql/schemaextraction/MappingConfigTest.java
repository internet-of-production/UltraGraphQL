package org.hypergraphql.schemaextraction;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MappingConfigTest {


    @Test
    void getTypeMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2000/01/rdf-schema#Class, http://example.org/Klasse]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getTypeMapping().toString());
    }

    @Test
    void getFieldsMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/1999/02/22-rdf-syntax-ns#Property, http://example.org/Eigenschaft]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getFieldsMapping().toString());
    }

    @Test
    void getOutputTypeMapping() throws FileNotFoundException {
        String res = "[http://schema.org/rangeIncludes, http://www.w3.org/2000/01/rdf-schema#range]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getOutputTypeMapping().toString());
    }

    @Test
    void getFieldAffiliationMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2000/01/rdf-schema#domain, http://schema.org/domainInclude]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getFieldAffiliationMapping().toString());
    }

    @Test
    void getImpliedFieldMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2000/01/rdf-schema#subPropertyOf]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getImpliedFieldMapping().toString());
    }

    @Test
    void getImplementsMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2000/01/rdf-schema#subClassOf]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getImplementsMapping().toString());
    }

    @Test
    void getEquivalentFieldMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2002/07/owl#equivalentProperty, http://www.w3.org/2002/07/owl#equivalentEigenschaft]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getEquivalentFieldMapping().toString());
    }

    @Test
    void getEquivalentTypeMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2002/07/owl#equivalentKlasse, http://www.w3.org/2002/07/owl#equivalentClass]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getEquivalentTypeMapping().toString());
    }

    @Test
    void getSameAsMapping() throws FileNotFoundException {
        String res = "[http://www.w3.org/2002/07/owl#sameAs]";
        Model model = ModelFactory.createDefaultModel();
        String inputFileName = "./src/test/resources/test_mapping/mapping_extended.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        MappingConfig conf = new MappingConfig(model);
        assertEquals(res, conf.getSameAsMapping().toString());
    }

    @Test
    void getSubjectsOfObjectProperty() {
        //ToDo:
    }

    @Test
    void testGetSubjectsOfObjectProperty() {
        //ToDo:
    }
}
