package org.hypergraphql.datafetching;

import com.fasterxml.jackson.databind.JsonNode;
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


public class SPARQLEndpointExecution implements Callable<SPARQLExecutionResult> {


    protected Query query;
    Set<String> inputSubset;
    Set<String> markers;
    SPARQLEndpointService sparqlEndpointService;
    protected HGQLSchema schema ;
    protected Logger LOGGER = LoggerFactory.getLogger(SPARQLEndpointExecution.class);
    String rootType;
    SPARQLServiceConverter converter;

    public SPARQLEndpointExecution(Query query, Set<String> inputSubset, Set<String> markers, SPARQLEndpointService sparqlEndpointService, HGQLSchema schema, String rootType) {
        this.query = query;
        this.inputSubset = inputSubset;
        this.markers = markers;
        this.sparqlEndpointService = sparqlEndpointService;
        this.schema = schema;
        this.rootType=rootType;
        this.converter = new SPARQLServiceConverter(schema);
    }

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

