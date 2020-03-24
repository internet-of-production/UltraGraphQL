package org.hypergraphql.config.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.datamodel.HGQLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
            @JsonProperty("query") String queryFile
    ) {
        this.name = name;
        this.schemaFile = schemaFile;
        this.graphqlConfig = graphqlConfig;
        this.serviceConfigs = services;
        this.extraction = extraction != null? extraction : false;
        this.mappingFile = mappingFile;
        this.queryFile = queryFile;
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
     * @return True if the schema MUST be extracted, FALSE if the schema is provided in the schema file.
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
}


