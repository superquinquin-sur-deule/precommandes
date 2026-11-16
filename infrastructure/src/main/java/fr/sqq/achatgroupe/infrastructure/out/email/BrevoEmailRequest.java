package fr.sqq.achatgroupe.infrastructure.out.email;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrevoEmailRequest(
        Contact sender,
        List<Contact> to,
        String subject,
        String htmlContent,
        String textContent,
        List<String> tags
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Contact(String email, String name) {
    }
}
