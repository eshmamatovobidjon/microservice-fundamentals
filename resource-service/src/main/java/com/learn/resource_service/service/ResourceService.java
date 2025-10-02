package com.learn.resource_service.service;

import com.learn.resource_service.entity.Resource;

import java.util.List;

public interface ResourceService {
    Long uploadResource(byte[] mp3Data);
    Resource getResourceById(Long id);
    List<Long> deleteResourcesByIds(String csvIds);
    byte[] getResourceContent(Long id);

    void process(Long resourceId);
}
