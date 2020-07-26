package org.hypergraphql.datafetching.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.hypergraphql.config.schema.FieldConfig;
import org.hypergraphql.config.schema.QueryFieldConfig;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.datamodel.QueryNode;

import java.util.*;

import static org.hypergraphql.config.schema.HGQLVocabulary.*;

public abstract class Service {

    protected String type;
    protected String id;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public abstract TreeExecutionResult executeQuery(JsonNode query, Set<String> input, Set<String> strings, String rootType, HGQLSchema schema);

    public abstract void setParameters(ServiceConfig serviceConfig);

    /**
     * Builds a RDF model that contains information about the schema of the used SPARQL variables in the given query.
     * Adds for the currentNode a triple using the variable id of the parentNode as subject and the
     * variable id of the outputtype/target as object. Furthermore a triple indicating which object/class the
     * outputtype/target variable id is is added.
     * Example: A Query Company{owner{...}}  on the schema Company{owner: Person} will add the triples:
     *      x_1 owner x_1_2.
     *      x_1_2 a Person.
     *      hgql:query hgql:query/owner x_1_2.
     * Note: The variables used in the example can be arbitrary.
     * @param query JSON representation of a GraphQL query or subquery containing the variables for the SPARQL query
     * @param results Results of the query
     * @param schema HGQLSchema
     * @return Returns a model containing schema information of the query variables
     */
    public Model getModelFromResults(JsonNode query, QuerySolution results , HGQLSchema schema) {

        Model model = ModelFactory.createDefaultModel();
        if (query.isNull()) {
            return model;
        }

        if (query.isArray()) { // selectionSet with multiple fields

            Iterator<JsonNode> nodesIterator = query.elements();

            while (nodesIterator.hasNext()) {
                JsonNode currentNode = nodesIterator.next();
                Model currentModel = buildModel(results, currentNode , schema);
                model.add(currentModel);
                model.add(getModelFromResults(currentNode.get("fields"), results ,schema));   // recursive call for selectionSet of the field
            }
        } else {
            Model currentModel = buildModel(results, query , schema);
            model.add(currentModel);
            model.add(getModelFromResults(query.get("fields"), results, schema));   // recursive call for selectionSet of the field
        }
        return model;

    }

    /**
     * Builds a RDF model that contains information about the schema of the used SPARQL variables by querying the given
     * currentNode. Adds for the currentNode a triple using the variable id of the parentNode as subject and the
     * variable id of the outputtype/target as object. Furthermore a triple indicating which object/class the
     * outputtype/target variable id is is added.
     * Example: A Query Company{owner{...}}  on the schema Company{owner: Person} will add the triples:
     *      x_1 owner x_1_2.
     *      x_1_2 a Person.
     *      hgql:query hgql:query/owner x_1_2.
     * Note: The variables used in the example can be arbitrary.
     * @param results Results of the query
     * @param currentNode JSON representation of a GraphQL query or subquery containing the variables for the SPARQL query
     * @param schema HGQLSchema
     * @return Returns a model containing schema information of the query variables
     */
    private Model buildModel(QuerySolution results, JsonNode currentNode , HGQLSchema schema) {

        Model model = ModelFactory.createDefaultModel();

        FieldConfig propertyString = schema.getFields().get(currentNode.get("name").asText());
        TypeConfig targetTypeString = schema.getTypes().get(currentNode.get("targetName").asText());   // field output type
        populateModel(results, currentNode, model, propertyString, targetTypeString);

        QueryFieldConfig queryField = schema.getQueryFields().get(currentNode.get("name").asText());

        if (queryField != null) {

            String typeName = (currentNode.get("alias").isNull()) ? currentNode.get("name").asText() : currentNode.get("alias").asText();  // use the name if alias is null otherwise use alias
            Resource object = results.getResource(currentNode.get("nodeId").asText());
            Resource subject = model.createResource(HGQL_QUERY_URI);
            Property predicate = model.createProperty("", HGQL_QUERY_NAMESPACE + typeName);

            model.add(subject, predicate, object);
        }
        return model;
    }

