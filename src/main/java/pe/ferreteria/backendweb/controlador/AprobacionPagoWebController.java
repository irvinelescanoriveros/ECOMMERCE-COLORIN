
package pe.ferreteria.backendweb.controlador;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ferreteria.backendweb.servicio.AprobacionPagoWebService;

@RestController
@RequestMapping("/api/pedidos-web")
@CrossOrigin(origins = "*")
public class AprobacionPagoWebController {

    private final AprobacionPagoWebService aprobacionPagoWebService;

    public AprobacionPagoWebController(
            AprobacionPagoWebService aprobacionPagoWebService) {
        this.aprobacionPagoWebService = aprobacionPagoWebService;
    }

    // =========================
    // APROBAR PAGO
    // =========================

    @PutMapping("/{id}/simular-aprobacion")
    public ResponseEntity<?> simularAprobacion(
            @PathVariable int id,
            HttpSession sesion) {

        Integer idClienteWeb = obtenerIdClienteWeb(sesion);

        if (idClienteWeb == null) {
            return respuestaNoAutorizada();
        }

        return aprobacionPagoWebService.simularAprobacion(
                id, idClienteWeb);
    }

    // =========================
    // RECHAZAR PAGO
    // =========================

    @PutMapping("/{id}/simular-rechazo")
    public ResponseEntity<?> simularRechazo(
            @PathVariable int id,
            HttpSession sesion) {

        Integer idClienteWeb = obtenerIdClienteWeb(sesion);

        if (idClienteWeb == null) {
            return respuestaNoAutorizada();
        }

        return aprobacionPagoWebService.simularRechazo(
                id, idClienteWeb);
    }

    // =========================
    // DESPACHAR PEDIDO
    // =========================

    @PutMapping("/{id}/simular-despacho")
    public ResponseEntity<?> simularDespacho(
            @PathVariable int id,
            HttpSession sesion) {

        Integer idClienteWeb = obtenerIdClienteWeb(sesion);

        if (idClienteWeb == null) {
            return respuestaNoAutorizada();
        }

        return aprobacionPagoWebService.simularDespacho(
                id, idClienteWeb);
    }

    // =========================
    // REGISTRAR ENTREGA
    // =========================

    @PutMapping("/{id}/simular-entrega")
    public ResponseEntity<?> simularEntrega(
            @PathVariable int id,
            HttpSession sesion) {

        Integer idClienteWeb = obtenerIdClienteWeb(sesion);

        if (idClienteWeb == null) {
            return respuestaNoAutorizada();
        }

        return aprobacionPagoWebService.simularEntrega(
                id, idClienteWeb);
    }

    // =========================
    // VALIDAR SESIÓN
    // =========================

    private Integer obtenerIdClienteWeb(HttpSession sesion) {
        Object idSesion = sesion.getAttribute("idClienteWeb");

        if (!(idSesion instanceof Number)) {
            return null;
        }

        return ((Number) idSesion).intValue();
    }

    private ResponseEntity<Map<String, String>> respuestaNoAutorizada() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "mensaje",
                        "Inicia sesión para continuar."));
    }
}
