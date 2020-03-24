package org.hypergraphql.datafetching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Field;
import graphql.language.IntValue;
import graphql.language.Node;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.Value;
import org.apache.jena.rdf.model.Model;
import org.hypergraphql.config.schema.FieldOfTypeConfig;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExecutionTreeNode {

    private Service service; // getService configuration
    private JsonNode query; // GraphQL in a basic Json format
    private String executionId; // unique identifier of this execution node
    private Map<String, ExecutionForest> childrenNodes; // succeeding executions - Forest contains the ExecutionTreeNodes of fields that have a different service
    private String rootType;
    private Map<String, String> ldContext;
    private HGQLSchema hgqlSchema;

    private final static Logger LOGGER = LoggerFactory.getLogger(ExecutionTreeNode.class);

    public void setService(Service service) {
        this.service = service;
    }

    public void setQuery(JsonNode query) {
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

    public JsonNode getQuery() { return query; }

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

    ExecutionTreeNode(Field field, String nodeId , HGQLSchema schema ) {

        if(schema.getQueryFields().containsKey(field.getName())) {
            this.service = schema.getQueryFields().get(field.getName()).service();   // service that is responsible for given field
        } else if(schema.getFields().containsKey(field.getName())) {
            LOGGER.info("here");
        } else {
            throw new HGQLConfigurationException("Field '" + field.getName() + "' not found in schema");
        }
        this.executionId = createId();
        this.childrenNodes = new HashMap<>();
        this.ldContext = new HashMap<>();
        this.ldContext.putAll(HGQLVocabulary.JSONLD);
        this.rootType = "Query";
        this.hgqlSchema = schema;
        this.query = getFieldJson(field, null, nodeId, "Query");
    }

    private ExecutionTreeNode(Service service, Set<Field> fields, String parentId, String parentType, HGQLSchema schema) {

        this.service = service;
        this.executionId = createId();
        this.childrenNodes = new HashMap<>();
        this.ldContext = new HashMap<>();
        this.rootType = parentType;
        this.hgqlSchema = schema;
        this.query = getFieldsJson(fields, parentId, parentType);
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
    private JsonNode getFieldsJson(Set<Field> fields, String parentId, String parentType) {

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode query = mapper.createArrayNode();

        int i = 0;

        for (Field field : fields) {

            i++;
            String nodeId = parentId + "_" + i;
            query.add(getFieldJson(field, parentId, nodeId, parentType));

        }
        return query;
    }


    /**
     * Generates a JSON object representing the given field and its subfield if defined. For subfields with a different
     * service a new ExecutionTreeNode is created  and added to the ExecutionForest of this object
     * @param field Field of the query
     * @param parentId objectId of this field
     * @param nodeId The query variable that MUST be used to query the given field
     * @param parentType object name of the field
     * @return JSON object o the given field
     */
    private JsonNode getFieldJson(Field field, String parentId, String nodeId, String parentType) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode query = mapper.createObjectNode();

        query.put("name", field.getName());
        query.put("alias", field.getAlias());
        query.put("parentId", parentId);
        query.put("nodeId", nodeId);
        List<Argument> args = field.getArguments();

        String contextLdKey = (field.getAlias()==null) ? field.getName() : field.getAlias();
        String contextLdValue = getContextLdValue(contextLdKey);

        this.ldContext.put(contextLdKey, contextLdValue);

        if (args.isEmpty()) {
            query.set("args", null);
        } else {
            query.set("args", getArgsJson(args));
        }

        FieldOfTypeConfig fieldConfig = hgqlSchema.getTypes().get(parentType).getField(field.getName());
        String targetName = fieldConfig.getTargetName();

        query.put("targetName", targetName);
        query.set("fields", this.traverse(field, nodeId, parentType));

        return query;
    }

    /**
     * Returns the IRI of the field that corresponds to the given key
     * @param contextLdKey Key of a field
     * @return context of the given field (in most cases he IRI of the field)
     */
    private String getContextLdValue(String contextLdKey) {

        if (hgqlSchema.getFields().containsKey(contextLdKey)) {
            return hgqlSchema.getFields().get(contextLdKey).getId();
        } else {
            return HGQLVocabulary.HGQL_QUERY_NAMESPACE + contextLdKey;
        }
    }

    /**
     * Generates a JSON representation for the subfields with the same service. For subfields with a different service
     * create a new ExecutionTreeNode and add the new object to the ExecutionForest of this object
     * @param field Field containing a query selection set
     * @param parentId id of the objectType of the given field
     * @param parentType name of the objectType of the given field
     * @return JSON object of the subfields that have the same service or null if no subfield uses this service
     */
    private JsonNode traverse(Field field, String parentId, String parentType) {

        SelectionSet subFields = field.getSelectionSet();
        if (subFields != null) {

            FieldOfTypeConfig fieldConfig = hgqlSchema.getTypes().get(parentType).getField(field.getName());
            String targetName = fieldConfig.getTargetName();

            Map<Service, Set<Field>> splitFields = getPartitionedFields(targetName, subFields);   // service_1 [field_1, field_3] means service_1 is responsible for field_1 and field_3

            Set<Service> serviceCalls = splitFields.keySet();   // All services that need to be called

            for (Map.Entry<Service, Set<Field>> entry : splitFields.entrySet()) {
                if (!entry.getKey().equals(this.service)) {   // If the service is NOT the same service as the service of this object create a new ExecutionTreeNode
                    ExecutionTreeNode childNode = new ExecutionTreeNode(
                            entry.getKey(),   //service
                            entry.getValue(),   // fields
                            parentId,   // object of the given field
                            targetName,   //outputtype of given field
                            hgqlSchema
                    );


                    // Add newly created ExecutionTreeNode to the ExecutionForest of the object
                    if (this.childrenNodes.containsKey(parentId)) {
                        try {
                            this.childrenNodes.get(parentId).getForest().add(childNode);
                        } catch (Exception e) {
                            LOGGER.error("Problem adding parent", e);
                        }
                    } else {
                        ExecutionForest forest = new ExecutionForest();
                        forest.getForest().add(childNode);
                        try {
                            this.childrenNodes.put(parentId, forest);
                        } catch (Exception e) {
                            LOGGER.error("Problem adding child", e);
                        }
                    }
                }
            }

            if (serviceCalls.contains(this.service)) {  // at least one subfield has the same service as this field

                Set<Field> subfields = splitFields.get(this.service);
                return getFieldsJson(subfields, parentId, targetName);   // return Json representation of the subfields of the selectionSet
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
    private JsonNode getArgsJson(List<Argument> args) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode argNode = mapper.createObjectNode();

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
                    Boolean value = ((BooleanValue) val).isValue();
                    argNode.put(arg.getName(), value);
                    break;
                }
                case "ArrayValue": {
                    List<Node> nodes = val.getChildren();
                    ArrayNode arrayNode = mapper.createArrayNode();

                    for (Node node : nodes)  {
                        String value = ((StringValue) node).getValue();
                        arrayNode.add(value);
                    }
                    argNode.set(arg.getName(), arrayNode);
                    break;
                }
            }

        }

        return argNode;
    }


    /**
     * Generates a mapping of the services needed for the selectionSet to a Set of fields from the selection set using
     * these services.
     * @param parentType name of an objectType
     * @param selectionSet selectionSet with the given parentType as parent
     * @return Mapping between services and fields which defines which service is responsible for which field
     */
    private Map<Service, Set<Field>> getPartitionedFields(String parentType, SelectionSet selectionSet) {

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
                    if (result.containsKey(serviceConfig)) {

                        result.get(serviceConfig).add(field);

                    } else {

                        Set<Field> newFieldSet = new HashSet<>();
                        newFieldSet.add(field);
                        result.put(serviceConfig, newFieldSet);

                    }
                }
            }
        }

        return result;
    }


    private String createId() {
        return "execution-"+ UUID.randomUUID();
    }

    Model generateTreeModel(Set<String> input) {

        TreeExecutionResult executionResult = service.executeQuery(query, input,  childrenNodes.keySet() , rootType, hgqlSchema);

        Map<String,Set<String>> resultSet = executionResult.getResultSet();

        Model model = executionResult.getModel();

        Set<Model> computedModels = new HashSet<>();

        //    StoredModel.getInstance().add(model);

        Set<String> vars = resultSet.keySet();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        Set<Future<Model>> futureModels = new HashSet<>();

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
        computedModels.forEach(model::add);
        return model;
    }
}
