package org.hypergraphql.datamodel;

import graphql.language.*;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.hypergraphql.config.schema.FieldConfig;
import org.hypergraphql.config.schema.FieldOfTypeConfig;
import org.hypergraphql.config.schema.QueryFieldConfig;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.datafetching.services.ManifoldService;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_Boolean;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_HAS_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_HAS_ID;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_HAS_NAME;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_HAS_SERVICE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_HREF;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_ID;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_Int;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_LIST_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_NON_NULL_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_OBJECT_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_OF_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_OUTPUT_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_GET_BY_ID_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_GET_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA_NAMESPACE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SERVICE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SERVICE_NAMESPACE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_STRING;
import static org.hypergraphql.config.schema.HGQLVocabulary.RDF_TYPE;
import static org.hypergraphql.config.schema.HGQLVocabulary.SCALAR_TYPES;
import static org.hypergraphql.config.schema.HGQLVocabulary.SCALAR_TYPES_TO_GRAPHQL_OUTPUT;


public class HGQLSchema {

    private final static Logger LOGGER = LoggerFactory.getLogger(HGQLSchema.class);

    private String schemaUri;
    private String schemaNamespace;


    public Map<String, TypeConfig> getTypes() {
        return types;
    }

    public Map<String, FieldConfig> getFields() {
        return fields;
    }

    public Map<String, QueryFieldConfig> getQueryFields() {
        return queryFields;
    }

    public String getRdfSchemaOutput(String format) {
        return rdfSchema.getDataOutput(format);
    }

    private Map<String, TypeConfig> types;
    private Map<String, FieldConfig> fields;
    private Map<String, QueryFieldConfig> queryFields;

    private ModelContainer rdfSchema = new ModelContainer(ModelFactory.createDefaultModel());

