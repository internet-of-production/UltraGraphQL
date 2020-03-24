package org.hypergraphql.config.schema;

import graphql.schema.GraphQLOutputType;
import org.hypergraphql.datafetching.services.Service;

import java.util.Set;

public class FieldOfTypeConfig {

    public String getId() {
        return id;
    }

    public Service getService() {
        return service.iterator().next();
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

    private String id;
    private String name;
    private Set<Service> service;
    private GraphQLOutputType graphqlOutputType;
    private Boolean isList;
    private String targetName;

    public FieldOfTypeConfig(String name, String id, Set<Service> service, GraphQLOutputType graphqlOutputType, Boolean isList, String targetName) {

        this.name = name;
        this.id=id;
        this.service=service;
        this.graphqlOutputType = graphqlOutputType;
        this.targetName=targetName;
        this.isList=isList;

    }


}
