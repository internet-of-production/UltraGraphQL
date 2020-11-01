package org.hypergraphql.datafetching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.language.*;
import org.hypergraphql.config.schema.FieldOfTypeConfig;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.datafetching.services.ManifoldService;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.datafetching.services.resultmodel.ObjectResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.hypergraphql.query.pattern.Query;
import org.hypergraphql.query.pattern.QueryPattern;
import org.hypergraphql.query.pattern.QueryPatternBuilder;
import org.hypergraphql.query.pattern.SubQueriesPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_ID;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_GQL_NAME;

/**
 * This class handles and coordinates the query execution of a GraphQL query.
 * The GraphQL query is provided to the object at the initiation of the object along with the schema the query is based on.
 * The query is than converted to an internal structure where a SPARQL variable is assigned to each field name.
 * Furthermore, the query is split up into service related parts. By considering the query as tree-structure a coherent
 * set of fields that is controlled by one service, starting by the root of the given query, is converted to SPARQL and
 * by this object (this object only manages and initiates this process). For the other query fields new ExecutionTreeNodes
 * are created controlling and managing the execution of these query parts. These additional ExecutionTreeNodes are stored
 * in the childrenNodes attribute and during the final result transformation phase the results of the children are merged
 * into the final result accordingly.
 * Further technical details:
 * In the case multiple services are assigned to schema entities with the ManifoldService this rules of creating the
 * childrenNodes is changed to creating a new ExecutionTreeNode for each query level. This is due to the case that the
 * queried IRIs on one service can lead to results of sub-results on another service even though the IRI is not queried
 * by the parent query field on this service. Therefore, for ManifoldServices on each level a new ExecutionTreeNode is
 * created even though they share the same set of services to ensure a separate execution of the sub-queries.
 *
 */
public class ExecutionTreeNode {

    private Service service; // getService configuration
    private Query query; // GraphQL in a basic Json format
    private String executionId; // unique identifier of this execution node
    private Map<String, ExecutionForest> childrenNodes; // succeeding executions - Forest contains the ExecutionTreeNodes of fields that have a different service
    private String rootType;
    private Map<String, String> ldContext;
    private HGQLSchema hgqlSchema;

    // constant names of the fields int the JSON query representation

    public static final String ROOT_TYPE = "Query";
    public static final String NODE_ID = "nodeId";
    public static final String ARGS = "args";
    public static final String NAME = "name";
    public static final String ALIAS = "alias";
    public static final String TARGET_NAME = "targetName";
    public static final String FIELDS = "fields";
    public static final String PARENT_NODE_ID = "parentId";
    public static final String PARENT_ARGS = "parentArgs";
    public static final String PARENT_NAME = "parentName";
    public static final String PARENT_ALIAS = "parentAlias";
    public static final String PARENT_TYPE = "parentType";

    private final static Logger LOGGER = LoggerFactory.getLogger(ExecutionTreeNode.class);

