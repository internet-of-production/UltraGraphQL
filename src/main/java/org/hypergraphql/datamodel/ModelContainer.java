package org.hypergraphql.datamodel;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by szymon on 22/08/2017.
 */

public class ModelContainer {

    protected Model model;   // An RDF Model

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelContainer.class);

    public String getDataOutput(String format) {

        StringWriter out = new StringWriter();
        model.write(out, format);
        return out.toString();
    }

    public ModelContainer(Model model) {
        this.model = model;
    }

    /**
     * Return a Property instance in this model.
     * @param propertyURI the URI of the property
     * @return a property object
     */
    private Property getPropertyFromUri(String propertyURI) {

        return this.model.getProperty(propertyURI);
    }

    /**
     * Return a Resource instance with the given URI in this model.
     * @param resourceURI the URI of the resource
     * @return a resource instance
     */
    private Resource getResourceFromUri(String resourceURI) {

        return this.model.getResource(resourceURI);
    }

    /**
     * Returns all subjects from the mapping configuration that have the given predicate and object.
     * @param predicateURI predicate the triple must contain
     * @param objectURI object the triple must contain
     * @return List of subjects that occur a triple with the given predicate and object
     */
    List<RDFNode> getSubjectsOfObjectProperty(String predicateURI, String objectURI) {

        ResIterator iterator = this.model.listSubjectsWithProperty(getPropertyFromUri(predicateURI), getResourceFromUri(objectURI));
        List<RDFNode> nodeList = new ArrayList<>();
        iterator.forEachRemaining(nodeList::add);
        return nodeList;
    }

    String getValueOfDataProperty(RDFNode subject, String predicateURI) {

        return getValueOfDataProperty(subject, predicateURI, new HashMap<>());
    }

    String getValueOfDataProperty(RDFNode subject, String predicateURI, Map<String, Object> args) {

        final NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));
        while (iterator.hasNext()) {

            final RDFNode data = iterator.next();
            if (data.isLiteral()) {

                if (args.containsKey("lang")
                        && args.get("lang").toString().equalsIgnoreCase(data.asLiteral().getLanguage())) {
                    return data.asLiteral().getString();
                } else {
                    return data.asLiteral().getString();
                }
            }
        }
        return null;
    }

    List<String> getValuesOfDataProperty(RDFNode subject, String predicateURI, Map<String, Object> args) {

        final List<String> valList = new ArrayList<>();

        final NodeIterator iterator = model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));

        while (iterator.hasNext()) {

            RDFNode data = iterator.next();

            if (data.isLiteral()) {
                if (!args.containsKey("lang") || args.get("lang").toString().equalsIgnoreCase(data.asLiteral().getLanguage())) {
                        valList.add(data.asLiteral().getString());
                }
            }
        }
        return valList;
    }

    /**
     * Queries the model for all objects that have the given subject AND predicateURI.
     * @param subjectURI
     * @param predicateURI
     * @return
     */
    List<RDFNode> getValuesOfObjectProperty(String subjectURI, String predicateURI) {

        return getValuesOfObjectProperty(getResourceFromUri(subjectURI), predicateURI);
    }

    /**
     * Queries the model for all objects that have the given subject AND predicateURI.
     * @param subject
     * @param predicateURI
     * @return
     */
    List<RDFNode> getValuesOfObjectProperty(RDFNode subject, String predicateURI) {

        return getValuesOfObjectProperty(subject, predicateURI, null);
    }

    /**
     * Queries the model for all objects that have the given subject AND predicateURI. If targetURI is null all objects are returned
     * otherwise only objects of the given typeURI are returned. Query: subject predicateURI ?object. ?object a targetURI.
     * @param subject
     * @param predicateURI
     * @param targetURI Type the result should have
     * @return
     */
    List<RDFNode> getValuesOfObjectProperty(RDFNode subject, String predicateURI, String targetURI) {

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI)); // subject predicateURI ?object
        List<RDFNode> rdfNodes = new ArrayList<>();
        iterator.forEachRemaining(node -> {
            if (!node.isLiteral()) {
                if(targetURI == null) {
                    rdfNodes.add(node);
                } else if(this.model.contains(node.asResource(), getPropertyFromUri(HGQLVocabulary.RDF_TYPE), getResourceFromUri(targetURI))) {
                    rdfNodes.add(node); // Add node if (node rdf:type targetURI.)
                }
            }
        });
        return rdfNodes;
    }

    RDFNode getValueOfObjectProperty(RDFNode subject, String predicateURI) {

        final List<RDFNode> values = getValuesOfObjectProperty(subject, predicateURI);
        if(values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    RDFNode getValueOfObjectProperty(RDFNode subject, String predicateURI, String targetURI) {
        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));

        while (iterator.hasNext()) {
            RDFNode next = iterator.next();
            if (!next.isLiteral()) {
                if (targetURI != null && this.model.contains(next.asResource(), getPropertyFromUri(HGQLVocabulary.RDF_TYPE), getResourceFromUri(targetURI))) {
                    return next;
                }
            }
        }
        return null;
    }

    /**
     *  Adds a RDF triple to the local RDF Model of the Object. All parameters MUST be URIs.
     * @param subjectURI subject of the triple
     * @param predicateURI predicate subject of the triple
     * @param objectURI object subject of the triple
     */
    void insertObjectTriple(String subjectURI, String predicateURI, String objectURI) {

        model.add(getResourceFromUri(subjectURI), getPropertyFromUri(predicateURI), getResourceFromUri(objectURI));

    }

    /**
     *  Adds a RDF Literal triple to the RDF Model of the Object. The Object is here a Literal (String) value
     * @param subjectURI subject of the triple
     * @param predicateURI predicate of the triple
     * @param value literal of the triple
     */
    void insertStringLiteralTriple(String subjectURI, String predicateURI, String value) {

        model.add(getResourceFromUri(subjectURI), getPropertyFromUri(predicateURI), value);

    }
}
