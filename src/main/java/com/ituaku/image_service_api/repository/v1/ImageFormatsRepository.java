package com.ituaku.image_service_api.repository.v1;

import com.ituaku.image_service_api.model.v1.ImageFormats;
import com.ituaku.image_service_api.model.v1.ImageFormatsPublic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;

import java.util.List;

@Repository
public interface ImageFormatsRepository extends JpaRepository<ImageFormats, Integer> {
    // Standard methods like .findAll() and .findById() are already included!
    List<ImageFormatsPublic> findAllProjectedBy(Sort sort);
}