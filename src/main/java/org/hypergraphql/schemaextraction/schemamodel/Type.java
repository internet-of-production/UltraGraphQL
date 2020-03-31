package org.hypergraphql.schemaextraction.schemamodel;

import org.hypergraphql.config.schema.HGQLVocabulary;
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
    private Set<Type> sameAs = new HashSet<>();
    private String base_interface_id = "";
    private Interface base_interface = null;

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
        //ToDo: Add a directive indicating the heritage of the equivalent Type
    }

    public void addSameAsType(Type type){
        this.sameAs.add(type);
        addSchemaDirective(HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_SAMEAS, type.id);
        //ToDo: add the services of the given type to the services of this type ?????????????????
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
            //ToDo merge Outputtypes
        }else{
            this.fields.add(field);
        }
    }

    public void addDirective(String name, String parameter, Set<String> values){
        // Check if the directive is already added if so only add the parameter
        Optional<Directive> directive = this.directives.stream()
                .filter(dir -> dir.getName().equals(name))
                .findFirst();
        if(directive.isPresent()){
            directive.get().addParameter(parameter, values);
        }else{
            Directive dir = new Directive(name);
            dir.addParameter(parameter, values);
            this.directives.add(dir);
        }
    }
    public void addDirective(String name, String parameter, String value){
        // Check if the directive is already added if so only add the parameter
        Optional<Directive> directive = this.directives.stream()
                .filter(dir -> dir.getName().equals(name))
                .findFirst();
        if(directive.isPresent()){
            directive.get().addParameter(parameter, value);
        }else{
            Directive dir = new Directive(name);
            dir.addParameter(parameter, value);
            this.directives.add(dir);
        }
    }

    public void addSchemaDirective(String parameter, String value){
        Optional<Directive> direc = this.directives.stream()
                .filter(directive -> directive.getName().equals(HGQLVocabulary.HGQL_DIRECTIVE_SCHEMA))
                .findFirst();
        if(direc.isPresent()){
            //Add parameter
            direc.get().addParameter(parameter, value);
        }else{
            addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SCHEMA, parameter, value);
        }
    }

    /**
     * Add the given service id to the service directive. If the service directive is not set add one.
     * @param serviceId id of the service
     */
    public void addServiceDirective(String serviceId){
        Optional<Directive> direc = this.directives.stream()
                .filter(directive -> directive.getName().equals(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE))
                .findFirst();
        if(direc.isPresent()){
            //Add parameter
            direc.get().addParameter(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, serviceId);
        }else{
            addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE, HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, serviceId);
        }
    }

    public String getBase_interface_id(){
        return this.base_interface_id;
    }

    public Interface getBase_interface(){
        return this.base_interface;
    }

    /**
     * Generates the base interface of this type and adds the interface to the interface set.
     * @return Interface of this type
     */
    public Interface generateBaseInterface(){
        Interface inter = new Interface(this.uri, this.prefixService);
        this.interfaces.add(inter);
        this.base_interface_id = inter.getId();
        this.base_interface = inter;
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
        fetchInterfaces();
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
     * Fetches all fields of this type from the defined interfaces.
     * If an field already exists in this type merge the output types.
     */
    private void fetchFields(){
        for (Interface inter : this.interfaces) {
            if (!inter.getFields().isEmpty()) {
                Set<Field> fields = inter.getFields();
                fields.forEach(this::addField);
            }
        }
    }

    /**
     * Fetches all Interfaces of this type from the equivalent classes
     */
    private void fetchInterfaces(){
        for(Type t : this.equivalentTypes){
            if(t.getBase_interface() != null){
                this.addInterface(t.getBase_interface());
            }
        }
        for(Type t : this.sameAs){
            if(t.getBase_interface() != null){
                this.addInterface((t.getBase_interface()));
            }
        }
    }
}
