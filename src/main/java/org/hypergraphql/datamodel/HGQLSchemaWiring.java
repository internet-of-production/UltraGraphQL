package org.hypergraphql.datamodel;

import graphql.GraphQL;
import graphql.schema.*;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.hypergraphql.config.schema.FieldOfTypeConfig;
import org.hypergraphql.config.schema.QueryFieldConfig;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.mutation.SPARQLMutationConverter;
import org.hypergraphql.mutation.SPARQLMutationConverter.MUTATION_ACTION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;
import static org.hypergraphql.config.schema.HGQLVocabulary.*;

/**
 * Created by szymon on 24/08/2017.
 * <p>
 * This class defines the GraphQL wiring (data fetchers)
 */

public class HGQLSchemaWiring {

    private static final Logger LOGGER = LoggerFactory.getLogger(HGQLSchemaWiring.class);

    private HGQLSchema hgqlSchema;
    private GraphQLSchema schema;

    public GraphQLSchema getSchema() {

        return schema;
    }

    public HGQLSchema getHgqlSchema() {
        return hgqlSchema;
    }

    private Map<String, GraphQLArgument> defaultArguments = new HashMap<String, GraphQLArgument>() {{
        put("limit", new GraphQLArgument("limit", GraphQLInt));
        put("offset", new GraphQLArgument("offset", GraphQLInt));
        put("lang", new GraphQLArgument("lang", GraphQLString));
        put("uris", new GraphQLArgument("uris", new GraphQLNonNull(new GraphQLList(GraphQLID))));
        put("_id", new GraphQLArgument("_id", new GraphQLList(GraphQLID)));  // ToDo: currently added for default Query support, when schema loading is complete this is not needed
        put("order", GraphQLArgument.newArgument()
                .name("order")
                .type(GraphQLEnumType.newEnum()
                        .name("Order_ENUM")
                        .value("DESCENDING", "DESC")
                        .value("ASCENDING", "ASC")
                        .description("Operators to define the order of the resulting list")
                        .build())
                .build());
    }};

    private List<GraphQLArgument> getQueryArgs = new ArrayList<GraphQLArgument>() {{
        add(defaultArguments.get("limit"));
        add(defaultArguments.get("offset"));
        add(defaultArguments.get("_id")); //ToDo: Maybe a list
        add(defaultArguments.get("order"));
    }};

//    private List<GraphQLArgument> getByIdQueryArgs = new ArrayList<GraphQLArgument>() {{
//        add(defaultArguments.get("uris"));
//    }};

    /**
     * Generates an HGQLSchema for the given schema and based on this schema a GraphQLSchema is generated with query
     * support for the schema. The GraphQLSchema is wired with the HGQLSchema and enriched with DataFetchers.
     * @param registry Registry containing the schema information (types, fields, queries)
     * @param schemaName Name of the Schema
     * @param serviceConfigs All services that this HGQL Schema supports.
     * @throws HGQLConfigurationException
     */
    public HGQLSchemaWiring(TypeDefinitionRegistry registry, String schemaName, List<ServiceConfig> serviceConfigs) throws HGQLConfigurationException {

        try {
            this.hgqlSchema = new HGQLSchema(registry, schemaName, generateServices(serviceConfigs));
            this.schema = generateSchema();

        } catch (Exception e) {
            throw new HGQLConfigurationException("Unable to perform schema wiring", e);
        }
    }

    /**
     *  Converts the list of ServiceConfig into Service objects that are mapped with an unique id (defined in ServiceConfig)
     * @param serviceConfigs List of ServiceConfigs to be converted in actual Service objects.
     * @return Mapping of unique id to the corresponding Service
     * @throws HGQLConfigurationException
     */
    private Map<String, Service> generateServices(List<ServiceConfig> serviceConfigs) throws HGQLConfigurationException {

        Map<String, Service> services = new HashMap<>();

        String packageName = "org.hypergraphql.datafetching.services";

        serviceConfigs.forEach(serviceConfig -> {
            try {
                String type = serviceConfig.getType();

                Class serviceType = Class.forName(packageName + "." + type);
                Service service = (Service) serviceType.getConstructors()[0].newInstance();

                service.setParameters(serviceConfig);

                services.put(serviceConfig.getId(), service);
            } catch (IllegalAccessException
                    | InstantiationException
                    | ClassNotFoundException
                    | InvocationTargetException e) {
                LOGGER.error("Problem adding service {}", serviceConfig.getId(), e);
                throw new HGQLConfigurationException("Error wiring up services", e);
            }
        });

        return services;
    }

