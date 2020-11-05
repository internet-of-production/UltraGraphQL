# UltraGraphQL Performance Evaluation

In [this](evaluation.ipynb) Evaluation UltraGraphQL 1.1.0 is compared with **UGQL 1.0.0**, **GraphQL Transformer** and, **SPARQL**.

>Before UGQL 1.1.0 the evaluation included also **HyperGraphQL**, you find those results under the tag **UGQL_v1.0.0** in the respository.

All UGQL server configurations are stored in the folder **configs**, and the logs of the server in the folder **logs**.
The queries used in the tests can be found in the folder **queries** each test has an own subfolder in the folder.
The query results are stored in a **results** folder located in the corresponding query folder along with the figures.

## Setup
### Datasets
Before executing the tests the datasets have to be unzipped by running the following command:
```shell script
python data/unzip.py
```

### Apache Jena Fuseki
To execute the evaluation **Apache Jena Fuseki 3.7.0** is required and need to be present in this directory.
Download the version [3.7.0](http://archive.apache.org/dist/jena/binaries/apache-jena-3.7.0.zip) directly or look in the [archive](http://archive.apache.org/dist/jena/binaries/).
After the extraction this folder should have the following structure:
```
evaluation/
├── apache-jena-fuseki-3.7.0
│   ├── bin
│   └── webapp
│       ├── css
│       ├── fonts
│       ├── images
│       ├── js
│       │   ├── app
│       │   │   ├── controllers
│       │   │   ├── layouts
│       │   │   ├── models
│       │   │   ├── routers
│       │   │   ├── services
│       │   │   ├── templates
│       │   │   ├── util
│       │   │   └── views
│       │   └── lib
│       │       ├── addon
│       │       │   └── fold
│       │       ├── lib
│       │       ├── mode
│       │       │   ├── javascript
│       │       │   ├── sparql
│       │       │   ├── turtle
│       │       │   └── xml
│       │       └── plugins
│       ├── test
│       └── WEB-INF
├── configs
│   ├── dbpedia
│   ├── growing_fields
│   ├── multiple_services
│   ├── mutations
│   ├── nested_queries
│   └── one_service
├── data
│   └── raw
├── logs
│   ├── dbpedia
│   ├── growing_fields
│   ├── multiple_services
│   ├── mutations
│   ├── nested_queries
│   └── one_service
├── mapping_evaluation
├── queries
│   ├── dbpedia
│   │   └── results
│   ├── growing_fields
│   │   └── results
│   ├── multiple_services
│   │   └── results
│   ├── mutations
│   │   └── results
│   ├── nested_queries
│   │   └── results
│   └── one_service
│       ├── results
│       └── temp
└── run
    ├── backups
    ├── configuration
    ├── databases
    ├── logs
    ├── system
    ├── system_files
    └── templates

``` 
## Jupyter Lab
All tests are written in a python [jupyter notebook](evaluation.ipynb).
> Note: **Instalation guide** for Jupyter Lab is provided [here](https://jupyterlab.readthedocs.io/en/stable/getting_started/installation.html).

To start the jupyter notebook run the command:
```shell script
jupyter lab
```

>Note: UGQL was initially developed under the project name *HGQL 2.0.0*, therefore the evaluation and the code still contain the abbreviation *hgql* instead of *ugql*.

# Query tests with one service

Before starting the test in the notebook run the commands below to start the server
```shell script
bash run_server.sh
bash start_hgql_server_one_service_test.sh
```

# Query Test with Growing Nested Queries
Before starting the test in the notebook run the commands below to start the server
```shell script
bash run_server.sh
bash start_hgql_server_nested_queries.sh
```

# Query Test with Growing Amount of Fields
Before starting the test in the notebook run the commands below to start the server
```shell script
bash run_fuseki_growing_field_server.sh
bash start_hgql_server_growing_fields.sh
```

# Query Test with DBpedia
Before starting the test in the notebook run the commands below to start the server
```shell script
bash start_hgql_server_dbpedia.sh
```

# Multiple service tests
Before starting the test in the notebook run the commands below to start the server
```shell script
bash run_server.sh
bash start_hgql_server_multiple_services_test.sh
```

# Mutation Tests
Before starting the test in the notebook run the commands below to start the server
```shell script
bash run_server.sh
bash start_hgql_server_mutations.sh
```
