package com.learn.storageservice.controller;

import com.learn.storageservice.dto.StorageDTO;
import com.learn.storageservice.entity.Storage;
import com.learn.storageservice.entity.StorageType;
import com.learn.storageservice.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/storages")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    public ResponseEntity<?> createStorage(@Valid @RequestBody StorageDTO storageDTO) {
        Storage created = storageService.createStorage(storageDTO);
        return ResponseEntity.ok(Map.of("id", created.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStorage(@PathVariable("id") Long id) {
        Storage storage = storageService.getStorage(id);
        return ResponseEntity.ok(storage);
    }

    @GetMapping()
    public ResponseEntity<?> getStorageByType(@RequestParam(value = "type") StorageType type) {
        Storage storage = storageService.getStorageByType(type);
        return ResponseEntity.ok(storage);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteStorages(@RequestParam("id") String csvIds) {
        List<Long> deletedIds = storageService.deleteStorages(csvIds);
        return ResponseEntity.ok(Map.of("ids", deletedIds));
    }
}