    public void setService(Service service) {
        this.service = service;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public Map<String, ExecutionForest> getChildrenNodes() {
        return childrenNodes;
    }

    public String getRootType() {
        return rootType;
    }

    public Map<String, String> getLdContext() { return this.ldContext; }

    public Service getService() {
        return service;
    }

    public Query getQuery() { return query; }

    public String getExecutionId() {
        return executionId;
    }

    Map<String, String> getFullLdContext() {

        Map<String, String> result = new HashMap<>(ldContext);

        Collection<ExecutionForest> children = getChildrenNodes().values();

        if (!children.isEmpty()) {
            for (ExecutionForest child : children) {
                    result.putAll(child.getFullLdContext());
            }
        }

        return result;

    }

    /**
     * Initiates the conversion of the given query into a query structure that is executed if the generateTreeModel method is called.
     * The given query (field) is converted according to the assigned services in the given HGQLSchema.
     * @param field Root query field of a query or sub-query
     * @param nodeId ID that is used to identify the given field in the whole query. Used as SPARQL variable for the field.
     * @param schema HGQLSchema the query (field) is based on
     */
    ExecutionTreeNode(Field field, String nodeId , HGQLSchema schema) {
        this(field, nodeId, schema, null);
    }

    private ExecutionTreeNode(Service service, Set<Field> fields, String parentId, String parentType, HGQLSchema schema){
        this(service, fields, parentId, parentType, schema, null);
    }

    /**
     * Initiates the conversion of the given query into a query structure that is executed if the generateTreeModel method is called.
     * The given query (field) is converted according to the assigned services in the given HGQLSchema.
     * @param field Root query field of a query or sub-query
     * @param nodeId ID that is used to identify the given field in the whole query. Used as SPARQL variable for the field.
     * @param schema HGQLSchema the query (field) is based on
     * @param parentField ID (SPARQL variable) that identifies the parent field of the given field
     */
    ExecutionTreeNode(Field field, String nodeId , HGQLSchema schema, Field parentField ) {

        if(schema.getQueryFields().containsKey(field.getName())) {
            this.service = schema.getQueryFields().get(field.getName()).service();   // service that is responsible for given field
        } else if(schema.getFields().containsKey(field.getName())) {
            LOGGER.info("here");   //ToDo:
        } else {
            throw new HGQLConfigurationException("Field '" + field.getName() + "' not found in schema");
        }
        this.executionId = createId();
        this.childrenNodes = new HashMap<>();
        this.ldContext = new HashMap<>();
        this.ldContext.putAll(HGQLVocabulary.JSONLD);
        this.rootType = ROOT_TYPE;
        this.hgqlSchema = schema;
        this.query = getFieldJson(field, ROOT_TYPE, nodeId, ROOT_TYPE, parentField);

    }

    /**
     * Initiates the conversion of the given query into a query structure that is executed if the generateTreeModel method is called.
     * The given query (field) is converted according to the assigned services in the given HGQLSchema.
     *
     * Note: Carefully use this constructor as it uses the given service as service for the given fields.
     *       Intention of this constructor is the use with subfields that also have the same ManifoldService as there parent field.
     * @param service Service that is responsible for the given fields.
     * @param fields Set of sub-queries (sub-fields)
     * @param parentId ID (SPARQL variable) that identifies the parent field of the given field
     * @param parentType Type of the parent field
     * @param schema HGQLSchema the query is based on
     * @param parentField Parent field of the given fields
     */
    private ExecutionTreeNode(Service service, Set<Field> fields, String parentId, String parentType, HGQLSchema schema, Field parentField) {

        this.service = service;
        this.executionId = createId();
        this.childrenNodes = new HashMap<>();
        this.ldContext = new HashMap<>();
        this.rootType = parentType;
        this.hgqlSchema = schema;
        this.query = getFieldsJson(fields, parentId, parentType, parentField);
        this.ldContext.putAll(HGQLVocabulary.JSONLD);
    }


    public String toString(int i) {

        StringBuilder space = new StringBuilder();
        for (int n = 0; n < i ; n++) {
            space.append("\t");
        }

        StringBuilder result = new StringBuilder("\n")
            .append(space).append("ExecutionNode ID: ").append(this.executionId).append("\n")
            .append(space).append("Service ID: ").append(this.service.getId()).append("\n")
            .append(space).append("Query: ").append(this.query.toString()).append("\n")
            .append(space).append("Root type: ").append(this.rootType).append("\n")
            .append(space).append("LD context: ").append(this.ldContext.toString()).append("\n");
        Set<Map.Entry<String, ExecutionForest>> children = this.childrenNodes.entrySet();
        if (!children.isEmpty()) {
            result.append(space).append("Children nodes: \n");
            for (Map.Entry<String, ExecutionForest> child : children) {
                result.append(space).append("\tParent marker: ")
                        .append(child.getKey()).append("\n")
                        .append(space).append("\tChildren execution nodes: \n")
                        .append(child.getValue().toString(i+1)).append("\n");
            }
        }

        return result.append("\n").toString();
    }


    /**
     * Generates a JSON object containing the JSON representation of all given fields
     * @param fields set of fields of one object
     * @param parentId objectId of the object tht has the given fields
     * @param parentType object name
     * @return JSON object containing the given fields
     */
    private SubQueriesPattern getFieldsJson(Set<Field> fields, String parentId, String parentType, Field parentField) {

//        ObjectMapper mapper = new ObjectMapper();
//        ArrayNode query = mapper.createArrayNode();
        SubQueriesPattern query = new SubQueriesPattern();

        int i = 0;

        for (Field field : fields) {

            i++;
            String nodeId = parentId + "_" + i;
            FieldOfTypeConfig fieldConfig = this.hgqlSchema.getTypes().get(parentType).getField(field.getName());
            TypeConfig target = null;
            if(fieldConfig != null){
                target = this.hgqlSchema.getTypes().get(fieldConfig.getTargetName());
            }
            if(target != null){
                if(target.isInterface()){
                    query.addAll(getVirtualFieldsJson(field, parentId, nodeId, parentType, target, parentField));
                } else if (target.isUnion()) {
                    query.addAll(getVirtualFieldsJson(field, parentId, nodeId, parentType, target, parentField));
                }else{
                    query.add(getFieldJson(field, parentId, nodeId, parentType, parentField));
                }
            }else{
                query.add(getFieldJson(field, parentId, nodeId, parentType, parentField));
            }

        }
        return query;
    }


    /**
     * Handling of fields that have union or interface as outputType.
     * Generates a virtual field by adding the field for all possible types for the interface/union with different corresponding output types.
     * The SPARQL variable name generation is hereby altered to indicate the virtual field. Instead of underscore
     * plus incrementing number for the number fo field a "_y" is appended to indicate the virtual field.
     * The result transformation then knows how to translate the results accordingly.
     * @param field set of fields of one object
     * @param parentId objectId of the object tht has the given fields
     * @param nodeId
     * @param parentType object name
     * @return
     */
    private SubQueriesPattern getVirtualFieldsJson(Field field, String parentId, String nodeId, String parentType, TypeConfig target, Field parentField) {
        nodeId = nodeId + "_y";
//        ObjectMapper mapper = new ObjectMapper();
//        ArrayNode query = mapper.createArrayNode();
        SubQueriesPattern query = new SubQueriesPattern();
        final SelectionSet selectionSet = field.getSelectionSet();
        AtomicBoolean hasInlineFragment = new AtomicBoolean(false);
        if (selectionSet != null) {
            List<Selection> selectionList = selectionSet.getSelections();
            if(!selectionList.isEmpty()){
                List<Field> fields = selectionList.stream()
                        .filter(selection -> selection instanceof Field)
                        .map(selection -> (Field)selection)
                        .collect(Collectors.toList());
                List<InlineFragment> inlineFragments = selectionList.stream()
                        .filter(selection -> selection instanceof InlineFragment)
                        .map(selection -> (InlineFragment)selection)
                        .collect(Collectors.toList());
                boolean has__typename = fields.stream().anyMatch(field1 -> field1.getName().equals("__typename"));

                if(target.isUnion()){
                    // ignore all fields but __typename
                    // if no inlineFragment but __typename query all union members with _id
                    if(inlineFragments.isEmpty() && has__typename){
                        for(String member : target.getUnionMembers().keySet()){
                            InlineFragment inlineFragment = InlineFragment.newInlineFragment()
                                    .typeCondition(TypeName.newTypeName()
                                            .name(member)
                                            .build())
                                    .selectionSet(SelectionSet.newSelectionSet()
                                            .selection(Field.newField("_type")
                                                    .build())
                                            .build())
                                    .build();
                            inlineFragments.add(inlineFragment);
                        }
                    }
                }else if(target.isInterface()){
                    // add fields outside the inlineFragments into the SelectionSet of each InlineFragment
                    if(!fields.isEmpty()){
                        for(String object : target.getInterafaceObjects()){
                            InlineFragment inlineFragment;
                            final Optional<InlineFragment> inlineFragmentOptional = inlineFragments.stream()
                                    .filter(inlineFragment1 -> inlineFragment1.getTypeCondition().getName().equals(object))
                                    .findFirst();
                            if(inlineFragmentOptional.isPresent()){
                                InlineFragment oldFragment = inlineFragmentOptional.get();
                                List<Selection> newFields = new ArrayList<>(fields);
                                newFields.addAll(oldFragment.getSelectionSet().getSelections());
                                inlineFragment = InlineFragment.newInlineFragment()
                                        .typeCondition(oldFragment.getTypeCondition())
                                        .additionalData(oldFragment.getAdditionalData())
                                        .comments(oldFragment.getComments())
                                        .directives(oldFragment.getDirectives())
                                        .ignoredChars(oldFragment.getIgnoredChars())
                                        .sourceLocation(oldFragment.getSourceLocation())
                                        .selectionSet(SelectionSet.newSelectionSet()
                                                .selections(newFields)
                                                .build())
                                        .build();
                                inlineFragments.remove(oldFragment);
                            }else {
                                inlineFragment = InlineFragment.newInlineFragment()
                                        .typeCondition(TypeName.newTypeName()
                                                .name(object)
                                                .build())
                                        .selectionSet(SelectionSet.newSelectionSet()
                                                .selections(fields)
                                                .build())
                                        .build();
                            }
                            inlineFragments.add(inlineFragment);
                        }
                    }
                }
                int j = 0;
                for(InlineFragment inlineFragment : inlineFragments){
                    j++;
                    Field typeField = Field.newField()
                            .name(field.getName())
                            .alias(field.getAlias())
                            .arguments(field.getArguments())
                            .comments(field.getComments())
                            .directives(field.getDirectives())
                            .ignoredChars(field.getIgnoredChars())
                            .selectionSet(inlineFragment.getSelectionSet())
                            .build();
                    query.add(getFieldJson(typeField, parentId, nodeId + "_" + j, parentType, inlineFragment.getTypeCondition().getName(), parentField));
                }
            }
        }
        return query;
    }


    /**
     * Generates a QueryPattern object representing the given field and its subfield if defined. For subfields with a different
     * service a new ExecutionTreeNode is created  and added to the ExecutionForest of this object
     * @param field Field of the query
     * @param parentId objectId of this field
     * @param nodeId The query variable that MUST be used to query the given field
     * @param parentType object name of the field
     * @return JSON object o the given field
     */
    private QueryPattern getFieldJson(Field field, String parentId, String nodeId, String parentType, Field parentField) {
        FieldOfTypeConfig fieldConfig = hgqlSchema.getTypes().get(parentType).getField(field.getName());
        String targetName = fieldConfig == null ? HGQL_ID : fieldConfig.getTargetName();

        return getFieldJson(field, parentId, nodeId, parentType, targetName, parentField);
    }

    /**
     * Generates a QueryPattern object representing the given field and its subfield if defined. For subfields with a different
     * service a new ExecutionTreeNode is created  and added to the ExecutionForest of this object
     * @param field Field of the query
     * @param parentId objectId of this field
     * @param nodeId The query variable that MUST be used to query the given field
     * @param parentType object name of the field
     * @return JSON object o the given field
     */
    private QueryPattern getFieldJson(Field field, String parentId, String nodeId, String parentType, String  targetName, Field parentField) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode query = mapper.createObjectNode();

        List<Argument> args = field.getArguments();
        QueryPatternBuilder queryPatternBuilder = new QueryPatternBuilder()
                .setName(field.getName())
                .setAlias(field.getAlias())
                .setNodeId(nodeId)
                .setParentId(parentId)
                .setArgs(field.getArguments().isEmpty() ? null : getArgsJson(field.getArguments()))
                .setTargetType(targetName)
                .setFields(this.traverse(field, nodeId, parentId, targetName));

//        query.put(NAME, field.getName());
//        query.put(ALIAS, field.getAlias());
//        query.put(PARENT_NODE_ID, parentId);
//        query.put(NODE_ID, nodeId);


        String contextLdKey = (field.getAlias()==null) ? field.getName() : field.getAlias();
        String contextLdValue = getContextLdValue(contextLdKey);

        this.ldContext.put(contextLdKey, contextLdValue);

//        if (args.isEmpty()) {
//            query.set(ARGS, null);
//        } else {
//            query.set(ARGS, getArgsJson(args));
//        }

//        query.put(TARGET_NAME, targetName);
//        query.set(FIELDS, this.traverse(field, nodeId, parentType, targetName));

        if(parentField != null){
            // Needed during Result Transformation
//            query.put(PARENT_NAME, parentField.getName());
//            query.put(PARENT_ALIAS, parentField.getAlias());
//            query.put(PARENT_ARGS, parentField.getArguments().isEmpty() ? null : getArgsJson(parentField.getArguments()));
//            query.put(PARENT_TYPE, parentType);
            queryPatternBuilder.setParentName(parentField.getName())
                    .setParentAlias(parentField.getAlias())
                    .setParentArgs(parentField.getArguments().isEmpty() ? null : getArgsJson(parentField.getArguments()))
                    .setParentType(parentType);
        }
        return queryPatternBuilder.createQueryPattern();
    }

