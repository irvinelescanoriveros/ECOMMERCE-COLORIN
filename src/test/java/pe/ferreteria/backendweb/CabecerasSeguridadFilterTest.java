package pe.ferreteria.backendweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.ferreteria.backendweb.config.CabecerasSeguridadFilter;

class CabecerasSeguridadFilterTest {

    private MockHttpServletResponse ejecutar() throws Exception {
        CabecerasSeguridadFilter filtro = new CabecerasSeguridadFilter();
        MockHttpServletRequest peticion =
                new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(peticion, respuesta, cadena);

        // La peticion debe seguir su camino por la cadena de filtros
        assertNotNull(cadena.getRequest());
        return respuesta;
    }

    @Test
    @DisplayName("Agrega las cabeceras de seguridad a la respuesta")
    void agregaCabecerasDeSeguridad() throws Exception {
        MockHttpServletResponse respuesta = ejecutar();

        assertEquals("nosniff", respuesta.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", respuesta.getHeader("X-Frame-Options"));
        assertNotNull(respuesta.getHeader("Content-Security-Policy"));
        assertNotNull(respuesta.getHeader("Referrer-Policy"));
    }

    @Test
    @DisplayName("La CSP bloquea marcos externos y no permite codigo en linea")
    void cspEsEstricta() throws Exception {
        String csp = ejecutar().getHeader("Content-Security-Policy");

        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertFalse(csp.contains("unsafe-inline"));
        assertFalse(csp.contains("unsafe-eval"));
    }
}