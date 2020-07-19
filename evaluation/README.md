#HGQL 2.0.0 Performance Evaluation

The feature extensions for HGQL 1.0.3 are referenced as version 2.0.0 during the evaluation.
To evaluate the performance HGQL is compared to the old version of HGQL, SPARQL Transformer and against SPARQL it self.
Due to multiple possible ways to setup HGQL and to execute the query the evaluation will compare HGQL setup with local rdf files and configured with a SPARQL service also running on the same system.
For this reason for the SPARQL service a fuseki server is setup with the same datasets.

All HGQL server configurations are stored in the folder **configs** and the logs of the server in the folder **logs**.
The queries used in the tests can be found in the folder **queries** each test has a own subfolder in the folder.
The query results are stored in a **results** folder located in the corresponding query folder along with the figures.


All tests are written in a python jupyter notebook.
To start the jupyter notebook run the command  **$jupyter lab**


# Query tests with one service

Before starting the test in the notebook run the commands below to start the server
```
bash run_server.sh
bash start_hgql_server_one_service_test.sh
```

# Query Test with Growing Nested Queries
Before starting the test in the notebook run the commands below to start the server
```
bash run_server.sh
bash start_hgql_server_nested_queries.sh
```

# Query Test with Growing Amount of Fields
Before starting the test in the notebook run the commands below to start the server
```
bash run_fuseki_growing_field_server.sh
bash start_hgql_server_growing_fields.sh
```

# Query Test with DBpedia
Before starting the test in the notebook run the commands below to start the server
```
bash start_hgql_server_dbpedia.sh
```

# Multiple service tests
Before starting the test in the notebook run the commands below to start the server
```
bash run_server.sh
bash start_hgql_server_multiple_services_test.sh
```

# Mutation Tests
Before starting the test in the notebook run the commands below to start the server
```
bash run_server.sh
bash start_hgql_server_mutations.sh
```
