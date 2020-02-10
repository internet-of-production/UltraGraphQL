package org.hypergraphql.schemaextraction;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.graalvm.compiler.nodeinfo.InputType;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.schemaextraction.schemamodel.*;
import spark.utils.IOUtils;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RDFtoHGQL {
    private Model schema;   //Queried schema
    private Model typeMapping;
    private Map<String, Type> types = new HashMap<String, Type>();
    private Map<String, Field> fields = new HashMap<String, Field>();
    private Map<String, Directive> directives = new HashMap<String, Directive>();
    private Map<String, Interface> interfaces = new HashMap<String, Interface>();
    private Map<String, Inputtype> inputtypes = new HashMap<String, Inputtype>();
    private Map<String, String> context = new HashMap<>();   // (hgql_id, uri)
    private PrefixService prefixService = new PrefixService();

    public RDFtoHGQL(Model model){
        this.schema = model;
    }

    public void create(){
        // Type

        Set<RDFNode> types = getTypesMapping();   // Get all objects that represent a type in HGQL
        Property a = this.schema.getProperty(HGQLVocabulary.RDF_TYPE);
        // For each type create a corresponding interface and type
        for (RDFNode type : types) {
            ResIterator iterator = this.schema.listSubjectsWithProperty(a, type);
            while (iterator.hasNext()){
                RDFNode next = iterator.next();
                buildType(next);
            }
        }

        // implements Interface (subClassOf)

        Set<Property> impls = getImplementsMapping();
        for(Property impl : impls){
            this.types.values().forEach(type -> {   // For each type defined in the schema check for implemented interfaces
                NodeIterator nodeIterator = this.schema.listObjectsOfProperty(type.getResource(), impl);
                while (nodeIterator.hasNext()){
                    RDFNode node = nodeIterator.next();
                    String id = this.prefixService.getId(node.asResource());
                    if(this.types.containsKey(id)){
                        // The type which 'type' implements exists
                        type.addInterface(this.interfaces.get(this.types.get(id).getBase_interface_id()));
                    }else{
                        // The type implements an interface that has no defined type in the schema
                        //ToDo: Implement a handling for this case
                    }
                }
            });
        }

        // Field

        Set<RDFNode> fieldMappingss = getFieldsMapping();   // Get all objects that represent a field in HGQL
        for (RDFNode fieldMapping : fieldMappingss) {   //iterate over all field mappings
            ResIterator iterator = this.schema.listSubjectsWithProperty(a, fieldMapping);  //fields in the schema under the current mapping object 'field'
            while (iterator.hasNext()){   //iterate over all fields in the schema under the current mapping
                RDFNode field = iterator.next();
                Set<Property> fieldAffiliationMappings = getFieldAffiliationMapping();
                for(Property fieldAffiliationMapping : fieldAffiliationMappings){   //iterate over all field affiliation mappings
                    NodeIterator fieldAffiliations = this.schema.listObjectsOfProperty(field.asResource(), fieldAffiliationMapping);
                    while (fieldAffiliations.hasNext()) {   //iterate over all field affiliations defined for the current field in the schema
                        RDFNode fieldAffiliation = fieldAffiliations.next();
                        //ToDo: What happens if multiple outputTypes are defined -> currently only handel the first found outputType
                        Set<Property> outputTypeMappings = getOutputTypenMapping();
                        for(Property outputTypeMapping : outputTypeMappings) {   //iterate over all outputType mappings
                            NodeIterator outputTypes = this.schema.listObjectsOfProperty(field.asResource(), outputTypeMapping);   //ToDo: Handling of empty result and multiple results
                            if(outputTypes.hasNext()){
                                Type typeObj = this.types.get(this.prefixService.getId(fieldAffiliation.asResource()));
                                Field fieldObj = new Field(field.asResource(), this.prefixService);
                                fieldObj.setRange(this.prefixService.getId(outputTypes.next().asResource()));
                                this.interfaces.get(typeObj.getBase_interface_id()).addField(fieldObj);
                                this.fields.put(fieldObj.getId(), fieldObj);
                            }
                        }
                    }
                }
            }
        }

        // implied fields

        Set<Property> impliedFieldMappings = getImpliedFieldMapping();   // Get all objects that represent a field in HGQL
        for (Property impliedFieldMapping : impliedFieldMappings) {   //iterate over all field mappings
            StmtIterator stmtIterator = this.schema.listStatements(null, impliedFieldMapping, (RDFNode) null);//fields in the schema under the current mapping object 'field'

            while (stmtIterator.hasNext()){   //iterate over all fields in the schema under the current mapping
                Statement stmt = stmtIterator.next();
                RDFNode field = stmt.getSubject();
                RDFNode impliedField = stmt.getObject();
                Set<Property> impliedFieldAffiliationMappings = getFieldAffiliationMapping();
                for(Property impliedFieldAffiliationMapping : impliedFieldAffiliationMappings){   //iterate over all field affiliation mappings
                    NodeIterator impliedFieldAffiliations = this.schema.listObjectsOfProperty(field.asResource(), impliedFieldAffiliationMapping);
                    while (impliedFieldAffiliations.hasNext()) {   //iterate over all field affiliations defined for the current field in the schema
                        RDFNode impliedFieldAffiliation = impliedFieldAffiliations.next();
                        //ToDo: What happens if multiple outputTypes are defined -> currently only handel the first found outputType
                        Set<Property> outputTypeMappings = getOutputTypenMapping();
                        for(Property outputTypeMapping : outputTypeMappings) {   //iterate over all outputType mappings
                            NodeIterator outputTypes = this.schema.listObjectsOfProperty(field.asResource(), outputTypeMapping);
                            if(outputTypes.hasNext()){
                                Type typeObj = this.types.get(this.prefixService.getId(impliedFieldAffiliation.asResource()));
                                Field fieldObj = new Field(field.asResource(), this.prefixService);
                                fieldObj.setRange(this.prefixService.getId(outputTypes.next().asResource()));
                                this.interfaces.get(typeObj.getBase_interface_id()).addField(fieldObj);   // add field to the base interface of the type
                                this.fields.put(fieldObj.getId(), fieldObj);
                                String id = this.prefixService.getId(impliedField.asResource());
                                Field impliedFieldObj;
                                if(this.fields.containsKey(id)){
                                    // Field already exist
                                    impliedFieldObj = new Field(this.fields.get(id));
                                }else{
                                    //Field does currently not exist create new field
                                    // Create new Field add it to fields but add a copy to the type to keep the directive only at the type
                                    impliedFieldObj = new Field(impliedField.asResource(), this.prefixService);
                                    this.fields.put(impliedFieldObj.getId(), impliedFieldObj);
                                }
                                Field impliedFieldForType = new Field(impliedFieldObj);
                                impliedFieldForType.addDirective(HGQLVocabulary.HGQL_DIRECTIVE_AUTOGENERATED,HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_IMPLIED_BY,fieldObj.getId());
                                this.interfaces.get(typeObj.getBase_interface_id()).addField(impliedFieldForType);
                            }
                        }
                    }
                }
            }
        }

        // equivalent type

        Set<Property> equivalentTypeMappings = getEquivalentTypeMapping();   // Get all objects that represent a field in HGQL
        for (Property equivalentTypeMapping : equivalentTypeMappings) {   //iterate over all field mappings
            StmtIterator stmtIterator = this.schema.listStatements(null, equivalentTypeMapping, (RDFNode) null);//fields in the schema under the current mapping object 'field'

            while (stmtIterator.hasNext()) {   //iterate over all fields in the schema under the current mapping
                Statement stmt = stmtIterator.next();
                RDFNode type_a = stmt.getSubject();
                RDFNode type_b = stmt.getObject();
                // Add the interface of type_a to type_b and vice versa
                //ToDo: Approach A: Fetch all equivalent types of an arbitrary length with an SPARQL query directly from the schema
                /**
                 * Query:
                 * {
                 *     ?a owl:equivalentClass ?b.
                 *     ?b owl:equivalentClass+ ?c .
                 *   }
                 * {
                 *     ?b owl:equivalentClass ?a.
                 *     ?a owl:equivalentClass+ ?d .
                 *   }
                 *
                 *   Then c are all types that  are equivalent to b and d stores all types that are equivalent to a.
                 */
                Type type_a_obj;
                Type type_b_obj;
                String id_a = this.prefixService.getId(type_a.asResource());
                String id_b = this.prefixService.getId(type_b.asResource());
                buildType(type_a); // only builds type_a if it NOT exists already
                type_a_obj = this.types.get(id_a);
                buildType(type_b); // only builds type_a if it NOT exists already
                type_b_obj = this.types.get(id_b);
                type_a_obj.addEquivalentType(type_b_obj);
                type_b_obj.addEquivalentType(type_a_obj);
            }
        }

        buildContext();
    }

    private Set<Property> getEquivalentTypeMapping(){
        Set<Property> res = new HashSet<>();
        res.add(this.schema.getProperty("http://www.w3.org/2002/07/owl#equivalentClass"));
        return res;
    }

    private Set<Property> getImpliedFieldMapping() {
        Set<Property> res = new HashSet<>();
        res.add(this.schema.getProperty("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"));
        return res;
    }

    private Set<Property> getOutputTypenMapping() {
        Set<Property> res = new HashSet<>();
        res.add(this.schema.getProperty("http://www.w3.org/2000/01/rdf-schema#range"));
        res.add(this.schema.getProperty("http://schema.org/rangeIncludes"));
        return res;
    }

    private Set<Property> getFieldAffiliationMapping() {
        Set<Property> res = new HashSet<>();
        res.add(this.schema.getProperty("http://www.w3.org/2000/01/rdf-schema#domain"));
        res.add(this.schema.getProperty("http://schema.org/domainIncludes"));
        return res;
    }

    private Set<RDFNode> getFieldsMapping() {
        Set<RDFNode> res = new HashSet<>();
        res.add(this.schema.getResource("http://www.w3.org/2000/01/rdf-schema#Property"));
        res.add(this.schema.getResource("http://example.org/Eigenschaft"));
        return res;
    }

    private Set<Property> getImplementsMapping() {
        Set<Property> res = new HashSet<>();
        res.add(this.schema.getProperty("http://www.w3.org/2000/01/rdf-schema#subClassOf"));
        return res;
    }

    private Set<RDFNode> getTypesMapping() {
        Set<RDFNode> res = new HashSet<>();
        res.add(this.schema.getResource("http://www.w3.org/2000/01/rdf-schema#Class"));
        res.add(this.schema.getResource("http://example.org/Klasse"));
        return res;
    }

    /**
     * Generates a type and the corresponding interface an inserts these objects into the corresponding mappings.
     * @param type URI of the type
     */
    private void buildType(RDFNode type){
        if(type.isResource()){
            String id = prefixService.getId(type.asResource());
            if(!this.types.containsKey(id)){
                // create new type and (base-)interface
                Type obj = new Type(type.asResource(), this.prefixService);
                this.types.put(id, obj);
                Interface inter = obj.generateBaseInterface();
                this.interfaces.put(inter.getId(), inter);
            }
        }
    }

    private void buildContext(){
        this.types.values().forEach(value -> this.context.put(value.getId(), value.getUri()));
        this.fields.values().forEach(value -> this.context.put(value.getId(), value.getUri()));
    }

    /**
     * NOT finished currently only for testing
     * //ToDo:
     * @return
     */
    public String buildSDL(){
        String sdl = "";
        //build context
        String context_content = this.context.keySet().stream()
                .map(key -> String.format("\t%s:\t_@href(iri:\"%s\"", key, this.context.get(key)))
                .collect(Collectors.joining("\n"));
        String context = String.format("type __context{\n%s\n}", context_content);
        //build interfaces (come with the fields)
        String interfaces = this.interfaces.values().stream()
                .map(Interface::build)
                .collect(Collectors.joining("\n"));
        //build types (come with the fields)
        String types = this.types.values().stream()
                .map(Type::build)
                .collect(Collectors.joining("\n"));
        //build inputtypes
        return String.format("%s\n%s\n%s",context,interfaces,types);
    }



    //ToDo: parse the queried schema into the corresponding objects

    public static void main(String[] args) throws FileNotFoundException {
        Model model = ModelFactory.createDefaultModel();
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        String inputFileName = "./src/main/java/org/hypergraphql/schemaextraction/test.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        model.write(System.out);

        RDFtoHGQL converter = new RDFtoHGQL(model);
        converter.create();
        String sdl = converter.buildSDL();
        System.out.println(sdl);


    }
}
