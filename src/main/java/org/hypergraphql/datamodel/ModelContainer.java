package org.hypergraphql.datamodel;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.*;
import org.apache.log4j.Logger;
import org.hypergraphql.config.schema.HGQLVocabulary;

/**
 * Created by szymon on 22/08/2017.
 */

public class ModelContainer {

    protected Model model;

    private static final Logger LOGGER = Logger.getLogger(ModelContainer.class);

    public String getDataOutput(String format) {

        StringWriter out = new StringWriter();
        model.write(out, format);
        return out.toString();
    }

    public ModelContainer(Model model) {
        this.model = model;
    }


    public Property getPropertyFromUri(String propertyURI) {

        return this.model.getProperty(propertyURI);
    }

    public Resource getResourceFromUri(String resourceURI) {

        return this.model.getResource(resourceURI);
    }

    public List<RDFNode> getSubjectsOfObjectProperty(String predicateURI, String objectURI) {

        ResIterator iterator = this.model.listSubjectsWithProperty(getPropertyFromUri(predicateURI), getResourceFromUri(objectURI));
        List<RDFNode> nodeList = new ArrayList<>();
        iterator.forEachRemaining(nodeList::add);
        return nodeList;
    }

    public String getValueOfDataProperty(RDFNode subject, String predicateURI) {

        return getValueOfDataProperty(subject, predicateURI, new HashMap<>());
    }

    public String getValueOfDataProperty(RDFNode subject, String predicateURI, Map<String, Object> args) {

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));
        while (iterator.hasNext()) {

            RDFNode data = iterator.next();
            if (data.isLiteral()) {

                if (args.containsKey("lang")) {
                    if (data.asLiteral().getLanguage().equals(args.get("lang").toString()))
                        return data.asLiteral().getString();
                } else {
                    return data.asLiteral().getString();
                }
            }
        }
        return null;
    }

    public List<String> getValuesOfDataProperty(RDFNode subject, String predicateURI, Map<String, Object> args) {

        List<String> valList = new ArrayList<>();

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));

        while (iterator.hasNext()) {

            RDFNode data = iterator.next();

            if (data.isLiteral()) {
                if (args.containsKey("lang")) {
                    if (data.asLiteral().getLanguage().equals(args.get("lang").toString()))
                        valList.add(data.asLiteral().getString());
                } else {
                    valList.add(data.asLiteral().getString());
                }
            }
        }
        return valList;
    }

    public List<RDFNode> getValuesOfObjectProperty(String subjectURI, String predicateURI) {

        return getValuesOfObjectProperty(getResourceFromUri(subjectURI), predicateURI);
    }

    public List<RDFNode> getValuesOfObjectProperty(RDFNode subject, String predicateURI) {

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));
        List<RDFNode> rdfNodes = new ArrayList<>();
        while (iterator.hasNext()) {
            RDFNode next = iterator.next();
            if (!next.isLiteral()) rdfNodes.add(next);
        }
        return rdfNodes;
    }

    public List<RDFNode> getValuesOfObjectProperty(RDFNode subject, String predicateURI, String targetURI) {

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));
        List<RDFNode> rdfNodes = new ArrayList<>();
        while (iterator.hasNext()) {
            RDFNode next = iterator.next();
            if (!next.isLiteral()) {
                if (targetURI!=null && this.model.contains(next.asResource(), getPropertyFromUri(HGQLVocabulary.RDF_TYPE), getResourceFromUri(targetURI))) {
                    rdfNodes.add(next);
                }
            }
        }
        return rdfNodes;

    }

    public RDFNode getValueOfObjectProperty(RDFNode subject, String predicateURI) {

        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));
        while (iterator.hasNext()) {

            RDFNode next = iterator.next();
            if (!next.isLiteral()) {
                return next;
            }
        }
        return null;
    }

    public RDFNode getValueOfObjectProperty(RDFNode subject, String predicateURI, String targetURI) {
        NodeIterator iterator = this.model.listObjectsOfProperty(subject.asResource(), getPropertyFromUri(predicateURI));

        do { // I _think_ this was the intention here
            RDFNode next = iterator.next();
            if (!next.isLiteral()) {
                if (targetURI!=null && this.model.contains(next.asResource(), getPropertyFromUri(HGQLVocabulary.RDF_TYPE), getResourceFromUri(targetURI))) {
                    return next;
                }
            }
        }
        while (iterator.hasNext());
        return null;
    }

    public void insertObjectTriple(String subjectURI, String predicateURI, String objectURI) {

        model.add(getResourceFromUri(subjectURI), getPropertyFromUri(predicateURI), getResourceFromUri(objectURI));

    }

    public void insertStringLiteralTriple(String subjectURI, String predicateURI, String value) {

        model.add(getResourceFromUri(subjectURI), getPropertyFromUri(predicateURI), value);

    }
}
