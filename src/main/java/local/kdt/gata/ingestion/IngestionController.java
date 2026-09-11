package local.kdt.gata.ingestion;

import local.kdt.gata.ingestion.model.IngestSrc;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {
    private final IngestionService service;
    private final ObjectMapper mapper;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "overwrite", required = false) Boolean overwrite,
            @RequestPart(value = "metadata", required = false) String jsonMetadata
            ) {
        try {
            long size = file.getSize();
//            Map<String, Object> metadata = ( jsonMetadata==null ) ? null : mapper.readValue(jsonMetadata, new TypeReference<Map<String, Object>>(){});
//            IngestedFileDto dto = IngestedFileDto.builder()
//                    .filename(file.getOriginalFilename())
//                    .ingestionDate(Instant.now())
//                    .ingestSrc(IngestSrc.REST)
//                    .overwrite(overwrite == null ? false : overwrite)
//                    .size(size)
//                    .metadata(metadata).build();
            String name = service.uploadFromRest(file);
            return ResponseEntity.ok("Upload "+name);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}