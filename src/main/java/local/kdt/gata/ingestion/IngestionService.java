package local.kdt.gata.ingestion;

import jakarta.annotation.PostConstruct;
import local.kdt.gata.common.util.FileUtil;
import local.kdt.gata.event.EventService;
import local.kdt.gata.ingestion.model.Ingestion;
import local.kdt.gata.ingestion.model.IngestSrc;
import local.kdt.gata.ingestion.model.IngestionRepository;
import local.kdt.gata.ingestion.model.IngestionStatus;
import local.kdt.gata.minio.BucketNotificationListener;
import local.kdt.gata.minio.MinioService;
import local.kdt.gata.minio.MinioUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionService implements BucketNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionProperties properties;
    private final IngestionRepository repository;
    private final EventService eventService;
    private final MinioService minioService;

    public IngestionService(IngestionProperties properties, IngestionRepository repository, EventService eventService,
                            MinioService minioService) {
        this.properties = properties;
        this.repository = repository;
        this.eventService = eventService;
        this.minioService = minioService;
        this.properties.setS3IngestFolder(MinioUtil.conditionFolder(properties.getS3IngestFolder()));
    }

    @PostConstruct
    public void init() {
        if ( minioService.isEnabled() ) {
            try {
                minioService.ensureFolder(properties.getS3IngestFolder());
                minioService.registerListener(properties.getS3IngestFolder(), this);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize MinIO bucket/folders", e);
            }
        }
    }

    @Override
    public boolean minioBucketNotification(String folder, String filename, long objectSize) {
        if ( filename.startsWith(".") )
            return false;
        LOG.info("minioBucketNotification Folder={}, name={}, Size={}", folder, filename, objectSize);
        Ingestion ingest = Ingestion.builder().filename(filename).s3Folder(folder).status(IngestionStatus.INGESTED).ingestSrc(IngestSrc.MINIO).build();
        eventService.post(ingest);
        return true;
    }

    public String ingestFromRest(MultipartFile file) throws Exception {
        String filename = FileUtil.getJustFilename(file.getOriginalFilename());
        String key =  properties.getS3IngestFolder()+filename;
        minioService.putObject(key, file);
        LOG.info("ingestFromRest key={}", key);
        Ingestion ingest = Ingestion.builder().filename(filename).s3Folder(properties.getS3IngestFolder()).status(IngestionStatus.INGESTED).ingestSrc(IngestSrc.REST).build();
        eventService.post(ingest);
        return filename;
    }

    public Ingestion getIngestByFilename(String filename) {
        return repository.getProcessByFilename(filename);
    }

    @Transactional
    public Ingestion addIngest(String filename, IngestSrc ingestSrc) {
        System.out.println("ingestSrc="+ingestSrc);
        repository.addPipeline(filename, 1);
        return repository.getProcessByFilename(filename);
    }

    @Transactional
    public void updateIngest(Ingestion ingeest) {
        repository.updateIngest(ingeest.getFilename(), ingeest.getStatus().getId(),
                ingeest.getIngestSrc().getId(), ingeest.getFailureReason(), ingeest.getS3Folder(), ingeest.getCompletedAt());
    }

    @Transactional
    public void deleteIngest(String filename) {
        repository.deletePipeline(filename);
    }

