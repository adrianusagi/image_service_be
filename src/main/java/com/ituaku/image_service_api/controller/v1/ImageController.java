package com.ituaku.image_service_api.controller.v1;

// Multipart and Web imports
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.ImmutableImageLoader;
import com.sksamuel.scrimage.Position;
import com.sksamuel.scrimage.webp.WebpWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

// Your custom DTO and Entities
import com.ituaku.image_service_api.model.v1.ImageFormats;
import com.ituaku.image_service_api.model.v1.ImageFormatsPublic;
import com.ituaku.image_service_api.model.v1.Images;
import com.ituaku.image_service_api.common.dto.GenericResponse;
import com.ituaku.image_service_api.common.exception.GlobalExceptionHandler;
import com.ituaku.image_service_api.repository.v1.ImageFormatsRepository;
import com.ituaku.image_service_api.repository.v1.ImagesRepository;

// Utility and Logging
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Configuration
public class ImageController {

    @Value("${app.image.delete-after}")
    private Integer deleteImgAfter;

    @Value("${app.image.upload-directory}")
    private String uploadDir;

    private final ImageFormatsRepository imageFormatsRepository;
    private final ImagesRepository imagesRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @GetMapping("/formats")
    public ResponseEntity<ImageFormatsResponse> getAllFormats() {
        List<ImageFormatsPublic> results = imageFormatsRepository.findAllProjectedBy(
            Sort.by(Sort.Direction.ASC, "customOrder")
            .and(Sort.by(Sort.Direction.ASC, "name")));
        
        return ResponseEntity.ok(new ImageFormatsResponse(200, "success", results));
    }

