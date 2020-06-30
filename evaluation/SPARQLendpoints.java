import org.apache.commons.io.IOUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class SPARQLendpoints{
    public void public static void main(String[]args){
        ARQ.init();
        Model model = ModelFactory.createDefaultModel();
        model.read(new FileInputStream("data/raw/persons_and_cars.json"), null, type);

        LOGGER.info("Build up model from file {}", type);
        Dataset ds = DatasetFactory.create(model);
        FusekiServer server = FusekiServer.create()
                .add("/dataset", ds)
                .build() ;
        server.start() ;

    }
}

