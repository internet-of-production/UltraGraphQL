package org.hypergraphql.query.converters;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.ArrayNode;
import jdk.internal.jimage.ImageReaderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.json.JsonArray;
import org.hypergraphql.config.schema.QueryFieldConfig;

import org.hypergraphql.datafetching.services.ManifoldService;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.datamodel.HGQLSchema;

import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.datafetching.services.SPARQLEndpointService;

import java.util.*;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_GQL_NAME;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_VALUE_GQL_NAME;

/**
 * The SPARQLServiceConverter provides methods to covert GraphQl queries into SPARQL queries according to the schema this
 * class is instantiated with.
 */
public class SPARQLServiceConverter {

    private final static String RDF_TYPE_URI = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
    private final static String NAME = "name";
    private final static String URIS = "uris";
    private final static String NODE_ID = "nodeId";
    private final static String LANG = "lang";
    private final static String FIELDS = "fields";
    private final static String ARGS = "args";
    private final static String TARGET_NAME = "targetName";
    private final static String PARENT_ID = "parentId";
    private final static String LIMIT = "limit";
    private final static String OFFSET = "offset";
    private final static String ORDER = "order";
    private final static String ORDER_DESC = "DESCENDING";
    private final static String ORDER_ASC = "ASCENDING";
    private final static String ID = "_id";
    private final static String SAMEAS = "sameas";

    private final HGQLSchema schema;


    public SPARQLServiceConverter(HGQLSchema schema) {
        this.schema = schema;
    }

    /**
     * Wraps the SPARQL OPTIONAL clause around the given sparqlPattern.
     * @param sparqlPattern SPARQL pattern
     * @return SPARQL OPTIONAL clause
     */
    private String optionalClause(String sparqlPattern) {
        return " OPTIONAL { " + sparqlPattern + " } ";
    }

    /**
     * Generates a SPARQL Query where the given id is selected from the values defined by the sparqlPattern.
     * The results may be restricted with limit and offset with the given limitOffset variable.
     * @param id Variable that is selected in the query
     * @param sparqlPattern SPARQL Pattern defining the data. WHERE section of an SPARQL Query
     * @param limitOffset Usually the limit or offset of the Query but anything that is valid after the WHERE clause is possible. (Like ORDER BY)
     * @return
     */
    private String selectSubqueryClause(String id, String sparqlPattern, String limitOffset) {
        return "{ SELECT " + toVar(id) + " WHERE { " + sparqlPattern + " } " + limitOffset + " } ";
    }

    /**
     * Generates a SPARQL query in which the given graphID is queried with the given query (where). The query is uses a
     * wildcard in the selection this means all variables are queried.
     * @param where Graph to access
     * @param graphID Query for the graph
     * @return
     */
    private String selectQueryClause(String where, String graphID) {
        return  "SELECT * WHERE { " + graphClause(graphID, where) + " } ";
    }

    /**
     * Generates a SPARQL GRAPH clause in which the given graphID is queried by the given query (where).
     * @param graphID Graph to access
     * @param where Query for the graph
     * @return SPARQL GRAPH clause
     */
    private String graphClause(String graphID, String where) {
        if (StringUtils.isEmpty(graphID)) {
            return where;
        } else {
            return "GRAPH <" + graphID + "> { " + where + " } ";
        }
    }

    /**
     * Generates a SPARQL VALUES clause for the given id. The VALUES clause is commonly used to filter out results (id) that do NOT
     * match the given input. The given id is typically a SPARQL variable. Only supports single column input.
     * @param id URI or SPARQL variable the filter is applied
     * @param input List of values id should match
     * @return Corresponding VALUES clause
     */
    private String valuesClause(String id, Set<String> input) {
        String var = toVar(id);
        Set<String> uris = new HashSet<>();
        input.forEach(uri -> uris.add(uriToResource(uri)));

        String urisConcat = String.join(" ", uris);

        return  "VALUES " + var + " { " + urisConcat + " } ";
    }

    private String filterClause(final String id, final Set<String> input) {

        String var = toVar(id);
        Set<String> uris = new HashSet<>();
        input.forEach(uri -> uris.add(uriToResource(uri)));

        String urisConcat = String.join(" , ", uris);

        return "FILTER ( " + var + " IN ( " + urisConcat + " ) )";
    }