    /**
     * Builds up an RDF graph that represents the HGQL Schema and based on that graph for any type, field and queryfield
     * a corresponding HGQL object (TypeConfig, FieldConfig and QueryFieldConfig) is generated.
     * @param registry Registry containing the schema information (types, fields, queries)
     * @param schemaName Name of the Schema
     * @param services All services that this HGQL Schema supports
     * @throws HGQLConfigurationException Thrown if the schema context is missing or incorrect
     */
    public HGQLSchema(TypeDefinitionRegistry registry, String schemaName, Map<String, Service> services)
            throws HGQLConfigurationException {

        schemaUri = HGQL_SCHEMA_NAMESPACE + schemaName;
        schemaNamespace = schemaUri + "/";

        rdfSchema.insertObjectTriple(schemaUri, RDF_TYPE, HGQL_SCHEMA);
        rdfSchema.insertObjectTriple(schemaNamespace + "query", RDF_TYPE, HGQL_QUERY_TYPE);
        rdfSchema.insertStringLiteralTriple(schemaNamespace + "query", HGQL_HAS_NAME, "Query");
        rdfSchema.insertObjectTriple(HGQL_STRING, RDF_TYPE, HGQL_SCALAR_TYPE);
        rdfSchema.insertStringLiteralTriple(HGQL_STRING, HGQL_HAS_NAME, "String");
        rdfSchema.insertObjectTriple(HGQL_Int, RDF_TYPE, HGQL_SCALAR_TYPE);
        rdfSchema.insertStringLiteralTriple(HGQL_Int, HGQL_HAS_NAME, "Int");
        rdfSchema.insertObjectTriple(HGQL_Boolean, RDF_TYPE, HGQL_SCALAR_TYPE);
        rdfSchema.insertStringLiteralTriple(HGQL_Boolean, HGQL_HAS_NAME, "Boolean");
        rdfSchema.insertObjectTriple(HGQL_ID, RDF_TYPE, HGQL_SCALAR_TYPE);
        rdfSchema.insertStringLiteralTriple(HGQL_ID, HGQL_HAS_NAME, "ID");

        Map<String, TypeDefinition> types = registry.types();   // Contains all types that are specified in the Schema

        TypeDefinition context = types.get("__Context");   // HGQL Context

        if (context == null) {
            HGQLConfigurationException e =
                    new HGQLConfigurationException("The provided GraphQL schema IDL specification is missing the obligatory __Context type (see specs at http://hypergraphql.org).");
            LOGGER.error("Context not set!", e);
            throw(e);
        }

        List<Node> children = context.getChildren();   //URIs with the used abbreviations - Format: <abbr.>: _@href(iri: <URI>)

        Map<String, String> contextMap = new HashMap<>();

        children.forEach(node -> {
            FieldDefinition field = ((FieldDefinition) node);
            String iri = ((StringValue) field.getDirective("href").getArgument("iri").getValue()).getValue();
            contextMap.put(field.getName(), iri);
        });

        Set<String> typeNames = types.keySet();
        typeNames.remove("__Context");

        Set<String> serviceIds = services.keySet();   // Contains all defined services of one HGQLConfig

        // Add all services to the RDF representation of the schema
        serviceIds.forEach(serviceId -> {
            String serviceURI = HGQL_SERVICE_NAMESPACE + serviceId;
            rdfSchema.insertObjectTriple(serviceURI, RDF_TYPE, HGQL_SERVICE);   // <http://hypergraphql.org/service/serviceId> a <"http://hypergraphql.org/Service>
            rdfSchema.insertStringLiteralTriple(serviceURI, HGQL_HAS_ID, serviceId);   //  <http://hypergraphql.org/service/serviceId> hgql:id "serviceId"
        });


        for (String typeName : typeNames) {
            String typeUri = schemaNamespace + typeName;
            rdfSchema.insertStringLiteralTriple(typeUri, HGQL_HAS_NAME, typeName);
            rdfSchema.insertObjectTriple(typeUri, HGQL_HREF, contextMap.get(typeName));
            rdfSchema.insertObjectTriple(typeUri, RDF_TYPE, HGQL_OBJECT_TYPE);


            TypeDefinition type = types.get(typeName);
            final List<Directive> directives = type.getDirectives();

            for (Directive dir : directives) {
                if (dir.getName().equals("service")) {
                    String getQueryUri = typeUri;// + "_GET";
//                    String getByIdQueryUri = typeUri + "_GET_BY_ID";

                    rdfSchema.insertObjectTriple(getQueryUri, RDF_TYPE, HGQL_QUERY_FIELD);   //ToDo: HGQL_QUERY_FIELD and HGQL_QUERY_GET_FIELD are the same if _GET_BY_ID is removed
                    rdfSchema.insertObjectTriple(getQueryUri, RDF_TYPE, HGQL_QUERY_GET_FIELD);
                    rdfSchema.insertObjectTriple(schemaNamespace + "query", HGQL_HAS_FIELD, getQueryUri);
//                    rdfSchema.insertStringLiteralTriple(getQueryUri, HGQL_HAS_NAME, typeName + "_GET");
//                    rdfSchema.insertObjectTriple(getByIdQueryUri, RDF_TYPE, HGQL_QUERY_FIELD);
//                    rdfSchema.insertObjectTriple(getByIdQueryUri, RDF_TYPE, HGQL_QUERY_GET_BY_ID_FIELD);
//                    rdfSchema.insertObjectTriple(schemaNamespace + "query", HGQL_HAS_FIELD, getByIdQueryUri);
//                    rdfSchema.insertStringLiteralTriple(getByIdQueryUri, HGQL_HAS_NAME, typeName + "_GET_BY_ID");

                    String outputListTypeURI = schemaNamespace + UUID.randomUUID();

                    rdfSchema.insertObjectTriple(outputListTypeURI, RDF_TYPE, HGQL_LIST_TYPE);
                    rdfSchema.insertObjectTriple(outputListTypeURI, HGQL_OF_TYPE, typeUri);

                    rdfSchema.insertObjectTriple(getQueryUri, HGQL_OUTPUT_TYPE, outputListTypeURI);
//                    rdfSchema.insertObjectTriple(getByIdQueryUri, HGQL_OUTPUT_TYPE, outputListTypeURI);

                    if (dir.getArgument("id").getValue() instanceof ArrayValue) {
                        // Multiple services are defined for one type add all serviceIds for this type
                        final List<Value> serviceIds_type = ((ArrayValue) dir.getArgument("id").getValue()).getValues();
                        for(Value seriveId : serviceIds_type){
                            addTypeService(getQueryUri, ((StringValue)seriveId).getValue());
                        }
                    } else{
                        String serviceId = ((StringValue) dir.getArgument("id").getValue()).getValue();   // The serviceId that is extracted here is from @service(id:<serviceName>) of the type in the schema
                        addTypeService(getQueryUri, serviceId);
                    }

                }
                //ToDo: Implement the functionality for the newly defined directives
                //ToDo: sameAs
            }

            List<Node> typeChildren = type.getChildren();

            for (Node node : typeChildren) {//ToDo: Implement the functionality for the newly defined directives
                if (node.getClass().getSimpleName().equals("FieldDefinition")) {
                    FieldDefinition field = (FieldDefinition) node;
                    String fieldURI = schemaNamespace + typeName + "/" + field.getName();

                    rdfSchema.insertStringLiteralTriple(fieldURI, HGQL_HAS_NAME, field.getName());
                    rdfSchema.insertObjectTriple(fieldURI, HGQL_HREF, contextMap.get(field.getName()));

                    rdfSchema.insertObjectTriple(fieldURI, RDF_TYPE, HGQL_FIELD);
                    rdfSchema.insertObjectTriple(typeUri, HGQL_HAS_FIELD, fieldURI);

                    final List<Directive> field_directives = field.getDirectives();
                    for (Directive dir : field_directives) {
                        if (dir.getArgument("id").getValue() instanceof ArrayValue) {
                            // Multiple services are defined for one type add all serviceIds for this type
                            // The serviceIds that is extracted here is from @service(id:[serviceNames]) of the type in the schema
                            final List<Value> serviceIds_field = ((ArrayValue) dir.getArgument("id").getValue()).getValues();
                            for (Value seriveId : serviceIds_field) {
                                addTypeService(fieldURI, ((StringValue) seriveId).getValue());
                            }
                        } else {
                            // The serviceId that is extracted here is from @service(id:"serviceName") of the type in the schema
                            String serviceId = ((StringValue) dir.getArgument("id").getValue()).getValue();
                            addTypeService(fieldURI, serviceId);
                        }

                        //ToDo: sameAs
                    }

                    String outputTypeUri = getOutputType(field.getType());
                    rdfSchema.insertObjectTriple(fieldURI, HGQL_OUTPUT_TYPE, outputTypeUri);

                }
            }
        }

        generateConfigs(services);

    }

