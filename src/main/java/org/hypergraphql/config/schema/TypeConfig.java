package org.hypergraphql.config.schema;

import java.util.Map;
import java.util.Set;

public class TypeConfig  {

    public enum  TYPE {
        OBJECT ,UNION, INTERFACE
    }

    private TYPE type;

    private Map<String, TypeConfig> unionMembers; // contains either the members of a union
    private Set<String> implementedBy;

    private Set<String> sameAs;

    public String getName() {
        return name;
    }

    public String getId() {
        if(isUnion()){
            return null;
        }
        return this.id;
    }

    public FieldOfTypeConfig getField(String name) {
        if(isUnion()){
            return null;
        }
        return this.fields.get(name);
    }

    private String id;

    private String name;

    public Map<String, FieldOfTypeConfig> getFields() {
        if(isUnion()){
            return null;
        }
        return fields;
    }

    private Map<String, FieldOfTypeConfig> fields;

    public TypeConfig(String name, String id, Map<String, FieldOfTypeConfig> fields) {

        this.name=name;
        this.id = id;
        this.fields=fields;
        this.type = TYPE.OBJECT;
    }

    public TypeConfig(String name, Map<String, TypeConfig> members) {

        this.name=name;
        this.unionMembers = members;
        this.type = TYPE.UNION;
    }

    public TypeConfig(String name, Map<String, FieldOfTypeConfig> fields,Set<String> members) {

        this.name=name;
        this.fields=fields;
        this.implementedBy = members;
        this.type = TYPE.INTERFACE;
    }

    public Set<String> getSameAs() {
        return sameAs;
    }

    public void setSameAs(Set<String> sameAs) {
        this.sameAs = sameAs;
    }

    public boolean isObject(){
        return this.type == TYPE.OBJECT;
    }

    public boolean isUnion(){
        return this.type == TYPE.UNION;
    }

    public boolean isInterface(){
        return this.type == TYPE.INTERFACE;
    }

    public  Map<String, TypeConfig> getUnionMembers(){
        if (isUnion()) {
            return this.unionMembers;
        }else{
            return null;
        }
    }

    public Set<String> getInterafaceObjects(){
        if(isInterface()){
            return this.implementedBy;
        }else{
            return null;
        }
    }

}
