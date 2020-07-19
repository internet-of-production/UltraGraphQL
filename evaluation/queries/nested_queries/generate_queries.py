def generate_graphql_queries_hgql_1(n):
    root_query_graphql = "{\n  ex_Person_GET{\n    _id\n    %s  }\n}"
    nested_query_template_graphql = "ex_relatedWith{\n%s_id\n%s%s\n%s}\n"
    nested_query_graphql = nested_query_template_graphql
    for i in range(n):
        space_filler = " " *2*(i+3)
        space_filler_bracket = " "*2*(i+2)
        nest_graphql = nested_query_graphql % (space_filler,"","", space_filler_bracket)
        with open("query_%s_hgql_1.0.3.graphql"%(i+1), "w") as f:
            f.write(root_query_graphql % nest_graphql)
        nested_query_graphql = nested_query_graphql % (space_filler, space_filler, nested_query_template_graphql, space_filler_bracket)


def generate_graphql_queries(n):
    root_query_graphql = "{\n  ex_Person{\n    _id\n    %s  }\n}"
    nested_query_template_graphql = "ex_relatedWith{\n%s_id\n%s%s\n%s}\n"
    nested_query_graphql = nested_query_template_graphql
    for i in range(n):
        space_filler = " " *2*(i+3)
        space_filler_bracket = " "*2*(i+2)
        nest_graphql = nested_query_graphql % (space_filler,"","", space_filler_bracket)
        with open("query_%s_hgql_2.0.0.graphql"%(i+1), "w") as f:
            f.write(root_query_graphql % nest_graphql)
        nested_query_graphql = nested_query_graphql % (space_filler, space_filler, nested_query_template_graphql, space_filler_bracket)

def generate_sparql_queries(n):
    root_query_sparql = "PREFIX ex: <http://example.org/>\nSELECT *\nWHERE{\n  { SELECT ?x_1\n    WHERE{\n      ?x_1 a ex:Person .\n    }\n  }\n  %s\n}"
    nested_query_template_sparql = "OPTIONAL{\n%s%s ex:relatedWith %s .\n%s%s a ex:Person .\n%s%s%s}\n"
    nested_query_sparql = nested_query_template_sparql
    var = "?x_1"
    for i in range(n):
        var_new = var + "_1"
        space_filler = " " *2*(i+2)
        space_filler_bracket = " "*2*(i+1)
        nested_query = nested_query_sparql % (space_filler, var, var_new, space_filler, var_new, "", "", space_filler_bracket)
        with open("query_%s.sparql"%(i+1), "w") as f:
            f.write(root_query_sparql % nested_query)
        nested_query_sparql = nested_query_sparql % (space_filler, var, var_new, space_filler, var_new, space_filler, nested_query_template_sparql, space_filler_bracket)
        var = var_new

def generate_sparql_transformer_queries(n):
   root_query = '{\n "proto":[{\n  "id": "?rel_0"%s\n }],\n "$where": [\n  "?rel_0 a ex:Person"%s\n ],\n "$prefixes":{\n  "ex": "http://example.org/"\n }\n}'
   select_template = ',\n%s"rel_%s":{\n%s\"id": "?rel_%s"%s%s}\n'
   where_template = ',\n  "?rel_%s ex:relatedWith ?rel_%s",\n  "?rel_%s a ex:Person"%s'
   select_tree = select_template
   where_tree = where_template
   for i in range(1, n+1):
      space_filler = " " *2*(i+1)
      nested_select = select_tree % (space_filler, i, space_filler, i, "", space_filler)
      nested_where = where_tree % (i-1, i, i, "")
      with open("query_%s.json"%(i), "w") as f:
            f.write(root_query % (nested_select, nested_where))
      select_tree = select_tree % (space_filler, i, space_filler,  i, select_template, space_filler)
      where_tree = where_tree % (i-1, i, i, where_template)

if __name__ == "__main__":
    n=50
    #generate_graphql_queries(n)
    #generate_graphql_queries_hgql_1(n)
    generate_sparql_queries(n)
    #generate_sparql_transformer_queries(n)
