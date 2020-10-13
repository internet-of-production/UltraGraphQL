package org.hypergraphql.datafetching.services;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.query.pattern.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * The ManifoldService represents a set of multiple services. Incoming queries are forwarded to all services of this object and executed.
 * Returning results are merged and returned.
 * Note: This class is not intended to be used as a service type in the UGQL config file and will only be created internally
 *       if multiple service ids are assigned to one schema entity.
 */
public class ManifoldService extends Service {

    private final static Logger LOGGER = LoggerFactory.getLogger(ManifoldService.class);
    private Set<Service> services;
    private String level = null;

    public ManifoldService(){
        super();
    }

    public ManifoldService(ManifoldService parent, String level){
        super();
        setParameters(parent.getServices());
        setLevel(level);
    }


    /**
     * Executes the query by handing over the query to each service of this object to execute the query to then
     * merge the results of each service.
     * @param query query or sub-query to be executed
     * @param input Possible IRIs of the parent query that are used to limit the results of this query/sub-query
     * @param markers variables for the SPARQL query
     * @param rootType type of the query root
     * @param schema HGQLSchema the query is based on
     * @return Query results and IRIs for underlying queries
     */
    @Override
    public TreeExecutionResult executeQuery(Query query, Set<String> input, Set<String> markers, String rootType, HGQLSchema schema) {
        LOGGER.debug(String.format("%s: Start query execution for all services", this.getId()));
        TreeExecutionResult treeExecutionResult = new TreeExecutionResult();
        Map<String, Set<String>> resultSet = new HashMap<>();
        Model model = ModelFactory.createDefaultModel();
        Result formatedResult = null;
//        Set<Future<TreeExecutionResult>> futureResults = new HashSet<>();
        for( Service service : services){
              TreeExecutionResult res_part = service.executeQuery(query, input, markers, rootType, schema);
              res_part.getResultSet().forEach((var, uri) ->{
                  if(resultSet.get(var)== null){
                      resultSet.put(var, uri);
                  }else{
                      resultSet.get(var).addAll(uri);
                  }
              });
//              model.add(res_part.getModel());
              if(formatedResult == null){
                  formatedResult = res_part.getFormatedResult();
              }else{
                  formatedResult.merge(res_part.getFormatedResult());
              }
//            ExecutorService executor = Executors.newFixedThreadPool(5);
//            CallableService execution = new CallableService(service, query, input, strings,rootType, schema);
//            futureResults.add(executor.submit(execution));
        }
        treeExecutionResult.setFormatedResult(formatedResult);
        treeExecutionResult.setResultSet(resultSet);
        LOGGER.debug("Merge Service results");
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
                .filter(service -> service.getId().equals(id))
                .findFirst();
        return optionalService.orElse(null);
    }

    /**
     * Merge the given futureResults to one TreeExecutionResult
     * @param futureResults set of future results that should be merged
     * @param resultUnion TreeExecutionResult object to store the merged result
     */
    private void iterateFutureResults(final Set<Future<TreeExecutionResult>> futureResults, TreeExecutionResult resultUnion){
        for (Future<TreeExecutionResult> futureExecutionResult : futureResults) {
            try {
                TreeExecutionResult result = futureExecutionResult.get();
//                resultUnion.getModel().add(result.getModel());
                if(resultUnion.getFormatedResult() == null){
                    resultUnion.setFormatedResult(result.getFormatedResult());
                }else{
                    resultUnion.getFormatedResult().merge(result.getFormatedResult());
                }
                resultUnion.getResultSet().putAll(result.getResultSet());
            } catch (InterruptedException
                    | ExecutionException e) {
                e.printStackTrace();
            }
        }
        LOGGER.debug("Result:");
        LOGGER.debug(resultUnion.getResultSet().toString());
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * Wrapper class for Service classes to make the executeQuery() method Callable.
     */
    private class CallableService implements Callable<TreeExecutionResult> {

        private Service service;
        private Query query;
        private Set<String> input;
        private Set<String> strings;
        private String rootType;
        private HGQLSchema schema;


        public CallableService(Service service, Query query, Set<String> input, Set<String> strings, String rootType, HGQLSchema schema) {
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