    /**
     * Uses hgqlSchema to generate a GraphQLObjectType for the Query Type of the Schema and a set of GraphQLTypes for the
     * non Query types of the Schema. All of the type objects provide then a DataFetcher for retrieving the data.
     * @return GraphQLSchema based on the hgqlSchema.
     */
    private GraphQLSchema generateSchema() {

        Set<String> typeNames = this.hgqlSchema.getTypes().keySet();
        GraphQLObjectType builtQueryType = registerGraphQLQueryType(this.hgqlSchema.getTypes().get("Query"));   // convert query type to GraphQLObjectType
        Set<GraphQLType> builtTypes = typeNames.stream()
                .filter(typeName -> !typeName.equals("Query"))
                .map(typeName -> {
                    TypeConfig type = this.hgqlSchema.getTypes().get(typeName);
                    if(type.isUnion()){
                        return registerGrapQLUnionType(type);
                    }else if(type.isInterface()){
                        return  registerGraphQLInterfaceType(type);
                    }else{
                        return registerGraphQLObjectType(type);
                    }
                })   // implicit conversion to GraphQlType for GraphQlSchema
                .collect(Collectors.toSet());
        Set<GraphQLType> builtInputTypes= typeNames.stream()
                .map(typeName -> registerGraphQLInputType(this.hgqlSchema.getTypes().get(typeName)))
                .filter(graphQLInputType -> graphQLInputType != null)
                .collect(Collectors.toSet());
        GraphQLObjectType builtMutationType = registerGraphQLMutationType();

        return GraphQLSchema.newSchema()
                .query(builtQueryType)
                .mutation(builtMutationType)
                .additionalTypes(builtTypes)
                .additionalTypes(builtInputTypes)
                .build();   // ToDo: build(Set<GraphQLType> additionalTypes) is deprecated change to additionalType method

    }

    private GraphQLInputObjectType registerGraphQLInputType(TypeConfig typeConfig) {
        if(typeConfig.isInterface()){
            return null;
        }
        if(typeConfig.isUnion()){
            return null;
        }
        String name = String.format("input_%s",typeConfig.getName());
        this.hgqlSchema.addInputObject(name, typeConfig.getName());
        String description = "Generated input object to be used in the auto generated mutation functions";
        if(typeConfig.getName().equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            description += "\n Placeholder object for literal values of fields with multiple output types. Strings given" +
                    " for the field of this object are inserted as direct literal of the parent field of this object";
        }
        List<GraphQLInputObjectField> fields = typeConfig.getFields().values().stream()
                .flatMap(fieldOfTypeConfig -> registerGraphQLInputField(typeConfig, fieldOfTypeConfig).stream())
                .collect(Collectors.toList());
        return newInputObject()
                .name(name)
                .description(description)
                .fields(fields)
                .build();
    }

