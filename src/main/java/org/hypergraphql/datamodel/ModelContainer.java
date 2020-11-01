package org.hypergraphql.datamodel;

import org.apache.jena.arq.querybuilder.Order;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
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

    public void close(){
        this.model.close();
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

    public List<RDFNode> getValuesOfObjectPropertyWithArgs(String subjectURI, String predicateURI, String targetURI, Map<String, Object> args){
        return getValuesOfObjectPropertyWithArgs(getResourceFromUri(subjectURI),
                predicateURI,
                targetURI,
                args);
    }

    public List<RDFNode> getValuesOfObjectPropertyWithArgs(RDFNode subjectURI, String predicateURI, String targetURI, Map<String, Object> args){
        final Property property = getPropertyFromUri(predicateURI);
        final Resource target = (targetURI == null) ? null : getResourceFromUri(targetURI);

        return getValuesOfObjectPropertyWithArgs(subjectURI,
                property,
                target,
                args);
    }


        public List<RDFNode> getValuesOfObjectPropertyWithArgs(RDFNode subjectURI, RDFNode predicateURI, RDFNode targetURI, Map<String, Object> args){

        List<RDFNode> res = new ArrayList<>();
        SelectBuilder builder = new SelectBuilder();
        builder.addPrefix( "rdfs",  "http://www.w3.org/2000/01/rdf-schema#" );
        builder.addPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        builder.addVar( "?object" );
        if(args.containsKey("limit")){
            builder.setLimit((Integer) args.get("limit"));
        }
        // offset interferes with multiple services limiter are applied to fill the result pool and applied by extracting from the result pool. offset is therefore only applied during result pool fillup.
//        if(args.containsKey("offset")){
//            builder.setOffset((Integer) args.get("offset"));
//        }
        if(args.containsKey("_id")){
            final Object idValue = args.get("_id");
            if(idValue instanceof List){
                List<String> idlist = (List<String>) idValue;
                String ids = "";
                for(String id : idlist){
                    builder.addValueVar("?object",getResourceFromUri(id));

                }

            }else{

            }
        }
        if(args.containsKey("order")){
            String order = (String) args.get("order");
            if(order.equals("ASC")){
                builder.addOrderBy("?object", Order.ASCENDING);
            }else if(order.equals("DESC")){
                builder.addOrderBy("?object", Order.DESCENDING);
            }

        }
        builder.addWhere(subjectURI, predicateURI,"?object");
        if(targetURI != null){
            builder.addWhere("?object", "rdf:type", targetURI);
        }
        Query query = builder.build();
        LOGGER.debug("SPARQL query aganst the result pool: \n {}",query.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, this.model) ;
        ResultSet results = qexec.execSelect();
        while (results.hasNext()){
            final QuerySolution next = results.next();
            final RDFNode rdfNode = next.get("?object");
            res.add(rdfNode);
        }
        return res;
    }

    List<String> getValuesOfDataPropertyWithArgs(RDFNode subject, String predicateURI, Map<String, Object> args){
        final String OBJECT = "?object";
        List<String> res = new ArrayList<>();
        SelectBuilder builder = new SelectBuilder();
        builder.addPrefix( "rdfs",  "http://www.w3.org/2000/01/rdf-schema#" );
        builder.addPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        builder.addVar( OBJECT );
        if(args.containsKey("limit")){
            builder.setLimit((Integer) args.get("limit"));
        }
        if(args.containsKey("offset")){
            builder.setOffset((Integer) args.get("offset"));
        }
        if(args.containsKey("order")){
            String order = (String) args.get("order");
            if(order.equals("ASC")){
                builder.addOrderBy(OBJECT, Order.ASCENDING);
            }else if(order.equals("DESC")){
                builder.addOrderBy(OBJECT, Order.DESCENDING);
            }

        }
        builder.addWhere(subject, getPropertyFromUri(predicateURI),OBJECT);
        Query query = builder.build();
        LOGGER.debug("SPARQL query against the result pool: \n {}",query.toString());
        QueryExecution qexec = QueryExecutionFactory.create(query, this.model) ;
        ResultSet results = qexec.execSelect();
        while (results.hasNext()){
            final QuerySolution next = results.next();
            final RDFNode rdfNode = next.get("?object");
            if(rdfNode.isLiteral()){
                if(args.containsKey("lang")){
                    if(rdfNode.asLiteral().getLanguage().equals(args.get("lang"))){
                        res.add(rdfNode.asLiteral().getString());
                    }
                }else{
                    res.add(rdfNode.asLiteral().getString());
                }

            }
        }
        return res;
    }

    public static void main(String[] arguments){
        ModelContainer model = new ModelContainer(ModelFactory.createDefaultModel());
        String subject = "http://example.org/alice";
        String predicate = "http://example.org/friends";
        String target = "http://example.org/Person";
        Map<String, Object> args = new HashMap<>();
        args.put("limit", 2);
        args.put("offset", 4);
        args.put("_id", "http://example.org/bob");
        args.put("order", "DESC");
        model.getValuesOfObjectPropertyWithArgs(subject, predicate, target, args);
    }
}
