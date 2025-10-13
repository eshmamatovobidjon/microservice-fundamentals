package com.learn.songservice.service.impl;

import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.exception.ConflictException;
import com.learn.songservice.repository.SongRepository;
import com.learn.songservice.service.SongService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
public class SongServiceImpl implements SongService {

    private final SongRepository songRepository;

    public SongServiceImpl(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Override
    public Song createSong(SongDTO songDTO) {
        Song song = convertToEntity(songDTO);
        if (songRepository.existsById(song.getId())) {
            log.warn("Song metadata for this resource already exists id = {}", song.getId());
            throw new ConflictException("Song metadata for this resource already exists id = " + song.getId());
        }
        log.info("Creating new song: {}", song);
        return songRepository.save(song);
    }

    @Override
    public Song getSong(Long id) {
        validateId(id);
        log.info("Fetching song with ID: {}", id);
        return songRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Song with ID={} not found", id);
                    return new NoSuchElementException("Song with ID=" + id + " not found");
                });
    }

    @Override
    public List<Long> deleteSongs(String csvIds) {
        validateCsvIds(csvIds);
        String[] idArray = csvIds.split(",");
        List<Long> deletedIds = new ArrayList<>();
        for (String idStr : idArray) {
            try {
                Long id = Long.valueOf(idStr.trim());
                if (songRepository.existsById(id)) {
                    songRepository.deleteById(id);
                    deletedIds.add(id);
                    log.info("Deleted song with ID: {}", id);
                } else {
                    log.warn("Song with ID {} does not exist, skipping delete", id);
                }
            } catch (NumberFormatException ignored) {
                log.warn("Invalid song ID format in deleteSongs: '{}', skipping", idStr);
            }
        }
        return deletedIds;
    }

    private Song convertToEntity(SongDTO dto) {
        Song song = new Song();
        song.setId(dto.getId());
        song.setName(dto.getName());
        song.setArtist(dto.getArtist());
        song.setAlbum(dto.getAlbum());
        song.setDuration(dto.getDuration());
        song.setYear(dto.getYear());
        return song;
    }

    public void validateId(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid ID: {}", id);
            throw new IllegalArgumentException("Invalid ID");
        }
    }

    public void validateCsvIds(String csvIds) {
        if (csvIds == null || csvIds.isEmpty()) {
            log.error("CSV IDs are required but got: '{}'", csvIds);
            throw new IllegalArgumentException("CSV IDs are required");
        }
        if (csvIds.length() >= 200) {
            log.error("CSV string length must be less than 200 characters. Got {}", csvIds.length());
            throw new IllegalArgumentException("CSV string length must be less than 200 characters. Got " + csvIds.length());
        }
        String[] ids = csvIds.split(",");
        for (String idStr : ids) {
            try {
                long id = Long.parseLong(idStr.trim());
                validateId(id);
            } catch (NumberFormatException e) {
                log.error("Invalid ID format in CSV: '{}'", idStr);
                throw new IllegalArgumentException("Invalid ID format: " + idStr);
            }
        }
    }
}
