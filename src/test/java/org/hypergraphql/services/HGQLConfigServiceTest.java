package org.hypergraphql.services;

import org.apache.log4j.Logger;
import org.hypergraphql.config.system.HGQLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class HGQLConfigServiceTest {

    static Logger log = Logger.getLogger(HGQLConfigServiceTest.class.getName());
    private String config_1 = "./src/test/resources/test_config_with_mapping.json";
    private InputStream config_1_input_stream;

    @BeforeEach
    void setUp() throws FileNotFoundException {
         config_1_input_stream = new FileInputStream(config_1);
    }

    @Test
    void loadHGQLConfig() {
        HGQLConfigService confService = new HGQLConfigService();
        HGQLConfig hgqlConfig = confService.loadHGQLConfig(config_1, config_1_input_stream, false);
        log.info("Finished");
    }

    @Test
    void testLoadHGQLConfig() {
    }
}