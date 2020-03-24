package org.hypergraphql.config.schema;

import org.hypergraphql.datafetching.services.Service;

import java.util.Set;

public class QueryFieldConfig {

    private Set<Service> service;
    private String type;

    public QueryFieldConfig(Set<Service> service, String type ) {

        if (service!=null) this.service = service;
        this.type = type;

    }
    public Service service() { return this.service.iterator().next(); }
    public String type() { return this.type; }


}