/*
    @Transactional
    public void processMinerUZip(InputStream zipStream, String originalFilename) throws Exception {
        LOG.info("Starting ingestion workflow for package file: {}", originalFilename);

        List<ParsedChunk> chunks = new ArrayList<>();
        List<MarkdownSection> sections = new ArrayList<>();
        Map<String, Long> assetPathToIdMap = new HashMap<>();

        String markdownObjectKey = null;
        int assetCount = 0;

        // Create isolated temp directory to unpack safely
        Path tempDir = Files.createTempDirectory("gata-ingest-");

        try {
            // Because ZipInputStream is forward-only, write files locally first to handle dependency ordering
            // (E.g., upload assets first, register them in db, and link chunk elements)
            byte[] zipBytes = zipStream.readAllBytes();

            // Step 1: Scan ZIP for binary assets, upload to MinIO, and persist metadata
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;

                    // Capture all extraction images generated by MinerU (diagrams, figures, math equations)
                    if (name.startsWith("images/") || name.contains("/images/")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        zis.transferTo(bos);
                        byte[] imgBytes = bos.toByteArray();

                        String cleanImageName = FilenameUtils.getName(name);
                        String objectKey = "documents/" + originalFilename + "/images/" + cleanImageName;
                        String mimeType = getMimeType(cleanImageName);

                        // Upload asset to MinIO bucket
                        uploadBytesToMinio(objectKey, imgBytes, mimeType);

                        // Save metadata reference to the database and retrieve its generated primary key
                        long assetId = registerDocAsset(originalFilename, "image", objectKey, mimeType);
                        assetPathToIdMap.put(name, assetId);
                        assetCount++;
                    }
                    zis.closeEntry();
                }
            }

            // Step 2: Parse textual data structures
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;

                    if (name.endsWith("_content_list_v2.json")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        zis.transferTo(bos);
                        chunks = contentListParser.parse(new ByteArrayInputStream(bos.toByteArray()));
                    } else if (name.endsWith(".md")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        zis.transferTo(bos);
                        byte[] mdBytes = bos.toByteArray();

                        sections = markdownParser.parse(new ByteArrayInputStream(mdBytes));

                        // Store the completed markdown file back to MinIO as a consolidated RAG source file
                        markdownObjectKey = "documents/" + originalFilename + "/" + FilenameUtils.getName(name);
                        uploadBytesToMinio(markdownObjectKey, mdBytes, "text/markdown");
                    }
                    zis.closeEntry();
                }
            }
        } finally {
            // Clean up temporary workspace safely
            try {
                Files.walk(tempDir)
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                LOG.warn("Failed to clean up temp directories safely: {}", e.getMessage());
            }
        }

        // Link chunk objects with their respective uploaded database asset IDs
        List<ParsedChunk> linkedChunks = associateAssetsWithChunks(chunks, assetPathToIdMap);

        // Phase 2: Embeddings generation
        List<String> chunkTexts = linkedChunks.stream().map(ParsedChunk::textContent).toList();
        List<Embedding> chunkEmbeddings = embeddingService.embedAll(chunkTexts);

        List<String> sectionTexts = sections.stream().map(MarkdownSection::content).toList();
        List<Embedding> sectionEmbeddings = embeddingService.embedAll(sectionTexts);

        // Phase 3: Persist central Document Record details
        Documents doc = Documents.builder()
                .filename(originalFilename)
                .ingestStatus(IngestStatus.COMPLETED)
                .chunkCount(linkedChunks.size() + sections.size())
                .assetCount(assetCount)
                .markdownObjectKey(markdownObjectKey)
                .ingestedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        repository.save(doc);

        // Phase 4: Batch upsert structured vector contents
        docChunkStore.batchUpsert(originalFilename, linkedChunks, chunkEmbeddings);
        docChunkStore.batchUpsertMarkdown(originalFilename, sections, sectionEmbeddings, linkedChunks.size());

        LOG.info("Ingestion process completed successfully for document={}. Extracted chunks={}, assets={}",
                originalFilename, linkedChunks.size(), assetCount);
    }

    private void uploadBytesToMinio(String objectKey, byte[] data, String contentType) throws Exception {
        LOG.debug("Uploading stream artifact to MinIO. Key={}, Size={} bytes", objectKey, data.length);
        try (InputStream bais = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectKey)
                            .stream(bais, data.length, -1)
                            .contentType(contentType)
                            .build()
            );
        }
    }

    private long registerDocAsset(String filename, String assetType, String objectKey, String mimeType) {
        String sql = """
            INSERT INTO doc_assets (filename, asset_type, object_key, bucket, mime_type)
            VALUES (:filename, :assetType, :objectKey, :bucket, :mimeType)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("filename", filename)
                .addValue("assetType", assetType)
                .addValue("objectKey", objectKey)
                .addValue("bucket", minioBucket)
                .addValue("mimeType", mimeType);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("Failed to register document asset; no primary ID was returned.");
        }
        return key.longValue();
    }

    private List<ParsedChunk> associateAssetsWithChunks(List<ParsedChunk> chunks, Map<String, Long> assetPathToIdMap) {
        List<ParsedChunk> linked = new ArrayList<>();
        for (ParsedChunk chunk : chunks) {
            Map<String, Object> metadata = chunk.metadata() != null ? new HashMap<>(chunk.metadata()) : new HashMap<>();

            // Extract the path if it exists inside MinerU's nested map structure
            String imagePath = getImagePathFromChunk(chunk);
            if (imagePath != null && assetPathToIdMap.containsKey(imagePath)) {
                long dbAssetId = assetPathToIdMap.get(imagePath);
                metadata.put("document_asset_id", dbAssetId);
            }

            linked.add(new ParsedChunk(
                    chunk.pageIdx(),
                    chunk.componentIdx(),
                    chunk.chunkType(),
                    chunk.subType(),
                    chunk.textContent(),
                    chunk.rawContentJson(),
                    chunk.bbox(),
                    metadata
            ));
        }
        return linked;
    }

    private String getImagePathFromChunk(ParsedChunk chunk) {
        // If it's an image or table chunk, find its reference location map
        if (chunk.metadata() == null) return null;
        Object sourceObj = chunk.metadata().get("image_source");
        if (sourceObj instanceof Map<?, ?> sourceMap) {
            return Objects.toString(sourceMap.get("path"), null);
        }
        return null;
    }

    private String getMimeType(String filename) {
        String ext = FilenameUtils.getExtension(filename).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }
*/
}