package org.hypergraphql.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.*;
import graphql.language.*;
import graphql.schema.GraphQLSchema;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.datafetching.ExecutionForest;
import org.hypergraphql.datafetching.ExecutionForestFactory;
import org.hypergraphql.datafetching.services.LocalModelSPARQLService;
import org.hypergraphql.datafetching.services.SPARQLEndpointService;
import org.hypergraphql.datafetching.services.Service;
import org.hypergraphql.datafetching.services.resultmodel.ObjectResult;
import org.hypergraphql.datafetching.services.resultmodel.QueryRootResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.mutation.SPARQLMutationConverter;
import org.hypergraphql.query.ValidatedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HGQLMutationService {
    private final static Logger LOGGER = LoggerFactory.getLogger(HGQLMutationService.class);
    private final HGQLSchema hgqlSchema;
    private final GraphQLSchema schema;
    private final SPARQLMutationConverter converter;
    private final HGQLQueryService query_handler;
    private GraphQL graphql;
    private HGQLConfig config;

    public HGQLMutationService(HGQLConfig config){
        this.config = config;
        this.hgqlSchema = config.getHgqlSchema();
        this.schema = config.getSchema();
        this.converter = new SPARQLMutationConverter(this.hgqlSchema);
        this.query_handler = new HGQLQueryService(config);
        this.graphql = GraphQL.newGraphQL(config.getSchema()).build();
    }

    public Map<String, Object> results(String request, String acceptType, ValidatedQuery validatedQuery) {

        SelectionSet selectionSet = ExecutionForestFactory.selectionSet(validatedQuery.getParsedQuery());
        final List<Selection> selections = selectionSet.getSelections();
        //ToDo: Translate each given Mutation and execute each mutation for its own (possibly multiple mutations per request)
        List<String> sparql_translation = new ArrayList<>();
        List<Field> mutation_fields = new ArrayList<>();
        for(Selection selection : selections){
            String mutation = this.converter.translateMutation((Field) selection);
            LOGGER.info(mutation);
            final Service service = this.hgqlSchema.getServiceList().get(this.config.getMutationService());
            if(service instanceof LocalModelSPARQLService){
                ((LocalModelSPARQLService) service).executeUpdate(mutation);
            }else if(service instanceof SPARQLEndpointService){
                ((SPARQLEndpointService) service).executeUpdate(mutation);
            }
            //ToDo: Add a new response category "mutation" that informs about the status of the query (or in error segment)
            sparql_translation.addAll(Arrays.asList(mutation.split("\n")));
            mutation_fields.add(Field.newField()
                    .name(this.hgqlSchema.getMutationFields().get(((Field) selection).getName()))
                    .alias(((Field) selection).getAlias())
                    .comments(selection.getComments())
                    .directives(((Field) selection).getDirectives())
                    .selectionSet(((Field) selection).getSelectionSet())
                    .build());
        }

        Document mutation_selectionSets = Document.newDocument()
                .definition(OperationDefinition.newOperationDefinition()
                        .name(OperationDefinition.Operation.QUERY.toString())
                        .operation(OperationDefinition.Operation.QUERY)
                        .selectionSet(SelectionSet.newSelectionSet()
                                .selections(mutation_fields)
                                .build())
                        .build())
                .build();
        final Map<String, Object> res_selectionSet = executeSelectionSet(request, mutation_selectionSets, acceptType);
//        res_selectionSet.put("mutation", sparql_translation);
        return res_selectionSet;
    }


    Map<String, Object> executeSelectionSet(String query, Document document, String acceptType){
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<Object, Object> extensions = new HashMap<>();
        List<GraphQLError> errors = new ArrayList<>();

        result.put("errors", errors);
        result.put("extensions", extensions);

        ExecutionInput executionInput;
        ExecutionResult qlResult = null;

        ExecutionForest queryExecutionForest =
                new ExecutionForestFactory().getExecutionForest(document, hgqlSchema);

//        ModelContainer client = new ModelContainer(queryExecutionForest.generateModel());
        Result formattedResult = queryExecutionForest.generateModel();
//        ObjectMapper mapper = new ObjectMapper();
        if (acceptType == null) {
//            executionInput = ExecutionInput.newExecutionInput()
//                    .query(query)
//                    .context(client)
//                    .build();
//
//
//            qlResult = graphql.execute(executionInput);

            if(formattedResult instanceof ObjectResult || formattedResult instanceof QueryRootResult){
                Map<String, Object> json = (Map<String, Object>) (formattedResult).generateJSON();
                data.putAll(json);
            }else{
                LOGGER.error("Result of query should not be a single JSON Array");
            }
            data.put("@context", queryExecutionForest.getFullLdContext());

            if (data != null) {
                result.put("data", data);
                //ToDo: Add the error messages from the result object
//                errors.addAll(qlResult.getErrors());
            }
        } else {
            result.put("data", formattedResult.generateJSON());
        }
        //ToDo: Improve the Error build-up
        if(formattedResult.getErrors() != null && !formattedResult.getErrors().equals("")){
            final GraphQLError graphQLError = GraphqlErrorBuilder.newError()
                    .message(formattedResult.getErrors())
                    .build();
            errors.add(graphQLError);
        }

        return result;
    }
}
