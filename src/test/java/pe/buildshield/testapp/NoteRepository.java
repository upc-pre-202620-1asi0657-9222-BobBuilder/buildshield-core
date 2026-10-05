package pe.buildshield.testapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    @Query("select count(n) from Note n where n.text = :text")
    long countByTextJpql(String text);
}
