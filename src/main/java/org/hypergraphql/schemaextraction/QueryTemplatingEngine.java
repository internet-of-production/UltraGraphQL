package org.hypergraphql.schemaextraction;

import org.apache.jena.query.ParameterizedSparqlString;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * QueryTemplatingEngine obtains a template query and a mapping configuration and provides an interface to get the query
 * customized for given services. The template that is supported is described in the documentation.
 */
public class QueryTemplatingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryTemplatingEngine.class);
    private MappingConfig mapping;
    ParameterizedSparqlString template_query;
    private String query_service_template;
    private String one_class;
    private String one_property;
    private String one_domain;
    private String one_range;
    private String one_subClassOf;
    private String one_subPropertyOf;
    private String one_equivalentClass;
    private String one_equivalentProperty;
    private String one_sameAs;

    private HashMap<String, String> one_node;
    private HashMap<String,Set<String>> all_nodes;


    /**
     * Obtains a template query and a mapping configuration and initializes the template variables.
     * @param template_query SPARQL query with template variables
     * @param mapping mapping configuration
     */
    public QueryTemplatingEngine(String template_query, MappingConfig mapping){
        this.mapping = mapping;
        this.template_query  = new ParameterizedSparqlString(template_query);

        one_node = new HashMap<>();
        all_nodes = new HashMap<>();
        //class
        if(!mapping.getTypeMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_CLASS,mapping.getTypeMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_CLASSES,
                    mapping.getTypeMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //property
        if(!mapping.getFieldsMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_PROPERTY,mapping.getFieldsMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_PROPERTIES,
                    mapping.getFieldsMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //domain
        if(!mapping.getFieldAffiliationMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_DOMAIN,mapping.getFieldAffiliationMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_DOMAINS,
                    mapping.getFieldAffiliationMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //range
        if(!mapping.getOutputTypeMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_RANGE,mapping.getOutputTypeMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_RANGES,
                    mapping.getOutputTypeMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //subClassOf
        if(!mapping.getImplementsMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBCLASSOF,mapping.getImplementsMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBCLASSESOF,
                    mapping.getImplementsMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //subPropertyOf
        if(!mapping.getImpliedFieldMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBPROPERTYOF,mapping.getImpliedFieldMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBPROPERTIESOF,
                    mapping.getImpliedFieldMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //equivalentClass
        if(!mapping.getEquivalentTypeMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTCLASS,mapping.getEquivalentTypeMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTCLASSES,
                    mapping.getEquivalentTypeMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //equivalentProperty
        if(!mapping.getEquivalentFieldMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTPROPERTY,mapping.getEquivalentFieldMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTPROPERTIES,
                    mapping.getEquivalentFieldMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        //sameAs
        if(!mapping.getSameAsMapping().isEmpty()){
            one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SAMEAS,mapping.getSameAsMapping().iterator().next().toString());
            all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SAMEASES,
                    mapping.getSameAsMapping().stream()
                            .map(t->t.toString())
                            .collect(Collectors.toSet()));
        }

        prebuild();
    }

    /**
     * Replace all template variables but the service variable.
     */
    private void prebuild(){
        for(HashMap.Entry<String,String> entry : one_node.entrySet()){
            String key = entry.getKey();
            String node = entry.getValue();
            template_query.setIri(key, node);
        }
        for(HashMap.Entry<String,Set<String>> entry : all_nodes.entrySet()){
            String key = entry.getKey();
            Set<String> nodes = entry.getValue();
            String currQ = template_query.toString();

            template_query = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+key+")+\\b",alternativePath(nodes)));

        }
        this.query_service_template = template_query.toString();
    }

    /**
     * Builds a property path with the given nodes as alternative paths.
     * @param nodes
     * @return
     */
    private String alternativePath(Set<String> nodes){
        return nodes.stream()
                .map(t->"<" + t + ">")
                .collect(Collectors.joining("|"));
    }

    /**
     * Replace the service template variable with the given service URL and return the query.
     * The returned query does not contain any template variables.
     * @param service Service URL
     * @return SPARQL query
     */
    public String buildQuery(String service, String graph){
        ParameterizedSparqlString res = new ParameterizedSparqlString(query_service_template);
        res.setIri("service", service);
        if(graph != null && !graph.equals("")){
            String currQ = res.toString();
            res = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+"graph"+")+\\b","Graph <" + graph + ">"));
            currQ = res.toString();
            res = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+"from"+")+\\b","FROM NAMED <" + graph + ">"));
        }else{
            String currQ = res.toString();
            res = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+"graph"+")+\\b",""));
            currQ = res.toString();
            res = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+"from"+")+\\b",""));
        }
        LOGGER.debug("Generated Extraction Query: {}", res.toString());
        return res.toString();
    }
}
