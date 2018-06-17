package org.hypergraphql;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.exception.HGQLConfigurationException;
import org.hypergraphql.services.ApplicationConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class CLIApplication extends Application {

    private final static Logger LOGGER = LoggerFactory.getLogger(CLIApplication.class);

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
}