    /**
     * Generates the input fields for the given field. If the output type of the given field is an interface then for all
     * objects that implement the interface a input field is generated with the corresponding type as output. If the
     * output type of the given field is an union then for all member sof the union a field is generated with the
     * corresponding type as output type of the field. Since the names of the fields must be unique the fields of unions
     * and interfaces get the type attached so that the user also knows which type is inserted.
     * @param parentType object that contains the given field - only used for the description of the input field
     * @param fieldOfTypeConfig field for which a corresponding input field is needed
     * @return Set containing all generated input fields
     */
    private Set<GraphQLInputObjectField> registerGraphQLInputField(TypeConfig parentType, FieldOfTypeConfig fieldOfTypeConfig) {
        Set<GraphQLInputObjectField> res = new HashSet<>();
        String description = String.format("Generated input field from field %s in objectType %s", fieldOfTypeConfig.getName(), parentType.getName());
        if(!parentType.getName().equals(HGQL_SCALAR_LITERAL_GQL_NAME)){
            res.add(newInputObjectField()
                    .name("_id")
                    .type(GraphQLNonNull.nonNull(GraphQLID))
                    .description("IRI of the object. MUST be defined")
                    .build());
        }else{
            // the Literal placeholder object has no id as it only represents the literal of the parent field
            description += "\n This field is part of the Literal placeholder object. Values given here will be inserted " +
                    "as direct Literal of the field that linked to the literal placeholder object";
        }
        String name = fieldOfTypeConfig.getName();
        if(fieldOfTypeConfig.getGraphqlOutputType().equals(GraphQLString) || fieldOfTypeConfig.getGraphqlOutputType().equals(GraphQLList.list(GraphQLString))){
            this.hgqlSchema.addInputField(name, fieldOfTypeConfig.getName());
            this.hgqlSchema.addinputFieldsOutput(name, "String");
            res.add(newInputObjectField()
                    .name(name)
                    .description(description)
                    .type(GraphQLList.list(GraphQLString))
                    .build());
        }else{
            TypeConfig output_type = this.hgqlSchema.getTypes().get(fieldOfTypeConfig.getTargetName());
            if(output_type.isInterface()){
                final Set<String> interafaceObjects = output_type.getInterafaceObjects();
                for(String obj_name : interafaceObjects){
                    TypeConfig  type = this.hgqlSchema.getTypes().get(obj_name);
                    if(type.isObject()){
                        String name_plus_type = name + HGQL_MUTATION_INPUT_FIELD_INFIX + type.getName();
                        this.hgqlSchema.addInputField(name_plus_type, fieldOfTypeConfig.getName());
                        this.hgqlSchema.addinputFieldsOutput(name_plus_type, type.getName());
                        res.add(newInputObjectField()
                                .name(name_plus_type)
                                .description(description)
                                .type(GraphQLList.list(GraphQLTypeReference.typeRef(HGQL_MUTATION_INPUT_PREFIX + obj_name)))
                                .build());
                    }
                }
            }else if(output_type.isUnion()){
                final Collection<TypeConfig> typeConfigs = output_type.getUnionMembers().values();
                for(TypeConfig type : typeConfigs){
                    if(type.isObject()){
                        String name_plus_type = name + HGQL_MUTATION_INPUT_FIELD_INFIX + type.getName();
                        this.hgqlSchema.addinputFieldsOutput(name_plus_type, type.getName());
                        this.hgqlSchema.addInputField(name_plus_type, fieldOfTypeConfig.getName());
                        res.add(newInputObjectField()
                                .name(name_plus_type)
                                .description(description)
                                .type(GraphQLList.list(GraphQLTypeReference.typeRef(HGQL_MUTATION_INPUT_PREFIX + type.getName())))
                                .build());
                    }
                }
            }else{
                this.hgqlSchema.addInputField(name, fieldOfTypeConfig.getName());
                this.hgqlSchema.addinputFieldsOutput(name, fieldOfTypeConfig.getTargetName());
                res.add(newInputObjectField()
                        .name(name)
                        .description(description)
                        .type(GraphQLList.list(GraphQLTypeReference.typeRef(HGQL_MUTATION_INPUT_PREFIX + fieldOfTypeConfig.getTargetName())))
                        .build());
            }
        }
        return res;
    }

    private GraphQLObjectType registerGraphQLMutationType() {
        String typeName = "Mutation";
        String description = "Mutation functions for all queryable objects.";
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        List<GraphQLFieldDefinition> mutationFields = new ArrayList<>();
        List<GraphQLFieldDefinition> builtInsertFields;
        List<GraphQLFieldDefinition> builtDeleteFields;

        Set<TypeConfig> fields = this.hgqlSchema.getTypes().values().stream()
                .filter(typeConfig -> !typeConfig.getName().equals("Query") && !typeConfig.getName().equals(HGQL_SCALAR_LITERAL_GQL_NAME))
                .filter(typeConfig -> typeConfig.isObject())
                .collect(Collectors.toSet());

        builtInsertFields = fields.stream()
                .map(field -> registerGraphQLMutationField(field, MUTATION_ACTION.INSERT, fetcherFactory))
                .collect(Collectors.toList());

        builtDeleteFields = fields.stream()
                .map(field -> registerGraphQLMutationField(field, MUTATION_ACTION.DELETE, fetcherFactory))
                .collect(Collectors.toList());

        mutationFields.addAll(builtInsertFields);
        mutationFields.addAll(builtDeleteFields);

        return newObject()
                .name(typeName)
                .description(description)
                .fields(mutationFields)
                .build();
    }

