package org.hypergraphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQLError;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.hypergraphql.config.system.HGQLConfig;
import org.hypergraphql.services.HGQLRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Service;
import spark.template.velocity.VelocityTemplateEngine;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.before;

/**
 * Created by szymon on 05/09/2017.
 *
 * This is the primary &quot;Controller&quot; used by the application.
 * The handler methods are in the get() and post() lambdas
 */
public class Controller {

    private final static Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    private Service hgqlService;
    private HGQLConfig config;

    private static final String SERVER_FRAMEWORK_SPARK = "spark";
    private static final String SERVER_FRAMEWORK_JAXRS = "jaxrs";
    private static final String DEFAULT_MIME_TYPE = "RDF/XML";
    private static final String DEFAULT_ACCEPT_TYPE = "application/rdf+xml";

    final List<String> headersList = Arrays.asList(
            "Origin",
            "X-Requested-With",
            "Content-Type",
            "Accept",
            "authorization",
            "x-auth-token"
    );

    private static final Map<String, String> MIME_MAP = new HashMap<String, String>() {{
        put("application/json+rdf+xml", "RDF/XML");
        put("application/json+turtle", "TTL");
        put("application/json+ntriples", "N-TRIPLES");
        put("application/json+n3", "N3");
        put("application/rdf+xml", "RDF/XML");
        put("application/turtle", "TTL");
        put("application/ntriples", "N-TRIPLES");
        put("application/n3", "N3");
        put("text/turtle", "TTL");
        put("text/ntriples", "N-TRIPLES");
        put("text/n3", "N3");
    }};

    private static final Map<String, Boolean> GRAPHQL_COMPATIBLE_TYPE = new HashMap<String, Boolean>() {{
        put("application/json+rdf+xml", true);
        put("application/json+turtle", true);
        put("application/json+ntriples", true);
        put("application/json+n3", true);
        put("application/rdf+xml", false);
        put("application/turtle", false);
        put("application/ntriples", false);
        put("application/n3", false);
        put("text/turtle", false);
        put("text/ntriples", false);
        put("text/n3", false);
    }};

    public Controller(){

    }

    private Controller(Service hgqlService, HGQLConfig config) {
        this.hgqlService = hgqlService;
        this.config = config;
    }

    /**
     * Starts the HGQL REST API.
     * If no framework is defined in the configuration then the API is started over spark.
     * @param config HGQL configuration
     */
    public void start(HGQLConfig config){
        System.out.println("HGQL service name: " + config.getName());
        System.out.println("GraphQL server started at: http://localhost:" + config.getGraphqlConfig().port() + config.getGraphqlConfig().graphQLPath());
        System.out.println("GraphiQL UI available at: http://localhost:" + config.getGraphqlConfig().port() + config.getGraphqlConfig().graphiQLPath());

        this.config = config;
        if(config.getGraphqlConfig().serverFramwork() != null && config.getGraphqlConfig().serverFramwork().equals(SERVER_FRAMEWORK_JAXRS)){
            startCXF();
        }
        else {
            startSpark();
        }
    }