    /**
     * Returns the IRI of the field that corresponds to the given key
     * @param contextLdKey Key of a field
     * @return context of the given field (in most cases he IRI of the field)
     */
    private String getContextLdValue(String contextLdKey) {

        if (hgqlSchema.getFields().containsKey(contextLdKey)) {
            return hgqlSchema.getFields().get(contextLdKey).getId();
        } else if (hgqlSchema.getTypes().containsKey(contextLdKey)) {
            return hgqlSchema.getTypes().get(contextLdKey).getId();
        } else {
            return HGQLVocabulary.HGQL_QUERY_NAMESPACE + contextLdKey;
        }
    }

    /**
     * Generates a SubQueriesPattern object for the subfields with the same service. For subfields with a different service
     * create a new ExecutionTreeNode and add the new object to the ExecutionForest of this object
     * @param field Field containing a query selection set
     * @param parentId id of the objectType of the given field
     * @param parentType name of the objectType of the given field
     * @return JSON object of the subfields that have the same service or null if no subfield uses this service
     */
    private SubQueriesPattern traverse(Field field, String parentId, String parentType) {
        FieldOfTypeConfig fieldConfig = hgqlSchema.getTypes().get(parentType).getField(field.getName());
        String targetName = fieldConfig.getTargetName();
        return traverse(field, parentId, parentType, targetName);
    }

