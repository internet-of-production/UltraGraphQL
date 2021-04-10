# Changelog

#### Version 1.1.2
- Added support for named graphs for LocalModelSPARQLService
   - e.g. TriG, N-Quads
- Fixed bug in query result build-up
   - merging of complex types resulted in some cases in adding the field name twice
- Fixed bug in the mutation SelectionSet result
- Stabilized unittests
   - previously the prefixes were queried from prefix.cc
   - now they are provided by the config file to get consistent results
- Fixed bug in config prefix mappings
- Changed protocol of prefix fetching URL
   - https leads to an error switched to http

#### Version 1.1.1
- Fixed bug in query result merging (only occured in multi service configuration)

### Version 1.1.0
Changes:
 - New result transformation
   - Improved performance
   - Simplified code
 - Changed internal query translation form JSON to java objects
   - Increased type strictness 
 - Added user definable prefixes
 - Updated and improved documentation
 - Added support for bundled root queries
   - Query resolving of multiple root queries is now possible
 - Updated evaluation results for this version
 - Removed data fetchers
   - New result transformation does not require data fetchers
 
 
Bug fixes:
- Fixed [type resolver issue](./docs/evaluation/type_resolver_problem.md)
- Fixed unnecessary generation of extracted schemas during JUnit-Tests
- Fixed query handling of mutations
- Fixed internal schema translation for equivalent property definitions

## Version 1.0.0
Initial release of UltraGraphQL

UltraGraphQL is based on **HyperGraphQL version 1.0.3** and was heavily modified.
 - Automatic bootstrapping phase (through schema summarization)
    - Configurable summarization querying
    - Configurable mapping vocabulary
 - Mutation support
   - Insert and delete mutation fields are generated for all objects in the schema
   - Mutation action is limited to one service (MUST be LocalModelSPARQLService or SPARQLEndpointService)
 - Support for [multiple services](./docs/multiple_service_feature.md) per schema entity
 - Support of equivalence relations in the schema and during querying
 - [Interafaces](./docs/interface.md) and [Unions](./docs/union.md) supported in the schema
 - Filter options now also avaliable for fields (prior only avaliable for the root query fields)
 - Simplified query naming schema
 - Additional web server framework to host the UltraGraphQL instance to allow multiple instances running on the same system