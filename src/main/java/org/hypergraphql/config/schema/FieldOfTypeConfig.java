package org.hypergraphql.config.schema;

import graphql.schema.GraphQLOutputType;
import org.hypergraphql.datafetching.services.Service;

public class FieldOfTypeConfig {

    public String getId() {
        return id;
    }

    public Service getService() {
        return service;
    }

    public GraphQLOutputType getGraphqlOutputType() {
        return graphqlOutputType;
    }

    public Boolean isList() {
        return isList;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getName() {
        return name;
    }

    private String id;   // IRI that is associated with this field
    private String name;   // name of the IRI in this schema
    private Service service;   // type of service the field is queryed with e.g. ManifoldService, SPARQLEndpointService, etc.
    private GraphQLOutputType graphqlOutputType;
    private boolean isList;
    private String targetName;

    public FieldOfTypeConfig(String name, String id, Service service, GraphQLOutputType graphqlOutputType, Boolean isList, String targetName) {

        this.name = name;
        this.id=id;
        this.service=service;
        this.graphqlOutputType = graphqlOutputType;
        this.targetName=targetName;
        this.isList=isList;

    }


}