    /**
     * Generates an object based representation of the rdfSchema with the following classes FieldConfig, FieldOfTypeConfig,
     * QueryFieldConfig and TypeConfig.
     * @param services Must contain the services that where used to construct rdfSchema.
     */
    private void generateConfigs(Map<String, Service> services) {

        this.types = new HashMap<>();
        this.fields = new HashMap<>();
        this.queryFields = new HashMap<>();

        List<RDFNode> fieldNodes = rdfSchema.getSubjectsOfObjectProperty(RDF_TYPE, HGQL_FIELD);

        for (RDFNode fieldNode : fieldNodes) {
            String name = rdfSchema.getValueOfDataProperty(fieldNode, HGQL_HAS_NAME);
            RDFNode href = rdfSchema.getValueOfObjectProperty(fieldNode, HGQL_HREF);
            RDFNode serviceNode = rdfSchema.getValueOfObjectProperty(fieldNode, HGQL_HAS_SERVICE);
            String serviceId = rdfSchema.getValueOfDataProperty(serviceNode, HGQL_HAS_ID);   // Not used, because it is not supported in FieldConfig

            FieldConfig fieldConfig = new FieldConfig(href.asResource().getURI());
            fields.put(name, fieldConfig);
        }

        List<RDFNode> queryFieldNodes = rdfSchema.getSubjectsOfObjectProperty(RDF_TYPE, HGQL_QUERY_FIELD);
        List<RDFNode> queryGetFieldNodes = rdfSchema.getSubjectsOfObjectProperty(RDF_TYPE, HGQL_QUERY_GET_FIELD);

        for (RDFNode node : queryFieldNodes) {
            String name = rdfSchema.getValueOfDataProperty(node, HGQL_HAS_NAME);
            Service queryFieldService = null;
            Set<Service>  queryFieldServices = getServices(services, node);
            if(queryFieldServices.size() > 1){
                // If a query field has multiple responsible services create a ManifoldService to interact with all services through one interface
                ManifoldService manifoldService = new ManifoldService();
                manifoldService.setParameters(queryFieldServices);
                queryFieldService = manifoldService;
            }else if(queryFieldServices.size() == 1){
                queryFieldService = queryFieldServices.iterator().next();
            }else{
                LOGGER.debug(String.format("QueryField %s has no assigned service", name));
            }
            //ToDo: Implement the functionality for the newly defined directives

            String type = (queryGetFieldNodes.contains(node)) ? HGQL_QUERY_GET_FIELD : HGQL_QUERY_GET_BY_ID_FIELD;  //ToDo: unnecessary check if _GET_BY_ID is removed
            QueryFieldConfig fieldConfig = new QueryFieldConfig(queryFieldService, type);
            queryFields.put(name, fieldConfig);
        }

        List<RDFNode> typeNodes = rdfSchema.getSubjectsOfObjectProperty(RDF_TYPE, HGQL_OBJECT_TYPE);
        typeNodes.addAll(rdfSchema.getSubjectsOfObjectProperty(RDF_TYPE, HGQL_QUERY_TYPE));

        typeNodes.forEach(rdfNode -> {
            String typeName = rdfSchema.getValueOfDataProperty(rdfNode, HGQL_HAS_NAME);
            RDFNode typeHref = rdfSchema.getValueOfObjectProperty(rdfNode, HGQL_HREF);
            String typeURI = (typeHref!=null) ? typeHref.asResource().getURI() : null;

            List<RDFNode> fieldsOfType = rdfSchema.getValuesOfObjectProperty(rdfNode, HGQL_HAS_FIELD);
            Map<String, FieldOfTypeConfig> fields = new HashMap<>();

            fieldsOfType.forEach(field -> {

                String fieldOfTypeName = rdfSchema.getValueOfDataProperty(field, HGQL_HAS_NAME);
                RDFNode href = rdfSchema.getValueOfObjectProperty(field, HGQL_HREF);
                String hrefURI = (href!=null) ? href.asResource().getURI() : null;
                //ToDo: Create list of services
                Service fieldOfTypeService = null;
                Set<Service> fieldOfTypeServices = getServices(services, field);
                if(fieldOfTypeServices.size() > 1){
                    // If a query field has multiple responsible services create a ManifoldService to interact with all services through one interface
                    ManifoldService manifoldService = new ManifoldService();
                    manifoldService.setParameters(fieldOfTypeServices);
                    fieldOfTypeService = manifoldService;
                }else if(fieldOfTypeServices.size() == 1){
                    fieldOfTypeService = fieldOfTypeServices.iterator().next();
                }else{
                    LOGGER.debug(String.format("FieldOfType %s has no assigned service", fieldOfTypeName));
                }

                RDFNode outputTypeNode = rdfSchema.getValueOfObjectProperty(field, HGQL_OUTPUT_TYPE);
                GraphQLOutputType graphqlOutputType = getGraphQLOutputType(outputTypeNode);
                Boolean isList = getIsList(outputTypeNode);
                String targetTypeName = getTargetTypeName(outputTypeNode);
                //ToDo: Implement the functionality for the newly defined directives

                FieldOfTypeConfig fieldOfTypeConfig = new FieldOfTypeConfig(fieldOfTypeName,
                        hrefURI,
                        fieldOfTypeService,
                        graphqlOutputType,
                        isList,
                        targetTypeName);
                fields.put(fieldOfTypeName, fieldOfTypeConfig);

            });

            TypeConfig typeConfig = new TypeConfig(typeName, typeURI, fields);

            this.types.put(typeName, typeConfig);

        });
    }

