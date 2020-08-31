package org.hypergraphql.services;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import org.apache.commons.io.FilenameUtils;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ApplicationConfigurationService {

    private S3Service s3Service;
    private HGQLConfigService hgqlConfigService = new HGQLConfigService();

    private final static Logger LOGGER = LoggerFactory.getLogger(ApplicationConfigurationService.class);

    public List<HGQLConfig> readConfigurationFromS3(final String configUri, final String username, final String password) {

        final URI uri;
        try {
            uri = new URI(configUri);
        } catch (URISyntaxException e) {
            throw new HGQLConfigurationException("Invalid S3 URL", e);
        }

        if(s3Service == null) {
            s3Service = new S3Service();
        }

        final InputStream inputStream = s3Service.openS3Stream(uri, username, password);
        final HGQLConfig config = hgqlConfigService.loadHGQLConfig(configUri, inputStream, username, password, false);

        return Collections.singletonList(config);
    }

    /**
     * Generates a List of HGQLConfigs from the given configuration URL.
     * @param configUri HTML page with the configuration information in the body
     * @param username username to access the given URI
     * @param password password to access the given URI
     * @return List of HGQLConfigs
     */
    public List<HGQLConfig> readConfigurationFromUrl(final String configUri, final String username, final String password) {

        final GetRequest getRequest;
        if(username == null && password == null) {   // Assume that NO authentication is needed
            getRequest = Unirest.get(configUri);
        } else {   // Auth needed
            getRequest = Unirest.get(configUri).basicAuth(username, password);
        }

        try {
            final InputStream inputStream = getRequest.asBinary().getBody();
            final HGQLConfig config = hgqlConfigService.loadHGQLConfig(configUri, inputStream, username, password, false);
            inputStream.close();
            return Collections.singletonList(config);
        } catch (UnirestException | IOException e) {
            throw new HGQLConfigurationException("Unable to read from remote URL", e);
        }
    }

    /**
     * Generates for any given configuration file a corresponding list with HGQLConfigs and merges these lists together.
     * @param configPathStrings List of configuration files OR directories containing config files. Config files MUST
     *                          be of type JSON.
     * @return List of HGQLConfigs
     */
    public List<HGQLConfig> getConfigFiles(final String ... configPathStrings) {

        final List<HGQLConfig> configFiles = new ArrayList<>();
        if(configPathStrings != null) {
            Arrays.stream(configPathStrings).forEach(configPathString ->
                    configFiles.addAll(getConfigurationsFromFile(configPathString)));
        }
        return configFiles;
    }

    /**
     * Generates for given configuration file a corresponding list with HGQLConfigs.
     * @param configPathString Configuration file OR directory containing config files. Config files MUST
     *                         be of type JSON.
     * @return List of HGQLConfigs
     */
    List<HGQLConfig> getConfigurationsFromFile(final String configPathString) {
        System.out.print(configPathString);
        final File configPath = new File(configPathString); // it always has this
        final List<HGQLConfig> configurations = new ArrayList<>();
        try {
            if (configPath.isDirectory()) {
                LOGGER.debug("Directory");
                final File[] jsonFiles = configPath.listFiles(pathname ->
                        FilenameUtils.isExtension(pathname.getName(), "json"));
                if (jsonFiles != null) {
                    //Add all HGQLConfigs generated from the config files to the configurations list
                    Arrays.stream(jsonFiles).forEach(file -> {
                        final String path = file.getAbsolutePath();
                        try (InputStream in = new FileInputStream(file)) {
                            configurations.add(hgqlConfigService.loadHGQLConfig(path, in, false));
                        } catch (FileNotFoundException e) {
                            throw new HGQLConfigurationException("One or more config files not found", e);
                        } catch (IOException e) {
                            throw new HGQLConfigurationException("Unable to load configuration", e);
                        }
                    });
                }
            } else { // assume regular file
                LOGGER.debug("Regular File");
                try(InputStream in = new FileInputStream(configPath)) {
                    configurations.add(hgqlConfigService.loadHGQLConfig(configPathString, in, false));
                }
            }
        } catch (IOException e) {
            throw new HGQLConfigurationException("One or more config files not found", e);
        }
        return configurations;
    }

    private List<HGQLConfig> getConfigurationsFromClasspath(final String configPathString) {

        final String filename = getConfigFilename(configPathString);

        try(final InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {

            if(in == null) {
                // try to get from file - probably being run from an IDE with CP as filesystem
                return getConfigurationsFromFile(configPathString);
            }
            return Collections.singletonList(hgqlConfigService.loadHGQLConfig(configPathString, in, true));

        } catch (IOException e) {

            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    /**
     * If a "!" is in the given String then return the substring after the last "!" in the String and add a "/" at the
     * at the beginning of the result if not already present
     * @param configPathString Config file name
     * @return String without "!" and with an "/" ath the beginning
     */
    private String getConfigFilename(final String configPathString) {

        final String fn = configPathString.contains("!")
                ? configPathString.substring(configPathString.lastIndexOf("!") + 1)
                : configPathString;
        return configPathString.startsWith("/") ? fn : fn.substring(fn.indexOf("/") + 1);
    }

    public List<HGQLConfig> getConfigResources(final String ... resourcePaths) {

        final List<HGQLConfig> configurations = new ArrayList<>();

        if(resourcePaths != null) {
            Arrays.stream(resourcePaths).forEach(
                    resourcePath -> {
                        final URL sourceUrl = getClass().getClassLoader().getResource(resourcePath);

                        LOGGER.info("Resource path: {}", resourcePath);
                        if(sourceUrl != null) {
                            configurations.addAll(getConfigurationsFromClasspath(sourceUrl.getFile()));
                        }
                    }
            );
        }

        return configurations;
    }
}
