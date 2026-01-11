package com.ersistema.servicio_usuarios.servicio;

import com.ersistema.servicio_usuarios.dominio.Empresa;
import com.ersistema.servicio_usuarios.dto.ResultadoAutoRegistroDto;
import com.ersistema.servicio_usuarios.excepcion.NotFoundException;
import com.ersistema.servicio_usuarios.keycloak.KeycloakAdminService;
import com.ersistema.servicio_usuarios.repositorio.EmpresaRepositorio;
import com.ersistema.servicio_usuarios.repositorio.EmpresaUsuarioRepositorio;
import com.ersistema.servicio_usuarios.repositorio.UsuarioErpRepositorio;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class UsuariosServicioIT {
    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("bd_usuarios_test")
            .withUsername("postgres")
            .withPassword("postgres123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);

        // Importante para tests
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.cloud.config.enabled", () -> "false");
        r.add("eureka.client.enabled", () -> "false");
    }

    // Mockeamos Keycloak para que no llame a Keycloak real
    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    @Autowired private UsuariosServicio usuariosServicio;
    @Autowired private EmpresaRepositorio empresaRepo;
    @Autowired private UsuarioErpRepositorio usuarioRepo;
    @Autowired private EmpresaUsuarioRepositorio empresaUsuarioRepo;

    @BeforeEach
    void clean() {
        empresaUsuarioRepo.deleteAll();
        usuarioRepo.deleteAll();
        empresaRepo.deleteAll();
    }

    @Test
    void autoRegistrar_debeCrearUsuarioYRelacionEmpresaUsuario() {
        System.out.println("DOCKER_HOST=" + System.getenv("DOCKER_HOST"));
        Empresa empresa = empresaRepo.save(Empresa.builder()

                .nombre("NebulaSoft")
                .nit("123")
                .razonSocial("NebulaSoft SRL")
                .estado(true)
                .build());

        ResultadoAutoRegistroDto r = usuariosServicio.autoRegistrar(
                "kc-uuid-1",
                "Denis",
                "denis@test.com",
                empresa.getIdEmpresa()
        );

        assertNotNull(r.getIdUsuarioErp());
        assertEquals("kc-uuid-1", r.getKeycloakId());
        assertEquals(empresa.getIdEmpresa(), r.getIdEmpresa());

        // confirmar persistencia
        assertTrue(usuarioRepo.findByKeycloakId("kc-uuid-1").isPresent());
        assertTrue(empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(empresa.getIdEmpresa(), r.getIdUsuarioErp())
                .isPresent());
    }

    @Test
    void autoRegistrar_siEmpresaNoExiste_lanzaNotFound() {
        assertThrows(NotFoundException.class, () ->
                usuariosServicio.autoRegistrar("kc-uuid-x", "X", "x@test.com", 999L)
        );
    }
}