    private GraphQLFieldDefinition registerGraphQLMutationField(TypeConfig mutationfield, MUTATION_ACTION action, FetcherFactory fetcherFactory) {

        String name = "";
        if(action == MUTATION_ACTION.INSERT){
            name = String.format("%s%s", HGQL_MUTATION_INSERT_PREFIX,mutationfield.getName());
        }else if(action == MUTATION_ACTION.DELETE){
            name = String.format("%s%s", HGQL_MUTATION_DELETE_PREFIX,mutationfield.getName());
        }else{
            // Currently unsupported action
        }
        String description = "Autogenerated mutation function for the object " + mutationfield.getName();
        this.hgqlSchema.addMutationField(name,mutationfield.getName());
        List<GraphQLArgument> args = new ArrayList<>();
        //Todo: add all fields of the object as argument
        Map<String, FieldOfTypeConfig> fields = mutationfield.getFields();
        for(FieldOfTypeConfig field : fields.values()){
            if(field.getGraphqlOutputType().equals(GraphQLString) || field.getGraphqlOutputType().equals(GraphQLList.list(GraphQLString))){
                args.add(GraphQLArgument.newArgument()
                        .name(field.getName())
                        .type(GraphQLList.list(GraphQLString))
                        .description(description)
                        .build());
            }else{
                TypeConfig outputType = this.hgqlSchema.getTypes().get(field.getTargetName());
                if(outputType.isObject()){
                    args.add(GraphQLArgument.newArgument()
                            .name(field.getName())
                            .type(GraphQLList.list(GraphQLTypeReference.typeRef(HGQL_MUTATION_INPUT_PREFIX + outputType.getName())))
                            .description(description)
                            .build());
                }else{
                    if(outputType.isInterface()){
                        Set<String> objects = outputType.getInterafaceObjects();
                        for(String obj : objects){
                            TypeConfig type = this.hgqlSchema.getTypes().get(obj);
                            args.add(GraphQLArgument.newArgument()
                                    .name(field.getName() + HGQL_MUTATION_INPUT_FIELD_INFIX +  type.getName())
                                    .type(GraphQLList.list(GraphQLTypeReference.typeRef(HGQL_MUTATION_INPUT_PREFIX + type.getName())))
                                    .description(description)
                                    .build());
                        }
                    }
                }
            }

        }
        if(action == MUTATION_ACTION.INSERT){
            args.add(GraphQLArgument.newArgument()
                    .name("_id")
                    .type(GraphQLNonNull.nonNull(GraphQLID))
                    .build());
        }else if(action == MUTATION_ACTION.DELETE){
            args.add(GraphQLArgument.newArgument()
                    .name("_id")
                    .type(GraphQLID)
                    .build());
        }

        return newFieldDefinition()
                .name(name)
                .description(description)
                .arguments(args)
                .type(GraphQLList.list(GraphQLTypeReference.typeRef(mutationfield.getName())))
                .dataFetcher(fetcherFactory.instancesOfTypeFetcher())   //ToDo: Define Mutation Handler for the mutatuon fields
                .build();
    }

    /**
     * GraphQLFieldDefinition object definition of an default field.
     * @return
     */
    private GraphQLFieldDefinition getidField() {
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        return newFieldDefinition()
                .type(GraphQLNonNull.nonNull(GraphQLID))
                .name("_id")
                .description("The URI of this resource.")
                .dataFetcher(fetcherFactory.idFetcher()).build();
    }