    /**
     * Generates a SubQueriesPattern object for the subfields with the same service. For subfields with a different service
     * create a new ExecutionTreeNode and add the new object to the ExecutionForest of this object
     * @param field Field containing a query selection set
     * @param parentId id of the objectType of the given field
     * @param targetName targetName of the field (outputtype)
     * @return JSON object of the subfields that have the same service or null if no subfield uses this service
     */
    private SubQueriesPattern traverse(Field field, String nodeId, String parentId, String targetName) {
        SelectionSet subFields = field.getSelectionSet();
        if(targetName.equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            // The String placeholder object and its field have always the same service there parent field
            Set<Field> subfields = subFields.getSelections().stream()
                    .map(selection -> (Field)selection)
                    .collect(Collectors.toSet());
            return getFieldsJson(subfields, nodeId, targetName, field);
        }
        if (subFields != null) {
            Map<Service, Set<Field>> splitFields = getPartitionedFields(targetName, subFields, nodeId);   // service_1 [field_1, field_3] means service_1 is responsible for field_1 and field_3

            for (Map.Entry<Service, Set<Field>> entry : splitFields.entrySet()) {
                boolean differentLevel = entry.getKey() instanceof ManifoldService && this.service instanceof ManifoldService && !((ManifoldService) entry.getKey()).getLevel().equals(((ManifoldService) this.service).getLevel());
                if (!entry.getKey().getId().equals(this.service.getId()) || differentLevel) {  //ToDo:? // If the service is NOT the same service as the service of this object create a new ExecutionTreeNode
                    ExecutionTreeNode childNode = new ExecutionTreeNode(
                            entry.getKey(),   //service
                            entry.getValue(),   // fields
                            nodeId,   // object of the given field
                            targetName,   //outputtype of given field
                            hgqlSchema,
                            field
                    );


                    // Add newly created ExecutionTreeNode to the ExecutionForest of the object
                    if (this.childrenNodes.containsKey(nodeId)) {
                        try {
                            this.childrenNodes.get(nodeId).getForest().add(childNode);
                        } catch (Exception e) {
                            LOGGER.error("Problem adding parent", e);
                        }
                    } else {
                        ExecutionForest forest = new ExecutionForest();
                        forest.getForest().add(childNode);
                        try {
                            this.childrenNodes.put(nodeId, forest);
                        } catch (Exception e) {
                            LOGGER.error("Problem adding child", e);
                        }
                    }
                }
            }
            Optional<Map.Entry<Service, Set<Field>>> hasSameServiceSubfields = splitFields.entrySet().stream()
                    .filter(entry -> entry.getKey().getId().equals(this.service.getId()))
                    .filter(entry ->{
                                if(entry.getKey() instanceof ManifoldService && this.service instanceof ManifoldService){
                                    //entry and service are a ManifoldService check if both are on the same level (operate on the same knowledge level)
                                    if(((ManifoldService) entry.getKey()).getLevel().equals(((ManifoldService) this.service).getLevel())){
                                        // same level
                                        return true;
                                    }else{
                                        // different level
                                        return false;
                                    }
                                }else{
                                    // one of the services is not a ManifoldService
                                    return true;
                                }
                    })
                    .findAny();
            if (hasSameServiceSubfields.isPresent()){//ToDo: ?  // at least one subfield has the same service as this field
                return getFieldsJson(hasSameServiceSubfields.get().getValue(), nodeId, targetName, field);   // return Json representation of the subfields of the selectionSet
            }
        }
        return null;
    }

