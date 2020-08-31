package org.hypergraphql.schemaextraction.schemamodel;

import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.schemaextraction.PrefixService;
import org.apache.jena.rdf.model.Resource;
import org.hypergraphql.schemaextraction.RDFtoHGQL;

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

    public Set<Directive> getDirectives() {
        return directives;
    }

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

    /**
     * Adds the given type to the same as list of this object  and adds all services of the object to the service directive
     * Also adds the sameAs directive indicating the relation to the given type
     * @param type
     */
    public void addSameAsType(Type type){
        this.sameAs.add(type);
        addSchemaDirective(HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_SAMEAS, type.id);
        Optional<Object> services = type.getDirectives().stream()
                .filter(directive -> directive.getName().equals(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE))
                .findFirst()
                .map(directive -> directive.getParameter().get( HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID));
        if(services.isPresent()){
            if(services.get() instanceof Directive.DirectiveParameterList){
                addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE, HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, ((Directive.DirectiveParameterList)services.get()).getValues());
            }else if (services.get() instanceof  Directive.DirectiveParameter){
                addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE, HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, ((Directive.DirectiveParameter)services.get()).getValue());
            }
        }
    }
    public void addSameAsTypes(Set<Type> types){
        types.stream()
                .forEach(type -> addSameAsType(type));
    }

    public Set<Type> getSameAsTypes(){
        return this.sameAs;
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
            field.getOutputType().getTypes().stream()
                    .forEach(type -> optionalField.get().addOutputType(type)); //ToDo merge Outputtypes -> check if functions correctly

        }else{
            this.fields.add(field);
        }
    }

    /**
     * Getter function for the attribute field
     * Important: If the fields form the interfaces are not yet fetched this method only returns the directly defined fields
     * @return
     */
    public Set<Field> getFields(){
        return this.fields;
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
        if(serviceId == null){
            // object type with no service is created. Attention only suitable for schema internal objectTypes, pleas use with care.
            return;
        }
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
        return RDFtoHGQL.graphqlNameSanitation(prefix + "_" + name);
    }

    /**
     * Generates the SDL representation of this type including the directives, fileds and interface refrences.
     * @return Returns this type as SDL
     */
    public String build(){
        fetchInterfaces();
        fetchFields();
        return "type " + this.id + " " + buildImplements() + " " + buildDirectives() + " {\n \t" + buildFields() + "\n}";
    }

    private String buildDirectives(){
        return this.directives.stream()
                .map(Directive::build)
                .collect(Collectors.joining(" "));
    }
    private String buildFields(){
        return this.fields.stream()
                .map(Field::build)
                .filter(s -> !s.equals(""))
                .collect(Collectors.joining("\n\t"));
    }
    private String buildImplements(){
        final Set<Interface> interfaceSet = this.interfaces.stream()
                .filter(anInterface -> !(anInterface instanceof Union) || ((Union) anInterface).getTypes().size() > 1)   // Only implement those interfaces which have more han one type
                .collect(Collectors.toSet());
        String interfaces =  interfaceSet.stream()
                .map(Interface::getId)
                .collect(Collectors.joining(" & "));
        if(!interfaces.equals("")){
            return "implements " + interfaces;
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
                fields.forEach(field -> base_interface.addField(field));   // Add fetched fields to the interface
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
