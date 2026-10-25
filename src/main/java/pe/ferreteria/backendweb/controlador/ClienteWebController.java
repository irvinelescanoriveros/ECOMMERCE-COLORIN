package pe.ferreteria.backendweb.controlador;

import java.util.List;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.MessageDigest;
import org.springframework.dao.EmptyResultDataAccessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/clientes-web")
@CrossOrigin(origins = "*")
public class ClienteWebController {

    private static final int ITERACIONES = 210_000;
    private static final int BITS_HASH = 256;
    private static final int BYTES_SALT = 16;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public ClienteWebController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/registro")
    public ResponseEntity<Map<String, String>> registrar(
            @RequestBody RegistroRequest datos) {

        if (datos == null || datos.correo() == null || datos.password() == null) {
            return respuesta(HttpStatus.BAD_REQUEST, "Completa el correo y la contraseña.");
        }

        String correo = datos.correo().trim().toLowerCase(Locale.ROOT);
        String password = datos.password();

        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || correo.length() > 100) {
            return respuesta(HttpStatus.BAD_REQUEST, "El correo no es válido.");
        }

        if (password.length() < 8 || password.length() > 100) {
            return respuesta(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 8 y 100 caracteres.");
        }

        try {
            String hash = generarHash(password);

            jdbcTemplate.update("""
                INSERT INTO Cliente_Web (correo, password_hash)
                VALUES (?, ?)
                """, correo, hash);

            return respuesta(HttpStatus.CREATED, "Cuenta creada correctamente.");

        } catch (DuplicateKeyException e) {
            return respuesta(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");

        } catch (GeneralSecurityException e) {
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo proteger la contraseña.");
        }
    }

    private String generarHash(String password) throws GeneralSecurityException {
        byte[] salt = new byte[BYTES_SALT];
        random.nextBytes(salt);

        PBEKeySpec especificacion = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERACIONES,
                BITS_HASH
        );

        byte[] hash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(especificacion)
                .getEncoded();

        especificacion.clearPassword();

        return "pbkdf2$" + ITERACIONES + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private ResponseEntity<Map<String, String>> respuesta(
            HttpStatus estado,
            String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("mensaje", mensaje));
    }

    public record RegistroRequest(String correo, String password) {
    }
    
    @PostMapping("/login")
public ResponseEntity<Map<String, String>> iniciarSesion(
        @RequestBody RegistroRequest datos,
        HttpSession sesion) {

    if (datos == null || datos.correo() == null || datos.password() == null) {
        return respuesta(HttpStatus.BAD_REQUEST, "Ingresa correo y contraseña.");
    }

    String correo = datos.correo().trim().toLowerCase(Locale.ROOT);
    Map<String, Object> cuenta;

    try {
        cuenta = jdbcTemplate.queryForMap("""
            SELECT id_cliente_web, password_hash
            FROM Cliente_Web
            WHERE correo = ? AND activo = 1
            """, correo);
    } catch (EmptyResultDataAccessException e) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
    }

    try {
        String hash = (String) cuenta.get("password_hash");

        if (!verificarPassword(datos.password(), hash)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        }

        int idClienteWeb = ((Number) cuenta.get("id_cliente_web")).intValue();
        sesion.setAttribute("idClienteWeb", idClienteWeb);
        sesion.setAttribute("correoClienteWeb", correo);

        return respuesta(HttpStatus.OK, "Inicio de sesión correcto.");

    } catch (GeneralSecurityException e) {
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo verificar la contraseña.");
    }
}

private boolean verificarPassword(String password, String hashGuardado)
        throws GeneralSecurityException {

    String[] partes = hashGuardado.split("\\$", -1);
    if (partes.length != 4 || !partes[0].equals("pbkdf2")) {
        return false;
    }

    int iteraciones = Integer.parseInt(partes[1]);
    byte[] salt = Base64.getDecoder().decode(partes[2]);
    byte[] hashEsperado = Base64.getDecoder().decode(partes[3]);

    PBEKeySpec especificacion = new PBEKeySpec(
            password.toCharArray(), salt, iteraciones, hashEsperado.length * 8);

    byte[] hashCalculado = SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(especificacion)
            .getEncoded();

    especificacion.clearPassword();

    return MessageDigest.isEqual(hashEsperado, hashCalculado);
}
@GetMapping("/sesion")
public ResponseEntity<Map<String, Object>> consultarSesion(HttpSession sesion) {
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

@PostMapping("/logout")
public ResponseEntity<Map<String, String>> cerrarSesion(HttpSession sesion) {
    sesion.invalidate();
    return respuesta(HttpStatus.OK, "Sesión cerrada.");
}
@GetMapping("/perfil")
public ResponseEntity<?> obtenerPerfil(HttpSession sesion) {
    Object id = sesion.getAttribute("idClienteWeb");
    if (!(id instanceof Number)) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
    }

    try {
        Map<String, Object> perfil = jdbcTemplate.queryForMap("""
            SELECT dni, nombres AS nombre, apellidos, genero,
                   DATE_FORMAT(fecha_nacimiento, '%Y-%m-%d') AS fechaNacimiento,
                   telefono
            FROM Cliente_Web
            WHERE id_cliente_web = ? AND activo = 1
            """, ((Number) id).intValue());

        return ResponseEntity.ok(perfil);
    } catch (EmptyResultDataAccessException e) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontramos el perfil.");
    }
}