    @PostMapping("/upload")
    public ResponseEntity<GenericResponse<String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new GenericResponse<>(400, "Please select a file", null));
        }

        try {
            /** Log details for debugging */
            log.info("Received file: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());
            
            String uniqueID = UUID.randomUUID().toString().replace("-", "");

            /** Logic to save the file goes here */
            /** Store image */
            String oriFilename = file.getOriginalFilename();
            Integer fileSize = (int) file.getSize();
            LocalDateTime now = LocalDateTime.now();

            String extension = oriFilename.substring(oriFilename.lastIndexOf("."));
            String filename = StringUtils.stripFilenameExtension(oriFilename) + "-" + uniqueID + extension;

            Path path = Paths.get(uploadDir + filename);
            try {
                Long bytesRead = Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                if (bytesRead > 0) {
                    log.info("Successfully saved " + bytesRead + " bytes to " + path.toAbsolutePath());
                }
            } catch (IOException e) {
                // This is where you handle the "Result" of a failure
                log.info("Failed to save file: {}", e.getMessage());
                // In a Spring Controller, you'd likely throw a custom exception or return a 500 Error
            }

            /** Store image information to database */
            Images image = new Images(null, oriFilename, filename, fileSize, now, null);
            imagesRepository.save(image);

            /** Delete the files after particular time */
            scheduler.schedule(() -> {
                log.info("One-time delayed task executed for file: {}", filename);
                
                try {
                    // Attempt to delete the file
                    Path filePath = Paths.get(uploadDir).resolve(filename);
                    
                    boolean deleted = Files.deleteIfExists(filePath);
                    
                    if (deleted) {
                        log.info("File deleted successfully: {}", filename);

                        LocalDateTime deletedAt = LocalDateTime.now();
                        image.setDeletedAt(deletedAt);
                        imagesRepository.save(image);
                    } else {
                        log.warn("File not found, could not delete: {}", filename);
                    }
                } catch (IOException e) {
                    log.error("Failed to delete file: " + filename, e);
                }
            }, deleteImgAfter, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(new GenericResponse<>(200, "Upload successful", filename));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new GenericResponse<>(500, "Upload failed", null));
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Resource> processImage(@RequestBody ImageProcessRequest request) {

        try {
            String filename = request.getFilename();
            log.info("filename: {}", filename);

            File inputFile = new File(uploadDir + filename);

            // Check if it's HEIC
            if (filename.endsWith(".heic") || filename.endsWith(".heif")) {
                /** If heic image */
                inputFile = handleHeicConversion(inputFile);
            }

            // // Define the output path with .webp extension
            String webpFilename = StringUtils.stripFilenameExtension(filename) + ".webp";
            File outputFile = new File(uploadDir + webpFilename);

            ImageFormats requestFormat = request.getFormat();
            Integer formatId = requestFormat.getId();
            String jsonConfig = requestFormat.getConfigs();

            ObjectMapper mapper = new ObjectMapper();
            try {
                ImageFormatConfig config = mapper.readValue(jsonConfig, ImageFormatConfig.class);
                
                String proc_mode = config.getMode();
                Integer width = config.getWidth();
                Integer height = config.getHeight();
                String position = config.getPosition();
                log.info("format config: {}", config);

                try {
                    ImmutableImage img = ImmutableImage.loader().fromFile(inputFile);
                    log.info("Successfully loaded image: {}x{}", img.width, img.height);
                    
                    if(proc_mode.contains("[resize]")){
                        log.info("resize mode");
                        if(width != null && height == null){
                            img.scaleToWidth(width)
                                .output(WebpWriter.DEFAULT, outputFile);
                            log.info("scaleToWidth {}", width);
                        } else if(width == null && height != null){
                            img.scaleToHeight(height)
                                .output(WebpWriter.DEFAULT, outputFile);
                                log.info("scaleToHeight {}", height);
                        } else if(width != null && height != null){
                            img.scaleTo(width, height)
                                .output(WebpWriter.DEFAULT, outputFile);
                                log.info("scaleTo {}x{}", width, height);
                        }
                    } else if(proc_mode.contains("[crop]")){
                        if(width != null && height != null){
                            Float ratioSrc = (float) img.width / img.height;
                            Float ratioTgt = (float) width / height;

                            log.info("ration source :{}, target:{}", ratioSrc, ratioTgt);
    
                            if((ratioSrc < 1 && ratioTgt > 1) || ratioSrc < ratioTgt){
                                log.info("scaleToWidth");
                                if(position.equals("topCenter")){
                                    img.scaleToWidth(width).resizeTo(width, height, Position.TopCenter).output(WebpWriter.DEFAULT, outputFile);
                                    log.info("scaleToWidth {} then resizeTo {}x{} position: TopCenter", width, width, height);
                                } else if(position.equals("bottomCenter")){
                                    img.scaleToWidth(width).resizeTo(width, height, Position.BottomCenter).output(WebpWriter.DEFAULT, outputFile);
                                    log.info("scaleToWidth {} then resizeTo {}x{} position: BottomCenter", width, width, height);
                                } else {
                                    img.scaleToWidth(width).resizeTo(width, height).output(WebpWriter.DEFAULT, outputFile);
                                    log.info("scaleToWidth {} then resizeTo {}x{} position: center", width, width, height);
                                }
                            } else if((ratioSrc > 1 && ratioTgt < 1) || ratioSrc > ratioTgt){
                                log.info("scaleToHeight");
                                if(position.equals("topCenter"))
                                    img.scaleToHeight(height).resizeTo(width, height, Position.TopCenter).output(WebpWriter.DEFAULT, outputFile);
                                else if(position.equals("bottomCenter"))
                                    img.scaleToHeight(height).resizeTo(width, height, Position.BottomCenter).output(WebpWriter.DEFAULT, outputFile);
                                else img.scaleToHeight(height).resizeTo(width, height).output(WebpWriter.DEFAULT, outputFile);
                            } else {
                                log.info("no scale");
                                img.resizeTo(width, height).output(WebpWriter.DEFAULT, outputFile);
                            }
                        }
                    } else {
                        log.info("no reize, no crop");
                        ImmutableImage.loader()
                            .fromFile(inputFile)
                            .output(WebpWriter.DEFAULT, outputFile);
                    }

                    Path filePath = Paths.get(uploadDir).resolve(webpFilename).normalize();
                    Resource resource = new UrlResource(filePath.toUri());

                    // Automatically determine if it's a webp, png, or jpg
                    String contentType = Files.probeContentType(filePath);
                    if (contentType == null) contentType = "image/webp"; // Fallback

                    /** Delete the files after particular time */
                    scheduler.schedule(() -> {
                        try {
                            // Attempt to delete the file
                            Path delfilePath = Paths.get(uploadDir).resolve(webpFilename);
                            boolean deleted = Files.deleteIfExists(delfilePath);

                            String heicTempFilename = StringUtils.stripFilenameExtension(filename) + "-heic.jpg";
                            Path deleteInputFilePath = Paths.get(uploadDir).resolve(heicTempFilename);
                            boolean deleteInputFile = Files.deleteIfExists(deleteInputFilePath);
                            
                            if (deleted) {
                                log.warn("File deleted successfully: {}", webpFilename);
                            } else {
                                log.warn("File not found, could not delete: {}", webpFilename);
                            }
                        } catch (IOException e) {
                            log.error("Failed to delete file: " + webpFilename, e);
                        }
                    }, deleteImgAfter, TimeUnit.SECONDS);

                    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                        .header("Content-Disposition", "inline; filename=\"" + webpFilename + "\"")
                        .body(resource);
                } catch (IOException e) {
                    log.error("Failed to write WebP output to disk at path: {}", outputFile.getAbsolutePath());
                    log.error("Failed error: ", e);
                    // Return a 500 error or throw a custom exception here
                    return ResponseEntity.internalServerError().build();
                }
            } catch (JacksonException e) {
                log.info("failed to proceses image: {}", e);
                return ResponseEntity.internalServerError().build();
            }
        } catch (Exception e) {
            log.info("failed to proceses image: {}", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public File handleHeicConversion(File inputFile) throws Exception {
        log.info("Pre precessing HEIC file format");
        File outputFile = new File(inputFile.getParent(), 
                               StringUtils.stripFilenameExtension(inputFile.getName()) + "-heic.jpg");

        log.info("inputFile: {}", inputFile.getAbsolutePath());
        log.info("outputFile: {}", outputFile.getAbsolutePath());
        // Using ImageMagick is safer for existing files as it handles metadata better
        ProcessBuilder pb = new ProcessBuilder(
            "convert", 
            inputFile.getAbsolutePath(), 
            outputFile.getAbsolutePath()
        );

        // Redirect error stream so we can see why it fails in Docker logs
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // If a 1920px+ HEIC is corrupted, we don't want the thread to hang forever
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("HEIC conversion timed out for: " + inputFile.getName());
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("HEIC conversion failed. Exit code: " + process.exitValue());
        }

        return outputFile;
    }
}

@Data
@AllArgsConstructor
class ImageFormatsResponse {
    private int status;
    private String message;
    private List<ImageFormatsPublic> data;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class ImageProcessRequest {
    private String filename;
    private ImageFormats format;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class ImageFormatConfig {
    private String mode;
    private Integer width;
    private Integer height;
    private String position;
}