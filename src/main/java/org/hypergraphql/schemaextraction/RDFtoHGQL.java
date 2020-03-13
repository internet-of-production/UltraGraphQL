package org.hypergraphql.schemaextraction;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.schemaextraction.schemamodel.*;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RDFtoHGQL {
    private Model schema;   //Queried schema
    private Model typeMapping;
    private MappingConfig mapConfig;
    private Map<String, Type> types = new HashMap<String, Type>();
    private Map<String, Field> fields = new HashMap<String, Field>();
    private Map<String, Directive> directives = new HashMap<String, Directive>();
    private Map<String, Interface> interfaces = new HashMap<String, Interface>();
    private Map<String, Inputtype> inputtypes = new HashMap<String, Inputtype>();
    private Map<String, String> context = new HashMap<>();   // (hgql_id, uri)
    private PrefixService prefixService = new PrefixService();

    public RDFtoHGQL(Model model, Model mapModel){
        this.schema = model;
        this.mapConfig = new MappingConfig(mapModel);
    }

    public void create(){
        // Type

        Set<RDFNode> types = mapConfig.getTypeMapping();   // Get all objects that represent a type in HGQL
        Property a = this.schema.getProperty(HGQLVocabulary.RDF_TYPE);
        // For each type create a corresponding interface and type
        for (RDFNode type : types) {
            ResIterator iterator = this.schema.listSubjectsWithProperty(a, type);
            while (iterator.hasNext()){
                RDFNode next = iterator.next();
                buildType(next);
            }
        }
        System.out.print("1");

        // implements Interface (subClassOf)

        Set<Property> impls = mapConfig.getImplementsMapping();
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
        System.out.print("2");

        // Field

        Set<RDFNode> fieldMappingss = mapConfig.getFieldsMapping();   // Get all objects that represent a field in HGQL
        for (RDFNode fieldMapping : fieldMappingss) {   //iterate over all field mappings
            ResIterator iterator = this.schema.listSubjectsWithProperty(a, fieldMapping);  //fields in the schema under the current mapping object 'field'
            while (iterator.hasNext()){   //iterate over all fields in the schema under the current mapping
                RDFNode field = iterator.next();
                buildField(field);
            }
        }
        System.out.print("3");
        // implied fields

        Set<Property> impliedFieldMappings = mapConfig.getImpliedFieldMapping();   // Get all objects that represent a implied field in HGQL
        for (Property impliedFieldMapping : impliedFieldMappings) {   //iterate over all field mappings
            StmtIterator stmtIterator = this.schema.listStatements(null, impliedFieldMapping, (RDFNode) null);//fields in the schema under the current mapping object 'field'

            while (stmtIterator.hasNext()){   //iterate over all fields in the schema under the current mapping
                Statement stmt = stmtIterator.next();
                RDFNode field = stmt.getSubject();
                Field fieldObj = new Field(field.asResource(), this.prefixService);
                RDFNode impliedField = stmt.getObject();
                Set<Property> impliedFieldAffiliationMappings = mapConfig.getFieldAffiliationMapping();
                for(Property impliedFieldAffiliationMapping : impliedFieldAffiliationMappings){   //iterate over all field affiliation mappings
                    NodeIterator impliedFieldAffiliations = this.schema.listObjectsOfProperty(field.asResource(), impliedFieldAffiliationMapping);
                    while (impliedFieldAffiliations.hasNext()) {   //iterate over all field affiliations defined for the current field in the schema
                        RDFNode impliedFieldAffiliation = impliedFieldAffiliations.next();
                        Type typeObj = this.types.get(this.prefixService.getId(impliedFieldAffiliation.asResource()));
                        Set<Property> outputTypeMappings = mapConfig.getOutputTypeMapping();
                        for(Property outputTypeMapping : outputTypeMappings) {   //iterate over all outputType mappings
                            NodeIterator outputTypes = this.schema.listObjectsOfProperty(field.asResource(), outputTypeMapping);
                            while(outputTypes.hasNext()){
                                fieldObj.addOutputType(this.types.get(this.prefixService.getId(outputTypes.next().asResource())));
                                this.interfaces.get(typeObj.getBase_interface_id()).addField(fieldObj);   // add field to the base interface of the type
                                this.fields.put(fieldObj.getId(), fieldObj);
                            }
                        }
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
                        impliedFieldForType.addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SCHEMA,HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_IMPLIED_BY,fieldObj.getId());
                        this.interfaces.get(typeObj.getBase_interface_id()).addField(impliedFieldForType);
                    }
                }
            }
        }
        System.out.print("4");
        // equivalent type
        buildEquivalentTypes();
        System.out.print("5");
        // equivalent property
        buildEquivalentFields();
        System.out.print("6");

        // sameAS
        buildSameAs();
        System.out.print("7");


        // Context
        buildContext();
    }



    private String convertToSPARQLPropertyOr(Set<String> nodes){
        String res = nodes.stream()
                .collect(Collectors.joining("|"));
        return String.format("(%s)", res);
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
        }else{
            //ToDo: Handle this case
        }
    }

    private void buildField(RDFNode field){
        if(field.isResource()){
            String id = prefixService.getId(field.asResource());
            if(!this.fields.containsKey(id)){
                Set<Property> fieldAffiliationMappings = mapConfig.getFieldAffiliationMapping();
                for(Property fieldAffiliationMapping : fieldAffiliationMappings){   //iterate over all field affiliation mappings
                    NodeIterator fieldAffiliations = this.schema.listObjectsOfProperty(field.asResource(), fieldAffiliationMapping);
                    while (fieldAffiliations.hasNext()) {   //iterate over all field affiliations defined for the current field in the schema
                        RDFNode fieldAffiliation = fieldAffiliations.next();
                        Set<Property> outputTypeMappings = mapConfig.getOutputTypeMapping();
                        Type typeObj = this.types.get(this.prefixService.getId(fieldAffiliation.asResource())); // vor die for schleife legen ???
                        if(typeObj == null){
                            System.out.print("NULL");
                        }
                        Field fieldObj = new Field(field.asResource(), this.prefixService);
                        for(Property outputTypeMapping : outputTypeMappings) {   //iterate over all outputType mappings
                            NodeIterator outputTypes = this.schema.listObjectsOfProperty(field.asResource(), outputTypeMapping);   //ToDo: Handling of empty result
                            while(outputTypes.hasNext()){
                                Type outputType = this.types.get(this.prefixService.getId(outputTypes.next().asResource()));
                                fieldObj.addOutputType(outputType);
                            }
                        }
                        if(typeObj.getBase_interface_id() == null){
                            System.out.print("NULL");
                        }
                        this.interfaces.get(typeObj.getBase_interface_id()).addField(fieldObj);
                        this.fields.put(fieldObj.getId(), fieldObj);
                    }
                }
            }
        }else{
            // ToDo: Handle this case
        }
    }

    private void buildEquivalentTypes(){
        Set<Property> equivalentTypeMappings = mapConfig.getEquivalentTypeMapping();   // Get all objects that represent a equivalent type in HGQL
        String mappings = convertToSPARQLPropertyOr(equivalentTypeMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String queryString = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o }",mappings,mappings);
        // The equivalentTypeMapping is transitiv therefore the query extracts all mappings
        Query query = QueryFactory.create(queryString) ;
        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.schema)) {
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; )
            {
                QuerySolution soln = results.nextSolution() ;
                RDFNode type_a = soln.get("s") ;
                RDFNode type_b = soln.get("o") ;
                Type type_a_obj;
                Type type_b_obj;
                String id_a = this.prefixService.getId(type_a.asResource());
                String id_b = this.prefixService.getId(type_b.asResource());
                buildType(type_a); // only builds type_a if it NOT exists already
                type_a_obj = this.types.get(id_a);
                buildType(type_b); // only builds type_a if it NOT exists already
                type_b_obj = this.types.get(id_b);
                type_a_obj.addEquivalentType(type_b_obj);
            }
        }
    }

    private void buildEquivalentFields(){
        Set<Property> equivalentFieldMappings = mapConfig.getEquivalentFieldMapping();   // Get all objects that represent a equivalent field in HGQL
        String mappings = convertToSPARQLPropertyOr(equivalentFieldMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String queryString = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o }",mappings,mappings);
        // The equivalentFieldMapping is transitiv therefore the query extracts all mappings
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.schema)) {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                RDFNode field_s = soln.get("s");  //subject
                RDFNode field_o = soln.get("o");  //object
                String id_s = this.prefixService.getId(field_s.asResource());
                String id_o = this.prefixService.getId(field_o.asResource());
                buildField(field_s);
                buildField(field_o);
                Field field_s_obj = this.fields.get(id_s);
                Field field_o_obj = this.fields.get(id_o);
                field_o_obj.getOutputType().getTypes().forEach(type -> field_s_obj.addOutputType(type));   // Merge the output types of both fields
            }
        }
    }

    private void buildSameAs(){
        Set<Property> sameAsMappingMappings = mapConfig.getSameAsMapping();
        String mappings = convertToSPARQLPropertyOr(sameAsMappingMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String typeMappings = mapConfig.getTypeMapping().stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.joining(","));
        String queryStringType = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o. ?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",mappings,mappings,typeMappings,typeMappings);
        System.out.print(queryStringType);
        Query queryType = QueryFactory.create(queryStringType);
        try (QueryExecution qexec = QueryExecutionFactory.create(queryType, this.schema)) {
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; )
            {
                QuerySolution soln = results.nextSolution() ;
                RDFNode type_a = soln.get("s") ;
                RDFNode type_b = soln.get("o") ;
                Type type_a_obj;
                Type type_b_obj;
                String id_a = this.prefixService.getId(type_a.asResource());
                String id_b = this.prefixService.getId(type_b.asResource());
                buildType(type_a); // only builds type_a if it NOT exists already
                type_a_obj = this.types.get(id_a);
                buildType(type_b); // only builds type_a if it NOT exists already
                type_b_obj = this.types.get(id_b);
                type_a_obj.addSameAsType(type_b_obj);
            }
        }

        //Field sameAs
        String fieldMappings = mapConfig.getFieldsMapping().stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.joining(","));
        String queryStringField = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o.?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",mappings,mappings,fieldMappings,fieldMappings);
        try (QueryExecution qexec = QueryExecutionFactory.create(queryStringField, this.schema)) {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                RDFNode field_s = soln.get("s");  //subject
                RDFNode field_o = soln.get("o");  //object
                String id_s = this.prefixService.getId(field_s.asResource());
                String id_o = this.prefixService.getId(field_o.asResource());
                buildField(field_s);
                buildField(field_o);
                Field field_s_obj = this.fields.get(id_s);
                Field field_o_obj = this.fields.get(id_o);
                field_o_obj.getOutputType().getTypes().forEach(type -> field_s_obj.addOutputType(type));   // Merge the output types of both fields
                field_s_obj.addSchemaDirective(HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_SAMEAS, field_o_obj.getId());
            }
        }
        //ToDo: Same procedure for sameAs Properties/Fields
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
                .map(key -> String.format("\t%s:\t_@href(iri:\"%s\")", key, this.context.get(key)))
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
        // build unions - it is important that the unions are build after the types because then all fields are fetched
        String union = this.fields.values().stream()
                .map(field -> field.getOutputType().build())
                .filter(s -> !s.isEmpty())   // filterout unions that a have under two types
                .collect(Collectors.joining("\n"));
        //build inputtypes
        return String.format("%s\n%s\n%s\n%s",context,interfaces,union,types);
    }



    //ToDo: parse the queried schema into the corresponding objects

    public static void main(String[] args) throws FileNotFoundException {
        // Load mapping configuration
        Model mapModel = ModelFactory.createDefaultModel();
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        String mapInputFileName = "./src/main/resources/mapping.ttl";
        mapModel.read(new FileInputStream(mapInputFileName),null,"TTL");
        // Load Test RDF schema
        Model model = ModelFactory.createDefaultModel();
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        String inputFileName = "./src/main/java/org/hypergraphql/schemaextraction/test.ttl";
        model.read(new FileInputStream(inputFileName),null,"TTL");
        // Init Class
        model.write(System.out);
        RDFtoHGQL converter = new RDFtoHGQL(model, mapModel);
        converter.create();
        String sdl = converter.buildSDL();
        System.out.println(sdl);

    }
}
