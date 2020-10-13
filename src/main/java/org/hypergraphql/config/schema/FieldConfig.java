package org.hypergraphql.config.schema;

import java.util.Set;

public class FieldConfig {

    //ToDo: Replace the id of fields for this object with the actual IRI of the property instead of the virtual id identifying the field with its object.
    private String id;   // this id might not represent all occurrences of the field. Due to the generation of this object one id is kept meaning that this field (name) also occurs in other objects but this information gets lost during the building of this object. To my knowledge this id is not used anyways.
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
