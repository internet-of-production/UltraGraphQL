package org.hypergraphql.datafetching.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.SPARQLEndpointExecution;
import org.hypergraphql.datafetching.SPARQLExecutionResult;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datamodel.HGQLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ManifoldService extends Service {

    private final static Logger LOGGER = LoggerFactory.getLogger(ManifoldService.class);
    private Set<Service> services;


    /**
     * Executes the query by handing over the query to each service of this object to execute the query and  then the
     * merging the results of each service are merged together.
     * @param query JSON representation of a graphql query
     * @param input
     * @param strings
     * @param rootType
     * @param schema
     * @return
     */
    @Override
    public TreeExecutionResult executeQuery(JsonNode query, Set<String> input, Set<String> strings, String rootType, HGQLSchema schema) {
        LOGGER.debug(String.format("%s: Start query execution for all services", this.getId()));
        TreeExecutionResult treeExecutionResult = new TreeExecutionResult();
        Map<String, Set<String>> resultSet = new HashMap<>();
        Model model = ModelFactory.createDefaultModel();
//        Set<Future<TreeExecutionResult>> futureResults = new HashSet<>();
        for( Service service : services){
              TreeExecutionResult res_part = service.executeQuery(query, input, strings, rootType, schema);
              res_part.getResultSet().forEach((var, uri) ->
                      resultSet.merge(var, uri,(strings1, strings2) -> {
                          HashSet set = new HashSet<String>();
                          set.addAll(strings1);
                          set.addAll(strings2);
                          return set;
                      }));
              model.add(res_part.getModel());
//            ExecutorService executor = Executors.newFixedThreadPool(5);
//            CallableService execution = new CallableService(service, query, input, strings,rootType, schema);
//            futureResults.add(executor.submit(execution));
        }
        treeExecutionResult.setModel(model);
        treeExecutionResult.setResultSet(resultSet);
        LOGGER.info(String.format("Merge Service results"));
//        iterateFutureResultsx(futureResults, treeExecutionResult);
        return treeExecutionResult;
    }

    @Override
    public void setParameters(ServiceConfig serviceConfig) {
        LOGGER.warn("This method should not be used to set parameters.");
    }

    /**
     * Setup method for the class.
     * ATTENTION: It is possible that a ManifoldService contains another ManifoldService this behavior can lead to infinite
     *            recursive executeQuery() calls if there is a circle in through the services.
     * @param services Set of services this service queries
     */
    public void setParameters(Set<Service> services){
        this.services = services;
        this.id = generateId(services);
    }

    /**
     * Generates a ManifoldService id for the given set of services.
     * Format of the id: Any ManifoldService begins with the prefix "manifoldService_" and then the ids of the services
     * are appended to this string in increasing order separated by the infix "_"
     * @param services Set of services
     * @return id of the ManifoldService that has the given set of services
     */
    private static String generateId(Set<Service> services) {
        String id = "manifoldService_";
        id += services.stream()
                .sorted(Comparator.comparing(Service::getId))
                .map(Service::getId)
                .collect(Collectors.joining("_"));
        return id;
    }

    /**
     * Getter method for the set of services stored in this object.
     * @return set of services
     */
    public Set<Service> getServices(){
        return services;
    }

    /**
     * Getter method to get a service by id.
     * @param id id of the requested service
     * @return Service that has the given id or null if service not present
     */
    public Service getService(String id){
        Optional<Service> optionalService = this.services.stream()
                .filter(service -> service.getId() == id)
                .findFirst();
        return optionalService.orElse(null);
    }

    /**
     * Merge the given futureResults to one TreeExecutionResult
     * @param futureResults set of future results that should be merged
     * @param resultUnion TreeExecutionResult object to store the merged result
     */
    private void iterateFutureResultsx(final Set<Future<TreeExecutionResult>> futureResults, TreeExecutionResult resultUnion){
        for (Future<TreeExecutionResult> futureExecutionResult : futureResults) {
            try {
                TreeExecutionResult result = futureExecutionResult.get();
                resultUnion.getModel().add(result.getModel());
                resultUnion.getResultSet().putAll(result.getResultSet());
            } catch (InterruptedException
                    | ExecutionException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("Result:");
        LOGGER.info(resultUnion.getResultSet().toString());
    }

    /**
     * Wrapper class for Service classes to make the executeQuery() method Callable.
     */
    private class CallableService implements Callable<TreeExecutionResult> {

        private Service service;
        private JsonNode query;
        private Set<String> input;
        private Set<String> strings;
        private String rootType;
        private HGQLSchema schema;


        public CallableService(Service service, JsonNode query, Set<String> input, Set<String> strings, String rootType, HGQLSchema schema) {
            this.service = service;
            this.query = query;
            this.input = input;
            this.strings = strings;
            this.rootType = rootType;
            this.schema = schema;
        }


        @Override
        public TreeExecutionResult call() throws Exception {
            return this.service.executeQuery(query,input,strings,rootType,schema);
        }
    }
}
