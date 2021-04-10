package org.hypergraphql.datafetching.services;

import org.apache.jena.query.ARQ;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateAction;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.datafetching.LocalSPARQLExecution;
import org.hypergraphql.datafetching.SPARQLExecutionResult;
import org.hypergraphql.datafetching.TreeExecutionResult;
import org.hypergraphql.datafetching.services.resultmodel.Result;
import org.hypergraphql.datamodel.HGQLSchema;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.query.pattern.Query;
import org.hypergraphql.util.LangUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 *  This Service class is initialized with a local dataset. Queries that are executed on this service are evaluated against
 *  the data model of this object.
 *
 *  Note: In the UGQL config file local datasets are configured with the type LocalModelSPARQLService which resulting in
 *        an object of this class.
 */
public class LocalModelSPARQLService extends SPARQLEndpointService{

    private final static Logger LOGGER = LoggerFactory.getLogger(LocalModelSPARQLService.class);

    protected Dataset dataset;
    protected String filepath;
    protected String fileType;

    /**
     * Executes the given query on the dataset of this object.
     * If more IRIs are provided in input then defined in VALUES_SIZE_LIMIT as limit the values are distributed over multiple queries to sta below the limit.
     * If the amount of input is to large the resulting queries are executed in parallel.
     * @param query query or sub-query to be executed
     * @param input Possible IRIs of the parent query that are used to limit the results of this query/sub-query
     * @param markers variables for the SPARQL query
     * @param rootType type of the query root
     * @param schema HGQLSchema the query is based on
     * @return Query results and IRIs for underlying queries
     */
    @Override
    public TreeExecutionResult executeQuery(Query query, Set<String> input, Set<String> markers , String rootType , HGQLSchema schema) {

        LOGGER.debug(String.format("%s: Start query execution", this.getId()));
        Map<String, Set<String>> resultSet = new HashMap<>();
//        Model unionModel = ModelFactory.createDefaultModel();

        Set<Future<SPARQLExecutionResult>> futureSPARQLresults = new HashSet<>();

        List<String> inputList = getStrings(query, input, markers, rootType, schema, resultSet);
        List<ExecutorService> executors = new ArrayList<>();

        do {

            Set<String> inputSubset = new HashSet<>();
            if(!inputList.isEmpty()){
                int size = Math.min(inputList.size(), VALUES_SIZE_LIMIT);
                inputSubset = inputList.stream().limit(size).collect(Collectors.toSet());
                inputList = inputList.stream().skip(size).collect(Collectors.toList());
            }

            ExecutorService executor = Executors.newFixedThreadPool(50);
            executors.add(executor);
            LocalSPARQLExecution execution = new LocalSPARQLExecution(query,inputSubset,markers,this, schema , this.dataset, rootType);
            futureSPARQLresults.add(executor.submit(execution));

        } while (inputList.size()>0);

        Result result = iterateFutureResults(futureSPARQLresults, resultSet);

        TreeExecutionResult treeExecutionResult = new TreeExecutionResult();
        treeExecutionResult.setResultSet(resultSet);
//        treeExecutionResult.setModel(unionModel);
        treeExecutionResult.setFormatedResult(result);
        executors.forEach(executorService -> executorService.shutdown());

        return treeExecutionResult;
    }

    /**
     * Executes the given update and saves the updated data model in the original file.
     * ToDo: Optimize the model saving. For example only save to file periodically and at service shutdown instead of saving after each update.
     * @param update SPARQL Update
     * @return True if the update succeeds otherwise False
     */
    public Boolean executeUpdate(String update){
        try{
            UpdateAction.parseExecute(update, this.dataset);
            final File cwd = new File(".");
            try (FileOutputStream fout = new FileOutputStream(new File(cwd, this.filepath))){
                if(this.getLang() == Lang.TURTLE){
                    // Turtle does not support named graphs -> All data is located in the default graph to save it as turtle file we must only save the default graph
                    Model model = this.dataset.getDefaultModel();
                    RDFDataMgr.write(fout, model, this.getLang());
                }else{
                    RDFDataMgr.write(fout, this.dataset, this.getLang());
                }

            } catch (FileNotFoundException e) {
                throw new HGQLConfigurationException("Unable to locate local RDF file", e);
            } catch (IOException e) {
                throw new HGQLConfigurationException("Nonspecific IO exception", e);
            }
            return true;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setParameters(ServiceConfig serviceConfig) {
        super.setParameters(serviceConfig);

        ARQ.init();

        this.id = serviceConfig.getId();
        this.filepath = serviceConfig.getFilepath();
        this.fileType = serviceConfig.getFiletype();
        LOGGER.info("Current path: " + new File(".").getAbsolutePath());
        LOGGER.info(serviceConfig.getFilepath());
        Dataset dataset = RDFDataMgr.loadDataset(this.filepath, this.getLang());
        this.dataset = dataset;
    }

    private Lang getLang(){
        return LangUtils.forName(this.fileType);
    }
}