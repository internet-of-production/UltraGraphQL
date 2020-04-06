package org.hypergraphql.schemaextraction;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.*;
import org.apache.jena.reasoner.rulesys.OWLMicroReasonerFactory;
import org.apache.jena.reasoner.rulesys.OWLMiniReasoner;
import org.apache.log4j.Logger;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.exception.HGQLConfigurationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * Coordinates the schema extraction by extracting the schema from each service separately and merging them into one
 * HGQL schema.
 */
public class ExtractionController {

    static Logger log = Logger.getLogger(ExtractionController.class.getName());
    private static final String SPARQL_ENDPOINT = "SPARQLEndpointService";
    private static final String LOCAL_RDF_MODEL = "LocalModelSPARQLService";
    private List<ServiceConfig>  serviceConfigs;
    MappingConfig mapping;
    SPARQLExtraction extractor;
    RDFtoHGQL mapper;

    public ExtractionController(List<ServiceConfig> serviceConfigs, Model mapping, String query_template){
        log.info("Start extracting the schema");
        this.serviceConfigs = serviceConfigs;
        this.mapping = new MappingConfig(mapping);
        this.extractor = new SPARQLExtraction(this.mapping,query_template);
        this.mapper = new RDFtoHGQL(this.mapping);
        extractAndMap();
    }

    /**
     * Extracts the schema from the services available in the serviceConfigs attribute and merges the schemata in one
     * HGQL schema.
     */
    private void extractAndMap(){
        for (ServiceConfig conf: serviceConfigs) {
            if(conf.getType().equals(SPARQL_ENDPOINT)){
                log.debug(String.format("Extract the schema from SPARQL endpoint %s with auth username: %s, password: %s",
                        conf.getId(),
                        conf.getUser(),
                        conf.getPassword()));
                Model serviceSchema = this.extractor.extractSchema(conf.getUrl(), conf.getUser(), conf.getPassword(), conf.getGraph());
                this.mapper.create(serviceSchema, conf.getId());
            }else if(conf.getType().equals(LOCAL_RDF_MODEL)){
                Model serviceSchema = null;
                try{
                    log.debug(String.format("Extract schema form local RDF file for service %s", conf.getId()));
                    serviceSchema = this.extractor.extractSchemaFromLocalRDFFile(conf.getFilepath(), conf.getFiletype(), conf.getGraph());
                }catch(Exception e){
                    log.error("File "+ conf.getFilepath() +" not found skip the Service "+ conf.getId());
                    continue;
                }
                serviceSchema.write(System.out);
                this.mapper.create(serviceSchema, conf.getId());
            }else{
                log.info("The Service type \""+conf.getType()+"\" is NOT supported for the schema extraction. Skip this service type.");
            }

        }
    }

    /**
     * Initiates the building of the HGQL schema from the extracted RDF schema and returns the HGQL schema as string
     * @return HGQL schema
     */
    public String getHGQLSchema(){
        String schema  = this.mapper.buildSDL();
        log.info(schema);
        return  schema;
    }

    /**
     * Initiates the building of the HGQL schema from the extracted RDF schema and returns the HGQL schema as reader
     * @return HGQL schema
     * @throws HGQLConfigurationException Thrown if the HGQL schema can not be converted from string to InputStreamReader
     */
    public Reader getHGQLSchemaReader() throws HGQLConfigurationException{
        try {
            return new InputStreamReader(IOUtils.toInputStream(this.mapper.buildSDL(), "UTF-8"));
        } catch (IOException e) {
            log.error("Could not convert HGQL schema from String to InputStreamReader");
            throw new HGQLConfigurationException("Error extracting the schema", e);
        }
    }
}
