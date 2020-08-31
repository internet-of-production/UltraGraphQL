package org.hypergraphql.datafetching.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.*;
import org.hypergraphql.config.schema.FieldConfig;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datafetching.services.resultmodel.ObjectResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datafetching.services.resultmodel.StringResult;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.datamodel.QueryNode;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.hypergraphql.query.pattern.Query;
import org.hypergraphql.query.pattern.QueryPattern;
import org.hypergraphql.query.pattern.SubQueriesPattern;

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

    public abstract TreeExecutionResult executeQuery(Query query, Set<String> input, Set<String> strings, String rootType, HGQLSchema schema);

    public abstract void setParameters(ServiceConfig serviceConfig);

    /** ToDo: Review documentation -------------------------------------------------------------------------------------
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
    public Result getModelFromResults(Query query, QuerySolution results , HGQLSchema schema) {

        Result res = null; // ToDo: Initalize res properly i.e. parent npde
        Map<String, Result> subfields = new TreeMap<>();
        if (query == null) {
            return res;
        }
        String parentName = null;
        String parentAlias = null;
        String parentType = null;
        Map<String, Object> parentArgs = null;
        String parentId = null;

        if (query.isSubQuery()) { // selectionSet with multiple fields

            for(QueryPattern currentNode : ((SubQueriesPattern) query).getSubqueries()){
                Result subRes = buildModel(results, currentNode, schema, null);
                if(subRes instanceof ObjectResult && results.contains(currentNode.nodeId)) {
                    getModelFromResults(currentNode.fields,
                            results,
                            schema,
                            ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(currentNode.nodeId).toString()),
                            query);
                }
                addAndMerge(subfields, currentNode.name, subRes);
                if(currentNode.parentName != null){
                    parentName = currentNode.parentName;
                    parentAlias = currentNode.parentAlias;
                    parentType = currentNode.parentType;
                    parentArgs = currentNode.parentArgs;
                    parentId = currentNode.parentId;
                }
            }
//            Iterator<JsonNode> nodesIterator = query.elements();
//            while (nodesIterator.hasNext()) {
//                JsonNode currentNode = nodesIterator.next();
//                Result subRes = buildModel(results, currentNode, schema, null);
//                if(subRes instanceof ObjectResult) {
//                    getModelFromResults(currentNode.get("fields"),
//                            results,
//                            schema,
//                            ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(currentNode.get("nodeId").asText()).toString()),
//                            query);
//                }
//                addAndMerge(subfields, currentNode.get("name").asText(), subRes);
//                if(parentName == null && currentNode.has("parentName")){
//                    parentName = currentNode.get("parentName").asText();
//                    parentAlias = currentNode.get("parentAlias").asText();
//                    parentType = currentNode.get("parentType").asText();
//                    parentArgs = currentNode.get("parentArgs").asText();
//                    parentId = currentNode.get("parentId").asText();
//                }
//            }
        } else {
            QueryPattern queryPattern = (QueryPattern) query;
            Result subRes = buildModel(results, queryPattern, schema, null);
            if(subRes instanceof ObjectResult){
                getModelFromResults(queryPattern.fields,
                        results,
                        schema,
                        ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(queryPattern.nodeId).toString()),
                        queryPattern);
            }
            subfields.put(queryPattern.name, subRes);

            if(queryPattern.parentName != null){
                parentName = queryPattern.parentName;
                parentAlias = queryPattern.parentAlias;
                parentType = queryPattern.parentType;
                parentArgs = queryPattern.parentArgs;
                parentId = queryPattern.parentId;
            }
        }

        if(parentName != null){
            // If the parentTypeName is not null then the query is as subquery and depending on the object of the overlaying query
            res = new ObjectResult(parentName, parentAlias);
            res.setNodeId(parentId);
            if(schema.getFields().containsKey(parentType) && schema.getTypes().get(parentType).getFields().containsKey(parentName)){
                // parent field is normal field
                res.isList(schema.getTypes().get(parentType).getField(parentName).isList());
            }else if(schema.getQueryFields().containsKey(parentName)){
                // parent field is query field -> always list
                res.isList(true);
            }else{
                // parent field unkown -> assume list to avoid conflicts
                res.isList(true);
            }

            ((ObjectResult)res).addObject(results.get(parentId).toString(), subfields);
            return res;
        }
        if(subfields.size() == 1){
            return subfields.values().iterator().next();
        }else if(subfields.isEmpty()){
            return null;
        }
        return res;

    }

    /** ToDo: Review documentation -------------------------------------------------------------------------------------
     *
     * @param query
     * @param results
     * @param schema
     * @param subfields
     * @param parentQuery
     */
    private void getModelFromResults(Query query, QuerySolution results , HGQLSchema schema, Map<String, Result> subfields, Query parentQuery) {

        if (query == null) {
            return;
        }

        if (query.isSubQuery()) { // selectionSet with multiple fields

            for(QueryPattern currentNode : ((SubQueriesPattern)query).subqueries){
                if(JSONLD.containsKey(currentNode.name)){
                    //Internal field result to these fields is resolved differently
                    if(currentNode.name.equals(SPARQLServiceConverter.ID)){
                        final StringResult id = new StringResult(currentNode.name);
                        id.isList(false);
                        id.setNodeId(currentNode.nodeId);
                        if(results.contains(currentNode.parentId)){
                            id.addString(results.get(currentNode.parentId).toString());
                        }
                        subfields.put(currentNode.name,id);
                        continue;
                    }else if(currentNode.name.equals(SPARQLServiceConverter.TYPE)){
                        if(parentQuery instanceof QueryPattern){
                            String typeId = schema.getTypes().get(((QueryPattern) parentQuery).targetType).getId();  // ToDo: add existences check
                            if(typeId != null){
                                final StringResult type = new StringResult(currentNode.name);
                                type.setNodeId(currentNode.nodeId);
                                type.isList(false);
                                type.addString(typeId);
                                subfields.put(currentNode.name, type);
                            }
                        }else{
                            //ToDo: Handle this case
                        }
                        continue;
                    }
                }
                if((currentNode.name.equals(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME))){
                    continue;
                }
                Result subRes = null;
                if(parentQuery.isSubQuery()){
                    final Optional<QueryPattern> parent = ((SubQueriesPattern) parentQuery).subqueries.stream().filter(queryPattern -> queryPattern.nodeId.equals(currentNode.parentId)).findAny();
                    if(parent.isPresent()){
                        subRes = buildModel(results, currentNode, schema, (QueryPattern) parent.get());
                    }else{
                        //error this stat should not be reached
                    }

                }else{
                    subRes = buildModel(results, currentNode, schema, (QueryPattern) parentQuery);
                }

                if(subRes instanceof ObjectResult && !currentNode.targetType.equals(HGQL_SCALAR_LITERAL_GQL_NAME)) {
                    String nodeId = currentNode.nodeId;
                    if(results.contains(nodeId)){
                        getModelFromResults(currentNode.fields,
                                results,
                                schema,
                                ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(nodeId).toString()),
                                currentNode);
                    }
                }
                addAndMerge(subfields, currentNode.name, subRes);
            }

//            Iterator<JsonNode> nodesIterator = query.elements();
//
//            while (nodesIterator.hasNext()) {
//                JsonNode currentNode = nodesIterator.next();
//                if(JSONLD.containsKey(currentNode.get("name").asText())){
//                    //Internal field result to these fields is resolved differently
//                    if(currentNode.get("name").asText().equals(SPARQLServiceConverter.ID)){
//                        final StringResult id = new StringResult(currentNode.get("name").asText());
//                        id.isList(false);
//                        id.setNodeId(currentNode.get("nodeId").asText());
//                        if(results.contains(currentNode.get("parentId").asText())){
//                            id.addString(results.get(currentNode.get("parentId").asText()).toString());
//                        }
//                        subfields.put(currentNode.get("name").asText(),id);
//                        continue;
//                    }else if(currentNode.get("name").asText().equals(SPARQLServiceConverter.TYPE)){
//                        String typeId = schema.getTypes().get(parentQuery.get("targetName")).getId();  // ToDo: add existences check
//                        if(typeId != null){
//                            final StringResult type = new StringResult(currentNode.get("name").asText());
//                            type.setNodeId(currentNode.get("nodeId").asText());
//                            type.isList(false);
//                            type.addString(typeId);
//                            subfields.put(currentNode.get("name").asText(), type);
//                        }
//                        continue;
//                    }
//                }
//                if((currentNode.get("name").asText().equals(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME))){
//                    continue;
//                }
//                Result subRes = buildModel(results, currentNode, schema, parentQuery);
//                if(subRes instanceof ObjectResult && !currentNode.get("targetName").asText().equals(HGQL_SCALAR_LITERAL_GQL_NAME)) {
//                    String nodeId = currentNode.get("nodeId").asText();
//                    if(results.contains(nodeId)){
//                        getModelFromResults(currentNode.get("fields"),
//                                results,
//                                schema,
//                                ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(nodeId).toString()),
//                                currentNode);
//                    }
//                }
//                addAndMerge(subfields, currentNode.get("name").asText(), subRes);
//            }
        } else {
            QueryPattern queryPattern = (QueryPattern) query;
            if(JSONLD.containsKey(queryPattern.name)){
                //Internal field result to these fields is resolved differently
                if(queryPattern.name.equals(SPARQLServiceConverter.ID)){
                    final StringResult id = new StringResult(queryPattern.name);
                    id.isList(false);
                    id.setNodeId(queryPattern.nodeId);
                    if(results.contains(queryPattern.parentId)){
                        id.addString(results.get(queryPattern.parentId).toString());
                    }
                    subfields.put(queryPattern.name,id);
                    return;
                }else if(queryPattern.name.equals(SPARQLServiceConverter.TYPE)){
                    if(parentQuery instanceof QueryPattern){
                        String typeId = schema.getTypes().get(((QueryPattern) parentQuery).targetType).getId();  //ToDo: Add existence check
                        if(typeId != null){
                            final StringResult type = new StringResult(queryPattern.name);
                            type.isList(false);
                            type.setNodeId(queryPattern.nodeId);
                            type.addString(typeId);
                            subfields.put(queryPattern.name, type);
                        }
                    }else{
                        //Todo: handle this case
                    }

                    return;
                }
            }
            Result subRes = buildModel(results, queryPattern, schema, (QueryPattern) parentQuery);
            if(subRes instanceof ObjectResult){
                getModelFromResults(queryPattern.fields,
                        results,
                        schema,
                        ((ObjectResult) subRes).getSubfiedldsOfObject(results.get(queryPattern.nodeId).toString()),
                        query);
            }
            addAndMerge(subfields, queryPattern.name, subRes);
        }

    }

    /** ToDo: Review documentation -------------------------------------------------------------------------------------
     *
     * @param subfields
     * @param subfield
     * @param result
     */
    private void addAndMerge(Map<String, Result> subfields, String subfield, Result result){
        if(subfields.containsKey(subfield)){
            // Field already in list merge result sets
            subfields.get(subfield).merge(result);
        }else{
            subfields.put(subfield, result);
        }
    }

    /** ToDo: Review documentation -------------------------------------------------------------------------------------
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
    private Result buildModel(QuerySolution results, QueryPattern currentNode , HGQLSchema schema, QueryPattern parentNode) {



        FieldConfig propertyString = schema.getFields().get(currentNode.name);
        TypeConfig targetTypeString = schema.getTypes().get(currentNode.targetType);   // field output type
        Result res;
        String field = currentNode.name;
        String alias = currentNode.alias;
        if(currentNode.targetType.equals("String")){
            res = new StringResult(field, alias, currentNode.args);
            res.setNodeId(currentNode.nodeId);
        }else if(currentNode.targetType.equals(HGQL_SCALAR_LITERAL_GQL_NAME)) {
            res = new ObjectResult(field, alias, currentNode.args);
            String nodeId = currentNode.nodeId;
            res.setNodeId(nodeId);
            ((ObjectResult)res).addObject(HGQL_QUERY_NAMESPACE + nodeId.hashCode()); // Add Literal Placeholder object

        }else{
            res = new ObjectResult(field, alias, currentNode.args);
            String nodeId = currentNode.nodeId;
            res.setNodeId(nodeId);
            if(results.contains(nodeId)) {
                ((ObjectResult) res).addObject(results.get(nodeId).toString());
            }
        }
        if(parentNode == null){
            // root query field
            res.isList(true);
        }else{
            final String targetName = parentNode.targetType;
            if(targetName.equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
                res.isList(false);
            }else{
                res.isList(schema.getTypes().get(targetName).getField(field).isList());
            }
        }

        populateModel(results, currentNode, res, propertyString, targetTypeString);

        return res;
    }

    //only used by HGraphQLService
    Map<String, Set<String>> getResultset(Model model, Query query, Set<String> input, Set<String> markers, HGQLSchema schema) {

        Map<String, Set<String>> resultset = new HashMap<>();
        SubQueriesPattern node;

        if (query instanceof SubQueriesPattern) {
            node = (SubQueriesPattern) query; // TODO - in this situation, we should iterate over the array
        }else if(query instanceof QueryPattern) {
            node = ((QueryPattern) query).fields;
            if (markers.contains(((QueryPattern) query).nodeId)){
                resultset.put(((QueryPattern) query).nodeId,findRootIdentifiers(model,schema.getTypes().get(((QueryPattern) query).targetType)));
            }
        }else{
            node = null;
        }
        Set<LinkedList<QueryNode>> paths = new HashSet<>();
        if (node != null) {
//            paths = getQueryPaths(node, schema);   // commented out because UltraGraphQL cuurently does not support other UGQL instances as services
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

    /** ToDo: Review documentation -------------------------------------------------------------------------------------
     * ToDo: Rework Description
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
     * @param res A model to insert the generated triples.
     * @param propertyString FieldConfig of the field which is the root of the currentNode
     * @param targetTypeString TypeConfig of the field of which is the root of the currentNode
     */
    private void populateModel(
            final QuerySolution results,
            final QueryPattern currentNode,
            final Result res,
            final FieldConfig propertyString,
            final TypeConfig targetTypeString
    ) {
        if(currentNode.name.equals(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME)){
            return;
        }
        if(propertyString != null && currentNode.targetType.equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            String nodeId = currentNode.nodeId;
            if(results.contains(nodeId)){
                RDFNode value = results.get(nodeId);

                final StringResult literalValue = new StringResult(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME);
                literalValue.setNodeId(currentNode.nodeId);
                literalValue.addString(value.toString());
                literalValue.isList(true);
                Map<String, Result> literalValueField = new TreeMap<>();
                literalValueField.put(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME, literalValue);
                if(res instanceof ObjectResult){
                    ((ObjectResult) res).addObject(HGQL_QUERY_NAMESPACE + nodeId.hashCode(), literalValueField);
                }
            }
            return;
        }

        if (propertyString != null && !(currentNode.parentId.equals("null"))) {
            RDFNode object = results.get(currentNode.nodeId);
            if(object != null){
                // object exists
                final TreeMap<String, Result> subfields = new TreeMap<>();
                if (targetTypeString == null) {
                    // object is literal
                    if(res instanceof  StringResult){
                        ((StringResult) res).addString(object.asLiteral().getString());
                        res.setNodeId(currentNode.nodeId);
                    }
                }
                if(res instanceof ObjectResult){
                    ((ObjectResult) res).addObject(object.toString(), subfields);
                }
            }
        }
    }
}




