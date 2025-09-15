package com.learn.songservice;

import com.learn.songservice.controller.SongController;
import com.learn.songservice.entity.Song;
import com.learn.songservice.service.SongService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = SongController.class)
public abstract class BaseContractTest {
    @Autowired
    private SongController songController;

    @MockitoBean
    private SongService songService;

    @BeforeEach
    public void setup() throws IOException {

        Song song = new Song();
        song.setId(1L);
        when(songService.createSong(any())).thenReturn(song);

        RestAssuredMockMvc.standaloneSetup(songController);
    }
}