    /**
     * Filter clause that cheks if the id (sparql variable) is a literal
     * @param id sparql variable
     * @return Filter clause ensuring that the given id is a literal
     */
    private String isLiteralClause(String id){
        return String.format("FILTER(isLiteral(%s))", toVar(id));
    }

    /**
     * Generates the LIMIT and OFFSET clauses if they are defined in the given field/type (jsonQuery).
     * @param jsonQuery
     * @return
     */
    private String limitOffsetClause(JsonNode jsonQuery) {
        JsonNode args = jsonQuery.get(ARGS);
        String limit = "";
        String offset = "";
        if (args != null) {
            if (args.has(LIMIT)) {
                limit = limitClause(args.get(LIMIT).asInt());
            }
            if (args.has(OFFSET)) {
                offset = offsetClause(args.get(OFFSET).asInt());
            }
        }
        return limit + offset;
    }

    /**
     * Wraps the SPARQL LIMIT clause around the given limit
     * @param limit
     * @return
     */
    private String limitClause(int limit) {
        return "LIMIT " + limit + " ";
    }

    /**
     * Wraps the SPARQL OFFSET clause around the given offset
     * @param offset
     * @return
     */
    private String offsetClause(int offset) {
        return "OFFSET " + offset + " ";
    }


    private String orderClause(JsonNode jsonQuery){
        JsonNode args = jsonQuery.get(ARGS);
        String order = "";
        String nodeId = jsonQuery.get(NODE_ID).asText();
        if(args.has(ORDER)){
            order = args.get(ORDER).asText();
            if(order.equals(ORDER_DESC)){
                return  String.format("ORDER  BY DESC(%s)", toVar(nodeId));
            }else if(order.equals(ORDER_ASC)){
                return  String.format("ORDER  BY ASC(%s)", toVar(nodeId));
            }
        }
        return "";
    }

    /**
     * Format the given URI to a URI in SPARQL syntax
     * @param uri
     * @return
     */
    public static String uriToResource(String uri) {
        return "<" + uri + ">";
    }

    /**
     * Converts the given id into a SPARQL variable
     * @param id
     * @return SPARQL variable
     */
    public static String toVar(String id) {
        return "?" + id;
    }

    /**
     * Format the given subject predicate and object into a SPARQL sentence.
     * @param subject
     * @param predicate
     * @param object
     * @return SPARQL sentence
     */
    public static String toTriple(String subject, String predicate, String object) {
        return subject + " " + predicate + " " + object + " .";
    }

    /**
     * Returns a SPARQL language filter for the given field if the LANG argument is defined.
     * @param field
     * @return If LANG argument is defined return SPARQL language filter else empty String.
     */
    private String langFilterClause(JsonNode field) {
        final String PATTERN = "FILTER (lang(%s) = \"%s\") . ";
        String nodeVar = toVar(field.get(NODE_ID).asText());
        JsonNode args = field.get(ARGS);
        return (args.has(LANG)) ? String.format(PATTERN, nodeVar, args.get(LANG).asText()) : "";
    }

    /**
     * Returns a SPARQL sentence with parentId and node Id as SPARQL variable and predicateURI as URI. parentId is used
     * as subject and nodeId as object. If typeURI is not an empty string the result is extended by the restriction that
     * nodeId is a rdf:type of typeURI.
     * @param parentId Id to be used as variable in the sentence subject.
     * @param nodeId Id to be used as variable in the sentence object.
     * @param predicateURI predicate of the sentence. If the predicate has sameAs properties then the predicateURI is a property path
     * @param typeURI Required type of the given nodeId,
     * @return
     */
    private String fieldPattern(String parentId, String nodeId, String predicateURI, String typeURI) {
        String predicateTriple = (parentId.equals("")) ? "" : toTriple(toVar(parentId), predicateURI, toVar(nodeId));  // parentId == "" : "" | "?parentId <predicateURI> ?nodeId."
        String typeTriple = (typeURI.equals("")) ? "" : toTriple(toVar(nodeId), RDF_TYPE_URI, uriToResource(typeURI));   // typeURI == "" : "" | "?nodeId rdf:type <typeURI>."
        return predicateTriple + typeTriple;
    }

