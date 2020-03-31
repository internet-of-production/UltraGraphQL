package org.hypergraphql.config.schema;

import java.util.Set;

public class FieldConfig {

    private String id;
    private Set<String> sameAs;

    public FieldConfig(String id) {

        this.id = id;
    }

    public String getId() { return this.id; }

    public Set<String> getSameAs() {
        return sameAs;
    }

    public void setSameAs(Set<String> sameAs) {
        this.sameAs = sameAs;
    }
}
