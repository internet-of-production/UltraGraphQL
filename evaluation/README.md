#HGQL 2.0.0 Performance Evaluation

The feature extensions for HGQL 1.0.3 are referenced as version 2.0.0 during the evaluation.
To evaluate the performance HGQL is compared to the old version of HGQL and against SPARQL it self.
Due to multiple possible ways to setup HGQL and to execute the query the evaluation will compare HGQL setup with local rdf files and configured with a SPARQL service also running on the same system.
For this reason for the SPARQL service a fuseki server is setup with the same datasets.

# Query tests with one service

To run the HGQL query tests with local rdf files, no fuseki server is needed.
The Tests are executed with
```
bash run_query_hgql_2.0.0_standalone.sh
bash run_query_hgql_1.0.3_standalone.sh
```

Both command should be run separately.

For the Tests where the data only accessible through SPARQL the Fuseki server has to be started with
```
bash run_server.sh
```

The scripts with the tests are then executed in parrallel to the server one by one with the following commands

```
bash run_query_hgql_2.0.0.sh
bash run_query_hgql_1.0.3.sh
bash run_query_sparql.sh
```

The SPARQL conducts two tests. First the queries are executed with **SPARQL Over HTTP (SOH)** and afterwards all queries are queriedd again with an POST request through **curl**.
All queries for HGQL are also executed as POST request through curl.


# Multiple service tests
Two services with interlinked data and equivalence relations.
The old version of HGQL is excluded from this test as it only supports one service per schema entity.


To run the HGQL query tests with local rdf files, no fuseki server is needed.
The Tests are executed with
```
bash run_multi_service_test_hgql_standalone.sh
```

Both command should be run separately.
For the Tests where the data only accessible through SPARQL the Fuseki server has to be started with
```
bash run_server.sh
```
The same fuseki server script is used as for the tests with one service because the server is setup with multiple datasets which are not inter queryable unless specified as service in the query.

The scripts with the tests are then executed in parrallel to the server one by one with the following commands

```
bash run_multi_service_test_hgql.sh
bash run_multi_service_test_sparql.sh
```

The SPARQL conducts two tests. First the queries are executed with **SPARQL Over HTTP (SOH)** and afterwards all queries are queriedd again with an POST request through **curl**.
All queries for HGQL are also executed as POST request through curl.