    /**
     * Map the List of given GraphQL arguments to an JSON object
     * Example: (id:5, names:["Test"]) -> {"id": 5, "names": ["Test"]}
     * @param args GraphQL query arguments
     * @return JSON representation of the given arguments
     */
    private Map<String, Object> getArgsJson(List<Argument> args) {

//        ObjectMapper mapper = new ObjectMapper();
//        ObjectNode argNode = mapper.createObjectNode();
        Map<String, Object> argNode = new HashMap<>();

        for (Argument arg : args) {

            Value val = arg.getValue();
            String type = val.getClass().getSimpleName();

            switch (type) {
                case "IntValue": {
                    long value = ((IntValue) val).getValue().longValueExact();
                    argNode.put(arg.getName(), value);
                    break;
                }
                case "StringValue": {
                    String value = ((StringValue) val).getValue();
                    argNode.put(arg.getName(), value);
                    break;
                }
                case "BooleanValue": {
                    boolean value = ((BooleanValue) val).isValue();
                    argNode.put(arg.getName(), value);
                    break;
                }
                case "ArrayValue": {
                    List<Node> nodes = val.getChildren();
//                    ArrayNode arrayNode = mapper.createArrayNode();
                    List<String> arrayNode = new ArrayList<>();
                    for (Node node : nodes)  {
                        String value = ((StringValue) node).getValue();
                        arrayNode.add(value);
                    }
                    argNode.put(arg.getName(), arrayNode);
                    break;
                }
                case "EnumValue":{
                    EnumValue enum_value = ((EnumValue) val);
                    argNode.put(arg.getName(), enum_value.getName());
                }
            }

        }

        return argNode;
    }


