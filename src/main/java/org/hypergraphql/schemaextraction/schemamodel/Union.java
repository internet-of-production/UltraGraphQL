package org.hypergraphql.schemaextraction.schemamodel;

import java.util.*;
import java.util.stream.Collectors;

public class Union extends Interface {   // ToDo: Rename Union to sharedFieldsInterface

    private String name;
    private Set<Type> types;

    public Union(String name){
        super(name);
        this.name = name;
        this.types = new HashSet<>();
    }

    public void addType(Type type){
        this.types.add(type);
        type.addInterface(this);
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
            return String.format("interface %s {\n %s\n}", getName(), buildTypes());
        }else {
            return "";
        }
    }

    private String buildTypes() {
        final Iterator<Type> iterator = types.iterator();
        if(iterator.hasNext()){
            Set<Field> intersection = new HashSet<Field>(iterator.next().getFields());
            while(iterator.hasNext()){
                intersection.retainAll(iterator.next().getFields());
            }
            return intersection.stream()
                    .map(Field::build)
                    .collect(Collectors.joining("\n\t"));
        }else{
            return "";
        }

    }

    /**
     * This method returns an empty Set because the sharedField Interface is a trivial Interface in the context that
     * the field of this interface are already in the type and represent a intersection of fields from types that
     * are the OutputType of an field.
     * @return
     */
    @Override
    public Set<Field> getFields() {
        return new HashSet<>();
    }
}
