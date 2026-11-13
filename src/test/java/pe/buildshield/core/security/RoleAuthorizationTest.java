package pe.buildshield.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.buildshield.core.shared.security.JwtTokenIssuer;
import pe.buildshield.core.shared.security.RevokedTokenStore;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.iam.application.AuthenticationService;
import pe.buildshield.core.iam.application.PasswordResetService;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.iam.application.UserManagementService;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.dispatch.application.DispatchService;
import pe.buildshield.core.dispatch.domain.model.DispatchStatus;
import pe.buildshield.core.dispatch.domain.model.DispatchType;
import pe.buildshield.core.inventory.StockService;
import pe.buildshield.core.reception.application.ReceptionService;
import pe.buildshield.core.reception.domain.model.ReceptionStatus;
import pe.buildshield.core.inventory.application.StockQueries;
import pe.buildshield.core.ordering.application.OrderService;
import pe.buildshield.core.ordering.domain.model.OrderStatus;
import pe.buildshield.core.organization.application.AssignmentService;
import pe.buildshield.core.organization.application.MaterialService;
import pe.buildshield.core.organization.application.WarehouseService;
import pe.buildshield.core.organization.application.WorksiteService;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WarehouseType;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.support.WebSliceTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorización de toda la API del Core: cada endpoint contra usuario anónimo y cada rol
 * (los servicios están simulados). Además verifica que ningún endpoint quede sin una regla explícita
 * de {@code @PreAuthorize}.
 */
@WebMvcTest
@WebSliceTest
class RoleAuthorizationTest {

    private static final UUID ANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final Map<String, String> BODIES = Map.ofEntries(
            Map.entry("sign-up", "{\"ruc\":\"20123456789\",\"legalName\":\"Andina\",\"adminFullName\":\"Ana\",\"adminEmail\":\"ana@andina.pe\",\"password\":\"Segura123\"}"),
            Map.entry("sign-in", "{\"email\":\"ana@andina.pe\",\"password\":\"Segura123\"}"),
            Map.entry("refresh", "{\"refreshToken\":\"abc\"}"),
            Map.entry("sign-out", "{\"refreshToken\":\"abc\"}"),
            Map.entry("reset", "{\"email\":\"ana@andina.pe\"}"),
            Map.entry("reset-confirm", "{\"token\":\"abc\",\"newPassword\":\"Nueva12345\"}"),
            Map.entry("worksite", "{\"name\":\"Torre\",\"address\":\"Av. 1\",\"district\":\"Lince\",\"city\":\"Lima\",\"startDate\":\"2026-11-01\"}"),
            Map.entry("patch-worksite", "{\"name\":\"Torre 2\"}"),
            Map.entry("warehouse", "{\"name\":\"Central\",\"type\":\"WAREHOUSE\",\"address\":\"Av. 1\"}"),
            Map.entry("deactivate", "{\"active\":false}"),
            Map.entry("material", "{\"sku\":\"CEM-001\",\"name\":\"Cemento\",\"unit\":\"BAG\",\"wasteTolerancePercent\":2.5}"),
            Map.entry("patch-material", "{\"wasteTolerancePercent\":1.5}"),
            Map.entry("assignment", "{\"userId\":\"00000000-0000-0000-0000-0000000000bb\",\"siteType\":\"WORKSITE\",\"siteId\":\"00000000-0000-0000-0000-0000000000aa\"}"),
            Map.entry("end-assignment", "{\"active\":false}"),
            Map.entry("order", "{\"worksiteId\":\"00000000-0000-0000-0000-0000000000aa\",\"warehouseId\":\"00000000-0000-0000-0000-0000000000aa\",\"lines\":[{\"materialId\":\"00000000-0000-0000-0000-0000000000aa\",\"quantity\":5}]}"),
            Map.entry("reject", "{\"reason\":\"Sin transporte\"}"),
            Map.entry("stock-entry", "{\"warehouseId\":\"00000000-0000-0000-0000-0000000000aa\",\"materialId\":\"00000000-0000-0000-0000-0000000000aa\",\"quantity\":10}"),
            Map.entry("dispatch", "{\"orderId\":\"00000000-0000-0000-0000-0000000000aa\",\"lines\":[{\"orderLineId\":\"00000000-0000-0000-0000-0000000000aa\",\"quantity\":5}]}"),
            Map.entry("carrier", "{\"carrierName\":\"Transportes Rímac\",\"carrierDocument\":\"20555666777\",\"plate\":\"ABC-123\"}"),
            Map.entry("weighing", "{\"grossKg\":2500,\"tareKg\":1000}"),
            Map.entry("reception", "{\"dispatchId\":\"00000000-0000-0000-0000-0000000000aa\"}"),
            Map.entry("reception-line", "{\"receivedQty\":4.5}"),
            Map.entry("update-user", "{\"active\":false}"),
            Map.entry("create-user", "{\"fullName\":\"Rosa\",\"email\":\"rosa@andina.pe\",\"role\":\"WAREHOUSE_MANAGER\",\"password\":\"Almacen123\"}"));

