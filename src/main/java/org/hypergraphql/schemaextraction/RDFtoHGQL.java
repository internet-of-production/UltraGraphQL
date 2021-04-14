package org.hypergraphql.schemaextraction;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.schemaextraction.schemamodel.*;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_URI;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCALAR_LITERAL_VALUE_URI;

/**
 * RDFtoHGQL obtains a mapping configuration and can then be used to generate HGQL schema from RDF schemata.
 * It is possible to insert multiple RDF schemata into one HGQL schema by calling create() multiple times before the
 * buildSDL() call. To seperate different schemata use different service ids.
 */
public class RDFtoHGQL {
    private MappingConfig mapConfig;
    private Map<String, Type> types = new HashMap<String, Type>();
    private Map<String, Field> fields = new HashMap<String, Field>();
    private Map<String, Directive> directives = new HashMap<String, Directive>();
    private Map<String, Interface> interfaces = new HashMap<String, Interface>();
    private Map<String, Inputtype> inputtypes = new HashMap<String, Inputtype>();
    private Map<String, String> context = new HashMap<>();   // (hgql_id, uri)
    private PrefixService prefixService;
    private Model model = ModelFactory.createDefaultModel();

    public RDFtoHGQL(MappingConfig mappingConf){
        this(mappingConf, null);
    }
    public RDFtoHGQL(MappingConfig mappingConf, Map<String,String> namespace_prefixes){
        this.mapConfig = mappingConf;
        this.prefixService = new PrefixService(invert(namespace_prefixes));
    }

