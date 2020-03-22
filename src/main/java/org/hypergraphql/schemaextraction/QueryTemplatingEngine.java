package org.hypergraphql.schemaextraction;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.ARQException;
import org.hypergraphql.config.schema.HGQLVocabulary;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class QueryTemplatingEngine {

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





    public QueryTemplatingEngine(String template_query, MappingConfig mapping){
        this.mapping = mapping;
        this.template_query  = new ParameterizedSparqlString(template_query);

        one_node = new HashMap<>();
        all_nodes = new HashMap<>();
        //class
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_CLASS,mapping.getTypeMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_CLASSES,
                mapping.getTypeMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //property
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_PROPERTY,mapping.getFieldsMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_PROPERTIES,
                mapping.getFieldsMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //domain
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_DOMAIN,mapping.getFieldAffiliationMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_DOMAINS,
                mapping.getFieldAffiliationMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //range
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_RANGE,mapping.getOutputTypeMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_RANGES,
                mapping.getOutputTypeMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //subClassOf
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBCLASSOF,mapping.getImplementsMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBCLASSESOF,
                mapping.getImplementsMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //subPropertyOf
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBPROPERTYOF,mapping.getImpliedFieldMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SUBPROPERTIESOF,
                mapping.getImpliedFieldMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //equivalentClass
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTCLASS,mapping.getEquivalentTypeMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTCLASSES,
                mapping.getEquivalentTypeMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //equivalentProperty
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTPROPERTY,mapping.getEquivalentFieldMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_EQUIVALENTPROPERTIES,
                mapping.getEquivalentFieldMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));
        //smeAs
        one_node.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SAMEAS,mapping.getSameAsMapping().iterator().next().toString());
        all_nodes.put(HGQLVocabulary.HGQL_QUERY_TEMPLATE_SAMEASES,
                mapping.getSameAsMapping().stream()
                        .map(t->t.toString())
                        .collect(Collectors.toSet()));

        prebuild();
    }

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
            //System.out.print(currQ);
            //System.out.print(currQ.replaceAll("(\\?test)+\\b",alternativePath(nodes)));
            template_query = new ParameterizedSparqlString(currQ.replaceAll("(\\?"+key+")+\\b",alternativePath(nodes)));

        }
        this.query_service_template = template_query.toString();
    }

    private String alternativePath(Set<String> nodes){
        return nodes.stream()
                .map(t->String.format("<%s>",t.toString()))
                .collect(Collectors.joining("|"));
    }

    public String buildQuery(String service){
        ParameterizedSparqlString res = new ParameterizedSparqlString(query_service_template);
        res.setIri("service", service);
        System.out.print(res.toString());
        return res.toString();
    }
}