    /**
     * Starts the HGQL REST api over the spark framework.
     */
    private void startSpark() {

        hgqlService = Service
                .ignite()
                .port(config.getGraphqlConfig().port())
                .threadPool(50,10,1000);
        // CORS
        before((request, response) -> {
            response.header("Access-Control-Allow-Methods", "OPTIONS,GET,POST");
            response.header("Content-Type", "");
        });

        hgqlService.options("/*", (req, res) -> {
            setResponseHeaders(req, res);
            return "";
        });

        // get method for accessing the GraphiQL UI

        hgqlService.get(config.getGraphqlConfig().graphiQLPath(), (req, res) -> {

            Map<String, String> model = new HashMap<>();

            model.put("template", String.valueOf(config.getGraphqlConfig().graphQLPath()));

            setResponseHeaders(req, res);

            return new VelocityTemplateEngine().render(
                    new ModelAndView(model, "graphiql.vtl")
            );
        });

        // post method for accessing the GraphQL getService
        hgqlService.post(config.getGraphqlConfig().graphQLPath(), (req, res) -> {

            HGQLRequestService service = new HGQLRequestService(config);

            final String query = consumeRequest(req);
            String acceptType = req.headers("accept");

            String mime = MIME_MAP.getOrDefault(acceptType, null);
            String contentType = MIME_MAP.containsKey(acceptType) ? acceptType : "application/json";
            Boolean graphQLCompatible = GRAPHQL_COMPATIBLE_TYPE.getOrDefault(acceptType, true);

            res.type(contentType);

            Map<String, Object> result = service.results(query, mime);
            List<GraphQLError> errors = (List<GraphQLError>) result.get("errors");
            if (!errors.isEmpty()) {
                res.status(400);
            }

            setResponseHeaders(req, res);

            ObjectMapper mapper = new ObjectMapper();
            if (graphQLCompatible) {
                return mapper.readTree(new ObjectMapper().writeValueAsString(result));
            } else {
                if (result.containsKey("data")) {
                    return result.get("data").toString();
                } else {
                    JsonNode errorsJson = mapper.readTree(new ObjectMapper().writeValueAsString(errors));
                    return errorsJson.toString();
                }
            }
        });

        //Return the internal HGQL schema representation as rdf.

        hgqlService.get(config.getGraphqlConfig().graphQLPath() , (req, res) -> {

            String acceptType = req.headers("accept");

            boolean isRdfContentType =
                    (MIME_MAP.containsKey(acceptType)
                            && GRAPHQL_COMPATIBLE_TYPE.containsKey(acceptType)
                            && !GRAPHQL_COMPATIBLE_TYPE.get(acceptType));
            String mime = isRdfContentType ? MIME_MAP.get(acceptType) : DEFAULT_MIME_TYPE;
            String contentType = isRdfContentType ? acceptType : DEFAULT_ACCEPT_TYPE;

            res.type(contentType);

            setResponseHeaders(req, res);

            return config.getHgqlSchema().getRdfSchemaOutput(mime);
        });
    }

    private String consumeRequest(final Request request) throws IOException {

        if(request.contentType().equalsIgnoreCase("application-x/graphql")) {
            return consumeGraphQLBody(request.body());
        } else {
            return consumeJSONBody(request.body());
        }
    }

    private String consumeJSONBody(final String body) throws IOException {

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode requestObject = mapper.readTree(body);
        if(requestObject.get("query") == null) {
            throw new IllegalArgumentException(
                    "Body appears to be JSON but does not contain required 'query' attribute: " + body
            );
        }
        return requestObject.get("query").asText();
    }

    private String consumeGraphQLBody(final String body) {

        return body;
    }

    public void stop() {

        if(hgqlService != null) {
            LOGGER.info("Attempting to shut down service at http://localhost:" + hgqlService.port() + "...");
            hgqlService.stop();
            LOGGER.info("Shut down server");
        }
    }