    /**
     * Generates A SPARQL query from a given GraphQl SelectionSet.
     * @param jsonQuery
     * @param input
     * @param rootType
     * @param serviceId id of the service that called this method. Used to select the right service from a ManifoldService
     * @return
     */
    public String getSelectQuery(JsonNode jsonQuery, Set<String> input, String rootType, String serviceId) {

        Map<String, QueryFieldConfig> queryFields = schema.getQueryFields();

        Boolean root = (!jsonQuery.isArray() && queryFields.containsKey(jsonQuery.get(NAME).asText()));

        if (root) {
            JsonNode args = jsonQuery.get(ARGS);
            if (args != null) {
                if (args.has(ID)) {
                    return getSelectRoot_GET_BY_ID(jsonQuery, serviceId);
                }
            }
            return getSelectRoot_GET(jsonQuery, serviceId);

//            if (queryFields.get(jsonQuery.get(NAME).asText()).type().equals(HGQLVocabulary.HGQL_QUERY_GET_FIELD)) { // ToDo: Do NOT check the name, check the arguments for _id
//                return getSelectRoot_GET(jsonQuery);
//            } else {
//                return getSelectRoot_GET_BY_ID(jsonQuery);
//            }
        } else {
            return getSelectNonRoot((ArrayNode) jsonQuery, input, rootType, serviceId);
        }
    }

    /**
     * Generates A SPARQL query from a given GraphQl SelectionSet where the root is restricted by a list of given ids.
     * @param queryField
     * @param serviceId id of the service that called this method. Used to select the right service from a ManifoldService
     * @return
     */
    private String getSelectRoot_GET_BY_ID(JsonNode queryField, String serviceId) {

        Iterator<JsonNode> urisIter = queryField.get(ARGS).get(ID).elements();

        Set<String> uris = new HashSet<>();

        urisIter.forEachRemaining(uri -> uris.add(uri.asText()));

        String targetName = queryField.get(TARGET_NAME).asText();
        String targetURI = schema.getTypes().get(targetName).getId();

        String graphID = getGraphId(queryField, serviceId);
        String nodeId = queryField.get(NODE_ID).asText();
        String limitOffsetSTR = limitOffsetClause(queryField);
        String orderSTR = orderClause(queryField);
        String selectTriple ="";
        if(hasSameAsTypes(targetName)){
            Set<String> values = getSameAsTypes(targetName);
            values.add(targetName);
            values = values.stream()
                    .map(s -> schema.getTypes().get(s).getId())
                    .collect(Collectors.toSet());
            String value = valuesClause(SAMEAS, values);
            selectTriple = value + toTriple(toVar(nodeId), RDF_TYPE_URI, toVar(SAMEAS));
        }else{
            selectTriple = toTriple(toVar(nodeId), RDF_TYPE_URI, uriToResource(targetURI));
        }
        String valueSTR = valuesClause(nodeId, uris);
        String filterSTR = filterClause(nodeId, uris);   // NOT used ?? -> same as valuesClause?



        JsonNode subfields = queryField.get(FIELDS);
        String subQuery = getSubQueries(subfields);

        return selectQueryClause(valueSTR + selectTriple + subQuery, graphID) + orderSTR + limitOffsetSTR;
    }

