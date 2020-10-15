package org.hypergraphql.config.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.datamodel.HGQLSchema;

import java.util.List;
import java.util.Map;

/**
 * Created by szymon on 05/09/2017.
 */

public class HGQLConfig {

    //config file attributes
    private String name;   // Name of the HGQL endpoint
    private String schemaFile;
    private GraphqlConfig graphqlConfig;
    private List<ServiceConfig> serviceConfigs;
    private Boolean extraction;
    private  String mappingFile;
    private  String queryFile;
    private Boolean mutations;
    private String mutationService;
    private Map<String, String> prefixes;

    //Additional attributes
    private GraphQLSchema schema;
    private HGQLSchema hgqlSchema;

    /**
     * Sets up a HGQLConfig providing enough information to start the HGQL endpoint.
     * This constructor is meant to be used to directly parse the configuration file.
     * @param name Name of the HGQL endpoint
     * @param schemaFile schema file
     * @param graphqlConfig Information about the endpoint server - GraphqlConfig
     * @param services List of services this HGQL endpoint should use
     * @param extraction True if the schema MUST be extracted from the services otherwise False
     * @param mappingFile mapping file
     */
    @JsonCreator
    private HGQLConfig(
            @JsonProperty("name") String name,
            @JsonProperty("schema") String schemaFile,
            @JsonProperty("server") GraphqlConfig graphqlConfig,
            @JsonProperty("services") List<ServiceConfig> services,
            @JsonProperty("extraction") Boolean extraction,
            @JsonProperty("mapping") String mappingFile,
            @JsonProperty("query") String queryFile,
            @JsonProperty("mutations") Boolean mutations,
            @JsonProperty("mutationService") String mutationService,
            @JsonProperty("prefixes") Map<String, String> prefixes
    ) {
        this.name = name;
        this.schemaFile = schemaFile;
        this.graphqlConfig = graphqlConfig;
        this.serviceConfigs = services;
        this.extraction = extraction != null? extraction : false;
        this.mappingFile = mappingFile;
        this.queryFile = queryFile;
        this.mutations = mutations;
        this.mutationService = mutationService;
        this.prefixes = prefixes;
    }

    /**
     * Getter method for attribute name. Returns the name of the HGQL endpoint.
     * @return Returns the attribute name
     */
    public String getName() {
        return name;
    }

    /**
     * Setter method to set the attribute name. The name attribute defines the name of the HGQL endpoint.
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter method for the attribute schema.
     * @return GraphQLSchema of this HGQL endpoint
     */
    public GraphQLSchema getSchema() {
        return schema;
    }

    /**
     * Setter method to set the schema attribute.
     * @param schema GraphQLSchema
     */
    public void setSchema(GraphQLSchema schema) {
        this.schema = schema;
    }
    /**
     * Setter method to set the schema attribute.
     * @param schema GraphQLSchema
     */
    @JsonIgnore
    public void setGraphQLSchema(final GraphQLSchema schema) {
        this.schema = schema;
    }

    /**
     * Getter method for the hgqlSchema attribute
     * @return HGQLSchema of this HGQL endpoint
     */
    public HGQLSchema getHgqlSchema() {
        return hgqlSchema;
    }

    /**
     * Setter method to set the hgqlSchema attribute.
     * @param schema HGQLSchema
     */
    @JsonIgnore
    public void setHgqlSchema(final HGQLSchema schema) {
        this.hgqlSchema = schema;
    }

    /**
     * Getter method for the graphqlConfig attribute.
     * @return GraphqlConfig
     */
    public GraphqlConfig getGraphqlConfig() {
        return graphqlConfig;
    }

    /**
     * Getter method for the schemaFile attribute.
     * @return Path to the file containing the schema
     */
    @JsonIgnore
    public String getSchemaFile() {
        return schemaFile;
    }

    /**
     * Getter method for the serviceConfig attribute.
     * @return List containing information about the services of this HGQL endpoint.
     */
    @JsonIgnore
    public List<ServiceConfig> getServiceConfigs() {
        return serviceConfigs;
    }

    /**
     * Getter method for the attribute extraction.
     * @return TRUE if the schema MUST be extracted, FALSE if the schema is provided in the schema file.
     */
    public Boolean getExtraction(){
        return extraction;
    }

    /**
     * Getter method for the attribute mappingFile.
     * @return Path to the file that contains the mapping information.
     */
    public String getMappingFile(){
        return mappingFile;
    }

    /**
     * Getter method for the attribute queryFile.
     * @return Path to the file that contains the schema extraction query.
     */
    public String getQueryFile(){
        return queryFile;
    }

    /**
     * Getter method for the attribute mutations. If true it is expected to generate the mutation fields.
     * @return TRUE if mutation fields SHOULD be included in the schema, FALSE if mutations are permitted for this service.
     */
    public Boolean getMutations(){
        return mutations;
    }

    /**
     * Getter method for the attribute mutationService. Represents the service id of the service on which all mutation
     * actions are executed.
     * @return service id of the of the mutation service
     */
    public String getMutationService(){
        return mutationService;
    }

    /**
     * Getter method for the attribute prefixes. The defined prefixes are used fot the name generation of schema entities
     * during the bootstrapping phase.
     * @return Prefixes to be used for the name generation. The namespace abbreviation is used as key and the namespace is the value
     */
    public Map<String, String> getPrefixes(){
        return this.prefixes;
    }
}


