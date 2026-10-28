
package pe.ferreteria.backendweb.dao;

import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ClienteWebDAO {

    private final JdbcTemplate jdbcTemplate;

    public ClienteWebDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // REGISTRO Y AUTENTICACIÓN

    public int registrar(String correo, String hash) {
        return jdbcTemplate.update("""
            INSERT INTO Cliente_Web (correo, password_hash)
            VALUES (?, ?)
            """, correo, hash);
    }

    public Map<String, Object> buscarCuentaPorCorreo(String correo) {
        return jdbcTemplate.queryForMap("""
            SELECT id_cliente_web, password_hash
            FROM Cliente_Web
            WHERE correo = ? AND activo = 1
            """, correo);
    }

    // PERFIL DEL CLIENTE

    public Map<String, Object> obtenerPerfil(int idClienteWeb) {
        return jdbcTemplate.queryForMap("""
            SELECT dni, nombres AS nombre, apellidos, genero,
                   DATE_FORMAT(fecha_nacimiento, '%Y-%m-%d') AS fechaNacimiento,
                   telefono
            FROM Cliente_Web
            WHERE id_cliente_web = ? AND activo = 1
            """, idClienteWeb);
    }

    public int actualizarPerfil(
            int idClienteWeb,
            String dni,
            String nombre,
            String apellidos,
            String genero,
            String fecha,
            String telefono) {

        return jdbcTemplate.update("""
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
            dni, dni, nombre, apellidos, genero,
            fecha, fecha, telefono, idClienteWeb);
    }

    public Map<String, Object> buscarClientePorDni(String dni) {
        return jdbcTemplate.queryForMap("""
            SELECT nombre, apellidos
            FROM Cliente
            WHERE dni = ? AND activo = 1
              AND tipo_cliente = 'Natural'
            """, dni);
    }

    // DIRECCIONES

    public List<Map<String, Object>> listarDirecciones(int idClienteWeb) {
        return jdbcTemplate.queryForList("""
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
            """, idClienteWeb);
    }

    public int guardarDireccion(
            int idClienteWeb,
            String etiqueta,
            String departamento,
            String provincia,
            String distrito,
            String direccion,
            String pisoDepartamento,
            String referencia) {

        return jdbcTemplate.update("""
            INSERT INTO Direccion_Cliente_Web
                (id_cliente_web, etiqueta, departamento, provincia, distrito,
                 direccion, piso_departamento, referencia)
            VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''))
            """,
            idClienteWeb, etiqueta, departamento, provincia, distrito,
            direccion, pisoDepartamento, referencia);
    }

    public boolean existeDireccion(int idDireccion, int idClienteWeb) {
        Integer cantidad = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM Direccion_Cliente_Web
            WHERE id_direccion_web = ?
              AND id_cliente_web = ?
              AND activo = 1
            """, Integer.class, idDireccion, idClienteWeb);

        return cantidad != null && cantidad > 0;
    }

    @Transactional
    public int marcarDireccionPredeterminada(
            int idDireccion, int idClienteWeb) {

        return jdbcTemplate.update("""
            UPDATE Direccion_Cliente_Web
            SET predeterminada =
                CASE WHEN id_direccion_web = ? THEN 1 ELSE 0 END
            WHERE id_cliente_web = ? AND activo = 1
            """, idDireccion, idClienteWeb);
    }
}
