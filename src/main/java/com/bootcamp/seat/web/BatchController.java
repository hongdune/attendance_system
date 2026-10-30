package com.bootcamp.seat.web;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.service.BatchService;
import com.bootcamp.seat.web.dto.Requests;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Requests.CreateBatch request) {
        Batch batch = batchService.create(request.name());
        return Map.of("id", batch.getId(), "name", batch.getName());
    }

    @DeleteMapping("/batches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        batchService.delete(id);
    }
}
