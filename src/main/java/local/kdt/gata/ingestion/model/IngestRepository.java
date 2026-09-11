package local.kdt.gata.ingestion.model;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IngestRepository extends JpaRepository<Ingest, String> {

    @Query(value = "SELECT MIN(ingested_at) FROM gata_ingest", nativeQuery = true)
    LocalDateTime getEarliestIngestedDate();

    @Query(value = "SELECT MAX(ingested_at) FROM gata_ingest", nativeQuery = true)
    LocalDateTime getLatestIngestedDate();

    @Query(value = "SELECT * FROM gata_ingest WHERE ingest_status = :status", nativeQuery = true)
    List<Ingest> getIngestsByStatus(@Param("status") int status);

    @Query(value = "SELECT * FROM gata_ingest WHERE filename = :filename", nativeQuery = true)
    Ingest getIngestByFilename(@Param("filename") String filename);

    @Modifying
    @Transactional
    @Query(value="INSERT INTO gata_ingest (filename, ingest_src) VALUES (:filename, :ingestSrc)", nativeQuery = true)
    void addIngest(@Param("filename") String filename, @Param("ingestSrc") int ingestSrc);

    @Modifying
    @Transactional
    @Query(value="DELETE FROM gata_ingest WHERE filename=:filename", nativeQuery = true)
    void deleteIngest(@Param("filename") String filename);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
    UPDATE gata_ingest
       SET ingest_status  = :ingestStatus,
           ingest_src     = :ingestSrc,
           failure_reason = :failureReason,
           s3_folder      = :s3Folder,
           completed_at   = :completedAt
     WHERE filename = :filename
    """, nativeQuery = true)
    int updateIngest(
            @Param("filename") String filename,
            @Param("ingestStatus") int ingestStatus,
            @Param("ingestSrc") int ingestSrc,
            @Param("failureReason") String failureReason,
            @Param("s3Folder") String s3Folder,
            @Param("completedAt") Instant completedAt
    );

}