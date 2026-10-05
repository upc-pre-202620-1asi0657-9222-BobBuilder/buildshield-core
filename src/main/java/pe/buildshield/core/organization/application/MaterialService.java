package pe.buildshield.core.organization.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WasteTolerance;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US16: catálogo de materiales con SKU único por organización y tolerancia de merma. */
@Service
public class MaterialService {

    public static final String SKU_ALREADY_EXISTS = "SKU_ALREADY_EXISTS";

    private final AuditTrail audit;
    private final MaterialRepository materials;
    private final SiteVisibility visibility;

    public MaterialService(MaterialRepository materials, SiteVisibility visibility, AuditTrail audit) {
        this.materials = materials;
        this.visibility = visibility;
        this.audit = audit;
    }

    @Transactional
    public MaterialView register(RegisterMaterial command) {
        Sku sku = new Sku(command.sku());
        Material material = Material.register(sku, command.name(), command.unit(),
                new WasteTolerance(command.wasteTolerancePercent()));
        if (materials.existsBySku(sku)) {
            throw skuAlreadyExists(sku);
        }
        try {
            return audit.recorded("MATERIAL_CREATED", "MATERIAL", MaterialView.of(materials.save(material)));
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_materials_organization_sku")) {
                throw skuAlreadyExists(sku);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public MaterialView get(UUID id) {
        return MaterialView.of(materials.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<MaterialView> list() {
        return visibility.visibleMaterials().stream().map(MaterialView::of).toList();
    }

    @Transactional
    public MaterialView update(UUID id, UpdateMaterial command) {
        Material material = materials.findById(id).orElseThrow(() -> notFound(id));
        material.update(command.name(), command.unit(),
                command.wasteTolerancePercent() == null ? null : new WasteTolerance(command.wasteTolerancePercent()),
                command.active());
        return audit.recorded("MATERIAL_UPDATED", "MATERIAL", MaterialView.of(materials.save(material)));
    }

    private static ConflictException skuAlreadyExists(Sku sku) {
        return new ConflictException(SKU_ALREADY_EXISTS, "Ya existe un material con el SKU " + sku.value());
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("MATERIAL_NOT_FOUND", "El material " + id + " no existe");
    }

    public record RegisterMaterial(String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent) {
    }

    /** Campos nulos: no cambian. El SKU no se modifica. */
    public record UpdateMaterial(String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent, Boolean active) {
    }

    public record MaterialView(UUID id, String sku, String name, UnitOfMeasure unit, BigDecimal wasteTolerancePercent,
            boolean active) {

        static MaterialView of(Material material) {
            return new MaterialView(material.id(), material.sku().value(), material.name(), material.unit(),
                    material.wasteTolerance().percent(), material.active());
        }
    }
}
