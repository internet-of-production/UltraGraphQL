package org.hypergraphql.datamodel;

import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLNamedType;
import org.apache.jena.rdf.model.RDFNode;
import org.hypergraphql.config.schema.HGQLVocabulary;
import org.hypergraphql.config.schema.TypeConfig;
import org.hypergraphql.config.system.FetchParams;

import java.util.List;
import java.util.Map;

/**
 * DataFetchers called by the GraphQL library to fetch the data from the provided RDF Model with the results
 */
public class FetcherFactory {

    private final HGQLSchema schema;

    public FetcherFactory(HGQLSchema hgqlSchema ) {

        this.schema = hgqlSchema;
    }

    /**
     * Fetcher for the id of the given environment.
     * For Example used for the default _id field.
     * @return
     */
    public DataFetcher<String> idFetcher() {
        
        return environment -> {
            RDFNode thisNode = environment.getSource();

            if (thisNode.asResource().isURIResource()) {
                return thisNode.asResource().getURI();
            } else {
                return "_:" + thisNode.asNode().getBlankNodeLabel();
            }
        };
    }

    /**
     * Fetcher for the type of the given environment.
     * For Example used for the default _type field.
     * @param types
     * @return
     */
    public DataFetcher<String> typeFetcher(Map<String, TypeConfig> types) {
        return  environment -> {
            String typeName = ((GraphQLNamedType)environment.getParentType()).getName();   // Fix to use latest graphql version () added casting
            return (types.containsKey(typeName)) ? types.get(typeName).getId() : null;
        };
    }

    /**
     *
     * @return
     */
    public DataFetcher<List<RDFNode>> instancesOfTypeFetcher() {
        return environment -> {
            Field field = (Field) environment.getFields().toArray()[0];
            String predicate = (field.getAlias() == null) ? field.getName() : field.getAlias();   // predicate := field.name || field.alias
            ModelContainer client = environment.getContext();
            return client.getValuesOfObjectPropertyWithArgs(   // Both subject and predicate are generated in the Service object
                    HGQLVocabulary.HGQL_QUERY_URI,
                    HGQLVocabulary.HGQL_QUERY_NAMESPACE + predicate,
                    null,
                    environment.getArguments()
            );
        };
    }

    /**
     * Fetcher for retrieving the output object of a field (given environment)
     * The type of the output object is a type of the schema.
     * @return
     */
    public DataFetcher<RDFNode> objectFetcher() {
        return environment -> {
            FetchParams params = new FetchParams(environment, schema);
            return params.getClient().getValueOfObjectProperty(
                    params.getSubjectResource(),
                    params.getPredicateURI(),
                    params.getTargetURI()
            );
        };
    }

    /**
     * Fetcher for retrieving all output objects of a field (given environment).
     * The type of the output object is a type of the schema.
     * Here the OutputType of the field MUST be a list/array
     * @return
     */
    public DataFetcher<List<RDFNode>> objectsFetcher() {
        return environment -> {
            FetchParams params = new FetchParams(environment, schema);
            return params.getClient().getValuesOfObjectPropertyWithArgs(
                    params.getSubjectResource(),
                    params.getPredicateURI(),
                    params.getTargetURI(),
                    environment.getArguments()
            );
        };
    }

    /**
     * Fetcher for retrieving a scalar OutputType object of a field (given environment)
     * @return
     */
    public DataFetcher<String> literalValueFetcher() {
        return environment -> {
            FetchParams params = new FetchParams(environment, schema);
            return params.getClient().getValueOfDataProperty(
                    params.getSubjectResource(),
                    params.getPredicateURI(),
                    environment.getArguments()
            );
        };
    }

    /**
     * Fetcher for retrieving all scalar OutputType objects of a field (given environment)
     * @return
     */
    public DataFetcher<List<String>> literalValuesFetcher() {
        return environment -> {
            FetchParams params = new FetchParams(environment, schema);
            return params.getClient().getValuesOfDataPropertyWithArgs(
                    params.getSubjectResource(),
                    params.getPredicateURI(),
                    environment.getArguments()
            );
        };
    }
}
