package org.hypergraphql.schemaextraction.schemamodel;

import org.apache.jena.rdf.model.Resource;
import org.hypergraphql.schemaextraction.PrefixService;
import org.hypergraphql.schemaextraction.RDFtoHGQL;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Interface {
    private Resource uri;
    private String id;
    private PrefixService prefixService;
    private Set<Field> fields = new HashSet<>();
    private final String interfacePostfix = "_Interface";

    public Interface(Resource uri, PrefixService prefixService){
        this.uri = uri;
        this.prefixService = prefixService;
        this.id = this.generateName();
    }

    public Interface(String id){
        this.id = id;
    }

    //ToDo: add a unfetched Fields list where all equivalent types are stored. if the fields of the interface are queried fetch all fields from that list until the list is empty (permanet) This fetching is recursive in the other interfaces.
    //ToDo: Maybe not necessary because of an arbitrary length match in RDFtoHGQL
    public String getId() {
        return id;
    }

    public Set<Field> getFields(){
        return fields;
    }

    public void addField(Field field){
        // if a field with the same name already exists in this type merge the directives
        Optional<Field> optionalField = this.fields.stream()
                .filter(value -> value.getId().equals(field.getId()))
                .findFirst();
        if(optionalField.isPresent()){
            optionalField.get().mergeDirectives(field.getDirectives());
        }else{
            this.fields.add(field);
        }
    }

    /**
     * Generates the SDL representation of this interface including the fields.
     * @return Returns this interface as SDL
     */
    public String build() {
        return "interface " + getId() + " {\n\t" + buildFields() + "\n}";
    }
    private String buildFields(){
        return this.fields.stream()
                .map(Field::build)
                .collect(Collectors.joining("\n\t"));
    }

    /**
     * Generate a name for the interface using the name of the resource and the prefix.
     * @return Returns the name of the uri enriched with the prefix.
     */
    private String generateName(){
        String prefix = this.prefixService.getPrefix(this.uri);
        String name = this.uri.getLocalName();
        return RDFtoHGQL.graphqlNameSanitation(prefix + "_" + name + interfacePostfix);
    }
}