    /**
     * Extracts all services that are associated with the given subject from the rdfSchema and returns a set containing
     * the corresponding service objects.
     * @param services Services that are used in the rdfSchema
     * @param subject Field or type URI that has services
     * @return Set of service objects that are responsible for the given subject
     */
    private Set<Service> getServices(Map<String, Service> services, RDFNode subject) {
        Set<Service> responsibleServices = new HashSet<>();
        List<RDFNode> serviceNodes = rdfSchema.getValuesOfObjectProperty(subject, HGQL_HAS_SERVICE);
        for(RDFNode serviceNode : serviceNodes){
            String serviceId = rdfSchema.getValueOfDataProperty(serviceNode, HGQL_HAS_ID);
            responsibleServices.add(services.get(serviceId));
        }
        return responsibleServices;
    }

    /**
     * Returns the name of the given outputTypeNode. If the name is not defined the name of the type is returned.
     * All informations are extracted from rdfSchema.
     * @param outputTypeNode
     * @return
     */
    private String getTargetTypeName(RDFNode outputTypeNode) {
        String typeName = rdfSchema.getValueOfDataProperty(outputTypeNode, HGQL_HAS_NAME);
        if (typeName!=null) {
            return typeName;
        } else {
            RDFNode childOutputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, HGQL_OF_TYPE);
            return getTargetTypeName(childOutputNode);
        }
    }

    /**
     * Proves if the given outputTypeNode is a List.
     * @param outputTypeNode
     * @return
     */
    private Boolean getIsList(RDFNode outputTypeNode) {
        RDFNode outputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, RDF_TYPE);
        String typeURI = outputNode.asResource().getURI();
        if (typeURI.equals(HGQL_LIST_TYPE)) { return true; }
        else {
            RDFNode childOutputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, HGQL_OF_TYPE);
            if (childOutputNode!=null) { return getIsList(childOutputNode); }
            else {
                return false;
            }
        }
    }

    /**
     * Generates a GraphQlOutputType based on the given outputTypeNode and further information are extracted from rdfSchema.
     * @param outputTypeNode TypeNode from the rdfSchema
     * @return Returns the corresponding GraphQLOutputType object.
     */
    private GraphQLOutputType getGraphQLOutputType(RDFNode outputTypeNode) {
        List<RDFNode> outputNodes = rdfSchema.getValuesOfObjectProperty(outputTypeNode, RDF_TYPE);
        // schema types have multiple RDF_TYPEs (HGQL_QUERY_GET_FIELD, HGQL_QUERY_FIELD & HGQL_OBJECT_TYPE), here only HGQL_OBJECT_TYPE is needed
        outputNodes = outputNodes.stream()
                .filter(outputNode -> !outputNode.toString().equals(HGQL_QUERY_GET_FIELD) && !outputNode.toString().equals(HGQL_QUERY_FIELD))
                .collect(Collectors.toList());
        RDFNode outputNode = outputNodes.get(0);
//        RDFNode outputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, RDF_TYPE);
        String typeURI = outputNode.asResource().getURI();
        if (typeURI.equals(HGQL_SCALAR_TYPE)) {
            return SCALAR_TYPES_TO_GRAPHQL_OUTPUT.get(outputTypeNode.asResource().getURI());
        }
        if (typeURI.equals(HGQL_OBJECT_TYPE)) {
            String typeName = rdfSchema.getValueOfDataProperty(outputTypeNode, HGQL_HAS_NAME);
            return new GraphQLTypeReference(typeName);
        }
        if (typeURI.equals(HGQL_LIST_TYPE)) {
            RDFNode childOutputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, HGQL_OF_TYPE);
            return new GraphQLList(getGraphQLOutputType(childOutputNode));
        }
        if (typeURI.equals(HGQL_NON_NULL_TYPE)) {
            RDFNode childOutputNode = rdfSchema.getValueOfObjectProperty(outputTypeNode, HGQL_OF_TYPE);
            return new GraphQLNonNull(getGraphQLOutputType(childOutputNode));
        }
        return null;
    }

    /**
     * Returns the name of the given type.
     * If the given type is a TypeName Class then the name is returned and for non scalar types the name is
     * enriched with the schema namespace.
     * Otherwise  a random UUID with the schema namespace is returned and the rdfSchema is adapted if the type is a ListType
     * or an NonNullType.
     * Encapsulation of Type Classes: [Person]! == NonNullType[ListType[TypeName]]
     * @param type
     * @return Name of the Output type as String
     */
    private String getOutputType(Type type) {

        if (type.getClass() == TypeName.class) {
            TypeName castType = (TypeName) type;
            String name = castType.getName();

            if (SCALAR_TYPES.containsKey(name)) {
                return SCALAR_TYPES.get(name);
            } else return schemaNamespace + name;
        }

        String dummyNode = schemaNamespace + UUID.randomUUID();

        if (type.getClass() == ListType.class) {
            ListType castType = (ListType) type;
            String subTypeUri = getOutputType(castType.getType());
            rdfSchema.insertObjectTriple(dummyNode, RDF_TYPE, HGQL_LIST_TYPE);
            rdfSchema.insertObjectTriple(dummyNode, HGQL_OF_TYPE, subTypeUri);
        }

        if (type.getClass() == NonNullType.class) {
            NonNullType castType = (NonNullType) type;
            String subTypeUri = getOutputType(castType.getType());
            rdfSchema.insertObjectTriple(dummyNode, RDF_TYPE, HGQL_NON_NULL_TYPE);
            rdfSchema.insertObjectTriple(dummyNode, HGQL_OF_TYPE, subTypeUri);
        }

        return dummyNode;
    }

    public String getSchemaUri() {
        return schemaUri;
    }

    public String getSchemaNamespace() {
        return schemaNamespace;
    }

    private void addTypeService(String getQueryUri, String serviceId){
        String serviceURI = HGQL_SERVICE_NAMESPACE + serviceId;
        rdfSchema.insertObjectTriple(getQueryUri, HGQL_HAS_SERVICE, serviceURI);
//                        rdfSchema.insertObjectTriple(getByIdQueryUri, HGQL_HAS_SERVICE, serviceURI);
    }

    private void addFieldService(String fieldURI, String serviceId){
        String serviceURI = HGQL_SERVICE_NAMESPACE + serviceId;
        rdfSchema.insertObjectTriple(fieldURI, HGQL_HAS_SERVICE, serviceURI);
//                        rdfSchema.insertObjectTriple(getByIdQueryUri, HGQL_HAS_SERVICE, serviceURI);
        rdfSchema.insertObjectTriple(serviceURI, RDF_TYPE, HGQL_SERVICE);
    }
}
