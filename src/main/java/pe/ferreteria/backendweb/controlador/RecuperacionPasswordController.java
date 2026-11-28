package pe.ferreteria.backendweb.controlador;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ferreteria.backendweb.servicio.RecuperacionPasswordService;

@RestController
@RequestMapping("/api/clientes-web/recuperar")
public class RecuperacionPasswordController {

    private static final String PATRON_CORREO =
            "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final RecuperacionPasswordService recuperacionService;

    public RecuperacionPasswordController(
            RecuperacionPasswordService recuperacionService) {
        this.recuperacionService = recuperacionService;
    }

    @PostMapping("/solicitar")
    public ResponseEntity<Map<String, String>> solicitar(
            @RequestBody SolicitudRequest datos) {

        if (datos == null || datos.correo() == null
                || !correoValido(datos.correo())) {
            return respuesta(HttpStatus.BAD_REQUEST,
                    "Ingresa un correo válido.");
        }

        recuperacionService.solicitar(datos.correo());

        return respuesta(HttpStatus.OK,
                "Si el correo está registrado, enviamos un código de 6 "
                + "dígitos. Vence en 10 minutos.");
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, String>> confirmar(
            @RequestBody ConfirmacionRequest datos) {

        if (datos == null || datos.correo() == null
                || datos.codigo() == null || datos.password() == null
                || !correoValido(datos.correo())
                || !datos.codigo().trim().matches("^\\d{6}$")) {
            return respuesta(HttpStatus.BAD_REQUEST,
                    "El código no es válido o venció.");
        }

        if (datos.password().length() < 8
                || datos.password().length() > 100) {
            return respuesta(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener entre 8 y 100 caracteres.");
        }

        boolean cambiada = recuperacionService.confirmar(
                datos.correo(), datos.codigo(), datos.password());

        if (!cambiada) {
            return respuesta(HttpStatus.BAD_REQUEST,
                    "El código no es válido o venció.");
        }

        return respuesta(HttpStatus.OK,
                "Contraseña actualizada. Ya puedes iniciar sesión.");
    }

    private boolean correoValido(String correo) {
        String limpio = correo.trim().toLowerCase(Locale.ROOT);
        return limpio.length() <= 100 && limpio.matches(PATRON_CORREO);
    }

    private ResponseEntity<Map<String, String>> respuesta(
            HttpStatus estado, String mensaje) {

        return ResponseEntity.status(estado)
                .body(Map.of("mensaje", mensaje));
    }

    public record SolicitudRequest(String correo) {
    }

    public record ConfirmacionRequest(
            String correo, String codigo, String password) {
    }
}