@PutMapping("/perfil")
public ResponseEntity<Map<String, String>> guardarPerfil(
        @RequestBody PerfilRequest datos,
        HttpSession sesion) {

    Object id = sesion.getAttribute("idClienteWeb");
    if (!(id instanceof Number)) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
    }
    if (datos == null) {
        return respuesta(HttpStatus.BAD_REQUEST, "No recibimos los datos del perfil.");
    }

    String dni = datos.dni() == null ? "" : datos.dni().trim();
    String nombre = datos.nombre() == null ? "" : datos.nombre().trim();
    String apellidos = datos.apellidos() == null ? "" : datos.apellidos().trim();
    String genero = datos.genero() == null ? "" : datos.genero().trim();
    String fecha = datos.fechaNacimiento() == null ? "" : datos.fechaNacimiento().trim();
    String telefono = datos.telefono() == null ? "" : datos.telefono().trim();

    if (!dni.isEmpty() && !dni.matches("\\d{8}")) {
        return respuesta(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos.");
    }
    if (!fecha.isEmpty()) {
        try {
            LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            return respuesta(HttpStatus.BAD_REQUEST, "La fecha de nacimiento no es válida.");
        }
    }

    try {
        int filas = jdbcTemplate.update("""
            UPDATE Cliente_Web
            SET id_cliente = (
                    SELECT c.id_cliente
                    FROM Cliente c
                    WHERE c.dni = NULLIF(?, '') AND c.activo = 1
                    LIMIT 1
                ),
                dni = NULLIF(?, ''),
                nombres = NULLIF(?, ''),
                apellidos = NULLIF(?, ''),
                genero = NULLIF(?, ''),
                fecha_nacimiento = CASE
                    WHEN ? = '' THEN NULL
                    ELSE STR_TO_DATE(?, '%Y-%m-%d')
                END,
                telefono = NULLIF(?, '')
            WHERE id_cliente_web = ? AND activo = 1
            """,
            dni, dni, nombre, apellidos, genero, fecha, fecha, telefono,
            ((Number) id).intValue());

        if (filas == 0) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos el perfil.");
        }

        return respuesta(HttpStatus.OK, "Perfil guardado en la base de datos.");

    } catch (DuplicateKeyException e) {
        return respuesta(HttpStatus.CONFLICT, "Ese DNI ya está asociado a otra cuenta.");
    }
}

@GetMapping("/dni/{dni}")
public ResponseEntity<?> buscarClientePorDni(
        @PathVariable String dni,
        HttpSession sesion) {

    if (!(sesion.getAttribute("idClienteWeb") instanceof Number)) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
    }
    if (!dni.matches("\\d{8}")) {
        return respuesta(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos.");
    }

    try {
        Map<String, Object> cliente = jdbcTemplate.queryForMap("""
            SELECT nombre, apellidos
            FROM Cliente
            WHERE dni = ? AND activo = 1 AND tipo_cliente = 'Natural'
            """, dni);

        return ResponseEntity.ok(cliente);
    } catch (EmptyResultDataAccessException e) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontramos ese DNI.");
    }
}

public record PerfilRequest(
        String dni,
        String nombre,
        String apellidos,
        String genero,
        String fechaNacimiento,
        String telefono) {
}
@GetMapping("/direcciones")
public ResponseEntity<?> listarDirecciones(HttpSession sesion) {
    Object id = sesion.getAttribute("idClienteWeb");

    if (!(id instanceof Number)) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
    }

    List<Map<String, Object>> direcciones = jdbcTemplate.queryForList("""
        SELECT
            id_direccion_web AS id,
            etiqueta,
            departamento,
            provincia,
            distrito,
            direccion,
            piso_departamento AS pisoDepartamento,
            referencia,
            predeterminada
        FROM Direccion_Cliente_Web
        WHERE id_cliente_web = ? AND activo = 1
        ORDER BY predeterminada DESC, id_direccion_web DESC
        """, ((Number) id).intValue());

    return ResponseEntity.ok(direcciones);
}

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
        return respuesta(HttpStatus.BAD_REQUEST, "Completa los campos obligatorios.");
    }

    jdbcTemplate.update("""
        INSERT INTO Direccion_Cliente_Web
            (id_cliente_web, etiqueta, departamento, provincia, distrito,
             direccion, piso_departamento, referencia)
        VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''))
        """,
        ((Number) id).intValue(),
        datos.etiqueta().trim(),
        datos.departamento().trim(),
        datos.provincia().trim(),
        datos.distrito().trim(),
        datos.direccion().trim(),
        datos.pisoDepartamento() == null ? "" : datos.pisoDepartamento().trim(),
        datos.referencia() == null ? "" : datos.referencia().trim());

    return respuesta(HttpStatus.CREATED, "Dirección guardada en la base de datos.");
}

private boolean vacio(String texto) {
    return texto == null || texto.trim().isEmpty();
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
@PutMapping("/direcciones/{id}/predeterminada")
@Transactional
public ResponseEntity<Map<String, String>> marcarDireccionPredeterminada(
        @PathVariable int id,
        HttpSession sesion) {

    Object idCliente = sesion.getAttribute("idClienteWeb");

    if (!(idCliente instanceof Number)) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
    }

    int idClienteWeb = ((Number) idCliente).intValue();

    Integer existe = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM Direccion_Cliente_Web
        WHERE id_direccion_web = ?
          AND id_cliente_web = ?
          AND activo = 1
        """, Integer.class, id, idClienteWeb);

    if (existe == null || existe == 0) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontramos esa dirección.");
    }

    jdbcTemplate.update("""
        UPDATE Direccion_Cliente_Web
        SET predeterminada =
            CASE WHEN id_direccion_web = ? THEN 1 ELSE 0 END
        WHERE id_cliente_web = ? AND activo = 1
        """, id, idClienteWeb);

    return respuesta(HttpStatus.OK, "Dirección predeterminada actualizada.");
}
}