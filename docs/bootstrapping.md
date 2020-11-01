# Automatic Bootstrapping Through Schema Extraction


The bootstrapping phase is a **optional** phase during the start up of the UGQL endpoint and is only performed if enabled in the [endpoint configuration](./config.md).
When the bootstrapping is enabled the RDF schema is extracted from thee services defined in the enpoint configuration.
The schema extraction is based on the schema [extraction query](./schema_extraction_query.md) and the [schema mapping](./schema_mapping.md).
Both can be configured through the endpoint configuration or if not defined the defualt query and mapping will be used.
The configured mapping is then used to generate the final extraction query which is then executed against the SPARQL services.
The resulting SPARQL results which contain the RDF schema of the datasets are then translated to the corresponding **UltraGraphQL schema (UGQLS)**.

An UGQLS is a valid GraphQL schema but does not define query and mutation fields.
Furthermore, it requires the definition of the *__Context* object type in which stores the name resolution of the used GQL names to the corresponding IRIs.
For the translation of the schema the configured mapping vocabulary defines how the extracted RDF schema is mapped to the corresponding UGQLS features.
If multiples services are defined the extraction query is executed separetly and translated to a unified UGQLS by maintaining the service origin of the different schema entities and by maintaining schema relations across service boundaries.

The bootstrapping phase ends with the completion of the UGQLS for the configured services.

> RDF allows a wide variety of different schemas. 
> The default mapping vocabulary and extraction query tries to cover the most common cases. 
> If the default configuration is not well suited for a configured data set the mapping or the extraction query have to be adjusted to the actual dataset in order to function correctly.

![Abstract overview about the bootstrapping phase of UGQL. UGQL is started with the Endpoint configuration in which the services are defined and also the mapping and extraction query are configured or if not the default will be used. With this configuration the extraction queries are build and executed against the defined services. The resulting SPARQL results contain the extracted RDF schema which is then maped to corresponding UGQL schema. This UGQLS is then used to start the UGQL endpoint.](./figures/bootstrapping_phase_schematic.png)

## Related Documentation
|Link|Description|
|---------------|-------------------------|
| [HGQL Configuration](./config.md)| Expanded the configuration with bootstapping feature|
|[Schema Exraction Query](./schema_extraction_query.md)| Explaination how to write or alter a UGQL compatible schema extraction query|
| [Schema Mapping](./schema_mapping.md)| Explaination how the schema is mapped and how to configure the mapping|
