package fr.sqq.achatgroupe.infrastructure.out.email;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** API transactionnelle Brevo : https://developers.brevo.com/reference/sendtransacemail */
@RegisterRestClient(configKey = "brevo")
@Path("/smtp/email")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BrevoClient {

    @POST
    void sendTransactionalEmail(@HeaderParam("api-key") String apiKey, BrevoEmailRequest request);
}
