package org.hypergraphql.schemaextraction;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.jena.rdf.model.Resource;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA_NAMESPACE;
import static org.hypergraphql.config.schema.HGQLVocabulary.HGQL_SCHEMA_NAMESPACE_PREFIX;


/**
 * This Class serves as a prefix lookup for RDF resources. It first checks if a prefix is user defined if that is not the
 * case prefix.cc is asked for a prefix. If prefix.cc does not offer a prefix a prefix is generated.
 */
public class PrefixService {
    private Map<String,String> namespaceMapping = new HashMap<String, String>();   //namespace - prefix
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final int DEFAULT_PREFIX_LENGTH = 4;
    static final String ABC = "abcdefghijklmnopqrstuvwxyz";
    static SecureRandom random = new SecureRandom();


    public PrefixService(){
        this(null);
    }

    public PrefixService(Map<String,String> namespaceMapping){
        if(namespaceMapping != null){
            // filter out any occurrences of the internal IRI (namespace and prefix of it)
            namespaceMapping = namespaceMapping.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(HGQL_SCHEMA_NAMESPACE) && !entry.getValue().equals(HGQL_SCHEMA_NAMESPACE_PREFIX))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            this.namespaceMapping = namespaceMapping;
        }
        this.namespaceMapping.put(HGQL_SCHEMA_NAMESPACE, HGQL_SCHEMA_NAMESPACE_PREFIX);
    }

    public Map<String, String> getNamespaceMapping() {
        return namespaceMapping;
    }

    /**
     * Returns the id of the given URI. The ID contains the prefix of the namespace and the name of the resource (prefix_name)
     * @param uri URI form which the prefix is needed
     * @return Id of the given URI
     */
    public String getId(Resource uri){
        String suffix = uri.getLocalName();
        String prefix = getPrefix(uri);
        return prefix + "_" + suffix;
    }

    /**
     * Return the prefix of the given uri. If the prefix is not already in the namespaceMapping then the prefix is either
     * fetched or randomly generate if no commonly used prefix is found.
     * @param uri URI form which the prefix is needed
     * @return Prefix of the given URI
     */
    public String getPrefix(Resource uri){
        String namespace = uri.getNameSpace();
        if(this.namespaceMapping.containsKey(namespace)){
            return this.namespaceMapping.get(namespace); // prefix exist
        }else{
            String prefix = fetchPrefix(namespace);
            if(prefix.equals("")){  //No prefix could be fetched generate one
                do{
                    prefix = randomString(DEFAULT_PREFIX_LENGTH);
                }while(this.namespaceMapping.containsValue(prefix));   //  potential bottleneck if schema contains more then 26^DEFAULT_PREFIX_LENGTH namespaces
            }
            this.namespaceMapping.put(namespace, prefix);
            return prefix;
        }
    }

    /**
     * Find the most common prefix for the given namespace. To find the prefix www.prefix.cc is queried.
     * If no prefix is found return a empty String.
     * @param namespace namespace
     * @return Prefix of the given namespace, if none is found return empty string
     */
    private String fetchPrefix(String namespace) {
        String query_uri = "http://prefix.cc/reverse";
        URIBuilder builder = new URIBuilder();
        if(namespace.endsWith("#")){
            namespace = namespace.substring(0, namespace.length() - 1);
        }
        builder.setScheme("http").setHost("prefix.cc").setPath("/reverse")
                .setParameter("uri", namespace)
                .setParameter("format", "json");
        URI uri = null;
        HttpGet request = null;
        try {
            uri = builder.build();
            String url = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8.name());
            request = new HttpGet(url);
        } catch (URISyntaxException e) {
            return "";
        } catch (UnsupportedEncodingException e) {
            //e.printStackTrace();
            return "";
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            Header headers = entity.getContentType();
            if (entity != null) {
                // return it as a String
                String result = EntityUtils.toString(entity);
                JSONObject resJson = new JSONObject(result);   // Result of the Query contains the most common prefix
                return resJson.keys().next();   // return the first, it is most likely the most common
            }

        } catch (IOException e) {
            return "";
        } catch (JSONException e){
            return "";
        }
        return "";
    }

    /**
     * Generate a random string out of the lower case alphabet with the given length.
     * @param len Length of the random string
     * @return random string in lower case
     */
    String randomString( int len ){
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( ABC.charAt( random.nextInt(ABC.length() ) ));
        return sb.toString();
    }
}
