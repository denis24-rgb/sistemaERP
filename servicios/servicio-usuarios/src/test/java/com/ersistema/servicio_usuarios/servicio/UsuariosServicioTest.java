package com.ersistema.servicio_usuarios.servicio;

import com.ersistema.servicio_usuarios.dominio.Empresa;
import com.ersistema.servicio_usuarios.dominio.EmpresaUsuario;
import com.ersistema.servicio_usuarios.dominio.UsuarioErp;
import com.ersistema.servicio_usuarios.excepcion.BadRequestException;
import com.ersistema.servicio_usuarios.excepcion.ForbiddenException;
import com.ersistema.servicio_usuarios.excepcion.NotFoundException;
import com.ersistema.servicio_usuarios.keycloak.KeycloakAdminService;
import com.ersistema.servicio_usuarios.repositorio.EmpresaRepositorio;
import com.ersistema.servicio_usuarios.repositorio.EmpresaUsuarioRepositorio;
import com.ersistema.servicio_usuarios.repositorio.UsuarioErpRepositorio;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuariosServicioTest {

    @Mock private UsuarioErpRepositorio usuarioRepo;
    @Mock private EmpresaRepositorio empresaRepo;
    @Mock private EmpresaUsuarioRepositorio empresaUsuarioRepo;
    @Mock private KeycloakAdminService keycloakAdminService;

    @InjectMocks private UsuariosServicio usuariosServicio;

    // ========= validarPerteneceEmpresa =========

    @Test
    void validarPerteneceEmpresa_siNoExisteUsuario_lanzaNotFound() {
        when(usuarioRepo.findByKeycloakId("kc1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(1L, "kc1")
        );
    }

    @Test
    void validarPerteneceEmpresa_siNoPertenece_lanzaForbidden() {
        UsuarioErp u = UsuarioErp.builder().idUsuarioErp(10L).keycloakId("kc1").build();
        when(usuarioRepo.findByKeycloakId("kc1")).thenReturn(Optional.of(u));
        when(empresaUsuarioRepo.findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(1L, "kc1")
        );
    }

    @Test
    void validarPerteneceEmpresa_siEstaInactivo_lanzaForbidden() {
        UsuarioErp u = UsuarioErp.builder().idUsuarioErp(10L).keycloakId("kc1").build();
        EmpresaUsuario eu = EmpresaUsuario.builder()
                .idEmpresaUsuario(99L)
                .usuario(u)
                .empresa(Empresa.builder().idEmpresa(1L).build())
                .estado(false)
                .build();

        when(usuarioRepo.findByKeycloakId("kc1")).thenReturn(Optional.of(u));
        when(empresaUsuarioRepo.findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(1L, 10L))
                .thenReturn(Optional.of(eu));

        assertThrows(ForbiddenException.class, () ->
                usuariosServicio.validarPerteneceEmpresa(1L, "kc1")
        );
    }

    @Test
    void validarPerteneceEmpresa_siTodoOk_noLanza() {
        UsuarioErp u = UsuarioErp.builder().idUsuarioErp(10L).keycloakId("kc1").build();
        EmpresaUsuario eu = EmpresaUsuario.builder()
                .idEmpresaUsuario(99L)
                .usuario(u)
                .empresa(Empresa.builder().idEmpresa(1L).build())
                .estado(true)
                .build();

        when(usuarioRepo.findByKeycloakId("kc1")).thenReturn(Optional.of(u));
        when(empresaUsuarioRepo.findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(1L, 10L))
                .thenReturn(Optional.of(eu));

        assertDoesNotThrow(() ->
                usuariosServicio.validarPerteneceEmpresa(1L, "kc1")
        );
    }

    // ========= autoRegistrar =========

    @Test
    void autoRegistrar_siEmpresaNoExiste_lanzaNotFound() {
        when(empresaRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                usuariosServicio.autoRegistrar("kc1", "Denis", "denis@test.com", 1L)
        );
    }

    @Test
    void autoRegistrar_usuarioNuevo_creaUsuarioYRelacion() {
        Empresa empresa = Empresa.builder().idEmpresa(1L).nombre("Nebula").build();
        when(empresaRepo.findById(1L)).thenReturn(Optional.of(empresa));

        // usuario no existe -> se crea
        when(usuarioRepo.findByKeycloakId("kc1")).thenReturn(Optional.empty());
        when(usuarioRepo.save(any(UsuarioErp.class))).thenAnswer(inv -> {
            UsuarioErp u = inv.getArgument(0);
            u.setIdUsuarioErp(10L);
            return u;
        });

        // relación no existe -> se crea
        when(empresaUsuarioRepo.findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(1L, 10L))
                .thenReturn(Optional.empty());
        when(empresaUsuarioRepo.save(any(EmpresaUsuario.class))).thenAnswer(inv -> {
            EmpresaUsuario eu = inv.getArgument(0);
            eu.setIdEmpresaUsuario(50L);
            return eu;
        });

        var r = usuariosServicio.autoRegistrar("kc1", "Denis", "denis@test.com", 1L);

        assertEquals(10L, r.getIdUsuarioErp());
        assertEquals("kc1", r.getKeycloakId());
        assertEquals(1L, r.getIdEmpresa());
        assertEquals(50L, r.getIdEmpresaUsuario());

        verify(usuarioRepo).save(any(UsuarioErp.class));
        verify(empresaUsuarioRepo).save(any(EmpresaUsuario.class));
        verifyNoInteractions(keycloakAdminService);
    }

    // ========= reemplazarRoles =========
    @Test
    void reemplazarRoles_debeQuitarActualesYAgregarNuevos() {
        // usuario pertenece a empresa
        UsuarioErp u = UsuarioErp.builder().idUsuarioErp(10L).keycloakId("kc-user-uuid").build();
        EmpresaUsuario eu = EmpresaUsuario.builder()
                .empresa(Empresa.builder().idEmpresa(3L).build())
                .usuario(u)
                .estado(true)
                .build();

        when(empresaUsuarioRepo.findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(3L, 10L))
                .thenReturn(Optional.of(eu));

        // grupos actuales en keycloak
        when(keycloakAdminService.getUserGroupPaths("kc-user-uuid")).thenReturn(List.of(
                "/empresa-3/VENTAS",
                "/empresa-3/COMPRAS",
                "/empresa-2/VENTAS",  // no debe tocar
                "/otro-grupo"         // no debe tocar
        ));

        usuariosServicio.reemplazarRoles(3L, 10L, List.of("ventas", "admin"));

        // Debe quitar VENTAS y COMPRAS de empresa-3
        verify(keycloakAdminService).removeUserFromEmpresaRole(3L, "VENTAS", "kc-user-uuid");
        verify(keycloakAdminService).removeUserFromEmpresaRole(3L, "COMPRAS", "kc-user-uuid");

        // Debe agregar los nuevos (por path)
        verify(keycloakAdminService).addUserToGroupByPath("kc-user-uuid", "/empresa-3/VENTAS");
        verify(keycloakAdminService).addUserToGroupByPath("kc-user-uuid", "/empresa-3/ADMIN");

        // No debe tocar empresa-2
        verify(keycloakAdminService, never()).removeUserFromEmpresaRole(2L, "VENTAS", "kc-user-uuid");
    }

    @Test
    void reemplazarRoles_listaVacia_lanzaBadRequest() {
        assertThrows(BadRequestException.class, () ->
                usuariosServicio.reemplazarRoles(1L, 1L, List.of())
        );
    }
}
