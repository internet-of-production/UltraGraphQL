package org.hypergraphql.schemaextraction.schemamodel;

import org.apache.jena.rdf.model.Resource;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.schemaextraction.PrefixService;
import org.hypergraphql.schemaextraction.RDFtoHGQL;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Field {
    private Resource uri;
    private String id;
    private PrefixService prefixService;
    private Union outputType;
    private Boolean isList = true;   // default
    private Boolean isNonNull = false;   // default
    private Boolean isClone = false;   // indicates if this object is a clone
    private Field baseObject = null;
    private Set<Directive> directives = new HashSet<>();

    public Field(Resource uri, PrefixService prefixService){
        this.uri = uri;
        this.prefixService = prefixService;
        this.id = this.generateName();
        this.outputType = new Union(String.format("%s_OutputType", this.id));
    }

    /**
     * This Constructor is used to clone a Field object. This is needed provide the same Field object to different types
     * by allowing to add type specific changes to an field such as type depending directives.
     * @param clone
     */
    public Field(Field clone){
        this.isClone = true;
        this.baseObject = clone;
        this.uri = clone.uri;
        this.id = clone.id;
        this.prefixService = clone.prefixService;
        this.outputType = clone.outputType;
        this.isList = clone.isList;
        this.isNonNull = clone.isNonNull;
        this.directives = clone.directives.stream()
            .map(Directive::new)
            .collect(Collectors.toSet());
    }

    public void addDirective(Directive directive){
        this.directives.add(directive);
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

    /**
     * Add the given service ids to the service directive. If the service directive is not set add one.
     * @param serviceIds ids of the services
     */
    public void addServiceDirective(Set<String> serviceIds){
        Optional<Directive> direc = this.directives.stream()
                .filter(directive -> directive.getName().equals(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE))
                .findFirst();
        if(direc.isPresent()){
            //Add parameter
            direc.get().addParameter(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, serviceIds);
        }else{
            addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE, HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID, serviceIds);
        }
    }

    /**
     * Returns a list off all current survices of the field
     * @return Set of service IDs
     */
    public Set<String> getServices(){
        Set<String> result = new HashSet<>();
        Optional<Directive> direc = this.directives.stream()
                .filter(directive -> directive.getName().equals(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE))
                .findFirst();
        if(direc.isPresent()){
            final Directive.Parameter parameter = direc.get().getParameter().get(HGQLVocabulary.HGQL_DIRECTIVE_SERVICE_PARAMETER_ID);
            if(parameter instanceof  Directive.DirectiveParameter){
                result.add(((Directive.DirectiveParameter) parameter).getValue());
            }else if(parameter instanceof  Directive.DirectiveParameterList){
                result.addAll(((Directive.DirectiveParameterList) parameter).getValues());
            }
        }
        return result;
    }

    public Set<Directive> getDirectives(){
        return this.directives;
    }

    public void mergeDirectives(Set<Directive> mergeDirectives){
        //ToDo: Clone directives in the merging process  (really needed?)
        mergeDirectives.forEach(mergeDir->{
            mergeDir.getParameter().values().forEach(parameter -> {
                if(parameter instanceof Directive.DirectiveParameter){
                    addDirective(mergeDir.getName(),parameter.getName(), ((Directive.DirectiveParameter) parameter).getValue());
                }else if(parameter instanceof Directive.DirectiveParameterList){
                    addDirective(mergeDir.getName(), parameter.getName(),((Directive.DirectiveParameterList) parameter).getValues());
                }
            });
        });
    }

    public String getUri() {
        return uri.getURI();
    }

    public String getId() {
        return id;
    }

    public void addOutputType(Type type){
        this.outputType.addType(type);
    }

    public Union getOutputType(){
        return this.outputType;
    }

    public String getOutputtypeName(){
        return this.outputType.getOutputTypeName();
    }

    public void setList(Boolean list) {
        isList = list;
    }

    public void setNonNull(Boolean nonNull) {
        isNonNull = nonNull;
    }

    /**
     * Generates the SDL representation of this field including outputtype and directives.
     * @return Returns this field as SDL
     */
    public String build() {
        if(!isValid()){
            // No output type is defined for this field -> Do not include the field in the object
            return "";
        }
        fetchDirectives();
        return String.format("%s: %s %s", getId(), buildOutputType(), buildDirectives());
    }

    /**
     * Fetch missing directives from the base field.
     */
    private void fetchDirectives() {
        if(isClone){
            mergeDirectives(baseObject.directives);
        }
    }

    private String buildOutputType(){
        if(getOutputtypeName().equals("")){
            return "[String]";
        }else{
            String res = getOutputtypeName();
            if(isList){
                res = String.format("[%s]",res);
            }
            if(isNonNull){
                res += "!";
            }
            return res;
        }
    }

    private String buildDirectives(){
        return this.directives.stream()
                .map(Directive::build)
                .collect(Collectors.joining(" "));
    }

    /**
     * Generate a name for the field using the name of the resource and the prefix.
     * @return Returns the name of the uri enriched with the prefix.
     */
    private String generateName(){
        String prefix = this.prefixService.getPrefix(this.uri);
        String name = this.uri.getLocalName();
        return RDFtoHGQL.graphqlNameSanitation(String.format("%s_%s", prefix, name));
    }

    /**
     * A field is valid if it has a defined output type
     * @return returns true if the field has a defined output type
     */
    public boolean isValid() {
        //Todo: Maybe extend the validation process to more features and requirements to be valid
        return true;
    }
}
