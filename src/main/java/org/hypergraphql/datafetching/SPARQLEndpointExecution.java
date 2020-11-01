package org.hypergraphql.datafetching;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.query.*;
import org.apache.jena.riot.web.HttpOp;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.hypergraphql.datafetching.services.SPARQLEndpointService;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.converters.SPARQLServiceConverter;
import org.hypergraphql.query.pattern.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;


/**
 * This class is responsible for the execution of SPARQL queries against a remote SPARQL endpoint.
 * Intended use of this class is to execute and format the results of the given query.
 * The SPARQLEndpointService uses multiple instances of this class to execute parts of queries in parallel.
 */
public class SPARQLEndpointExecution implements Callable<SPARQLExecutionResult> {


    protected Query query;
    Set<String> inputSubset;
    Set<String> markers;
    SPARQLEndpointService sparqlEndpointService;
    protected HGQLSchema schema ;
    protected Logger LOGGER = LoggerFactory.getLogger(SPARQLEndpointExecution.class);
    String rootType;
    SPARQLServiceConverter converter;

    /**
     *
     * @param query query or sub-query to be executed
     * @param inputSubset Possible IRIs of the parent query that are used to limit the results of this query/sub-query. Should be below the defined value limit (VALUES_SIZE_LIMIT)
     * @param markers variables for the SPARQL query
     * @param sparqlEndpointService Service object with data model, query is executed on this model
     * @param schema HGQLSchema the query is based on
     * @param rootType type of the query root
     */
    public SPARQLEndpointExecution(Query query, Set<String> inputSubset, Set<String> markers, SPARQLEndpointService sparqlEndpointService, HGQLSchema schema, String rootType) {
        this.query = query;
        this.inputSubset = inputSubset;
        this.markers = markers;
        this.sparqlEndpointService = sparqlEndpointService;
        this.schema = schema;
        this.rootType=rootType;
        this.converter = new SPARQLServiceConverter(schema);
    }

    /**
     * Executes the query assigned to the object and builds-up the formatted result
     * @return Query results and IRIs for underlying queries
     */
    @Override
    public SPARQLExecutionResult call() {
        Map<String, Set<String>> resultSet = new HashMap<>();

        markers.forEach(marker -> resultSet.put(marker, new HashSet<>()));

        AtomicReference<Result> formatedResults = new AtomicReference<>();

        String sparqlQuery = converter.getSelectQuery(query, inputSubset, rootType, sparqlEndpointService.getId());
        LOGGER.debug("Execute the following SPARQL query at the service {}: \n{}",sparqlEndpointService.getId(),sparqlQuery);

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        Credentials credentials =
                new UsernamePasswordCredentials(this.sparqlEndpointService.getUser(), this.sparqlEndpointService.getPassword());
        credsProvider.setCredentials(AuthScope.ANY, credentials);
        HttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
        HttpOp.setDefaultHttpClient(httpclient);

        ARQ.init();
        org.apache.jena.query.Query jenaQuery = QueryFactory.create(sparqlQuery);

        QueryEngineHTTP qEngine = QueryExecutionFactory.createServiceRequest(this.sparqlEndpointService.getUrl(), jenaQuery);
        qEngine.setClient(httpclient);
        ResultSet results = qEngine.execSelect();
        results.forEachRemaining(solution -> {
            markers.stream()
                    .filter(solution::contains)
                    .forEach(marker -> resultSet.get(marker).add(solution.get(marker).asResource().getURI()));

            Result partialRes = this.sparqlEndpointService.getModelFromResults(query, solution, schema);
            if(formatedResults.get() == null){
                formatedResults.set(partialRes);
            }else{
                formatedResults.get().merge(partialRes);
            }
        });

        SPARQLExecutionResult sparqlExecutionResult = new SPARQLExecutionResult(resultSet, formatedResults.get());
        LOGGER.debug("Result: {}", sparqlExecutionResult);
        qEngine.close();
        if(!qEngine.isClosed()){
            qEngine.abort();
        }
        return sparqlExecutionResult;
    }

}

