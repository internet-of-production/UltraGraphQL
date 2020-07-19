def generate_graphql_queries_hgql_1(n):
    root_query_graphql = "{\n  ex_Person_GET{\n    _id\n%s  }\n}"
    nested_query_template_graphql = "   ex_field_%s\n%s"
    nested_query_graphql = nested_query_template_graphql
    for i in range(1, n+1):
        nest_graphql = nested_query_graphql % (i, "")
        nested_query_graphql = nested_query_graphql % (i, nested_query_template_graphql)
    return root_query_graphql % nest_graphql


def generate_graphql_queries(n):
    root_query_graphql = "{\n  ex_Person{\n    _id\n%s  }\n}"
    nested_query_template_graphql = "   ex_field_%s\n%s"
    nested_query_graphql = nested_query_template_graphql
    for i in range(1, n+1):
        nest_graphql = nested_query_graphql % (i, "")
        nested_query_graphql = nested_query_graphql % (i, nested_query_template_graphql)
    return root_query_graphql % nest_graphql

def generate_sparql_queries(n):
    root_query_sparql = "PREFIX ex: <http://example.org/>\nSELECT *\nWHERE{\n  { SELECT ?person\n    WHERE{\n      ?person a ex:Person .\n    }\n  }\n  %s\n}"
    nested_query_template_sparql = "OPTIONAL{\n   ?person ex:field_%s %s .\n  }\n  %s"
    nested_query_sparql = nested_query_template_sparql
    literal = "?x_%s"
    for i in range(1, n+1):
        nested_query = nested_query_sparql % (i, literal % i, "")
        nested_query_sparql = nested_query_sparql % (i, literal % i, nested_query_template_sparql )
    return root_query_sparql % nested_query


def generate_sparql_transformer_queries(n):
   root_query = "{\n \"proto\":[{\n  \"id\": \"?person\"%s\n }],\n \"$where\": [\n  \"?person a ex:Person\"\n ],\n \"$prefixes\":{\n  \"ex\": \"http://example.org/\"\n }\n}"
   select_template = ",\n   \"field_%s\": \"$ex:field_%s\"%s"
   select_tree = select_template
   for i in range(1, n+1):
      nested_select = select_tree % (i ,i, "")
      select_tree = select_tree % (i, i, select_template)
   return root_query % (nested_select)

if __name__ == "__main__":
    n=50
    #print(generate_graphql_queries(n))
    #print(generate_graphql_queries_hgql_1(n))
    print(generate_sparql_queries(n))
    #print(generate_sparql_transformer_queries(n))
