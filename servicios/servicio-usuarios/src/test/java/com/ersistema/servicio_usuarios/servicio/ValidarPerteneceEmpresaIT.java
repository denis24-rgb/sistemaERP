package com.ersistema.servicio_usuarios.servicio;

import com.ersistema.servicio_usuarios.dominio.Empresa;
import com.ersistema.servicio_usuarios.dominio.EmpresaUsuario;
import com.ersistema.servicio_usuarios.dominio.UsuarioErp;
import com.ersistema.servicio_usuarios.excepcion.ForbiddenException;
import com.ersistema.servicio_usuarios.excepcion.NotFoundException;
import com.ersistema.servicio_usuarios.keycloak.KeycloakAdminService;
import com.ersistema.servicio_usuarios.repositorio.EmpresaRepositorio;
import com.ersistema.servicio_usuarios.repositorio.EmpresaUsuarioRepositorio;
import com.ersistema.servicio_usuarios.repositorio.UsuarioErpRepositorio;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ValidarPerteneceEmpresaIT {

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.cloud.config.enabled", () -> "false");
        r.add("eureka.client.enabled", () -> "false");
    }

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
    void validarPerteneceEmpresa_siNoRegistrado_lanzaNotFound() {
        Empresa e = empresaRepo.save(Empresa.builder().nombre("Nebula").estado(true).build());

        assertThrows(NotFoundException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(e.getIdEmpresa(), "kc-no-existe")
        );
    }

    @Test
    void validarPerteneceEmpresa_siNoPertenece_lanzaForbidden() {
        Empresa e = empresaRepo.save(Empresa.builder().nombre("Nebula").estado(true).build());
        UsuarioErp u = usuarioRepo.save(UsuarioErp.builder().keycloakId("kc1").estado(true).build());
        // no creamos EmpresaUsuario

        assertThrows(ForbiddenException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(e.getIdEmpresa(), "kc1")
        );
    }

    @Test
    void validarPerteneceEmpresa_siInactivo_lanzaForbidden() {
        Empresa e = empresaRepo.save(Empresa.builder().nombre("Nebula").estado(true).build());
        UsuarioErp u = usuarioRepo.save(UsuarioErp.builder().keycloakId("kc1").estado(true).build());

        empresaUsuarioRepo.save(EmpresaUsuario.builder()
                .empresa(e)
                .usuario(u)
                .estado(false)
                .build());

        assertThrows(ForbiddenException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(e.getIdEmpresa(), "kc1")
        );
    }

    @Test
    void validarPerteneceEmpresa_siActivo_noLanza() {
        Empresa e = empresaRepo.save(Empresa.builder().nombre("Nebula").estado(true).build());
        UsuarioErp u = usuarioRepo.save(UsuarioErp.builder().keycloakId("kc1").estado(true).build());

        empresaUsuarioRepo.save(EmpresaUsuario.builder()
                .empresa(e)
                .usuario(u)
                .estado(true)
                .build());

        assertDoesNotThrow(() ->
                usuariosServicio.validarPerteneceEmpresa(e.getIdEmpresa(), "kc1")
        );
    }
}
