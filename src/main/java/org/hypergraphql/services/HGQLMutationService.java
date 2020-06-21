package org.hypergraphql.services;

import graphql.language.Definition;
import graphql.language.Field;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.datafetching.ExecutionForestFactory;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.mutation.SPARQLMutationConverter;
import org.hypergraphql.query.ValidatedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class HGQLMutationService {
    private final static Logger LOGGER = LoggerFactory.getLogger(HGQLMutationService.class);
    private final HGQLSchema hgqlSchema;
    private final GraphQLSchema schema;
    private final SPARQLMutationConverter converter;

    public HGQLMutationService(HGQLConfig config){
        this.hgqlSchema = config.getHgqlSchema();
        this.schema = config.getSchema();
        this.converter = new SPARQLMutationConverter(this.hgqlSchema);
    }

    public Map<String, Object> results(String request, String acceptType, ValidatedQuery validatedQuery) {
        SelectionSet selectionSet = ExecutionForestFactory.selectionSet(validatedQuery.getParsedQuery());
        final List<Selection> selections = selectionSet.getSelections();
        //ToDo: Translate each given Mutation and execute each mutation for its own (possibly multiple mutations per request)
        String mutation = this.converter.translateMutation((Field) selections.get(0));
        LOGGER.info(mutation);

        //ToDo: Add a new response category "mutation" that informs about the status of the query (or in error segment)
        return null;
    }
}
