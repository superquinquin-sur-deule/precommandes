package fr.sqq.achatgroupe.acceptance.steps;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sqq.achatgroupe.acceptance.support.FakeBrevo;
import fr.sqq.achatgroupe.acceptance.support.TestContext;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Et;
import io.cucumber.java.fr.Quand;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class OrderConfirmationEmailSteps {

    @Inject
    TestContext testContext;

    @Inject
    FakeBrevo.Mailbox mailbox;

    /** Le faux Brevo conserve les emails de toute la suite : chaque scénario utilise ses propres adresses. */
    private final String scenarioToken = UUID.randomUUID().toString().substring(0, 8);

    private String email;
    private JsonPath order;

    private String unique(String address) {
        return scenarioToken + "." + address;
    }

    @Quand("{string} passe une commande de {int} unités du produit")
    public void passeUneCommande(String email, int quantity) {
        this.email = unique(email);
        order = postOrder(this.email, quantity, null).then().statusCode(201).extract().jsonPath();
    }

    @Quand("{string} passe deux fois la même commande avec la même clé d'idempotence")
    public void passeDeuxFoisLaMemeCommande(String email) {
        String key = UUID.randomUUID().toString();
        postOrder(unique(email), 1, key).then().statusCode(201);
        postOrder(unique(email), 1, key).then().statusCode(201);
    }

    @Quand("{string} tente de commander {int} unités du produit")
    public void tenteDeCommander(String email, int quantity) {
        int status = postOrder(unique(email), quantity, null).statusCode();
        assertTrue(status >= 400, "La commande aurait dû être refusée, statut " + status);
    }

    @Alors("un email de confirmation est envoyé via Brevo à {string}")
    public void unEmailEstEnvoye(String recipient) {
        List<FakeBrevo.ReceivedEmail> emails = mailbox.sentTo(unique(recipient));
        assertEquals(1, emails.size(), "Un email de confirmation doit être envoyé");
        FakeBrevo.ReceivedEmail sent = emails.get(0);
        assertEquals("test-brevo-key", sent.apiKey());
        assertEquals("commandes@achat-groupe.test", sent.body().path("sender").path("email").asText());
        assertEquals("Claire Martin", sent.body().path("to").path(0).path("name").asText());
    }

    @Et("l'email reprend le numéro de commande, le produit commandé, le total et le créneau de retrait")
    public void lEmailReprendLeDetail() {
        JsonNode body = mailbox.sentTo(email).get(0).body();
        String orderNumber = order.getString("data.orderNumber");
        String total = NumberFormat.getCurrencyInstance(Locale.FRANCE)
                .format(new BigDecimal(order.getString("data.totalAmount")))
                .replace(' ', ' ');
        String slotDay = String.valueOf(LocalDate.now().plusDays(30).getDayOfMonth());

        assertEquals("Confirmation de ta commande " + orderNumber, body.path("subject").asText());
        for (String content : List.of(body.path("htmlContent").asText(), body.path("textContent").asText())) {
            assertTrue(content.contains(orderNumber), "numéro de commande absent : " + content);
            assertTrue(content.contains("Produit stock limité"), "produit absent : " + content);
            assertTrue(content.contains(total), "total " + total + " absent : " + content);
            assertTrue(content.contains("10h00") && content.contains("11h00") && content.contains(slotDay),
                    "créneau absent : " + content);
            assertTrue(content.contains("Le règlement se fait en magasin"), "mention de paiement absente : " + content);
        }
    }

    @Alors("un seul email de confirmation est envoyé à {string}")
    public void unSeulEmail(String recipient) {
        assertEquals(1, mailbox.sentTo(unique(recipient)).size());
    }

    @Alors("aucun email de confirmation n'est envoyé à {string}")
    public void aucunEmail(String recipient) {
        assertEquals(0, mailbox.sentTo(unique(recipient)).size());
    }

    private io.restassured.response.Response postOrder(String email, int quantity, String idempotencyKey) {
        String key = idempotencyKey != null ? "\"" + idempotencyKey + "\"" : "null";
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "customerFirstName": "Claire",
                            "customerLastName": "Martin",
                            "email": "%s",
                            "phone": "06 12 34 56 78",
                            "timeSlotId": %d,
                            "idempotencyKey": %s,
                            "items": [{"variantId": %d, "quantity": %d}]
                        }
                        """.formatted(email, testContext.timeSlotIds().get(0), key,
                        testContext.variantIds().get(0), quantity))
                .when()
                .post("/api/ventes/" + testContext.venteId() + "/orders");
    }
}
