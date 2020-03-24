package org.hypergraphql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {

    @Test
    void main() throws Exception {
        System.out.print(System.getProperty("user.dir"));
        String config = "config_extraction.json";
        Application.main(new String[]{"--classpath","-config", config});
        while(true){

        }
    }

    @Test
    void start() {
    }

    @Test
    void testStart() {
    }
}