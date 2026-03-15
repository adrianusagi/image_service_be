package com.ituaku.image_service_api.controller.v1;

import com.ituaku.image_service_api.common.exception.GlobalExceptionHandler;
import com.ituaku.image_service_api.model.v1.ImageFormats;
import com.ituaku.image_service_api.model.v1.ImageFormatsPublic;
import com.ituaku.image_service_api.repository.v1.ImageFormatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageFormatsRepository imageFormatsRepository;

    @GetMapping("/formats")
    public ResponseEntity<ImageFormatsResponse> getAllFormats() {
        List<ImageFormatsPublic> results = imageFormatsRepository.findAllProjectedBy(
            Sort.by(Sort.Direction.ASC, "customOrder")
            .and(Sort.by(Sort.Direction.ASC, "name")));
        
        return ResponseEntity.ok(new ImageFormatsResponse(200, "success", results));
    }
}

@Data
@AllArgsConstructor
class ImageFormatsResponse {
    private int status;
    private String message;
    private List<ImageFormatsPublic> data;
}