    @MockitoBean
    pe.buildshield.core.audit.AuditTrail auditTrail;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    RevokedTokenStore revokedTokens;

    @MockitoBean
    SignUpService signUpService;

    @MockitoBean
    PasswordResetService passwordResetService;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    UserManagementService userManagementService;

    @MockitoBean
    WorksiteService worksiteService;

    @MockitoBean
    WarehouseService warehouseService;

    @MockitoBean
    MaterialService materialService;

    @MockitoBean
    AssignmentService assignmentService;

    @MockitoBean
    OrderService orderService;

    @MockitoBean
    StockQueries stockQueries;

    @MockitoBean
    DispatchService dispatchService;

    @MockitoBean
    ReceptionService receptionService;

    @BeforeEach
    void stubServices() {
        when(signUpService.signUp(any())).thenReturn(new SignUpService.SignUpResult(UUID.randomUUID(), UUID.randomUUID()));
        AuthenticationService.SessionTokens tokens = new AuthenticationService.SessionTokens("a", Instant.now(), "r", Instant.now());
        when(authenticationService.signIn(anyString(), anyString())).thenReturn(tokens);
        when(authenticationService.refresh(anyString())).thenReturn(tokens);
        when(userManagementService.create(any())).thenReturn(new UserManagementService.UserView(
                UUID.randomUUID(), "rosa@andina.pe", "Rosa", Role.WAREHOUSE_MANAGER, true));
        when(userManagementService.list()).thenReturn(List.of());
        WorksiteService.WorksiteView worksite = new WorksiteService.WorksiteView(ANY_ID, "Torre",
                new Location("Av. 1", "Lince", "Lima", null, null), LocalDate.of(2026, 11, 1), null);
        when(worksiteService.register(any())).thenReturn(worksite);
        when(worksiteService.update(any(), any())).thenReturn(worksite);
        when(worksiteService.get(any())).thenReturn(worksite);
        when(worksiteService.list()).thenReturn(List.of(worksite));
        WarehouseService.WarehouseView warehouse = new WarehouseService.WarehouseView(ANY_ID, "Central",
                WarehouseType.WAREHOUSE, "Av. 1", true);
        when(warehouseService.register(any())).thenReturn(warehouse);
        when(warehouseService.update(any(), any())).thenReturn(warehouse);
        when(warehouseService.get(any())).thenReturn(warehouse);
        when(warehouseService.list()).thenReturn(List.of(warehouse));
        MaterialService.MaterialView material = new MaterialService.MaterialView(ANY_ID, "CEM-001", "Cemento",
                UnitOfMeasure.BAG, new BigDecimal("2.50"), true);
        when(materialService.register(any())).thenReturn(material);
        when(materialService.update(any(), any())).thenReturn(material);
        when(materialService.get(any())).thenReturn(material);
        when(materialService.list()).thenReturn(List.of(material));
        AssignmentService.AssignmentView assignment = new AssignmentService.AssignmentView(ANY_ID, ANY_ID,
                SiteType.WORKSITE, ANY_ID, Instant.now(), null, true);
        when(assignmentService.assign(any())).thenReturn(assignment);
        when(assignmentService.update(any(), any())).thenReturn(assignment);
        when(assignmentService.get(any())).thenReturn(assignment);
        when(assignmentService.list()).thenReturn(List.of(assignment));
        OrderService.OrderView order = new OrderService.OrderView(ANY_ID, ANY_ID, ANY_ID, ANY_ID,
                OrderStatus.REGISTERED, null, null, Instant.now(), null, null, List.of());
        when(orderService.place(any())).thenReturn(order);
        when(orderService.approve(any())).thenReturn(order);
        when(orderService.reject(any(), any())).thenReturn(order);
        when(orderService.get(any())).thenReturn(order);
        when(orderService.list()).thenReturn(List.of(order));
        when(stockQueries.registerEntry(any(), any(), any(), any())).thenReturn(
                new StockService.StockLevel(ANY_ID, ANY_ID, BigDecimal.TEN, BigDecimal.ZERO));
        when(stockQueries.list(any())).thenReturn(List.of());
        when(userManagementService.update(any(), any())).thenReturn(new UserManagementService.UserView(
                ANY_ID, "rosa@andina.pe", "Rosa", Role.WAREHOUSE_MANAGER, false));
        DispatchService.DispatchView dispatch = new DispatchService.DispatchView(ANY_ID, ANY_ID, ANY_ID, ANY_ID,
                DispatchType.PARTIAL, DispatchStatus.PREPARED, "MAN-20261103-7KQ2M9XA", Instant.now(), null, null, null,
                null, List.of());
        when(dispatchService.create(any())).thenReturn(dispatch);
        when(dispatchService.list(any(), any())).thenReturn(List.of(dispatch));
        when(dispatchService.get(any())).thenReturn(dispatch);
        when(dispatchService.assignCarrier(any(), any(), any(), any())).thenReturn(dispatch);
        when(dispatchService.recordDepartureWeighing(any(), any(), any(), any())).thenReturn(dispatch);
        when(dispatchService.depart(any())).thenReturn(dispatch);
        when(dispatchService.manifest(any())).thenReturn(new DispatchService.ManifestView("MAN-20261103-7KQ2M9XA",
                dispatch, ANY_ID, Instant.now(), new DispatchService.ManifestSite(ANY_ID, "Torre", "Av. 1"),
                new DispatchService.ManifestSite(ANY_ID, "Central", "Av. 2"), List.of(), "iVBORw0KGgo=",
                "MAN-20261103-7KQ2M9XA"));
        ReceptionService.ReceptionView reception = new ReceptionService.ReceptionView(ANY_ID, ANY_ID, ANY_ID, ANY_ID,
                ANY_ID, ReceptionStatus.IN_PROGRESS, null, null, List.of());
        when(receptionService.start(any())).thenReturn(reception);
        when(receptionService.get(any())).thenReturn(reception);
        when(receptionService.recordLine(any(), any(), any())).thenReturn(reception);
        when(receptionService.confirm(any())).thenReturn(reception);
        when(receptionService.comparison(any())).thenReturn(new ReceptionService.ComparisonView(ANY_ID, ANY_ID, ANY_ID,
                ReceptionStatus.IN_PROGRESS, false, false, List.of()));
    }

