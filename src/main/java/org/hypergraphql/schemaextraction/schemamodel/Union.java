package org.hypergraphql.schemaextraction.schemamodel;

import java.util.*;
import java.util.stream.Collectors;

public class Union {

    private String name;
    private Set<Type> types;

    public Union(String name){
        this.name = name;
        this.types = new HashSet<>();
    }

    public void addType(Type type){
        this.types.add(type);
    }
    public Set<Type> getTypes(){
        return this.types;
    }

    public String getName(){
        return this.name;
    }

    /**
     * Returns the name of the unionType, but if only one type is definend then the name of this type is returned.
     * @return Name of the unionType or the type if the union only contains one type.
     */
    public String getOutputTypeName(){
        if(this.types.size() == 1){
            return this.types.iterator().next().getId();
        }else if(this.types.size() > 1){
            return this.name;
        }else{
            return "";
        }
    }

    public String build(){
        if(this.types.size() > 1){
            return String.format("union %s = %s", getName(), buildTypes());
        }else {
            return "";
        }
    }

    private String buildTypes() {
        return this.types.stream()
                .map(Type::getId)
                .collect(Collectors.joining(" | "));
    }
}
