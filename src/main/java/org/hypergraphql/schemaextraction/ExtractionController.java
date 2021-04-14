package org.hypergraphql.schemaextraction;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hypergraphql.config.system.ServiceConfig;
import org.hypergraphql.exception.HGQLConfigurationException;

import java.io.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the schema extraction by extracting the schema from each service separately and merging them into one
 * HGQL schema.
 */
public class ExtractionController {

    static Logger LOGGER = LoggerFactory.getLogger(ExtractionController.class);
    private static final String SPARQL_ENDPOINT = "SPARQLEndpointService";
    private static final String LOCAL_RDF_MODEL = "LocalModelSPARQLService";
    private List<ServiceConfig>  serviceConfigs;
    MappingConfig mapping;
    SPARQLExtraction extractor;
    RDFtoHGQL mapper;

    public ExtractionController(List<ServiceConfig> serviceConfigs, Model mapping, String query_template){
        this(serviceConfigs, mapping, query_template, null);
    }

    public ExtractionController(List<ServiceConfig> serviceConfigs, Model mapping, String query_template, Map<String, String> namespace_prefixes){
        LOGGER.info("Start extracting the schema");
        this.serviceConfigs = serviceConfigs;
        this.mapping = new MappingConfig(mapping);
        this.extractor = new SPARQLExtraction(this.mapping,query_template);
        this.mapper = new RDFtoHGQL(this.mapping, namespace_prefixes);
        extractAndMap();
    }

    /**
     * Extracts the schema from the services available in the serviceConfigs attribute and merges the schemata in one
     * HGQL schema.
     */
    private void extractAndMap(){
        for (ServiceConfig conf: serviceConfigs) {
            if(conf.isExcludeFromExtraction()){
                continue;
            }
            if(conf.getType().equals(SPARQL_ENDPOINT)){
                LOGGER.debug(MessageFormat.format("Extract the schema from SPARQL endpoint {0} with auth username: {1}, password: {2}", conf.getId(), conf.getUser(), conf.getPassword()));
                Model serviceSchema = this.extractor.extractSchema(conf.getUrl(), conf.getUser(), conf.getPassword(), conf.getGraph());
                this.mapper.create(serviceSchema, conf.getId());
            }else if(conf.getType().equals(LOCAL_RDF_MODEL)){
                Model serviceSchema = null;
                LOGGER.debug("Extract schema form local RDF file for service " + conf.getId());
                FileInputStream fileStream = null;
                try{
                    serviceSchema = this.extractor.extractSchemaFromLocalRDFFile(conf.getFilepath(), conf.getFiletype(), conf.getGraph());
                }catch(Exception e){
                    e.printStackTrace();
                    LOGGER.error("File "+ conf.getFilepath() +" not found skip the Service "+ conf.getId());
                    continue;
                }
                this.mapper.create(serviceSchema, conf.getId());
            }else{
                LOGGER.info("The Service type \""+conf.getType()+"\" is NOT supported for the schema extraction. Skip this service type.");
            }

        }
    }

    /**
     * Initiates the building of the HGQL schema from the extracted RDF schema and returns the HGQL schema as string
     * @return HGQL schema
     */
    public String getHGQLSchema(){
        String schema  = this.mapper.buildSDL();
        LOGGER.info("Extracted HyperGraphQL schema: \n{}", schema);
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
            LOGGER.error("Could not convert HGQL schema from String to InputStreamReader");
            throw new HGQLConfigurationException("Error extracting the schema", e);
        }
    }
}
