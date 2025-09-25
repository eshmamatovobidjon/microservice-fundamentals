package com.learn.songservice.service.impl;

import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.exception.ConflictException;
import com.learn.songservice.repository.SongRepository;
import com.learn.songservice.service.SongService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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
            throw new ConflictException("Song metadata for this resource already exists id = " + song.getId());
        }
        System.out.println("Song to be created: " + song);
        return songRepository.save(song);
    }

    @Override
    public Song getSong(Long id) {
        validateId(id);
        return songRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Song with ID=" + id + " not found"));
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
                }
            } catch (NumberFormatException ignored) {
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
            throw new IllegalArgumentException("Invalid ID");
        }
    }

    public void validateCsvIds(String csvIds) {
        if (csvIds == null || csvIds.isEmpty()) {
            throw new IllegalArgumentException("CSV IDs are required");
        }
        if (csvIds.length() >= 200) {
            throw new IllegalArgumentException("CSV string length must be less than 200 characters. Got " + csvIds.length());
        }
        String[] ids = csvIds.split(",");
        for (String idStr : ids) {
            try {
                long id = Long.parseLong(idStr.trim());
                validateId(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid ID format: " + idStr);
            }
        }
    }
}