    /**
     * Generates a mapping of the services, needed for the selectionSet, to a Set of fields from the selectionSet using
     * these services.
     * @param parentType name of an objectType
     * @param selectionSet selectionSet with the given parentType as parent
     * @return Mapping between services and fields which defines which service is responsible for which field
     */
    private Map<Service, Set<Field>> getPartitionedFields(String parentType, SelectionSet selectionSet, String parentId) {   //ToDo: correct handling of inline fragments in case of unions as output ---- Maybe not handeled here if previously changed to multiple fields

        Map<Service, Set<Field>> result = new HashMap<>();

        List<Selection> selections = selectionSet.getSelections();

        for (Selection child : selections) {

            if (child.getClass().getSimpleName().equals("Field")) {

                Field field = (Field) child;

                if (hgqlSchema.getFields().containsKey(field.getName())) {   //Field of the selection set is part of the object in the hgql schema

                    Service serviceConfig;

                    if(hgqlSchema.getTypes().containsKey(parentType)) {

                        if(hgqlSchema.getTypes().get(parentType).getFields().containsKey(field.getName())) {
                            serviceConfig = hgqlSchema.getTypes().get(parentType).getFields().get(field.getName()).getService();   // Service of field/child
                        } else {
                            throw new HGQLConfigurationException("Schema is missing field '"
                                    + parentType + "::" + field.getName() + "'");
                        }
                    } else {
                        throw new HGQLConfigurationException("Schema is missing type '" + parentType + "'");
                    }
                    // Add the service to the result
                    if(serviceConfig instanceof ManifoldService){
                        // crete new ManifoldService instance to ensure that the subfields of the query (for the same set
                        // of services) is executed separately to cover the potential case that data queried in the parent
                        // query are queried from the whole set of services.
                        serviceConfig = new ManifoldService((ManifoldService) serviceConfig, parentId);
                    }
                    addOrCreate(result, serviceConfig, field);

                }else{// internal field i.e. _id or _type
                    if(field.getName().equals(SPARQLServiceConverter.ID)){
                        addOrCreate(result, this.service, field);
                    }else if(field.getName().equals(SPARQLServiceConverter.TYPE)){
                        addOrCreate(result, this.service, field);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Adds the given field to the result map based on the given service. If the service is already in the result and has
     * the ame level the field is added to the set of fields that are already in the result. If they have a different
     * level the service is inserted as new object with the field in the fields list.
     * @param result Map containing the service as key and the fields queried on the service as value
     * @param service Service that is responsible for the given field.
     * @param field Field object to be added to the given result
     */
    private void addOrCreate(Map<Service, Set<Field>> result, Service service, Field field){
        final Optional<Map.Entry<Service, Set<Field>>> optionalServiceSetEntry = result.entrySet().stream()
                .filter(service1 -> service1.getKey().getId().equals(service.getId()))
                .filter(serviceSetEntry -> !(service instanceof ManifoldService) || ((ManifoldService) serviceSetEntry.getKey()).getLevel().equals(((ManifoldService) service).getLevel()))
                .findAny();
        if(optionalServiceSetEntry.isPresent()){
            optionalServiceSetEntry.get().getValue().add(field);
        }else{
            Set<Field> fieldSet = new HashSet<>();
            fieldSet.add(field);
            result.put(service, fieldSet);
        }
    }


    private String createId() {
        return "execution-"+ UUID.randomUUID();
    }

    /**
     * Generates the result for the query of this object by executing the query and requesting the results of the childNodes
     * (sub-queries with different service id). The result of the query and the results of the childNodes are than merged
     * to a complete result and returned.
     * @param input Input values (IRIs) of a parent query that limit the results of this query
     * @return Results for the query and 
     */
    Result generateTreeModel(Set<String> input) {

        TreeExecutionResult executionResult = service.executeQuery(query, input,  childrenNodes.keySet() , rootType, hgqlSchema);

        Map<String,Set<String>> resultSet = executionResult.getResultSet();
        AtomicReference<Result> formatedResult = new AtomicReference<>(executionResult.getFormatedResult());

        Set<Result> computedModels = new HashSet<>();
        Set<String> vars = resultSet.keySet();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        Set<Future<Result>> futureModels = new HashSet<>();

        vars.forEach(var ->{
            ExecutionForest executionChildren = this.childrenNodes.get(var);
            if (executionChildren.getForest().size() > 0) {
                Set<String> values = resultSet.get(var);
                executionChildren.getForest().forEach(node -> {
                    FetchingExecution childExecution = new FetchingExecution(values, node);
                    futureModels.add(executor.submit(childExecution));
                });
            }
        });

        futureModels.forEach(futureModel -> {
            try {
                computedModels.add(futureModel.get());
            } catch (InterruptedException
                    | ExecutionException e) {
                LOGGER.error("Problem adding execution result", e);
            }
        });
        for (Result result : computedModels) {
            if (formatedResult.get() == null) {
                formatedResult.set(result);
            } else {
                if(result != null){
                    if (formatedResult.get().getNodeId().equals(result.getNodeId())) {
                        formatedResult.get().merge(result);
                    } else {
                        if (formatedResult.get() instanceof ObjectResult) {
                            ((ObjectResult) formatedResult.get()).deepSubfieldMerge(result);
                        }
                    }
                }
            }
        }
        executor.shutdown();
        return formatedResult.get();
    }
}
