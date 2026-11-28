package pe.ferreteria.backendweb.servicio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class RecuperacionPasswordService {

    private static final Logger LOG =
            LoggerFactory.getLogger(RecuperacionPasswordService.class);

    static final int MINUTOS_VIGENCIA = 10;
    static final int MAX_INTENTOS = 5;
    static final int SEGUNDOS_ENTRE_SOLICITUDES = 30;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteWebService clienteWebService;
    private final Clock reloj;
    private final Supplier<String> generadorCodigo;
    private final Map<String, Solicitud> solicitudes = new ConcurrentHashMap<>();

    @Autowired
    public RecuperacionPasswordService(ClienteWebService clienteWebService) {
        this(clienteWebService, Clock.systemUTC(),
                RecuperacionPasswordService::generarCodigoSeguro);
    }

    RecuperacionPasswordService(ClienteWebService clienteWebService,
            Clock reloj, Supplier<String> generadorCodigo) {
        this.clienteWebService = clienteWebService;
        this.reloj = reloj;
        this.generadorCodigo = generadorCodigo;
    }

    private static String generarCodigoSeguro() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public void solicitar(String correo) {

        String clave = normalizar(correo);
        Instant ahora = reloj.instant();

        Solicitud previa = solicitudes.get(clave);

        if (previa != null
                && previa.creada.plusSeconds(SEGUNDOS_ENTRE_SOLICITUDES)
                        .isAfter(ahora)) {
            return;
        }

        try {
            Map<String, Object> cuenta =
                    clienteWebService.buscarCuentaPorCorreo(clave);

            int idClienteWeb =
                    ((Number) cuenta.get("id_cliente_web")).intValue();

            String codigo = generadorCodigo.get();

            solicitudes.put(clave, new Solicitud(
                    idClienteWeb,
                    codigo,
                    ahora,
                    ahora.plus(MINUTOS_VIGENCIA, ChronoUnit.MINUTES)
            ));

            LOG.info("CORREO SIMULADO para {}: su codigo de recuperacion es {} "
                    + "(vence en {} minutos)", clave, codigo, MINUTOS_VIGENCIA);

        } catch (EmptyResultDataAccessException e) {
            LOG.info("Recuperacion solicitada para un correo no registrado");
        }
    }

    public boolean confirmar(String correo, String codigo,
            String nuevaPassword) {

        String clave = normalizar(correo);
        Solicitud solicitud = solicitudes.get(clave);

        if (solicitud == null || codigo == null || nuevaPassword == null) {
            return false;
        }

        if (reloj.instant().isAfter(solicitud.vence)) {
            solicitudes.remove(clave);
            return false;
        }

        if (solicitud.intentos.incrementAndGet() > MAX_INTENTOS) {
            solicitudes.remove(clave);
            LOG.warn("Recuperacion bloqueada por demasiados intentos");
            return false;
        }

        boolean coincide = MessageDigest.isEqual(
                solicitud.codigo.getBytes(StandardCharsets.UTF_8),
                codigo.trim().getBytes(StandardCharsets.UTF_8));

        if (!coincide) {
            return false;
        }

        try {
            if (clienteWebService.cambiarPassword(
                    solicitud.idClienteWeb, nuevaPassword) <= 0) {
                return false;
            }
        } catch (GeneralSecurityException e) {
            LOG.error("No se pudo proteger la contrasena nueva", e);
            return false;
        }

        solicitudes.remove(clave);
        LOG.info("Contrasena restablecida cliente_web_id={}",
                solicitud.idClienteWeb);

        return true;
    }

    private static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Solicitud {

        final int idClienteWeb;
        final String codigo;
        final Instant creada;
        final Instant vence;
        final AtomicInteger intentos = new AtomicInteger();

        Solicitud(int idClienteWeb, String codigo, Instant creada,
                Instant vence) {
            this.idClienteWeb = idClienteWeb;
            this.codigo = codigo;
            this.creada = creada;
            this.vence = vence;
        }
    }
}