    /**
     * GraphQLFieldDefinition object definition of an default field.
     * @return
     */
    private GraphQLFieldDefinition gettypeField() {
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        return newFieldDefinition()
                .type(GraphQLID)
                .name("_type")
                .description("The rdf:type of this resource (used as a filter when fetching data from its original source).")
                .dataFetcher(fetcherFactory.typeFetcher(this.hgqlSchema.getTypes())).build();
    }

    /**
     * Creates a GraphQLObjectType for the "Query" type. Also creates GraphQLFieldDefinitions for each field of the type.
     * @param type TypeConfig from the Query type
     * @return GraphQLObjectType object corresponding to the Query type
     */
    private GraphQLObjectType registerGraphQLQueryType(TypeConfig type) {

        String typeName = type.getName();
        String description = "Top queryable predicates. If the _id argument is defined only results matching this id will be returned.";
//                "Top queryable predicates. " +
//                "_GET queries return all objects of a given type, possibly restricted by limit and offset values. " +
//                "_GET_BY_ID queries require a set of URIs to be specified.";

        List<GraphQLFieldDefinition> builtFields;

        Map<String, FieldOfTypeConfig> fields = type.getFields();

        Set<String> fieldNames = fields.keySet();

        builtFields = fieldNames.stream()
                .filter(s -> !s.equals(HGQL_SCALAR_LITERAL_GQL_NAME))   // the literal placeholder object is excluded as it is not allowed to be directly queried
                .map(fieldName -> registerGraphQLQueryField(type.getField(fieldName)))
                .collect(Collectors.toList());

        return newObject()
                .name(typeName)
                .description(description)
                .fields(builtFields)
                .build();
    }

    /**
     * Generates a GraphQLObjectType object for the given type and also GraphQLFieldDefinition objects for all its fields.
     * The type object does not provide a dataFetcher only fields do.
     * @param type TypeCobfig object that is not the Query field   //ToDo: Add Mutation if mutations are supported
     * @return GraphQLObjectType object corresponding to the given type
     */
    private GraphQLObjectType registerGraphQLObjectType(TypeConfig type) {

        String typeName = type.getName();
        String uri = this.hgqlSchema.getTypes().get(typeName).getId();
        String description = "Instances of \"" + uri + "\".";

        List<GraphQLFieldDefinition> builtFields;

        Map<String, FieldOfTypeConfig> fields = type.getFields();

        Set<String> fieldNames = fields.keySet();

        builtFields = fieldNames.stream()
                .map(fieldName -> registerGraphQLField(type.getField(fieldName)))
                .collect(Collectors.toList());

        builtFields.add(getidField());
        builtFields.add(gettypeField());

        Set<GraphQLTypeReference> interfaces = this.hgqlSchema.getImplementsInterface(type).stream()
                .map(GraphQLTypeReference::new)
                .collect(Collectors.toSet());
        GraphQLTypeReference[] interfaces_ref = new GraphQLTypeReference[interfaces.size()];
        interfaces_ref = interfaces.toArray(interfaces_ref);

        return newObject()
                .name(typeName)
                .description(description)
                .fields(builtFields)
                .withInterfaces(interfaces_ref)
                .build();
    }

    /*
     * Generates a GraphQLObjectType object for the given type and also GraphQLFieldDefinition objects for all its fields.
     * The type object does not provide a dataFetcher only fields do.
     * @param type TypeCobfig object that is not the Query field   //ToDo: Add Mutation if mutations are supported
     * @return GraphQLObjectType object corresponding to the given type
     */
    private GraphQLInterfaceType registerGraphQLInterfaceType(TypeConfig type) {

        String typeName = type.getName();
        String uri = this.hgqlSchema.getTypes().get(typeName).getId();
        String description = "Instances of \"" + uri + "\".";

        List<GraphQLFieldDefinition> builtFields;

        Map<String, FieldOfTypeConfig> fields = type.getFields();

        Set<String> fieldNames = fields.keySet();

        builtFields = fieldNames.stream()
                .map(fieldName -> registerGraphQLField(type.getField(fieldName)))
                .collect(Collectors.toList());

        builtFields.add(getidField());
        builtFields.add(gettypeField());

        return newInterface()
                .name(typeName)
                .description(description)
                .fields(builtFields)
                .typeResolver(env -> {
                    ModelContainer resultPool = env.getContext();
                    ResourceImpl resource = env.getObject();
                    if(resource.isLiteral()){
                        // Literal in an field with an interface as output means that the placeholder objectType was queryied
                        return this.schema.getObjectType(HGQL_SCALAR_LITERAL_GQL_NAME);
                    }
                    final NodeIterator nodeIterator = resultPool.model.listObjectsOfProperty(resource.asResource(), resultPool.model.getProperty(RDF_TYPE));
                    while (nodeIterator.hasNext()){   // only handle first found type because currently GraphQL only accepts one type answer
                        final RDFNode targetNode = nodeIterator.next();
                        String targetUri = targetNode.toString();
                        final Optional<String> targetId = type.getInterafaceObjects().stream()
                                .filter(typeConfig -> this.hgqlSchema.getTypes().get(typeConfig).getId().equals(targetUri))
                                .findFirst();
                        if(targetId.isPresent()){
                            if(type.getInterafaceObjects().contains(targetId.get())){
                                return this.schema.getObjectType(targetId.get());
                            }
                        }

                    }
                    return null;
                })
                .build();
    }

