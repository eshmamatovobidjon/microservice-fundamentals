package com.learn.resource_service;

import com.learn.resource_service.controller.ResourceController;
import com.learn.resource_service.service.ResourceService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = ResourceController.class)
public abstract class BaseContractTest {
    @Autowired
    private ResourceController resourceController;

    @MockitoBean
    private ResourceService resourceService;

    @BeforeEach
    public void setup() throws IOException {
        when(resourceService.uploadResource(any(byte[].class))).thenReturn(1L);

        byte[] mp3Bytes = Objects.requireNonNull(this.getClass()
                        .getClassLoader()
                        .getResourceAsStream("contracts/resource/test.mp3"))
                .readAllBytes();

        when(resourceService.getResourceContent(1L)).thenReturn(mp3Bytes);

        RestAssuredMockMvc.standaloneSetup(resourceController);
    }
}
