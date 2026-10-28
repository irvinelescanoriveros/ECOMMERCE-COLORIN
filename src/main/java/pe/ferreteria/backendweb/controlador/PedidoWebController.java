
package pe.ferreteria.backendweb.controlador;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ferreteria.backendweb.servicio.PedidoWebService;
import pe.ferreteria.backendweb.servicio.PedidoWebService.PedidoRequest;

@RestController
@RequestMapping("/api/pedidos-web")
@CrossOrigin(origins = "*")
public class PedidoWebController {

    private final PedidoWebService pedidoWebService;

    public PedidoWebController(PedidoWebService pedidoWebService) {
        this.pedidoWebService = pedidoWebService;
    }

    @GetMapping
    public ResponseEntity<?> listarPedidos(HttpSession sesion) {
        Object idSesion = sesion.getAttribute("idClienteWeb");

        if (!(idSesion instanceof Number)) {
            return respuesta(
                    HttpStatus.UNAUTHORIZED,
                    "Inicia sesión para ver tus pedidos."
            );
        }

        int idClienteWeb = ((Number) idSesion).intValue();

        return ResponseEntity.ok(
                pedidoWebService.listarPedidos(idClienteWeb)
        );
    }

    @PostMapping
    public ResponseEntity<?> registrarPedido(
            @RequestBody PedidoRequest datos,
            HttpSession sesion) {

        Object idSesion = sesion.getAttribute("idClienteWeb");

        if (!(idSesion instanceof Number)) {
            return respuesta(
                    HttpStatus.UNAUTHORIZED,
                    "Inicia sesión para registrar el pedido."
            );
        }

        int idClienteWeb = ((Number) idSesion).intValue();

        return pedidoWebService.registrarPedido(datos, idClienteWeb);
    }

    private ResponseEntity<java.util.Map<String, String>> respuesta(
            HttpStatus estado, String mensaje) {

        return ResponseEntity.status(estado)
                .body(java.util.Map.of("mensaje", mensaje));
    }
}
