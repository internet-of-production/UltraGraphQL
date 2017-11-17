package org.hypergraphql;

import com.fasterxml.jackson.databind.JsonNode;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by szymon on 01/11/2017.
 */
public class GraphqlServiceTest {

    private final int LIMIT = 100;

    private final String TEST_QUERY =
                    "{\n" +
                    "  people(limit:%s) {\n" +
                    "    name\n" +
                    "    birthDate\n" +
                    "    deathDate\n" +
                    "    birthPlace {\n" +
                    "      _id\n" +
                    "      label\n" +
                    "      country {\n" +
                    "        _id\n" +
                    "        label\n" +
                    "      }\n" +
                    "    }\n" +
                    "    deathPlace {\n" +
                    "      _id\n" +
                    "      label\n" +
                    "      country {\n" +
                    "        _id\n" +
                    "        label\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";

    @Test
    public void queryPerformanceTest() {
        Config config = new Config("properties.json");
        GraphqlWiring wiring = new GraphqlWiring(config);
        GraphQL graphQL = GraphQL.newGraphQL(wiring.schema()).build();

        List<Map<String, String>> sparqlQueries;

        String query = String.format(TEST_QUERY, LIMIT);

        Converter converter = new Converter(config);
        JsonNode jsonQuery = converter.query2json(query);

        sparqlQueries = converter.graphql2sparql(converter.includeContextInQuery(jsonQuery));

        SparqlClient client = new SparqlClient(sparqlQueries, config);

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(client)
                .build();

        long tStart = System.currentTimeMillis();

        ExecutionResult qlResult = graphQL.execute(executionInput);


        long tEnd = System.currentTimeMillis();
        long tDelta = tEnd - tStart;
        double elapsedSeconds = tDelta / 1000.0;

        System.out.println("Old method: " + elapsedSeconds);

        Map<String, Object> data =  qlResult.getData();

        ArrayList people = (ArrayList) data.get("people");

        assert(people.size()==LIMIT);


        SparqlClient clientExt = new SparqlClientExt(sparqlQueries, config);

        ExecutionInput executionInputExt = ExecutionInput.newExecutionInput()
                .query(query)
                .context(clientExt)
                .build();


        tStart = System.currentTimeMillis();

        ExecutionResult qlResultExt = graphQL.execute(executionInputExt);

        tEnd = System.currentTimeMillis();
        tDelta = tEnd - tStart;
        elapsedSeconds = tDelta / 1000.0;

        System.out.println("New method: " + elapsedSeconds);

        data =  qlResultExt.getData();

        people = (ArrayList) data.get("people");

        assert(people.size()==LIMIT);

    }

}
