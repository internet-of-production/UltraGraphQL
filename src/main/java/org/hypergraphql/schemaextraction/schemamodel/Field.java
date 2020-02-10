package org.hypergraphql.schemaextraction.schemamodel;

import org.apache.jena.rdf.model.Resource;
import org.hypergraphql.schemaextraction.PrefixService;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Field {
    private Resource uri;
    private String id;
    private PrefixService prefixService;
    private String range= "";
    private Boolean isList = true;   // default
    private Boolean isNonNull = false;   // default
    private Boolean isClone = false;   // indicates if this object is a clone
    private Field baseObject = null;
    private Set<Directive> directives = new HashSet<>();

    public Field(Resource uri, PrefixService prefixService){
        this.uri = uri;
        this.prefixService = prefixService;
        this.id = this.generateName();
    }

    public Field(Field clone){
        this.isClone = true;
        this.baseObject = clone;
        this.uri = clone.uri;
        this.id = clone.id;
        this.prefixService = clone.prefixService;
        this.range = clone.range;
        this.isList = clone.isList;
        this.isNonNull = clone.isNonNull;
        this.directives = clone.directives.stream()
            .map(Directive::new)
            .collect(Collectors.toSet());
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

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
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
        if(getRange().equals("")){
            return "";
        }else{
            String res = "";
            if(isList){
                res = String.format("[%s]",getRange());
            }else {
                res = getRange();
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
        return String.format("%s_%s", prefix, name);
    }
}
