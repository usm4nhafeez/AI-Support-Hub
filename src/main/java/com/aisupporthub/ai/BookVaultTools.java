package com.aisupporthub.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Component
public class BookVaultTools {
    private final RestClient restClient;

    public BookVaultTools(@Value("${support.demo.bookvault-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Tool(name = "getBookDetails", description = "Get book details from BookVault by title. Use this when a customer asks whether a specific book exists or is available.")
    public String getBookDetails(@ToolParam(description = "Book title") String title) {
        try {
            String response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/books").queryParam("title", title).build())
                .retrieve()
                .body(String.class);
            return response == null ? "No book information was returned." : response;
        } catch (Exception ex) {
            return "BookVault is currently unavailable. Do not invent book information.";
        }
    }
}
