package pe.buildshield.core.organization.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Catálogo de materiales de la organización del contexto. */
public interface MaterialRepository {

    Optional<Material> findById(UUID id);

    Optional<Material> findBySku(Sku sku);

    boolean existsBySku(Sku sku);

    List<Material> findAll();

    List<Material> findAllActive();

    Material save(Material material);
}
