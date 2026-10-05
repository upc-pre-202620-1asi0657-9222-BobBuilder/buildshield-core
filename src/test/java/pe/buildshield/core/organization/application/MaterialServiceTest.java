package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WasteTolerance;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialServiceTest {

    private final pe.buildshield.core.audit.AuditTrail audit = pe.buildshield.core.support.AuditTestSupport.noop();
    private static final UUID ID = UUID.randomUUID();

    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final SiteVisibility visibility = mock(SiteVisibility.class);
    private final MaterialService service = new MaterialService(materials, visibility, audit);

    @Test
    void registers_with_normalized_sku() {
        when(materials.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        MaterialService.MaterialView view = service.register(new MaterialService.RegisterMaterial(
                "cem-001", "Cemento", UnitOfMeasure.BAG, new BigDecimal("2.5")));

        assertThat(view.sku()).isEqualTo("CEM-001");
        assertThat(view.wasteTolerancePercent()).isEqualTo(new BigDecimal("2.50"));
        assertThat(view.active()).isTrue();
    }

    @Test
    void sku_already_in_the_organization_is_a_conflict() {
        when(materials.existsBySku(new Sku("CEM-001"))).thenReturn(true);

        assertThatThrownBy(() -> service.register(new MaterialService.RegisterMaterial(
                "cem-001", "Cemento", UnitOfMeasure.BAG, BigDecimal.ONE)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", MaterialService.SKU_ALREADY_EXISTS);
        verify(materials, never()).save(any());
    }

    @Test
    void concurrent_duplicate_sku_detected_by_the_database_is_a_conflict() {
        when(materials.save(any())).thenThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uk_materials_organization_sku\"")));

        assertThatThrownBy(() -> service.register(new MaterialService.RegisterMaterial(
                "CEM-001", "Cemento", UnitOfMeasure.BAG, BigDecimal.ONE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void other_integrity_violations_propagate() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("x", new SQLException("otro"));
        when(materials.save(any())).thenThrow(other);

        assertThatThrownBy(() -> service.register(new MaterialService.RegisterMaterial(
                "CEM-001", "Cemento", UnitOfMeasure.BAG, BigDecimal.ONE))).isSameAs(other);
    }

    @Test
    void invalid_tolerance_is_rejected_before_saving() {
        assertThatThrownBy(() -> service.register(new MaterialService.RegisterMaterial(
                "ARE-001", "Arena", UnitOfMeasure.M3, new BigDecimal("100.01"))))
                .isInstanceOf(ValidationException.class);
        verify(materials, never()).save(any());
    }

    @Test
    void update_changes_tolerance_and_can_deactivate() {
        when(materials.findById(ID)).thenReturn(Optional.of(existing()));
        when(materials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialService.MaterialView view = service.update(ID,
                new MaterialService.UpdateMaterial(null, null, new BigDecimal("1.5"), false));
        MaterialService.MaterialView unchanged = service.update(ID,
                new MaterialService.UpdateMaterial("Cemento tipo V", null, null, null));

        assertThat(view.wasteTolerancePercent()).isEqualTo(new BigDecimal("1.50"));
        assertThat(view.active()).isFalse();
        assertThat(unchanged.name()).isEqualTo("Cemento tipo V");
    }

    @Test
    void unknown_or_invisible_material_is_404() {
        Material material = existing();
        when(materials.findById(ID)).thenReturn(Optional.of(material));
        when(visibility.canSee(material)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "MATERIAL_NOT_FOUND");
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new MaterialService.UpdateMaterial("x", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(visibility.canSee(material)).thenReturn(true);
        assertThat(service.get(ID).sku()).isEqualTo("CEM-001");
    }

    @Test
    void list_returns_the_visible_catalog() {
        when(visibility.visibleMaterials()).thenReturn(List.of(existing()));

        assertThat(service.list()).extracting(MaterialService.MaterialView::sku).containsExactly("CEM-001");
    }

    private static Material existing() {
        return Material.restore(ID, new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG,
                new WasteTolerance(new BigDecimal("2.5")), true, 0L);
    }

    private static Material withId(Material m) {
        return Material.restore(ID, m.sku(), m.name(), m.unit(), m.wasteTolerance(), m.active(), 0L);
    }
}