    private void setResponseHeaders(final Request request, final Response response) {

        final String origin = request.headers("Origin") == null ? "*" : request.headers("Origin");
        response.header("Access-Control-Allow-Origin", origin);
        if(!origin.equals("*")) {
            response.header("Vary", "Origin");
        }
        response.header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","));

        response.header("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Starts the HGQL REST api over the apache cxf framework.
     */
    private void startCXF() {
//        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
//        sf.setServiceBean(this);
//        //sf.setResourceClasses(Controller.class);
//        //sf.setResourceProvider(Controller.class,
//        //        new SingletonResourceProvider(this));
//        sf.setAddress(String.format("http://localhost:%s/",config.getGraphqlConfig().port()));
//
//        sf.create();
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(Controller.class);
        sf.setResourceProvider(Controller.class, new SingletonResourceProvider(new Controller(this.hgqlService, this.config)));
        sf.setAddress(String.format("http://localhost:%s/",config.getGraphqlConfig().port()));
        BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());
        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        sf.create();
    }


    /**
     * Servces GraphQL queries
     * @param acceptType accepted type of the client
     * @param contentType format type of the query
     * @param req GraphQL query
     * @return JSON-LD response for the given query
     */
    @POST
    @Path( "graphql" )
    public javax.ws.rs.core.Response query(@HeaderParam("accept")String acceptType,@HeaderParam("content-type") String contentType,String req){

        HGQLRequestService service = new HGQLRequestService(config);

        String query = "";
        try {
            query = contentType.equals("application-x/graphql")? consumeGraphQLBody(req) : consumeJSONBody(req);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String mime = MIME_MAP.getOrDefault(acceptType, null);
        contentType = MIME_MAP.containsKey(acceptType) ? acceptType : "application/json";
        Boolean graphQLCompatible = GRAPHQL_COMPATIBLE_TYPE.getOrDefault(acceptType, true);

        String type = contentType;
        int status = 200;

        Map<String, Object> result = service.results(query, mime);
        List<GraphQLError> errors = (List<GraphQLError>) result.get("errors");
        if (!errors.isEmpty()) {
            status = 400;
        }

        ObjectMapper mapper = new ObjectMapper();
        if (graphQLCompatible) {
            JsonNode data = null;
            try {
                data = mapper.readTree(new ObjectMapper().writeValueAsString(result));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return javax.ws.rs.core.Response
                    .status(status)
                    .header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","))
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Content-Type", type)
                    .entity(data.toString())
                    .build();
        } else {
            if (result.containsKey("data")) {
                return javax.ws.rs.core.Response
                        .status(status)
                        .header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","))
                        .header("Access-Control-Allow-Credentials", "true")
                        .header("Content-Type", type)
                        .entity(result.get("data").toString())
                        .build();
            } else {
                JsonNode errorsJson = null;
                try {
                    errorsJson = mapper.readTree(new ObjectMapper().writeValueAsString(errors));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                return javax.ws.rs.core.Response
                        .serverError()
                        .header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","))
                        .header("Access-Control-Allow-Credentials", "true")
                        .header("Content-Type", type)
                        .entity(errorsJson.toString())
                        .build();
            }
        }
    }

    /**
     * Serves schema introspection queries.
     * @param acceptType accepted type of the client
     * @param req GraphQL query
     * @return GraphQL schema of the HGQL instance
     */
    @GET
    @Path("graphql")
    public javax.ws.rs.core.Response introspectionQuery(@HeaderParam("accept")String acceptType,String req){
        boolean isRdfContentType =
                (MIME_MAP.containsKey(acceptType)
                        && GRAPHQL_COMPATIBLE_TYPE.containsKey(acceptType)
                        && !GRAPHQL_COMPATIBLE_TYPE.get(acceptType));
        String mime = isRdfContentType ? MIME_MAP.get(acceptType) : DEFAULT_MIME_TYPE;

        String type = isRdfContentType ? acceptType : DEFAULT_ACCEPT_TYPE;

        final String rdfSchemaOutput = config.getHgqlSchema().getRdfSchemaOutput(mime);
        return javax.ws.rs.core.Response
                .ok()
                .header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","))
                .header("Access-Control-Allow-Credentials", "true")
                .header("Content-Type", type)
                .entity(rdfSchemaOutput)
                .build();
    }

    /**
     * Serves the GraphiQL interface of the HGQL instance if requested with GET.
     * @return HTML page of GraphiQL of this service
     */
    @GET
    @Path("graphiql")
    public javax.ws.rs.core.Response graphiql(){
        Map<String, String> model = new HashMap<>();

        model.put("template", String.valueOf(config.getGraphqlConfig().graphQLPath()));

        final String graphiql_render = new VelocityTemplateEngine().render(new ModelAndView(model, "graphiql.vtl"));
        return javax.ws.rs.core.Response
                .ok()
                .header("Access-Control-Allow-Headers", StringUtils.join(headersList, ","))
                .header("Access-Control-Allow-Credentials", "true")
                .entity(graphiql_render)
                .build();
    }

}


