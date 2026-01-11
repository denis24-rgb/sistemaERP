package com.ersistema.servicio_usuarios.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("authz")
public class EmpresaAuthz {

    public boolean enEmpresa(Long idEmpresa, Authentication authentication) {
        if (idEmpresa == null || authentication == null) return false;
        String prefix = "EMPRESA_" + idEmpresa + "_";
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority() != null && a.getAuthority().startsWith(prefix));
    }

    public boolean esAdminEmpresa(Long idEmpresa, Authentication authentication) {
        if (idEmpresa == null || authentication == null) return false;
        String target = "EMPRESA_" + idEmpresa + "_ADMIN";
        return authentication.getAuthorities().stream()
                .anyMatch(a -> target.equals(a.getAuthority()));
    }
}
