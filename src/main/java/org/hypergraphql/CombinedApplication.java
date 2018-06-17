package org.hypergraphql;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.services.ApplicationConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hypergraphql.util.PathUtils.isNormalURL;
import static org.hypergraphql.util.PathUtils.isS3;

public class CombinedApplication {

    private final static Logger LOGGER = LoggerFactory.getLogger(CombinedApplication.class);

    public static void main(final String[] args) throws Exception {

        final String[] trimmedArgs = trimValues(args);

        final Options options = buildOptions();
        final CommandLineParser parser = new DefaultParser();
        final CommandLine commandLine;
        try {
            commandLine = parser.parse(options, trimmedArgs);
        } catch (ParseException e) {
            throw new HGQLConfigurationException("Unable to parse command line", e);
        }

        final ApplicationConfigurationService service = new ApplicationConfigurationService();

        final List<HGQLConfig> configurations;

        final boolean showBanner = !commandLine.hasOption("nobanner");

        if(commandLine.hasOption("config") || commandLine.hasOption("s3")) {

            configurations = getConfigurationFromArgs(service, commandLine);
        } else {

            final Map<String, String> properties;
            if(commandLine.hasOption("D")) {
                final Properties props = commandLine.getOptionProperties("D");

                properties = new HashMap<>();
                props.forEach((k, v) -> properties.put((String)k, (String)v));

            } else {
                properties = System.getenv();
            }

            configurations = getConfigurationsFromProperties(properties, service);
        }

        if(configurations.size() == 0) {
            System.err.println("No configurations loaded, exiting");
            return;
        }

        start(configurations, showBanner);
    }

    protected static void start(final List<HGQLConfig> configurations) throws IOException {
        start(configurations, true);
    }

    protected static void start(final List<HGQLConfig> configurations, final boolean showBanner) throws IOException {

        if(showBanner) {
            showBanner();
        }

        configurations.forEach(config -> {
            LOGGER.info("Starting controller...");
            new Controller().start(config);
        });
    }

    static List<HGQLConfig> getConfigurationsFromProperties(
            final Map<String, String> properties,
            final ApplicationConfigurationService service
    ) {

        // look for environment variables
        final String configPath = properties.get("hgql_config");
        if(StringUtils.isBlank(configPath)) {
            throw new HGQLConfigurationException("No configuration parameters seem to have been provided");
        }
        final String username = properties.get("hgql_username");
        final String password = properties.get("hgql_password");

        LOGGER.debug("Config path: {}", configPath);
        LOGGER.debug("Username: {}", username);
        LOGGER.debug("Password: {}", password == null ? "Not provided" : "**********");

        // TODO - check for URL, S3, file://, 'file' and 'classpath:'
        if(isS3(configPath)) {
            return service.readConfigurationFromS3(configPath, username, password);
        } else if(isNormalURL(configPath)) {
            return service.readConfigurationFromUrl(configPath, username, password);
        } else {
            // assume it's a normal file
            return service.getConfigFiles(configPath);
        }
    }

    private static Options buildOptions() {

        return new Options()
                .addOption(
                        Option.builder("config")
                                .longOpt("config")
                                .hasArgs()
                                .numberOfArgs(Option.UNLIMITED_VALUES)
                                .desc("Location of config files (or absolute paths to config files)")
                                .build()
                )
                .addOption(
                        Option.builder("classpath")
                                .longOpt("classpath")
                                .hasArg(false)
                                .desc("Look on classpath instead of file system")
                                .build()
                ).addOption(
                        Option.builder("s3")
                                .longOpt("s3")
                                .hasArg(true)
                                .desc("Look at the provided URL for configuration")
                                .build()
                ).addOption(
                        Option.builder("u") // access key
                                .longOpt("username")
                                .hasArg(true)
                                .desc("Username (or access key ID for S3)")
                                .build()
                ).addOption(
                        Option.builder("p") // secret key
                                .longOpt("password")
                                .hasArg(true)
                                .desc("Password (or secret key for S3")
                                .build()
                ).addOption(
                        Option.builder("D")
                                .longOpt( "property=value" )
                                .hasArgs()
                                .numberOfArgs(2)
                                .valueSeparator()
                                .desc( "use value for given property" )
                                .build()
                ).addOption(
                        Option.builder("nobanner")
                                .longOpt("nobanner")
                                .hasArg(false)
                                .desc("Don't show the banner on startup")
                                .build()
                );
    }

    private static List<HGQLConfig> getConfigurationFromArgs(
            final ApplicationConfigurationService service,
            final CommandLine commandLine
    ) {
        if(commandLine.hasOption("s3")) {

            final String s3url = commandLine.getOptionValue("s3");
            final String accessKey = commandLine.getOptionValue('u');
            final String secretKey = commandLine.getOptionValue('p');

            // URL lookup
            return service.readConfigurationFromS3(s3url, accessKey, secretKey);
        } else if(commandLine.hasOption("config")) {

            if (commandLine.hasOption("classpath")) {
                return service.getConfigResources(commandLine.getOptionValues("config"));
            } else {
                return service.getConfigFiles(commandLine.getOptionValues("config"));
            }

        } else {

            throw new IllegalArgumentException("One of 'config' or 's3' MUST be provided");
        }
    }

    private static String[] trimValues(final String[] input) {

        return Arrays.stream(input)
                .map(String::trim)
                .collect(Collectors.toList())
                .toArray(new String[input.length]);
    }

    private static void showBanner() throws IOException {

        final String bannerFile = "banner.txt";
        final InputStream bannerInputStream = Application.class.getClassLoader().getResourceAsStream(bannerFile);
        if(bannerInputStream == null) {
            System.out.println(Application.class.getClassLoader().getResource(bannerFile));
            System.err.println("Banner is null");
        }
        IOUtils.copy(bannerInputStream, System.out);
        final String version = System.getProperty("hgql_version");
        if(version == null) {
            System.out.println("----------------------------------------------------------------------\n");
        } else {
            System.out.printf("------------------------------- v%1$s -------------------------------%n%n", version);
        }
    }
}
