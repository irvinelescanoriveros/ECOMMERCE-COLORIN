

package pe.ferreteria.backendweb.servicio;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;

import pe.ferreteria.backendweb.dao.ClienteWebDAO;

@Service
public class ClienteWebService {

    private static final int ITERACIONES = 210_000;
    private static final int BITS_HASH = 256;
    private static final int BYTES_SALT = 16;

    private final ClienteWebDAO clienteWebDAO;
    private final SecureRandom random = new SecureRandom();

    public ClienteWebService(ClienteWebDAO clienteWebDAO) {
        this.clienteWebDAO = clienteWebDAO;
    }

    // REGISTRO Y CONTRASEÑAS

    public int registrar(String correo, String password)
            throws GeneralSecurityException {

        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        String hash = generarHash(password);

        return clienteWebDAO.registrar(correoNormalizado, hash);
    }

    private String generarHash(String password)
            throws GeneralSecurityException {

        byte[] salt = new byte[BYTES_SALT];
        random.nextBytes(salt);

        PBEKeySpec especificacion = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERACIONES,
                BITS_HASH
        );

        try {
            byte[] hash = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(especificacion)
                    .getEncoded();

            return "pbkdf2$" + ITERACIONES + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } finally {
            especificacion.clearPassword();
        }
    }

    public Map<String, Object> buscarCuentaPorCorreo(String correo) {
        return clienteWebDAO.buscarCuentaPorCorreo(
                correo.trim().toLowerCase(Locale.ROOT));
    }

    public boolean verificarPassword(String password, String hashGuardado)
            throws GeneralSecurityException {

        if (password == null || hashGuardado == null) {
            return false;
        }

        String[] partes = hashGuardado.split("\\$", -1);

        if (partes.length != 4 || !partes[0].equals("pbkdf2")) {
            return false;
        }

        try {
            int iteraciones = Integer.parseInt(partes[1]);

            if (iteraciones <= 0) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] hashEsperado = Base64.getDecoder().decode(partes[3]);

            if (salt.length == 0 || hashEsperado.length == 0) {
                return false;
            }

            PBEKeySpec especificacion = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    iteraciones,
                    hashEsperado.length * 8
            );

            try {
                byte[] hashCalculado = SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(especificacion)
                        .getEncoded();

                return MessageDigest.isEqual(hashEsperado, hashCalculado);
            } finally {
                especificacion.clearPassword();
            }

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // PERFIL

    public Map<String, Object> obtenerPerfil(int idClienteWeb) {
        return clienteWebDAO.obtenerPerfil(idClienteWeb);
    }

    public int actualizarPerfil(
            int idClienteWeb,
            String dni,
            String nombre,
            String apellidos,
            String genero,
            String fecha,
            String telefono) {

        return clienteWebDAO.actualizarPerfil(
                idClienteWeb, dni, nombre, apellidos,
                genero, fecha, telefono);
    }

    public Map<String, Object> buscarClientePorDni(String dni) {
        return clienteWebDAO.buscarClientePorDni(dni);
    }

    // DIRECCIONES

    public List<Map<String, Object>> listarDirecciones(int idClienteWeb) {
        return clienteWebDAO.listarDirecciones(idClienteWeb);
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

        return clienteWebDAO.guardarDireccion(
                idClienteWeb, etiqueta, departamento, provincia,
                distrito, direccion, pisoDepartamento, referencia);
    }

    public boolean existeDireccion(int idDireccion, int idClienteWeb) {
        return clienteWebDAO.existeDireccion(idDireccion, idClienteWeb);
    }

    public int marcarDireccionPredeterminada(
            int idDireccion, int idClienteWeb) {

        if (!clienteWebDAO.existeDireccion(idDireccion, idClienteWeb)) {
            return 0;
        }

        return clienteWebDAO.marcarDireccionPredeterminada(
                idDireccion, idClienteWeb);
    }
}