    /**
     * Generates A SPARQL query from a given GraphQl SelectionSet with no restrictions to the root selection.
     * @param queryField GraphQl SelectionSet
     * @param serviceId id of the service that called this method. Used to select the right service from a ManifoldService
     * @return
     */
    private String getSelectRoot_GET(JsonNode queryField, String serviceId) {

        String targetName = queryField.get(TARGET_NAME).asText();
        String targetURI = schema.getTypes().get(targetName).getId();
        String graphID = getGraphId(queryField, serviceId);  // The Graph is defined over the HGQL Schema directive service
        String nodeId = queryField.get(NODE_ID).asText();   // SPARQL variable
        String limitOffsetSTR = limitOffsetClause(queryField);
        String orderSTR = orderClause(queryField);
        String selectTriple ="";
        if(hasSameAsTypes(targetName)){
            Set<String> values = getSameAsTypes(targetName);
            values.add(targetName);
            values = values.stream()
                    .map(s -> schema.getTypes().get(s).getId())
                    .collect(Collectors.toSet());
            String value = valuesClause(SAMEAS, values);
            selectTriple = value + toTriple(toVar(nodeId), RDF_TYPE_URI, toVar(SAMEAS));
        }else{
            selectTriple = toTriple(toVar(nodeId), RDF_TYPE_URI, uriToResource(targetURI));
        }
        String rootSubquery = selectSubqueryClause(nodeId, selectTriple, orderSTR + limitOffsetSTR);

        JsonNode subfields = queryField.get(FIELDS);
        String whereClause = getSubQueries(subfields);

        return selectQueryClause(rootSubquery + whereClause, graphID);   //ToDo: The generated Query is here only evalluated in one graph. If multiple endpoints have to be queried this has to be changed.
    }

    /**
     * Generates a SPARQL query that queries each given field in jsonQuery and restricts the result to the list given in input.
     * This means only results with one of the input values as subject are left in.
     * @param jsonQuery Multiple field elements
     * @param input Set of values which the result of the query should match in the subject of a triple
     * @param rootType type from which the graph is used
     * @param serviceId id of the service that called this method. Used to select the right service from a ManifoldService
     * @return
     */
    private String getSelectNonRoot(ArrayNode jsonQuery, Set<String> input, String rootType, String serviceId) {


        JsonNode firstField = jsonQuery.elements().next();
        Service service =  schema.getTypes().get(rootType).getFields().get(firstField.get(NAME).asText()).getService();
        if(service instanceof ManifoldService){
            service = ((ManifoldService) service).getService(serviceId);
        }
        String graphID = ((SPARQLEndpointService) service).getGraph();
        String parentId = firstField.get(PARENT_ID).asText();
        String valueSTR = valuesClause(parentId, input);   // restrict the ?parentId to the values defined in the input list

        StringBuilder whereClause = new StringBuilder();
        jsonQuery.elements().forEachRemaining(field -> whereClause.append(getFieldSubquery(field)));
        return selectQueryClause(valueSTR + (whereClause.toString()), graphID);
    }