    @ParameterizedTest(name = "{0} {1} como {2} -> {3}")
    @CsvSource({
            "GET, /api/v1/audit/events, , ANONYMOUS, 401",
            "GET, /api/v1/audit/events, , ADMINISTRATOR, 200",
            "GET, /api/v1/audit/events, , WAREHOUSE_MANAGER, 403",
            "GET, /api/v1/audit/events, , SITE_MANAGER, 403",
            // endpoint              , cuerpo     , quién             , estado
            "POST, /api/v1/auth/sign-up , sign-up    , ANONYMOUS        , 201",
            "POST, /api/v1/auth/sign-in , sign-in    , ANONYMOUS        , 200",
            "POST, /api/v1/auth/refresh , refresh    , ANONYMOUS        , 200",
            "POST, /api/v1/auth/password-reset        , reset        , ANONYMOUS, 202",
            "POST, /api/v1/auth/password-reset/confirm, reset-confirm, ANONYMOUS, 204",
            "POST, /api/v1/auth/sign-out, sign-out   , ANONYMOUS        , 401",
            "POST, /api/v1/auth/sign-out, sign-out   , ADMINISTRATOR    , 204",
            "POST, /api/v1/auth/sign-out, sign-out   , WAREHOUSE_MANAGER, 204",
            "POST, /api/v1/auth/sign-out, sign-out   , SITE_MANAGER     , 204",
            "POST, /api/v1/users        , create-user, ANONYMOUS        , 401",
            "POST, /api/v1/users        , create-user, ADMINISTRATOR    , 201",
            "POST, /api/v1/users        , create-user, WAREHOUSE_MANAGER, 403",
            "POST, /api/v1/users        , create-user, SITE_MANAGER     , 403",
            "GET , /api/v1/users        ,            , ANONYMOUS        , 401",
            "GET , /api/v1/users        ,            , ADMINISTRATOR    , 200",
            "GET , /api/v1/users        ,            , WAREHOUSE_MANAGER, 403",
            "GET , /api/v1/users        ,            , SITE_MANAGER     , 403",
            "POST , /api/v1/worksites, worksite, ANONYMOUS, 401",
            "POST , /api/v1/worksites, worksite, ADMINISTRATOR, 201",
            "POST , /api/v1/worksites, worksite, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/worksites, worksite, SITE_MANAGER, 403",
            "GET  , /api/v1/worksites, , ANONYMOUS, 401",
            "GET  , /api/v1/worksites, , ADMINISTRATOR, 200",
            "GET  , /api/v1/worksites, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/worksites, , SITE_MANAGER, 200",
            "GET  , /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, ANONYMOUS, 401",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, ADMINISTRATOR, 200",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, WAREHOUSE_MANAGER, 403",
            "PATCH, /api/v1/worksites/00000000-0000-0000-0000-0000000000aa, patch-worksite, SITE_MANAGER, 403",
            "POST , /api/v1/warehouses, warehouse, ANONYMOUS, 401",
            "POST , /api/v1/warehouses, warehouse, ADMINISTRATOR, 201",
            "POST , /api/v1/warehouses, warehouse, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/warehouses, warehouse, SITE_MANAGER, 403",
            "GET  , /api/v1/warehouses, , ANONYMOUS, 401",
            "GET  , /api/v1/warehouses, , ADMINISTRATOR, 200",
            "GET  , /api/v1/warehouses, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/warehouses, , SITE_MANAGER, 200",
            "GET  , /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, ANONYMOUS, 401",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, ADMINISTRATOR, 200",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, WAREHOUSE_MANAGER, 403",
            "PATCH, /api/v1/warehouses/00000000-0000-0000-0000-0000000000aa, deactivate, SITE_MANAGER, 403",
            "POST , /api/v1/materials, material, ANONYMOUS, 401",
            "GET  , /api/v1/materials, , ANONYMOUS, 401",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, ANONYMOUS, 401",
            "POST , /api/v1/materials, material, ADMINISTRATOR, 201",
            "GET  , /api/v1/materials, , ADMINISTRATOR, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, ADMINISTRATOR, 200",
            "POST , /api/v1/materials, material, WAREHOUSE_MANAGER, 403",
            "GET  , /api/v1/materials, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/materials, material, SITE_MANAGER, 403",
            "GET  , /api/v1/materials, , SITE_MANAGER, 200",
            "GET  , /api/v1/materials/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/materials/00000000-0000-0000-0000-0000000000aa, patch-material, SITE_MANAGER, 403",
            "POST , /api/v1/assignments, assignment, ANONYMOUS, 401",
            "GET  , /api/v1/assignments, , ANONYMOUS, 401",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, ANONYMOUS, 401",
            "POST , /api/v1/assignments, assignment, ADMINISTRATOR, 201",
            "GET  , /api/v1/assignments, , ADMINISTRATOR, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, ADMINISTRATOR, 200",
            "POST , /api/v1/assignments, assignment, WAREHOUSE_MANAGER, 403",
            "GET  , /api/v1/assignments, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, WAREHOUSE_MANAGER, 403",
            "POST , /api/v1/assignments, assignment, SITE_MANAGER, 403",
            "GET  , /api/v1/assignments, , SITE_MANAGER, 200",
            "GET  , /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/assignments/00000000-0000-0000-0000-0000000000aa, end-assignment, SITE_MANAGER, 403",
            "POST , /api/v1/orders, order, ANONYMOUS, 401",
            "GET  , /api/v1/orders, , ANONYMOUS, 401",
            "GET  , /api/v1/orders/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/approve, , ANONYMOUS, 401",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/reject, reject, ANONYMOUS, 401",
            "POST , /api/v1/stock/entries, stock-entry, ANONYMOUS, 401",
            "GET  , /api/v1/stock, , ANONYMOUS, 401",
            "POST , /api/v1/orders, order, ADMINISTRATOR, 403",
            "GET  , /api/v1/orders, , ADMINISTRATOR, 200",
            "GET  , /api/v1/orders/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/approve, , ADMINISTRATOR, 200",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/reject, reject, ADMINISTRATOR, 200",
            "POST , /api/v1/stock/entries, stock-entry, ADMINISTRATOR, 201",
            "GET  , /api/v1/stock, , ADMINISTRATOR, 200",
            "POST , /api/v1/orders, order, WAREHOUSE_MANAGER, 403",
            "GET  , /api/v1/orders, , WAREHOUSE_MANAGER, 200",
            "GET  , /api/v1/orders/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/approve, , WAREHOUSE_MANAGER, 200",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/reject, reject, WAREHOUSE_MANAGER, 200",
            "POST , /api/v1/stock/entries, stock-entry, WAREHOUSE_MANAGER, 201",
            "GET  , /api/v1/stock, , WAREHOUSE_MANAGER, 200",
            "POST , /api/v1/orders, order, SITE_MANAGER, 201",
            "GET  , /api/v1/orders, , SITE_MANAGER, 200",
            "GET  , /api/v1/orders/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/approve, , SITE_MANAGER, 403",
            "POST , /api/v1/orders/00000000-0000-0000-0000-0000000000aa/reject, reject, SITE_MANAGER, 403",
            "POST , /api/v1/stock/entries, stock-entry, SITE_MANAGER, 403",
            "GET  , /api/v1/stock, , SITE_MANAGER, 200",
            "POST, /api/v1/dispatches, dispatch, ANONYMOUS, 401",
            "POST, /api/v1/dispatches, dispatch, ADMINISTRATOR, 201",
            "POST, /api/v1/dispatches, dispatch, WAREHOUSE_MANAGER, 201",
            "POST, /api/v1/dispatches, dispatch, SITE_MANAGER, 403",
            "GET, /api/v1/dispatches, , ANONYMOUS, 401",
            "GET, /api/v1/dispatches, , ADMINISTRATOR, 200",
            "GET, /api/v1/dispatches, , WAREHOUSE_MANAGER, 200",
            "GET, /api/v1/dispatches, , SITE_MANAGER, 200",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PATCH, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/carrier, carrier, ANONYMOUS, 401",
            "PATCH, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/carrier, carrier, ADMINISTRATOR, 200",
            "PATCH, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/carrier, carrier, WAREHOUSE_MANAGER, 200",
            "PATCH, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/carrier, carrier, SITE_MANAGER, 403",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/departure-weighing, weighing, ANONYMOUS, 401",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/departure-weighing, weighing, ADMINISTRATOR, 200",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/departure-weighing, weighing, WAREHOUSE_MANAGER, 200",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/departure-weighing, weighing, SITE_MANAGER, 403",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/depart, , ANONYMOUS, 401",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/depart, , ADMINISTRATOR, 200",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/depart, , WAREHOUSE_MANAGER, 200",
            "POST, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/depart, , SITE_MANAGER, 403",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/manifest, , ANONYMOUS, 401",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/manifest, , ADMINISTRATOR, 200",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/manifest, , WAREHOUSE_MANAGER, 200",
            "GET, /api/v1/dispatches/00000000-0000-0000-0000-0000000000aa/manifest, , SITE_MANAGER, 200",
            "POST, /api/v1/receptions, reception, ANONYMOUS, 401",
            "POST, /api/v1/receptions, reception, ADMINISTRATOR, 201",
            "POST, /api/v1/receptions, reception, WAREHOUSE_MANAGER, 403",
            "POST, /api/v1/receptions, reception, SITE_MANAGER, 201",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa, , ANONYMOUS, 401",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa, , ADMINISTRATOR, 200",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa, , WAREHOUSE_MANAGER, 200",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa, , SITE_MANAGER, 200",
            "PUT, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/lines/00000000-0000-0000-0000-0000000000bb, reception-line, ANONYMOUS, 401",
            "PUT, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/lines/00000000-0000-0000-0000-0000000000bb, reception-line, ADMINISTRATOR, 200",
            "PUT, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/lines/00000000-0000-0000-0000-0000000000bb, reception-line, WAREHOUSE_MANAGER, 403",
            "PUT, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/lines/00000000-0000-0000-0000-0000000000bb, reception-line, SITE_MANAGER, 200",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/comparison, , ANONYMOUS, 401",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/comparison, , ADMINISTRATOR, 200",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/comparison, , WAREHOUSE_MANAGER, 200",
            "GET, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/comparison, , SITE_MANAGER, 200",
            "POST, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/confirm, , ANONYMOUS, 401",
            "POST, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/confirm, , ADMINISTRATOR, 200",
            "POST, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/confirm, , WAREHOUSE_MANAGER, 403",
            "POST, /api/v1/receptions/00000000-0000-0000-0000-0000000000aa/confirm, , SITE_MANAGER, 200",
            "PATCH, /api/v1/users/00000000-0000-0000-0000-0000000000bb, update-user, ANONYMOUS, 401",
            "PATCH, /api/v1/users/00000000-0000-0000-0000-0000000000bb, update-user, ADMINISTRATOR, 200",
            "PATCH, /api/v1/users/00000000-0000-0000-0000-0000000000bb, update-user, WAREHOUSE_MANAGER, 403",
            "PATCH, /api/v1/users/00000000-0000-0000-0000-0000000000bb, update-user, SITE_MANAGER, 403",
    })
    void endpoint_is_allowed_only_for_its_roles(String method, String path, String body, String who, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get(path);
            case "PATCH" -> patch(path);
            case "PUT" -> put(path);
            default -> post(path);
        };
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(BODIES.get(body));
        }
        if (!"ANONYMOUS".equals(who)) {
            String token = issuer.issue(new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), who)).value();
            request.header("Authorization", "Bearer " + token);
        }

        mvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void every_core_endpoint_declares_its_authorization_rule() {
        List<String> withoutRule = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("pe.buildshield.core"))
                .filter(handler -> !hasPreAuthorize(handler))
                .map(handler -> handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName())
                .toList();

        assertThat(handlerMapping.getHandlerMethods()).isNotEmpty();
        assertThat(withoutRule).as("endpoints sin @PreAuthorize").isEmpty();
    }

    private static boolean hasPreAuthorize(HandlerMethod handler) {
        return handler.hasMethodAnnotation(PreAuthorize.class)
                || handler.getBeanType().isAnnotationPresent(PreAuthorize.class);
    }
}
