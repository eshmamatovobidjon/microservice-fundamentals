package com.learn.resource_processor.service.impl;

import com.learn.resource_processor.dto.SongDTO;
import com.learn.resource_processor.kafka.ResourceProducer;
import com.learn.resource_processor.service.ResourceProcessorService;
import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class ResourceProcessorServiceImpl implements ResourceProcessorService {
    private final ResourceServiceClient resourceServiceClient;
    private final SongServiceClient songServiceClient;
    private final ResourceProducer resourceProducer;

    public ResourceProcessorServiceImpl(ResourceServiceClient resourceServiceClient, SongServiceClient songServiceClient, ResourceProducer resourceProducer) {
        this.resourceServiceClient = resourceServiceClient;
        this.songServiceClient = songServiceClient;
        this.resourceProducer = resourceProducer;
    }

    @Override
    public void process(Long resourceId) {
        log.info("Starting processing for resourceId: {}", resourceId);
        byte[] resourceData = resourceServiceClient.getResourceData(resourceId);
        log.info("Fetched resource data for ID: {} ({} bytes)", resourceId, resourceData != null ? resourceData.length : 0);
        SongDTO songDTO = processMp3Resource(resourceData, resourceId);
        log.info("Extracted metadata for resourceId {}: {}", resourceId, songDTO);
        songServiceClient.saveSongMetadata(songDTO);
        log.info("Saved song metadata for resourceId: {}", resourceId);
        resourceProducer.sendId(resourceId);
        log.info("Sent resourceId {} to Kafka topic for further processing", resourceId);
        log.info("Finished processing for resourceId: {}", resourceId);
    }

    private SongDTO processMp3Resource(byte[] mp3Data, Long resourceId) {
        log.debug("Processing MP3 resource for resourceId: {}", resourceId);
        Metadata metadata = getMetadata(mp3Data);

        String title = getOrDefault(metadata, "title", "Unknown Title");
        String artist = getOrDefault(metadata, "xmpDM:artist", "Unknown Artist");
        String album = getOrDefault(metadata, "xmpDM:album", "Unknown Album");
        String releaseDate = getOrDefault(metadata, "xmpDM:releaseDate", "1900");
        String durationStr = getOrDefault(metadata, "xmpDM:duration", "0");

        String formattedDuration = convertDuration(durationStr);

        SongDTO songDTO = new SongDTO();
        songDTO.setId(resourceId);
        songDTO.setName(title);
        songDTO.setArtist(artist);
        songDTO.setAlbum(album);
        songDTO.setDuration(formattedDuration);
        songDTO.setYear(releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "1900");

        log.debug("MP3 metadata for resourceId {}: title={}, artist={}, album={}, year={}, duration={}", resourceId, title, artist, album, songDTO.getYear(), formattedDuration);
        return songDTO;
    }

    private static Metadata getMetadata(byte[] mp3Data) {
        Metadata metadata = new Metadata();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(mp3Data)) {
            Mp3Parser parser = new Mp3Parser();
            BodyContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            parser.parse(bais, handler, metadata, context);
        } catch (Exception e) {
            log.error("Error parsing MP3 metadata", e);
            throw new RuntimeException("Error parsing MP3 metadata", e);
        }
        return metadata;
    }

    private String getOrDefault(Metadata metadata, String key, String defaultValue) {
        String value = metadata.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private String convertDuration(String durationStr) {
        try {
            double seconds = Double.parseDouble(durationStr);
            int minutes = (int) (seconds / 60);
            int secs = (int) (seconds % 60);
            return String.format("%02d:%02d", minutes, secs);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse duration '{}', defaulting to 00:00", durationStr);
            return "00:00"; // Fallback if conversion fails
        }
    }
}
