package org.hypergraphql.config.schema;

import org.apache.jena.rdf.model.RDFNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeConfig  {

    private Set<String> sameAs;

    public String getName() {
        return name;
    }

    public String getId() {
        return this.id;
    }

    public FieldOfTypeConfig getField(String name) {
        return this.fields.get(name);
    }

    private String id;

    private String name;

    public Map<String, FieldOfTypeConfig> getFields() {
        return fields;
    }

    private Map<String, FieldOfTypeConfig> fields;

    public TypeConfig(String name, String id, Map<String, FieldOfTypeConfig> fields) {

        this.name=name;
        this.id = id;
        this.fields=fields;

    }

    public Set<String> getSameAs() {
        return sameAs;
    }

    public void setSameAs(Set<String> sameAs) {
        this.sameAs = sameAs;
    }

}
