import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * Created by szymon on 05/09/2017.
 */
public class Controller {

    static Converter converter;


    public static void start(Config config, GraphQL graphQL) {

        converter = new Converter(config);

        port(config.graphql.port);

        System.out.println("GraphQL server started at: "  + config.graphql.port);

        get(config.graphql.graphiql, (req, res) -> {
            Map<String, String> model = new HashMap<>();
            String portScript = config.graphql.port.toString();
            model.put("template", portScript);
            return new VelocityTemplateEngine().render(
                    new ModelAndView(model, "graphiql.vtl")
            ); });

        post(config.graphql.path, (req, res) -> {

            ObjectMapper mapper = new ObjectMapper();
            JsonNode requestObject = mapper.readTree(req.body().toString());

            String query = requestObject.get("query").asText();

            if (!query.contains("IntrospectionQuery")) converter.graphql2sparql(query);

            Map<String, Object> result = new HashMap<>();

            ExecutionResult qlResult = graphQL.execute(query);

            Map<String, Object> data = qlResult.getData();

            List<GraphQLError> errors = qlResult.getErrors();
            Map<Object, Object> extensions = qlResult.getExtensions();

            if (extensions==null) extensions = new HashMap<>();
            if (data!=null&&!data.containsKey("__schema")) {

                extensions.put("json-ld", converter.graphql2jsonld(data));
            }

            if (data!=null) result.put("data", data);

            if (!errors.isEmpty()) result.put("errors", errors);
            if (extensions!=null) result.put("extensions", extensions);

            JsonNode resultJson = mapper.readTree( new ObjectMapper().writeValueAsString(result));
            res.type("application/json");

            return resultJson;

        });
    }


}