package pe.ferreteria.backendweb.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;

import pe.ferreteria.backendweb.dao.ClienteWebDAO;

class RecuperacionPasswordServiceTest {

    private static final String CORREO = "cliente@correo.com";
    private static final String CODIGO = "123456";

    private DaoFalso dao;
    private RelojFalso reloj;
    private RecuperacionPasswordService service;

    @BeforeEach
    void preparar() {
        dao = new DaoFalso();
        reloj = new RelojFalso();
        service = new RecuperacionPasswordService(
                new ClienteWebService(dao), reloj, () -> CODIGO);
    }

    @Test
    void codigoCorrectoCambiaLaPasswordYGuardaHash() {
        service.solicitar(CORREO);

        assertTrue(service.confirmar(CORREO, CODIGO, "ClaveNueva123"));
        assertEquals(7, dao.idActualizado);
        assertTrue(dao.hashGuardado.startsWith("pbkdf2$"));
        assertFalse(dao.hashGuardado.contains("ClaveNueva123"));
    }

    @Test
    void codigoIncorrectoNoCambiaNada() {
        service.solicitar(CORREO);

        assertFalse(service.confirmar(CORREO, "000000", "ClaveNueva123"));
        assertEquals(0, dao.cambios);
    }

    @Test
    void codigoVencidoEsRechazado() {
        service.solicitar(CORREO);
        reloj.avanzar(Duration.ofMinutes(11));

        assertFalse(service.confirmar(CORREO, CODIGO, "ClaveNueva123"));
        assertEquals(0, dao.cambios);
    }

    @Test
    void codigoSoloSirveUnaVez() {
        service.solicitar(CORREO);

        assertTrue(service.confirmar(CORREO, CODIGO, "ClaveNueva123"));
        assertFalse(service.confirmar(CORREO, CODIGO, "OtraClave456"));
        assertEquals(1, dao.cambios);
    }

    @Test
    void trasCincoIntentosFallidosSeBloqueaAunqueElCodigoSeaCorrecto() {
        service.solicitar(CORREO);

        for (int i = 0; i < 5; i++) {
            assertFalse(service.confirmar(CORREO, "000000", "ClaveNueva123"));
        }

        assertFalse(service.confirmar(CORREO, CODIGO, "ClaveNueva123"));
        assertEquals(0, dao.cambios);
    }

    @Test
    void correoNoRegistradoNoGeneraCodigoNiFalla() {
        service.solicitar("nadie@correo.com");

        assertFalse(service.confirmar("nadie@correo.com", CODIGO,
                "ClaveNueva123"));
        assertEquals(0, dao.cambios);
    }

    @Test
    void noSePuedeConfirmarSinSolicitarAntes() {
        assertFalse(service.confirmar(CORREO, CODIGO, "ClaveNueva123"));
    }

    // ---- Falsos para no depender de base de datos ni de Mockito ----

    private static class DaoFalso extends ClienteWebDAO {

        int cambios;
        int idActualizado;
        String hashGuardado;

        DaoFalso() {
            super(null);
        }

        @Override
        public Map<String, Object> buscarCuentaPorCorreo(String correo) {
            if (!CORREO.equals(correo)) {
                throw new EmptyResultDataAccessException(1);
            }
            return Map.of("id_cliente_web", 7, "password_hash", "x");
        }

        @Override
        public int actualizarPassword(int idClienteWeb, String hash) {
            cambios++;
            idActualizado = idClienteWeb;
            hashGuardado = hash;
            return 1;
        }
    }

    private static class RelojFalso extends Clock {

        private Instant ahora = Instant.parse("2026-12-02T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}