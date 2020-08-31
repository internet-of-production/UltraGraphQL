package org.hypergraphql.schemaextraction;

import org.apache.jena.rdf.model.*;
import org.hypergraphql.config.schema.HGQLVocabulary;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides an API for the schema mapping configuration
 */
public class MappingConfig {

    private Model mapping;
    Property a;

    /**
     * Initialize the Mapping Configuration with the given model.
     * @param mapConfig Schema mapping configuration
     */
    public MappingConfig(Model mapConfig){
        this.mapping = mapConfig;
        this.a = this.mapping.getProperty(HGQLVocabulary.RDF_TYPE);
    }

    /**
     * Returns all mappings to an HGQL object in the schema
     * @return Set of RDFNodes representing object mappings
     */
    Set<RDFNode> getTypeMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_OBJECT);
        return new HashSet<>(res);
    }

    /**
     * Returns all mappings to an HGQL field in the schema
     * @return Set of RDFNodes representing field mappings
     */
    Set<RDFNode> getFieldsMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_FIELD);
        return new HashSet<>(res);
    }

    /**
     * Returns all mappings to the outputtype of an field in the HGQL schema
     * @return Set of Properties representing mappings to the outputtype of an field
     */
    Set<Property> getOutputTypeMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_FIELD_OUTPUTTYPE);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings to the domains of an field in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getFieldAffiliationMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_FIELD_OBJECT);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings to implied fields in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getImpliedFieldMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_IMPLIED_FIELD);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings for objects that implement another object in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getImplementsMapping() {
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_IMPLEMENTS);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings to fields that share the output types in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getEquivalentFieldMapping(){   //ToDo: Change name to sharedOutputTyoe
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_SHARED_OUTPUTTYPE);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings to objects that share the same set of fields in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getEquivalentTypeMapping(){
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_IMPLEMENTS_MUTUALLY);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all mappings that represent that enteties are the same in the HGQL schema
     * @return Set of Properties representing mappings to the object of an field
     */
    Set<Property> getSameAsMapping(){
        List<RDFNode> res = this.getSubjectsOfObjectProperty(a, HGQLVocabulary.HGQLS_SAME_AS);
        return res.stream()
                .map(this::getPropertyFromRDFNode)
                .collect(Collectors.toSet());
    }



    /**
     * Return a Property instance in this model.
     * @param propertyURI the URI of the property
     * @return a property object
     */
    private Property getPropertyFromUri(String propertyURI) {

        return this.mapping.getProperty(propertyURI);
    }

    /**
     * Return a Property instance in this model.
     * @param node the property as RDFNode
     * @return a property object
     */
    private Property getPropertyFromRDFNode(RDFNode node) {

        return this.mapping.getProperty(node.toString());
    }

    /**
     * Return a Resource instance with the given URI in this model.
     * @param resourceURI the URI of the resource
     * @return a resource instance
     */
    private Resource getResourceFromUri(String resourceURI) {

        return this.mapping.getResource(resourceURI);
    }

    /**
     * Returns all subjects from the mapping configuration that have the given predicate and object.
     * @param predicateURI predicate the triple must contain
     * @param objectURI object the triple must contain
     * @return List of subjects that occur a triple with the given predicate and object
     */
    List<RDFNode> getSubjectsOfObjectProperty(String predicateURI, String objectURI) {

        return this.getSubjectsOfObjectProperty(getPropertyFromUri(predicateURI), objectURI);
    }

    /**
     * Returns all subjects from the mapping configuration that have the given predicate and object.
     * @param predicate predicate the triple must contain
     * @param objectURI object the triple must contain
     * @return List of subjects that occur a triple with the given predicate and object
     */
    List<RDFNode> getSubjectsOfObjectProperty(Property predicate, String objectURI) {

        ResIterator iterator = this.mapping.listSubjectsWithProperty(predicate, getResourceFromUri(objectURI));
        List<RDFNode> nodeList = new ArrayList<>();
        iterator.forEachRemaining(nodeList::add);
        return nodeList;
    }

    public static void main(String[] args) throws FileNotFoundException {
        Model model = ModelFactory.createDefaultModel();
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        String inputFileName = "./src/main/resources/mapping.ttl";
        System.out.print(inputFileName);
        model.read(new FileInputStream(inputFileName),null,"TTL");
        model.write(System.out);
        MappingConfig conf = new MappingConfig(model);
        System.out.print(conf.getTypeMapping());
    }
}