    /**
     * Given a RDF schema and a serviceId this method creates a corresponding HGQL representation.
     * If called again (with different schema) only new schema entities are added the rest is skipped.
     * Allows to insert multiple RDF schemata into one HGQL schema.
     * To get the HGQL schema call buildSDL().
     *
     * @param schema
     * @param serviceId
     */
    public void create(Model schema, String serviceId){

        this.model.add(schema);

        // add the Literal objectType as place holder for the scalar string and replacement for Literals in multiple output types.
        Resource literal = schema.getResource(HGQL_SCALAR_LITERAL_URI);
        buildType(literal,null);
        Type literal_obj = this.types.get(graphqlNameSanitation(this.prefixService.getId(literal)));
        Resource value = schema.getResource(HGQL_SCALAR_LITERAL_VALUE_URI);
        if(!literal_obj.getFields().stream().anyMatch(field -> field.getId().equals(graphqlNameSanitation(this.prefixService.getId(value))))){
            Interface literal_inter = literal_obj.getBase_interface();
            Field literal_value = new Field(value, prefixService);
            literal_value.addOutputType(literal_obj);
            literal_inter.addField(literal_value);
            this.fields.put(graphqlNameSanitation(this.prefixService.getId(value)), literal_value);
        }

        // Type

        Set<RDFNode> types = mapConfig.getTypeMapping();   // Get all objects that represent a type in HGQL
        Property a = schema.getProperty(HGQLVocabulary.RDF_TYPE);
        // For each type create a corresponding interface and type
        for (RDFNode type : types) {
            ResIterator iterator = schema.listSubjectsWithProperty(a, type);
            while (iterator.hasNext()){
                RDFNode next = iterator.next();
                buildType(next, serviceId);
            }
        }


        // implements Interface (subClassOf)

        Set<Property> impls = mapConfig.getImplementsMapping();
        Set<Property> getSubclassCain = mapConfig.getSameAsMapping();
        for(Type type : this.types.values()) {
            String mappings = convertToSPARQLPropertyOr(getSubclassCain.stream()
                    .map(property -> String.format("<%s>", property.toString()))
                    .collect(Collectors.toSet()));
            String allImplsORed = convertToSPARQLPropertyOr(impls.stream()
                    .map(property -> String.format("<%s>", property.toString()))
                    .collect(Collectors.toSet()));
            String typeMappings = mapConfig.getTypeMapping().stream()
                    .map(property -> String.format("<%s>", property.toString()))
                    .collect(Collectors.joining(","));
            String queryStringType = String.format("SELECT ?s ?o WHERE { ?s (%s)/(%s*|^%s*)* ?o. ?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",
                    allImplsORed,
                    String.join(" | ", mappings, allImplsORed),
                    mappings,
                    typeMappings,
                    typeMappings);
            Query queryType = QueryFactory.create(queryStringType);
            try (QueryExecution qexec = QueryExecutionFactory.create(queryType, this.model)) {
                ResultSet results = qexec.execSelect();
                while(results.hasNext()){
                    QuerySolution soln = results.nextSolution();
                    RDFNode type_a = soln.get("s");
                    RDFNode type_b = soln.get("o");
                    Type type_a_obj;   //subclass
                    Type type_b_obj;   //superclass
                    String id_a = graphqlNameSanitation(this.prefixService.getId(type_a.asResource()));
                    String id_b = graphqlNameSanitation(this.prefixService.getId(type_b.asResource()));
                    buildType(type_a, serviceId); // only builds type_a if it NOT exists already
                    type_a_obj = this.types.get(id_a);
                    buildType(type_b, serviceId); // only builds type_a if it NOT exists already
                    type_b_obj = this.types.get(id_b);
                    type_a_obj.addInterface(type_b_obj.getBase_interface());
                }
            }
        }


        // Field

        Set<RDFNode> fieldMappings = mapConfig.getFieldsMapping();   // Get all objects that represent a field in HGQL
        for (RDFNode fieldMapping : fieldMappings) {   //iterate over all field mappings
            ResIterator iterator = schema.listSubjectsWithProperty(a, fieldMapping);  //fields in the schema under the current mapping object 'field'
            while (iterator.hasNext()){   //iterate over all fields in the schema under the current mapping
                RDFNode field = iterator.next();
                buildField(schema, field, serviceId);
            }
        }

        // implied fields
        Set<Property> impliedFieldMappings = mapConfig.getImpliedFieldMapping();   // Get all objects that represent a implied field in HGQL
        Set<Property> getSubpropertyCain = mapConfig.getSameAsMapping();
        String mappings = convertToSPARQLPropertyOr(getSubpropertyCain.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String allImpliedFieldsORed = convertToSPARQLPropertyOr(impliedFieldMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String filterFieldMappings = mapConfig.getFieldsMapping().stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.joining(","));
        String queryStringField = String.format("SELECT ?s ?o WHERE { ?s (%s)/(%s*|^%s*)* ?o.?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",
                allImpliedFieldsORed,
                String.join(" | ", mappings, allImpliedFieldsORed),
                mappings,
                filterFieldMappings,
                filterFieldMappings);
        try (QueryExecution qexec = QueryExecutionFactory.create(queryStringField, this.model)) {
            ResultSet results = qexec.execSelect();
            while(results.hasNext()){
                QuerySolution soln = results.nextSolution();
                RDFNode field_s = soln.get("s");  //subproperty
                RDFNode field_o = soln.get("o");  //superproperty
                buildField(schema, field_s, field_o, serviceId);
            }
        }

        // equivalent type
        buildEquivalentTypes(schema, serviceId);

        // equivalent property
        buildEquivalentFields(schema, serviceId);

        // sameAS
        buildSameAs(schema, serviceId);
    }


    /**
     * Converts a set of RDFNodes(as string) to one string containing the nodes as alternative path.
     * The reurned string is intended to be used in a property path query.
     * @param nodes RDFNodes
     * @return Given nodes as alternative paths
     */
    private String convertToSPARQLPropertyOr(Set<String> nodes){
        String res = String.join("|", nodes);
        return String.format("(%s)", res);
    }

    /**
     * Generates a type and the corresponding interface an inserts these objects into the corresponding mappings.
     * @param type URI of the type
     * @param serviceId id of the service
     */
    private void buildType(RDFNode type, String serviceId){
        if(type.isResource()){
            String id = graphqlNameSanitation(prefixService.getId(type.asResource()));
            if(!this.types.containsKey(id)){
                // create new type and (base-)interface
                Type obj = new Type(type.asResource(), this.prefixService);
                obj.addServiceDirective(serviceId);
                this.types.put(id, obj);
                Interface inter = obj.generateBaseInterface();
                this.interfaces.put(inter.getId(), inter);
            }else{   // if the type already exists only add the service id to the directive
                this.types.get(id).addServiceDirective(serviceId);
            }
        }else{
            //ToDo: Handle this case
        }
    }


    /**
     * Check if the given field is already in the HGQL schema. If not in the schema add the field otherwise only add the
     * serviceId and merge output types and domains of the field.
     * @param schema RDF schema
     * @param field RDFNode that represents a field
     * @param serviceId Id of the service the given schema belongs to
     */
    private void buildField(Model schema, RDFNode field, String serviceId){
        buildField(schema, field, null, serviceId);
    }

    /**
     * Check if the given field or impliedField is already in the HGQL schema. If not in the schema add the field
     * otherwise only add the serviceId and merge output types and domains of the field.
     * @param schema RDF schema
     * @param field RDFNode that represents a field
     * @param impliedField field that is implied through the given field
     * @param serviceId Id of the service the given schema belongs to
     */
    private void buildField(Model schema, RDFNode field, RDFNode impliedField, String serviceId){
        if(field.isResource()){
            String id = graphqlNameSanitation(prefixService.getId(field.asResource()));
            Set<Property> fieldAffiliationMappings = mapConfig.getFieldAffiliationMapping();
            for(Property fieldAffiliationMapping : fieldAffiliationMappings){   //iterate over all field affiliation mappings
                NodeIterator fieldAffiliations = schema.listObjectsOfProperty(field.asResource(), fieldAffiliationMapping);
                while (fieldAffiliations.hasNext()) {   //iterate over all field affiliations defined for the current field in the schema
                    RDFNode fieldAffiliation = fieldAffiliations.next();
                    Set<Property> outputTypeMappings = mapConfig.getOutputTypeMapping();
                    Type typeObj = this.types.get(graphqlNameSanitation(this.prefixService.getId(fieldAffiliation.asResource()))); // vor die for schleife legen ???
                    if(typeObj == null){
                        System.out.print("NULL");
                    }
                    Field fieldObj = null;
                    if(!this.fields.containsKey(id)) {
                        fieldObj = new Field(field.asResource(), this.prefixService);
                        this.fields.put(fieldObj.getId(), fieldObj);
                    }else{
                        //field is already in the schema add the service and merge domain and range
                        fieldObj = this.fields.get(id);
                        fieldObj.addServiceDirective(serviceId);
                    }
                    fieldObj.addServiceDirective(serviceId);
                    for(Property outputTypeMapping : outputTypeMappings) {   //iterate over all outputType mappings
                        NodeIterator outputTypes = schema.listObjectsOfProperty(field.asResource(), outputTypeMapping);   //ToDo: Handling of empty result
                        while(outputTypes.hasNext()){
                            Type outputType = this.types.get(graphqlNameSanitation(this.prefixService.getId(outputTypes.next().asResource())));
                            fieldObj.addOutputType(outputType);
                        }
                    }
                    if(typeObj.getBase_interface_id() == null){
                        System.out.print("NULL");  //Todo: Properly handle this case
                    }
                    this.interfaces.get(typeObj.getBase_interface_id()).addField(fieldObj);

                    if(impliedField != null){
                        String id_ofImpliedField = graphqlNameSanitation(this.prefixService.getId(impliedField.asResource()));
                        Field impliedFieldObj;
                        if(this.fields.containsKey(id_ofImpliedField)){
                            // Field already exist create clone to separate object specific directives
                            impliedFieldObj = new Field(this.fields.get(id_ofImpliedField));
                        }else{
                            //Field does currently not exist create new field
                            // Create new Field add it to fields but add a copy to the field to keep the directive only at the field
                            impliedFieldObj = new Field(impliedField.asResource(), this.prefixService);
                            this.fields.put(impliedFieldObj.getId(), impliedFieldObj);
                        }
                        Field impliedFieldForType = new Field(impliedFieldObj);
                        fieldObj.getOutputType().getTypes().stream()
                                .forEach(type -> impliedFieldForType.addOutputType(type));   //ToDo: Add the outputtypes of field to the implied field Check if functions correctly
                        //ToDo: Add the output types to all fields in the chain: a->b then query for   b (subPropertyOf| equivalentProperty| sameAs)* c and add the output type to all c
                        impliedFieldForType.addServiceDirective(serviceId);
                        impliedFieldForType.addDirective(HGQLVocabulary.HGQL_DIRECTIVE_SCHEMA,HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_IMPLIED_BY,fieldObj.getId());
                        this.interfaces.get(typeObj.getBase_interface_id()).addField(impliedFieldForType);
                    }
                }
            }
        }else{
            // ToDo: Handle this case
        }
    }

    /**
     * Map the equivalentTypes relations from the given schema into the HGQL schema of this object.
     * The serviceId is added to all field sand types that are interlinked with this relation.
     * @param schema RDF schema containing potential information regarding equivalentTypes relations
     * @param serviceId Id of the service the schema belongs to.
     */
    private void buildEquivalentTypes(Model schema, String serviceId){
        Set<Property> equivalentTypeMappings = mapConfig.getEquivalentTypeMapping();   // Get all objects that represent a equivalent type in HGQL
        if(equivalentTypeMappings.isEmpty()){
            return;
        }
        String mappings = convertToSPARQLPropertyOr(equivalentTypeMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String queryString = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o }",mappings,mappings);
        // The equivalentTypeMapping is transitiv therefore the query extracts all mappings
        Query query = QueryFactory.create(queryString) ;
        try (QueryExecution qexec = QueryExecutionFactory.create(query, schema)) {
            ResultSet results = qexec.execSelect() ;
            while(results.hasNext()){
                QuerySolution soln = results.nextSolution();
                RDFNode type_a = soln.get("s") ;
                RDFNode type_b = soln.get("o") ;
                Type type_a_obj;
                Type type_b_obj;
                String id_a = graphqlNameSanitation(this.prefixService.getId(type_a.asResource()));
                String id_b = graphqlNameSanitation(this.prefixService.getId(type_b.asResource()));
                buildType(type_a, serviceId); // only builds type_a if it NOT exists already
                type_a_obj = this.types.get(id_a);
                buildType(type_b, serviceId); // only builds type_a if it NOT exists already
                type_b_obj = this.types.get(id_b);
                type_a_obj.addEquivalentType(type_b_obj);
            }
        }
    }

    /**
     * Map the equivalentFields relations from the given schema into the HGQL schema of this object.
     * The serviceId is added to all field sand types that are interlinked with this relation.
     * @param schema RDF schema containing potential information regarding equivalentFields relations
     * @param serviceId Id of the service the schema belongs to.
     */
    private void buildEquivalentFields(Model schema, String serviceId){
        Set<Property> equivalentFieldMappings = mapConfig.getEquivalentFieldMapping();   // Get all objects that represent a equivalent field in HGQL
        if(equivalentFieldMappings.isEmpty()){
            return;
        }
        String mappings = convertToSPARQLPropertyOr(equivalentFieldMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String queryString = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o }",mappings,mappings);
        // The equivalentFieldMapping is transitiv therefore the query extracts all mappings
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, schema)) {
            ResultSet results = qexec.execSelect();
            while(results.hasNext()){
                QuerySolution soln = results.nextSolution();
                RDFNode field_s = soln.get("s");  //subject
                RDFNode field_o = soln.get("o");  //object
                String id_s = graphqlNameSanitation(this.prefixService.getId(field_s.asResource()));
                String id_o = graphqlNameSanitation(this.prefixService.getId(field_o.asResource()));
                buildField(schema, field_s, serviceId);
                buildField(schema, field_o, serviceId);
                Field field_s_obj = this.fields.get(id_s);
                Field field_o_obj = this.fields.get(id_o);
                field_o_obj.getOutputType().getTypes().forEach(type -> field_s_obj.addOutputType(type));   // Merge the output types of both fields
            }
        }
    }

    /**
     * Map sameAs relations from the given schema into the HGQL schema of this object.
     * The sameAs relation is handled for types and fields. The serviceId is added to all field sand types that are
     * interlinked with this relation.
     * @param schema RDF schema containing potential information regarding sameAs relations
     * @param serviceId Id of the service the schema belongs to.
     */
    private void buildSameAs(Model schema, String serviceId){
        Set<Property> sameAsMappingMappings = mapConfig.getSameAsMapping();
        if(sameAsMappingMappings.isEmpty()){
            return;
        }
        String mappings = convertToSPARQLPropertyOr(sameAsMappingMappings.stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.toSet()));
        String typeMappings = mapConfig.getTypeMapping().stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.joining(","));
        String queryStringType = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o. ?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",
                mappings,
                mappings,
                typeMappings,
                typeMappings);
        Query queryType = QueryFactory.create(queryStringType);
        try (QueryExecution qexec = QueryExecutionFactory.create(queryType, this.model)) {
            ResultSet results = qexec.execSelect() ;
            while(results.hasNext()){
                QuerySolution soln = results.nextSolution();
                RDFNode type_a = soln.get("s") ;
                RDFNode type_b = soln.get("o") ;
                Type type_a_obj;
                Type type_b_obj;
                String id_a = graphqlNameSanitation(this.prefixService.getId(type_a.asResource()));
                String id_b = graphqlNameSanitation(this.prefixService.getId(type_b.asResource()));
//                buildType(type_a, serviceId); // only builds type_a if it NOT exists already  -> Maybe problem with service id
                type_a_obj = this.types.get(id_a);
//                buildType(type_b, serviceId); // only builds type_a if it NOT exists already
                type_b_obj = this.types.get(id_b);
                // Add all sameAs types of A to be and vice versa, they may come form different services and do not know of each other
                //ToDo: May xchange after the RDF schema merging for multiple services
                type_a_obj.addSameAsType(type_b_obj);
                Set<Type> sameAs_of_a = type_a_obj.getSameAsTypes();
                type_a_obj.addSameAsTypes(type_b_obj.getSameAsTypes());
                type_b_obj.addSameAsType(type_a_obj);   // Add all sameAs types of
                type_b_obj.addSameAsTypes(sameAs_of_a);
            }
        }

        //Field sameAs
        String fieldMappings = mapConfig.getFieldsMapping().stream()
                .map(property -> String.format("<%s>", property.toString()))
                .collect(Collectors.joining(","));
        String queryStringField = String.format("SELECT ?s ?o WHERE { ?s %s+|^%s+ ?o.?s a ?c1. ?o a ?c2. FILTER(?c1 IN ( %s )) FILTER(?c2 IN ( %s ))}",
                mappings,
                mappings,
                fieldMappings,
                fieldMappings);
        try (QueryExecution qexec = QueryExecutionFactory.create(queryStringField, this.model)) {
            ResultSet results = qexec.execSelect();
            while(results.hasNext()){
                QuerySolution soln = results.nextSolution();
                RDFNode field_s = soln.get("s");  //subject
                RDFNode field_o = soln.get("o");  //object
                String id_s = graphqlNameSanitation(this.prefixService.getId(field_s.asResource()));
                String id_o = graphqlNameSanitation(this.prefixService.getId(field_o.asResource()));
                buildField(schema, field_s, serviceId);
                buildField(schema, field_o, serviceId);
                Field field_s_obj = this.fields.get(id_s);
                Field field_o_obj = this.fields.get(id_o);
                if(field_o_obj == null  || field_s_obj == null){
                    System.out.println("It is NULL");
                    continue;
                }else if(!field_o_obj.getOutputType().getTypes().isEmpty()){
                    field_o_obj.getOutputType().getTypes().forEach(type -> field_s_obj.addOutputType(type));   // Merge the output types of both fields
                }else{
                    System.out.println("It is Empty");
                }
                field_s_obj.addSchemaDirective(HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_SAMEAS, field_o_obj.getId());
                field_o_obj.addSchemaDirective(HGQLVocabulary.HGQL_DIRECTIVE_PARAMETER_SAMEAS, field_s_obj.getId());
                field_s_obj.addServiceDirective(field_o_obj.getServices());
                field_o_obj.addServiceDirective(field_s_obj.getServices());
                //ToDo: Also add all other same as fields mutually
            }
        }
    }

    /**
     * Generates the HGQL context object type out of the types and fields.
     */
    private void buildContext(){
        this.types.values().forEach(value -> this.context.put(value.getId(), value.getUri()));
        this.fields.values().stream()
                .filter(field -> field.isValid())
                .forEach(value -> this.context.put(value.getId(), value.getUri()));
    }

    /**
     * Builds a SDL representation of the HGQL schema and returns it as string.
     * The order of the schema elements is context, interfaces, unions, types.
     * @return HGQL schema
     */
    public String buildSDL(){
        finalRelations();

        // Context
        buildContext();
        String context_content = this.context.keySet().stream()
                .map(key -> "\t" + key + ":\t_@href(iri:\"" + this.context.get(key) + "\")")
                .collect(Collectors.joining("\n"));
        String context = "type __Context{\n" + context_content + "\n}";
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
        return context + "\n" + interfaces + "\n" + union + "\n" + types;
    }

    private void finalRelations(){
        this.fields.values().stream()
                .forEach(field -> field.getOutputType().addInterfaceToObjects());
    }


    public static String graphqlNameSanitation(String name){
        name = name.replace("ü","ue");
        name = name.replace("Ü","Ue");
        name = name.replace("ö","oe");
        name = name.replace("Ö","Oe");
        name = name.replace("ä","ae");
        name = name.replace("Ä","Ae");
        name = name.replace("ß","ss");
        name = name.replace("@","at");
        return name.replaceAll("[^a-zA-Z0-9_]","");
    }

    /**
     * Inverts the given map by making the key to the new value and vice versa.
     * If the given map is null, null is returned.
     * Example:
     *    1->"Bob"
     *    2->"Alice"
     *
     *    is converted do
     *
     *    "Bob"->1
     *    "Alice"->2
     * @param map Map the inversion is performed on
     * @param <V> Datatype of the key
     * @param <K> Datatype of the value
     * @return Returns the inversion of the given map
     */
    public static <V, K> Map<V, K> invert(Map<K, V> map) {
        if(map == null){
            return null;
        }
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }


}
