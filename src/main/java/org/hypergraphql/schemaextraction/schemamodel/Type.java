package org.hypergraphql.schemaextraction.schemamodel;

import org.hypergraphql.schemaextraction.PrefixService;
import org.apache.jena.rdf.model.Resource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents a type in a HGQL Schema.
 * Normally the fields of a type are not manually added because in the building process the fields are fetched from the
 * defined interfaces. But the the fields are stores in a set which means that a double inserted field has only ones stored.
 * The build method returns this type in the SDL syntax.
 */
public class Type {
    private Resource uri;
    private String id;
    private PrefixService prefixService;
    private Set<Field> fields = new HashSet<>();
    private Set<Directive> directives = new HashSet<>();
    private Set<Interface> interfaces = new HashSet<>();
    private Set<Type> equivalentTypes = new HashSet<>();
    private String base_interface_id = "";

    public Type(Resource uri, PrefixService prefixService) {
        this.uri = uri;
        this.prefixService = prefixService;
        this.id = this.generateName();
    }

    public Resource getResource(){
        return this.uri;
    }

    public String getUri() {
        return this.uri.getURI();
    }

    public String getId() {
        return id;
    }

    public void addInterface(Interface inter) {
        this.interfaces.add(inter);
    }

    public void addEquivalentType(Type type){
        this.equivalentTypes.add(type);
    }

    /**
     * Returns the id of the interfaces of this type and of the interfaces of equivalent types.
     * The ids of interfaces of equivalent types are needed to guarantee the same results independent from the execution order.
     * @return List with interface ids
     */
    public Set<String> getEquivalentTypeInformation(){
        Set<String> res = new HashSet<>();
        this.equivalentTypes.forEach(type -> res.add(type.getBase_interface_id()));
        this.interfaces.forEach(inter -> res.add(inter.getId()));
        return res;
    }

    /**
     * Adds the given field to this type. If a field with the same name already exists in this type merge the directives.
     * @param field
     */
    public void addField(Field field){
        Optional<Field> optionalField = this.fields.stream()
                .filter(value -> value.getId().equals(field.getId()))
                .findFirst();
        if(optionalField.isPresent()){
            optionalField.get().mergeDirectives(field.getDirectives());
        }else{
            this.fields.add(field);
        }
    }
    public void addDirective(Directive directive){
        this.directives.add(directive);
    }

    public String getBase_interface_id(){
        return this.base_interface_id;
    }

    /**
     * Generates the base interface of this type and adds the interface to the interface set.
     * @return Interface of this type
     */
    public Interface generateBaseInterface(){
        Interface inter = new Interface(this.uri, this.prefixService);
        this.interfaces.add(inter);
        this.base_interface_id = inter.getId();
        return inter;
    }

    /**
     * Generate a name for the type using the name of the resource and the prefix.
     * @return Returns the name of the uri enriched with the prefix.
     */
    private String generateName(){
        String prefix = this.prefixService.getPrefix(this.uri);
        String name = this.uri.getLocalName();
        return String.format("%s_%s", prefix, name);
    }

    /**
     * Generates the SDL representation of this type including the directives, fileds and interface refrences.
     * @return Returns this type as SDL
     */
    public String build(){
        fetchFields();
        return String.format("type %s %s %s {\n \t%s\n}", this.id, buildImplements(), buildDirectives(), buildFields());
    }

    private String buildDirectives(){
        return this.directives.stream()
                .map(Directive::build)
                .collect(Collectors.joining(" "));
    }
    private String buildFields(){
        return this.fields.stream()
                .map(Field::build)
                .collect(Collectors.joining("\n\t"));
    }
    private String buildImplements(){
        String interfaces =  this.interfaces.stream()
                .map(Interface::getId)
                .collect(Collectors.joining(" & "));
        if(!interfaces.equals("")){
            return String.format("implements %s", interfaces);
        }else{
            return interfaces;
        }
    }

    /**
     * Fetches all field of this tpe from the defined interfaces.
     */
    private void fetchFields(){
        for (Interface inter : this.interfaces) {
            if (!inter.getFields().isEmpty()) {
                Set<Field> fields = inter.getFields();
                fields.forEach(this::addField);
            }
        }
    }
}
