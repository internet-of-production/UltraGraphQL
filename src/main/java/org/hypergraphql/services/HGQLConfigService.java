package org.hypergraphql.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.datamodel.HGQLSchemaWiring;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.schemaextraction.ExtractionController;
import org.hypergraphql.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Provides methods to load the HGQL configuration and based on the configuration to parse the schema to setup the
 * needed wiring.
 */
public class HGQLConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HGQLConfigService.class);
    private static final String S3_REGEX = "(?i)^https?://s3.*\\.amazonaws\\.com/.*";
    private static final String NORMAL_URL_REGEX = "(?i)^https?://.*";

    private S3Service s3Service = new S3Service();

    /**
     * Initiates the parsing of the schema and sets up the GraphQL wiring (data fetchers). All results are saved in an
     * HGQLConfig object containing the HGQL schema, GQL schema, and the service configurations.
     * @param hgqlConfigPath configuration path
     * @param inputStream content of the configuration
     * @param classpath True if the given path is a classpath otherwise False
     * @return Returns HGQLConfig object corresponding to the given configuration
     */
    public HGQLConfig loadHGQLConfig(final String hgqlConfigPath, final InputStream inputStream, final boolean classpath) {
        return loadHGQLConfig(hgqlConfigPath, inputStream, null, null, classpath);
    }

    /**
     * Initiates the parsing of the schema and sets up the GraphQL wiring (data fetchers). All results are saved in an
     * HGQLConfig object containing the HGQL schema, GQL schema, and the service configurations.
     * @param hgqlConfigPath configuration path
     * @param inputStream content of the configuration
     * @param username username to access the configuration
     * @param password password to access the configuration
     * @param classpath True if the given path is a classpath otherwise False
     * @return Returns HGQLConfig object corresponding to the given configuration
     */
    HGQLConfig loadHGQLConfig(final String hgqlConfigPath, final InputStream inputStream, final String username, final String password, boolean classpath) {

        final ObjectMapper mapper = new ObjectMapper();

        try {

            final HGQLConfig config = mapper.readValue(inputStream, HGQLConfig.class);
            final SchemaParser schemaParser = new SchemaParser();

            final String fullSchemaPath = extractFullSchemaPath(hgqlConfigPath, config.getSchemaFile());

            LOGGER.debug("Schema config path: " + fullSchemaPath);
            if(config.getExtraction()){
                //Extract schema
                Model mapping = ModelFactory.createDefaultModel();
                mapping.read(new FileInputStream(config.getMappingFile()),null,"TTL");
                ExtractionController exractionController = new ExtractionController(config.getServiceConfigs(),
                        mapping,
                        (new FileInputStream(config.getSchemaFile()).toString()));
                exractionController.extractAndMap();

            }
            final Reader reader = selectAppropriateReader(fullSchemaPath, username, password, classpath);  // Contains the schema as character stream
            final TypeDefinitionRegistry registry = schemaParser.parse(reader);

            final HGQLSchemaWiring wiring = new HGQLSchemaWiring(registry, config.getName(), config.getServiceConfigs());
            config.setGraphQLSchema(wiring.getSchema());
            config.setHgqlSchema(wiring.getHgqlSchema());
            return config;

        } catch (IOException | URISyntaxException e) {
            throw new HGQLConfigurationException("Error reading from configuration file", e);
        }
    }

    /**
     *
     * @param schemaPath path to the schema either a URL, classpath, jar-file or UTF_8 encoded file
     * @param username username to access the schema
     * @param password password to access the schema
     * @param classpath True if the given path is a classpath otherwise False
     * @return Reader instance of the schema
     * @throws IOException Thrown if the file is inaccessible or invalid
     * @throws URISyntaxException Thrown if the syntax of the given URL is invalid
     */
    private Reader selectAppropriateReader(final String schemaPath, final String username, final String password, final boolean classpath)
            throws IOException, URISyntaxException {

        if(schemaPath.matches(S3_REGEX)) {   // check if schemaPath AWS URL

            LOGGER.debug("S3 schema");
            // create S3 bucket request, etc.
            return getReaderForS3(schemaPath, username, password);

        } else if(schemaPath.matches(NORMAL_URL_REGEX)) {   // check if schemaPath normal URL
            LOGGER.info("HTTP/S schema");
            return getReaderForUrl(schemaPath, username, password);
        } else if (schemaPath.contains(".jar!") || classpath) {  // check if schemaPath is a jar file
            LOGGER.debug("Class path schema");
            // classpath
            return getReaderForClasspath(schemaPath);
        } else {   // schemaPath MUST be in SDL
            LOGGER.debug("Filesystem schema");
            // file
            return new InputStreamReader(new FileInputStream(schemaPath), StandardCharsets.UTF_8);
        }
    }

    /**
     * Requests the schema from the given URL and returns the schema as an reader instance.
     * @param schemaPath URL to the schema - schema MUST be in the body of the HTML document
     * @param username username to access the schema
     * @param password password to access the schema
     * @return Reader instance of the HTML body of the given URL
     */
    private Reader getReaderForUrl(final String schemaPath, final String username, final String password) {

        final GetRequest getRequest;
        if(username == null && password == null) {
            getRequest = Unirest.get(schemaPath);
        } else {
            getRequest = Unirest.get(schemaPath).basicAuth(username, password);
        }

        try {
            final String body = getRequest.asString().getBody();
            return new StringReader(body);
        } catch (UnirestException e) {
            throw new HGQLConfigurationException("Unable to load configuration", e);
        }
    }

    private Reader getReaderForS3(final String schemaPath, final String username, final String password)
            throws URISyntaxException {

        final URI uri = new URI(schemaPath);
        return new InputStreamReader(s3Service.openS3Stream(uri, username, password), StandardCharsets.UTF_8);
    }

    /**
     * Extracts the full schema path by using the configuration path for the current location.
     * @param hgqlConfigPath path to the configuration file
     * @param schemaPath path to the schema from the directory of the configuration
     * @return path to the HGQL schema
     */
    private String extractFullSchemaPath(final String hgqlConfigPath, final String schemaPath) {

        LOGGER.debug("HGQL config path: {}, schema path: {}", hgqlConfigPath, schemaPath);
        final String configPath = FilenameUtils.getFullPath(hgqlConfigPath);
        if(StringUtils.isBlank(configPath)) {
            return schemaPath;
        } else {
            final String abs = PathUtils.makeAbsolute(configPath, schemaPath);
            LOGGER.debug("Absolute path: {}", abs);
            return PathUtils.makeAbsolute(configPath, schemaPath);
        }
    }

    /**
     * Returns a Reader instance of the given file.
     * @param schemaPath path to an jar file or to an classpath file
     * @return Reader instance of the given file
     */
    private Reader getReaderForClasspath(final String schemaPath) {

        LOGGER.debug("Obtaining reader for: {}", schemaPath);

        final String fn = schemaPath.contains("!")
                ? schemaPath.substring(schemaPath.lastIndexOf("!") + 1)
                : schemaPath;
        final String filename = fn.startsWith("/") ? fn.substring(fn.indexOf("/") + 1) : fn;
        LOGGER.debug("For filename: {}", filename);
        return new InputStreamReader(getClass().getClassLoader().getResourceAsStream(filename));
     }
}