    //only used by HGraphQLService
    Map<String, Set<String>> getResultset(Model model, JsonNode query, Set<String> input, Set<String> markers, HGQLSchema schema) {

        Map<String, Set<String>> resultset = new HashMap<>();
        JsonNode node;

        if (query.isArray()) {
            node = query; // TODO - in this situation, we should iterate over the array
        } else {
            node = query.get("fields");
            if (markers.contains(query.get("nodeId").asText())){
                resultset.put(query.get("nodeId").asText(),findRootIdentifiers(model,schema.getTypes().get(query.get("targetName").asText())));
            }
        }
        Set<LinkedList<QueryNode>> paths = new HashSet<>();
        if (node != null && !node.isNull()) {
            paths = getQueryPaths(node, schema);
        }

        paths.forEach(path -> {
            if (hasMarkerLeaf(path, markers)) {
                Set<String> identifiers = findIdentifiers(model, input, path);
                String marker = getLeafMarker(path);
                resultset.put(marker, identifiers);
            }
        });

        // TODO query happens to be an array sometimes - then the following line fails.

        return resultset;
    }

    //only used by method call of HGraphQLService
    private Set<String> findRootIdentifiers(Model model, TypeConfig targetName) {

        Set<String> identifiers = new HashSet<>();
        Model currentmodel = ModelFactory.createDefaultModel();
        Resource res = currentmodel.createResource(targetName.getId());
        Property property = currentmodel.createProperty(RDF_TYPE);

        ResIterator iterator = model.listResourcesWithProperty(property, res);

        while (iterator.hasNext()) {
            identifiers.add(iterator.nextResource().toString());
        }
        return identifiers;
    }

    //only used by method call of HGraphQLService
    private String getLeafMarker(LinkedList<QueryNode> path) {

        return path.getLast().getMarker();
    }

    //only used by method call of HGraphQLService
    private Set<String> findIdentifiers(Model model, Set<String> input, LinkedList<QueryNode> path) {

        Set<String> subjects;
        Set<String> objects;
        if (input == null) {
            objects = new HashSet<>();
        } else {
            objects = input;
        }

        // NB: This hasn't been converted to use the NIO streaming API as it uses reentrant recursion
        for (QueryNode queryNode : path) {
            subjects = new HashSet<>(objects);
            objects = new HashSet<>();
            if (!subjects.isEmpty()) {
                for (String subject : subjects) {
                    Resource subjectResource = model.createResource(subject);
                    NodeIterator partialObjects = model.listObjectsOfProperty(subjectResource, queryNode.getNode());
                    while (partialObjects.hasNext()) {
                        objects.add(partialObjects.next().toString());
                    }
                }

            } else {

                NodeIterator objectsIterator = model.listObjectsOfProperty(queryNode.getNode());
                while (objectsIterator.hasNext()) {
                    objects.add(objectsIterator.next().toString());
                }
            }
        }
        return objects;
    }

    //only used by method call of HGraphQLService
    private boolean hasMarkerLeaf(LinkedList<QueryNode> path, Set<String> markers) {

        for (String marker : markers) {
            if (path.getLast().getMarker().equals(marker)) {
                return true;
            }
        }
        return false;
    }

    private Set<LinkedList<QueryNode>> getQueryPaths(JsonNode query, HGQLSchema schema) {
        Set<LinkedList<QueryNode>> paths = new HashSet<>();
        getQueryPathsRecursive(query, paths, null ,  schema);
        return paths;
    }

