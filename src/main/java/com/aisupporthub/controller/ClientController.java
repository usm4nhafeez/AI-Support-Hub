package com.aisupporthub.controller;

import com.aisupporthub.model.dto.CreateClientRequest;
import com.aisupporthub.model.entity.Client;
import com.aisupporthub.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService service) { this.service = service; }

    //@Valid does not enforce any rules; it acts as a trigger telling Spring,
    //Look inside this object and execute any constraint validation rules defined on its fields.
    //You apply specific constraint annotations
    // (like @NotNull, @NotBlank, @Size, or @Email) to the fields of your data object
    @PostMapping
    public Client create(@Valid @RequestBody CreateClientRequest request) {
        return service.create(request);
    }
}
