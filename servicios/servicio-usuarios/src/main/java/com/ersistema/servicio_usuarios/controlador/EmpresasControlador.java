package com.ersistema.servicio_usuarios.controlador;
import com.ersistema.servicio_usuarios.dto.CrearEmpresaRequest;
import com.ersistema.servicio_usuarios.dto.CrearEmpresaResponse;
import com.ersistema.servicio_usuarios.servicio.UsuariosServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empresas")
@RequiredArgsConstructor
public class EmpresasControlador {
    private final UsuariosServicio usuariosServicio;

    @PreAuthorize("hasRole('ERP_SUPERADMIN')")
    @PostMapping
    public ResponseEntity<CrearEmpresaResponse> crear(@Valid @RequestBody CrearEmpresaRequest request) {
        Long id = usuariosServicio.crearEmpresa(request);
        return ResponseEntity.ok(CrearEmpresaResponse.builder().idEmpresa(id).build());
    }
}