    /**
     * Generates a GraphQLUnionType for the given TypeConfig. If the given TypeConfig is a NOT a UNION null is returned.
     * @param type TypeConfig with type == UNION
     * @return GraphQLUnionType
     */
    private GraphQLUnionType registerGrapQLUnionType(TypeConfig type){
        String typeName = type.getName();
        if(type.isUnion()){
            final GraphQLUnionType.Builder unionBuilder = newUnionType()
                    .name(typeName);
            type.getUnionMembers().keySet().forEach(memberName -> {
                unionBuilder.possibleType(new GraphQLTypeReference(memberName));
            });
            unionBuilder.typeResolver(env -> {
                ModelContainer resultPool = env.getContext();
                ResourceImpl resource = env.getObject();
                final NodeIterator nodeIterator = resultPool.model.listObjectsOfProperty(resource.asResource(), resultPool.model.getProperty(RDF_TYPE));
                while (nodeIterator.hasNext()){
                    final RDFNode targetNode = nodeIterator.next();
                    String targetUri = targetNode.toString();
                    final Optional<TypeConfig> targetId = type.getUnionMembers().values().stream()
                            .filter(typeConfig -> typeConfig.getId().equals(targetUri))
                            .findFirst();
                    if(targetId.isPresent()){
                        if(type.getUnionMembers().keySet().contains(targetId.get().getName())){
                            return this.schema.getObjectType(targetId.get().getName());
                        }
                    }

                }
                //ToDo: Extend resolver for Interfaces
                return null;
            });
            unionBuilder.description("UnionType");
            return unionBuilder.build();
        }
        return null;
    }

    /**
     * Generates a GraphQLFieldDefinition object of the given field. Creates a FetcherFactory to
     * get the dataFetcher inorder to call the getBuiltQueryField method. Based on the output type of the field a different
     * dataFetcher is used.
     * @param field Query field to convert to corresponding GraphQLFieldDefinition.
     * @return Returns a GraphQLFieldDefinition object containing the name of the field, arguments, description, type and a datafetcher.
     */
    private GraphQLFieldDefinition registerGraphQLField(FieldOfTypeConfig field) {
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        Boolean isList = field.isList();

        if (SCALAR_TYPES.containsKey(field.getTargetName())) {
            if (isList) {
                return getBuiltField(field, fetcherFactory.literalValuesFetcher());
            } else {
                return getBuiltField(field, fetcherFactory.literalValueFetcher());
            }

        } else {
            if (isList) {
                return getBuiltField(field, fetcherFactory.objectsFetcher());
            } else {
                return getBuiltField(field, fetcherFactory.objectFetcher());
            }
        }
    }

    /**
     * Generates a GraphQLFieldDefinition object of an query field (Field of the type Query). Creates a FetcherFactory to
     * get the dataFetcher inorder to call the getBuiltQueryField method.
     * @param field Query field to convert to corresponding GraphQLFieldDefinition.
     * @return Returns a GraphQLFieldDefinition object containing the name of the field, arguments, description, type and a datafetcher.
     */
    private GraphQLFieldDefinition registerGraphQLQueryField(FieldOfTypeConfig field) {
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        return getBuiltQueryField(field, fetcherFactory.instancesOfTypeFetcher());
    }

