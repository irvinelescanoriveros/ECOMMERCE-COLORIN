package pe.ferreteria.backendweb.controlador;

import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ferreteria.backendweb.servicio.ClienteWebService;

@RestController
@RequestMapping("/api/clientes-web")
@CrossOrigin(origins = "*")
public class ClienteWebController {

    private final ClienteWebService clienteWebService;

    public ClienteWebController(ClienteWebService clienteWebService) {
        this.clienteWebService = clienteWebService;
    }

    // REGISTRO
    @PostMapping("/registro")
    public ResponseEntity<Map<String, String>> registrar(
            @RequestBody RegistroRequest datos) {

        if (datos == null || datos.correo() == null || datos.password() == null) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Completa el correo y la contraseña."
            );
        }

        String correo = datos.correo().trim().toLowerCase(Locale.ROOT);
        String password = datos.password();

        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || correo.length() > 100) {
            return respuesta(HttpStatus.BAD_REQUEST, "El correo no es válido.");
        }

        if (password.length() < 8 || password.length() > 100) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener entre 8 y 100 caracteres."
            );
        }

        try {
            clienteWebService.registrar(correo, password);

            return respuesta(
                    HttpStatus.CREATED,
                    "Cuenta creada correctamente."
            );

        } catch (DuplicateKeyException e) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "Ya existe una cuenta con ese correo."
            );

        } catch (GeneralSecurityException e) {
            return respuesta(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo proteger la contraseña."
            );
        }
    }

    // INICIO DE SESIÓN
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> iniciarSesion(
            @RequestBody RegistroRequest datos,
            HttpSession sesion) {

        if (datos == null || datos.correo() == null || datos.password() == null) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Ingresa correo y contraseña."
            );
        }

        String correo = datos.correo().trim().toLowerCase(Locale.ROOT);

        try {
            Map<String, Object> cuenta =
                    clienteWebService.buscarCuentaPorCorreo(correo);

            String hash = (String) cuenta.get("password_hash");

            if (!clienteWebService.verificarPassword(datos.password(), hash)) {
                return respuesta(
                        HttpStatus.UNAUTHORIZED,
                        "Correo o contraseña incorrectos."
                );
            }

            int idClienteWeb =
                    ((Number) cuenta.get("id_cliente_web")).intValue();

            sesion.setAttribute("idClienteWeb", idClienteWeb);
            sesion.setAttribute("correoClienteWeb", correo);

            return respuesta(
                    HttpStatus.OK,
                    "Inicio de sesión correcto."
            );

        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.UNAUTHORIZED,
                    "Correo o contraseña incorrectos."
            );

        } catch (GeneralSecurityException e) {
            return respuesta(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo verificar la contraseña."
            );
        }
    }

    // CONSULTAR SESIÓN
    @GetMapping("/sesion")
    public ResponseEntity<Map<String, Object>> consultarSesion(
            HttpSession sesion) {

        Object idCliente = sesion.getAttribute("idClienteWeb");
        Object correo = sesion.getAttribute("correoClienteWeb");

        if (idCliente == null || correo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("autenticado", false));
        }

        return ResponseEntity.ok(Map.of(
                "autenticado", true,
                "correo", correo
        ));
    }

    // CERRAR SESIÓN
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> cerrarSesion(
            HttpSession sesion) {

        sesion.invalidate();

        return respuesta(HttpStatus.OK, "Sesión cerrada.");
    }

    // OBTENER PERFIL
    @GetMapping("/perfil")
    public ResponseEntity<?> obtenerPerfil(HttpSession sesion) {

        Object id = sesion.getAttribute("idClienteWeb");

        if (!(id instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        try {
            Map<String, Object> perfil =
                    clienteWebService.obtenerPerfil(((Number) id).intValue());

            return ResponseEntity.ok(perfil);

        } catch (EmptyResultDataAccessException e) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos el perfil.");
        }
    }

    // GUARDAR PERFIL
    @PutMapping("/perfil")
    public ResponseEntity<Map<String, String>> guardarPerfil(
            @RequestBody PerfilRequest datos,
            HttpSession sesion) {

        Object id = sesion.getAttribute("idClienteWeb");

        if (!(id instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        if (datos == null) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "No recibimos los datos del perfil."
            );
        }

        String dni = datos.dni() == null ? "" : datos.dni().trim();
        String nombre = datos.nombre() == null ? "" : datos.nombre().trim();
        String apellidos = datos.apellidos() == null
                ? "" : datos.apellidos().trim();
        String genero = datos.genero() == null ? "" : datos.genero().trim();
        String fecha = datos.fechaNacimiento() == null
                ? "" : datos.fechaNacimiento().trim();
        String telefono = datos.telefono() == null
                ? "" : datos.telefono().trim();

        if (!dni.isEmpty() && !dni.matches("\\d{8}")) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "El DNI debe tener 8 dígitos."
            );
        }

        if (!fecha.isEmpty()) {
            try {
                LocalDate.parse(fecha);
            } catch (DateTimeParseException e) {
                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "La fecha de nacimiento no es válida."
                );
            }
        }

        try {
            int filas = clienteWebService.actualizarPerfil(
                    ((Number) id).intValue(),
                    dni,
                    nombre,
                    apellidos,
                    genero,
                    fecha,
                    telefono
            );

            if (filas == 0) {
                return respuesta(
                        HttpStatus.NOT_FOUND,
                        "No encontramos el perfil."
                );
            }

            return respuesta(
                    HttpStatus.OK,
                    "Perfil guardado en la base de datos."
            );

        } catch (DuplicateKeyException e) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "Ese DNI ya está asociado a otra cuenta."
            );
        }
    }

    // BUSCAR CLIENTE POR DNI
    @GetMapping("/dni/{dni}")
    public ResponseEntity<?> buscarClientePorDni(
            @PathVariable String dni,
            HttpSession sesion) {

        if (!(sesion.getAttribute("idClienteWeb") instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        if (!dni.matches("\\d{8}")) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "El DNI debe tener 8 dígitos."
            );
        }

        try {
            Map<String, Object> cliente =
                    clienteWebService.buscarClientePorDni(dni);

            return ResponseEntity.ok(cliente);

        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.NOT_FOUND,
                    "No encontramos ese DNI."
            );
        }
    }

    // LISTAR DIRECCIONES
    @GetMapping("/direcciones")
    public ResponseEntity<?> listarDirecciones(HttpSession sesion) {

        Object id = sesion.getAttribute("idClienteWeb");

        if (!(id instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        List<Map<String, Object>> direcciones =
                clienteWebService.listarDirecciones(
                        ((Number) id).intValue()
                );

        return ResponseEntity.ok(direcciones);
    }

    // GUARDAR DIRECCIÓN
    @PostMapping("/direcciones")
    public ResponseEntity<Map<String, String>> guardarDireccion(
            @RequestBody DireccionRequest datos,
            HttpSession sesion) {

        Object id = sesion.getAttribute("idClienteWeb");

        if (!(id instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        if (datos == null
                || vacio(datos.etiqueta())
                || vacio(datos.departamento())
                || vacio(datos.provincia())
                || vacio(datos.distrito())
                || vacio(datos.direccion())) {

            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Completa los campos obligatorios."
            );
        }

        clienteWebService.guardarDireccion(
                ((Number) id).intValue(),
                datos.etiqueta().trim(),
                datos.departamento().trim(),
                datos.provincia().trim(),
                datos.distrito().trim(),
                datos.direccion().trim(),
                datos.pisoDepartamento() == null
                        ? "" : datos.pisoDepartamento().trim(),
                datos.referencia() == null
                        ? "" : datos.referencia().trim()
        );

        return respuesta(
                HttpStatus.CREATED,
                "Dirección guardada en la base de datos."
        );
    }

    // MARCAR DIRECCIÓN PREDETERMINADA
    @PutMapping("/direcciones/{id}/predeterminada")
    public ResponseEntity<Map<String, String>> marcarDireccionPredeterminada(
            @PathVariable int id,
            HttpSession sesion) {

        Object idCliente = sesion.getAttribute("idClienteWeb");

        if (!(idCliente instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }

        int idClienteWeb = ((Number) idCliente).intValue();

        if (!clienteWebService.existeDireccion(id, idClienteWeb)) {
            return respuesta(
                    HttpStatus.NOT_FOUND,
                    "No encontramos esa dirección."
            );
        }

        clienteWebService.marcarDireccionPredeterminada(id, idClienteWeb);

        return respuesta(
                HttpStatus.OK,
                "Dirección predeterminada actualizada."
        );
    }

    // MÉTODO AUXILIAR
    private boolean vacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }

    private ResponseEntity<Map<String, String>> respuesta(
            HttpStatus estado,
            String mensaje) {

        return ResponseEntity.status(estado)
                .body(Map.of("mensaje", mensaje));
    }

    // DTOs DE ENTRADA
    public record RegistroRequest(String correo, String password) {
    }

    public record PerfilRequest(
            String dni,
            String nombre,
            String apellidos,
            String genero,
            String fechaNacimiento,
            String telefono) {
    }

    public record DireccionRequest(
            String etiqueta,
            String departamento,
            String provincia,
            String distrito,
            String direccion,
            String pisoDepartamento,
            String referencia) {
    }
}