    //only used by method call of HGraphQLService
    private void getQueryPathsRecursive(JsonNode query, Set<LinkedList<QueryNode>> paths, LinkedList<QueryNode> path, HGQLSchema schema) {

        Model model = ModelFactory.createDefaultModel();

        if (path == null) {
            path = new LinkedList<>();
        } else {
            paths.remove(path);
        }

        if (query.isArray()) {
            Iterator<JsonNode> iterator = query.elements();

            while (iterator.hasNext()) {
                JsonNode currentNode = iterator.next();
                getFieldPath(paths, path, schema, model, currentNode);
            }
        } else {
            getFieldPath(paths, path, schema, model, query);
        }
    }

    //only used by method call of HGraphQLService
    private void getFieldPath(Set<LinkedList<QueryNode>> paths, LinkedList<QueryNode> path, HGQLSchema schema, Model model, JsonNode currentNode) {

        LinkedList<QueryNode> newPath = new LinkedList<>(path);
        String nodeMarker = currentNode.get("nodeId").asText();
        String nodeName = currentNode.get("name").asText();
        FieldConfig field = schema.getFields().get(nodeName);
        if (field == null) {
            throw new RuntimeException("field not found");
        }

        Property predicate = model.createProperty(field.getId());
        QueryNode queryNode = new QueryNode(predicate, nodeMarker);
        newPath.add(queryNode);
        paths.add(newPath);
        JsonNode fields = currentNode.get("fields");
        if (fields != null && !fields.isNull()) {
            getQueryPathsRecursive(fields, paths, newPath, schema);
        }
    }

    /**
     * Builds a RDF model that contains information about the schema using the SPARQL variables. Adds for the
     * currentNode a triple using the variable id of the parentNode as subject and the variable id of the outputtype/target as
     * object. Furthermore a triple indicating which object/class the outputtype/target variable id is is added.
     * Example: A Query Company{owner{...}}  on the schema Company{owner: Person} will add the triples:
     *      x_1 owner x_1_2.
     *      x_1_2 a Person.
     * Note: The variables used in the example can be arbitrary.
     * ToDo: Change names of the parameter, the suffix "String" is misleading.
     * @param results Results of the query
     * @param currentNode JSON representation of a GraphQL query or subquery containing the variables for the SPARQL query
     * @param model A model to insert the generated triples.
     * @param propertyString FieldConfig of the field which is the root of the currentNode
     * @param targetTypeString TypeConfig of the field of which is the root of the currentNode
     */
    private void populateModel(
            final QuerySolution results,
            final JsonNode currentNode,
            final Model model,
            final FieldConfig propertyString,
            final TypeConfig targetTypeString
    ) {
        if(currentNode.get("name").asText().equals(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME)){
            return;
        }
        if(propertyString != null && currentNode.get("targetName").asText().equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            //ToDo:
            Resource subject = results.getResource(currentNode.get("parentId").asText());
            Property predicate = model.getProperty(propertyString.getId());
            final RDFNode nodeId = results.get(currentNode.get("nodeId").asText());
            if(nodeId != null){
                Resource object = model.getResource(HGQL_QUERY_NAMESPACE + nodeId.hashCode());
                Property literal_value = model.createProperty(HGQL_SCALAR_LITERAL_VALUE_URI);
                Resource literal = model.getResource(HGQL_SCALAR_LITERAL_URI);
                RDFNode value = results.get(currentNode.get("nodeId").asText());
                model.add(subject, predicate, object);
                model.add(object,literal_value,value);
                model.add(object, RDF.type, literal);
            }
            return;
        }

        if (propertyString != null && !(currentNode.get("parentId").asText().equals("null"))) {
            Property predicate = model.createProperty("", propertyString.getId());
            Resource subject = results.getResource(currentNode.get("parentId").asText());
            RDFNode object = results.get(currentNode.get("nodeId").asText());
            if (predicate != null && subject != null && object != null) {
                model.add(subject, predicate, object);
            }
        }

        if (targetTypeString != null) {
            Resource subject = results.getResource(currentNode.get("nodeId").asText());
            Resource object = model.createResource(targetTypeString.getId());
            if (subject != null && object != null) {
                model.add(subject, RDF.type, object);
            }
        }
    }
}