    /**
     * Generates a GraphQLFieldDefinition object of an field. Adds the arguments to the field (used in the SelectionSet).
     * @param field field to convert to corresponding GraphQLFieldDefinition.
     * @param fetcher DataFetcher of the field.
     * @return Returns a GraphQLFieldDefinition object containing the name of the field, arguments, description, type and a datafetcher.
     * @throws HGQLConfigurationException
     */
    private GraphQLFieldDefinition getBuiltField(FieldOfTypeConfig field, DataFetcher fetcher) throws HGQLConfigurationException {

        List<GraphQLArgument> args = new ArrayList<>();

        if (field.getTargetName().equals("String")) {
            args.add(defaultArguments.get("lang"));
        }
        if(!(SCALAR_TYPES.containsKey(field.getTargetName()))){
            if(field.isList()){
                args.addAll(getQueryArgs);
            }
        }else{
            if(field.isList()){
                args.add(defaultArguments.get("limit"));
                args.add(defaultArguments.get("offset"));
                args.add(defaultArguments.get("order"));
            }
        }
        //args.add(defaultArguments.get("limit")); // Default Argument for any field.
        String description = "";
        if(field.getService() == null) {
            if(field.getId().equals(HGQL_SCALAR_LITERAL_VALUE_URI)){
                // field is the value of the placeholder literal object
                // As this field has String as standard output the string specific arguments were already added before
                description = "Placeholder field for the Parent field, because a Scalar is not allowed to implement an" +
                        " interface. Therefore the handling of multiple output types for one field requires it that for " +
                        "the Scalar String a placeholder object is created cirrcumventing this limitation.";
            }else{
                throw new HGQLConfigurationException("Value of 'service' for field '" + field.getName() + "' cannot be null");
            }
        }else{
            description = field.getId() + " (source: " + field.getService().getId() + ").";

        }

        return newFieldDefinition()
                .name(field.getName())
                .argument(args)
                .description(description)
                .type(field.getGraphqlOutputType())
                .dataFetcher(fetcher)
                .build();
    }

    /**
     * Generates a GraphQLFieldDefinition object of an query field (Field of the type Query). Adds the arguments to the query field.
     * @param field Query field to convert to corresponding GraphQLFieldDefinition.
     * @param fetcher DataFetcher of the field.
     * @return Returns a GraphQLFieldDefinition object containing the name of the field, arguments, description, type and a datafetcher.
     * @throws HGQLConfigurationException
     */
    private GraphQLFieldDefinition getBuiltQueryField(FieldOfTypeConfig field, DataFetcher fetcher) throws HGQLConfigurationException {

        List<GraphQLArgument> args = new ArrayList<>();  // Arguments of the QueryField

        if (this.hgqlSchema.getQueryFields().get(field.getName()).type().equals(HGQL_QUERY_GET_FIELD)) {
            args.addAll(getQueryArgs);
            //ToDo: ADD individual Query Arguments: This is the place where the query arguments of an Field are defined
        }
//        else {
//            args.addAll(getByIdQueryArgs);
//        }

        final QueryFieldConfig queryFieldConfig = this.hgqlSchema.getQueryFields().get(field.getName()); // retrieve QueryFieldConfig of given FieldOfTypeConfig

        Service service = queryFieldConfig.service();
        if(service == null) {
            throw new HGQLConfigurationException("Service for field '" + field.getName() + "':['"
                    + queryFieldConfig.type() + "'] not specified (null)");
        }
        String serviceId = service.getId();
        String description = (queryFieldConfig.type().equals(HGQL_QUERY_GET_FIELD)) ?
                "Get instances of " + field.getTargetName() + " (service: " + serviceId + ")" :
                "Get instances of " + field.getTargetName() + " by URIs (service: " + serviceId + ")";

        return newFieldDefinition()
                .name(field.getName())
                .arguments(args)
                .description(description)
                .type(field.getGraphqlOutputType())
                .dataFetcher(fetcher)
                .build();
    }

}
