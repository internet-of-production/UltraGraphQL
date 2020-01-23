package org.hypergraphql.datamodel;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.hypergraphql.config.schema.FieldOfTypeConfig;
import org.hypergraphql.config.schema.QueryFieldConfig;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_QUERY_GET_FIELD;
import static org.hypergraphql.config.schema.HGQLVocabulary.SCALAR_TYPES;

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
    }};

    private List<GraphQLArgument> getQueryArgs = new ArrayList<GraphQLArgument>() {{
        add(defaultArguments.get("limit"));
        add(defaultArguments.get("offset"));
        add(defaultArguments.get("_id")); //ToDo: Maybe a list
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
        GraphQLObjectType builtQueryType = registerGraphQLQueryType(this.hgqlSchema.getTypes().get("Query"));
        Set<GraphQLType> builtTypes = typeNames.stream()
                .filter(typeName -> !typeName.equals("Query"))
                .map(typeName -> registerGraphQLType(this.hgqlSchema.getTypes().get(typeName)))   // implicit conversion to GraphQlType for GraphQlSchema
                .collect(Collectors.toSet());

        return GraphQLSchema.newSchema()
                .query(builtQueryType)
                .build(builtTypes);   // ToDo: build(Set<GraphQLType> additionalTypes) is deprecated change to additionalType method

    }

    /**
     * GraphQLFieldDefinition object definition of an default field.
     * @return
     */
    private GraphQLFieldDefinition getidField() {
        FetcherFactory fetcherFactory = new FetcherFactory(hgqlSchema);

        return newFieldDefinition()
                .type(GraphQLID)
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
    private GraphQLObjectType registerGraphQLType(TypeConfig type) {

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

        return newObject()
                .name(typeName)
                .description(description)
                .fields(builtFields)
                .build();
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
        //args.add(defaultArguments.get("limit")); // Default Argument for any field.
        if(field.getService() == null) {
            throw new HGQLConfigurationException("Value of 'service' for field '" + field.getName() + "' cannot be null");
        }

        String description = field.getId() + " (source: " + field.getService().getId() + ").";

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
                .argument(args)
                .description(description)
                .type(field.getGraphqlOutputType())
                .dataFetcher(fetcher)
                .build();
    }

}
