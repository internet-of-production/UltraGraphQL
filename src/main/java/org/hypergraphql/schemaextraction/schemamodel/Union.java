package org.hypergraphql.schemaextraction.schemamodel;

import java.util.*;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.*;

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
     * Returns the name of the sharedInterface, but if only one type is definend then the name of this type is returned.
     * Is the one type the Literal objectType, then an empty set is returned.
     * @return Name of the sharedInterface or the type if the sharedInterface only contains one type. In case of type Literal or no type an empty string is returned.
     */
    public String getOutputTypeName(){
        if(this.types.size() == 1){
            if(this.types.iterator().next().getUri().equals(HGQL_SCALAR_LITERAL_URI)){
                return "";
            }else{
                return this.types.iterator().next().getId();
            }
        }else if(this.types.size() > 1){
            return this.name;
        }else{
            return "";
        }
    }

    public String build(){
        if(this.types.size() > 1){
            return "interface " + getName() + " {\n " + buildTypes() + "\n}";
        }else {
            return "";
        }
    }

    /**
     * Generates the intersection of the fields by querying all its types.
     * @return
     */
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

    public void addInterfaceToObjects(){
        this.types.stream()
                .filter(type -> !this.types.iterator().next().getId().equals(HGQL_SCALAR_LITERAL) || !this.types.isEmpty())
                .forEach(type -> type.addInterface(this));
    }
}