    /**
     * Generates a SPARQL query for the given field and also for the subfields of the field.
     * @param fieldJson
     * @return
     */
    private String getFieldSubquery(JsonNode fieldJson) {

        String fieldName = fieldJson.get(NAME).asText();

        if (HGQLVocabulary.JSONLD.containsKey(fieldName)) {   // Check if the given field is none of the internal fields like _id or _type
            return "";
        }

        String fieldURI = schema.getFields().get(fieldName).getId();
        // if field has sameAs fields replace field uri with property path querying all sameAs fields
        if(hasSameAsFields(fieldName)){
            Set<String> sameAs_fields = getSameAsFields(fieldName).stream()
                    .map(s -> schema.getFields().get(s).getId())
                    .collect(Collectors.toSet());
            sameAs_fields.add(fieldURI);
            fieldURI = alternativePath(sameAs_fields);
        }else{
            fieldURI = uriToResource(fieldURI);
        }
        String targetName = fieldJson.get(TARGET_NAME).asText();
        String parentId = fieldJson.get(PARENT_ID).asText();
        String nodeId = fieldJson.get(NODE_ID).asText();

        String limitOffsetSTR = limitOffsetClause(fieldJson);
        String langFilter = langFilterClause(fieldJson);   // Add language filter if defined
        String orderSTR = orderClause(fieldJson);
        String valueSTR = "";
        if(fieldJson.get(ARGS).has(ID)){
            Iterator<JsonNode> urisIter = fieldJson.get(ARGS).get(ID).elements();
            Set<String> uris = new HashSet<>();
            urisIter.forEachRemaining(uri -> uris.add(uri.asText()));
            valueSTR = valuesClause(nodeId, uris);
        }

        String fieldPattern = "";
        String rest = "";

        if(targetName.equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            // field queries the String placeholder object -> query directly the string/Literal and ignore the subfields and type checking for the object
            fieldPattern = toTriple(toVar(parentId), fieldURI, toVar(nodeId));
            rest = isLiteralClause(nodeId);
            // overwrite the field arguments with the literal value arguments
            JsonNode literal_value = fieldJson.get(FIELDS);
            for(int i=0; i<literal_value.size(); i++){
                JsonNode field = literal_value.get(i);
                if(field.get(NAME).asText().equals(HGQL_SCALAR_LITERAL_VALUE_GQL_NAME)){
                    literal_value = field;
                    break;
                }
            }
            langFilter =  langFilterClause(literal_value);
            limitOffsetSTR = limitOffsetClause(literal_value);
            orderSTR = orderClause(literal_value);
        }else{
            String typeURI = (schema.getTypes().containsKey(targetName)) ? schema.getTypes().get(targetName).getId() : "";  // If the output type (targetName) is a type of the schema then typeURI is the Id of this type
            fieldPattern = fieldPattern(parentId, nodeId, fieldURI, typeURI);  // SPARQL query for only the field
            JsonNode subfields = fieldJson.get(FIELDS);
            rest = getSubQueries(subfields);   // SPARQL query for the SelectionSet of the field (subfields)
        }

        String selectField = "";
        if(!limitOffsetSTR.equals("") || !orderSTR.equals("") || !valueSTR.equals("")){   // Select wrapping is only needed if limit, offset, order or _id restrictions are defined
            selectField = "{ "+ selectQueryClause(valueSTR + fieldPattern + langFilter + rest, "") + orderSTR + limitOffsetSTR + " }";
        }else{
            selectField = fieldPattern + langFilter + rest;
        }

        return optionalClause(selectField); // Whole query for the field
    }

    /**
     * Generates for each field of the given subfields a corresponding SPARQL query.
     * @param subfields
     * @return
     */
    private String getSubQueries(JsonNode subfields) {

        if (subfields.isNull()) {
            return "";
        }
        StringBuilder whereClause = new StringBuilder();
        subfields.elements().forEachRemaining(field -> whereClause.append(getFieldSubquery(field)));
        return whereClause.toString();
    }

    /**
     * Returns the graph of the service that is responsible for the given queryField. If the responsible service is a
     * ManifoldService then select the service by the given serviceId.
     * @param queryField queryField must be the root field
     * @param serviceId
     * @return
     */
    private String getGraphId(JsonNode queryField, String serviceId){
        Service service =  schema.getQueryFields().get(queryField.get(NAME).asText()).service();
        if(service instanceof ManifoldService){
            service = ((ManifoldService) service).getService(serviceId);
        }
        return ((SPARQLEndpointService) service).getGraph();  // The Graph is defined over the HGQL Schema directive service
    }

    private boolean hasSameAsTypes(String targetName){
        if(schema.getTypes().containsKey(targetName)){
            return !schema.getTypes().get(targetName).getSameAs().isEmpty();

        }else{
            // Given targetName is NOT a type of the schema
            return false;
        }
    }

    private Set<String> getSameAsTypes(String targetName){
        if(schema.getTypes().containsKey(targetName)){
            return schema.getTypes().get(targetName).getSameAs();

        }else{
            // Given targetName is NOT a type of the schema
            return null;
        }
    }

    private boolean hasSameAsFields(String targetName){
        if(schema.getFields().containsKey(targetName)){
            return !schema.getFields().get(targetName).getSameAs().isEmpty();

        }else{
            // Given targetName is NOT a type of the schema
            return false;
        }
    }

    private Set<String> getSameAsFields(String targetName){
        if(schema.getFields().containsKey(targetName)){
            return schema.getFields().get(targetName).getSameAs();

        }else{
            // Given targetName is NOT a type of the schema
            return null;
        }
    }

    /**
     * Builds a property path with the given nodes as alternative paths.
     * @param nodes
     * @return
     */
    private String alternativePath(Set<String> nodes){
        return nodes.stream()
                .map(t->String.format("<%s>",t.toString()))
                .collect(Collectors.joining("|"));
    }


}
