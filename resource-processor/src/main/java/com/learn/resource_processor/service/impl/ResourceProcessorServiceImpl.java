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

import java.io.ByteArrayInputStream;

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
        byte[] resourceData = resourceServiceClient.getResourceData(resourceId);
        System.out.println("Fetched resource data for ID: " + resourceId);
        SongDTO songDTO = processMp3Resource(resourceData, resourceId);
        songServiceClient.saveSongMetadata(songDTO);
        resourceProducer.sendId(resourceId);
        System.out.println("Processed resource ID: " + resourceId);
    }

    private SongDTO processMp3Resource(byte[] mp3Data, Long resourceId) {
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
            return "00:00"; // Fallback if conversion fails
        }
    }
}
