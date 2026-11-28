
package pe.ferreteria.backendweb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Agrega cabeceras de seguridad HTTP a todas las respuestas del sitio.
 */
@Component
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    static final String POLITICA_CSP =
            "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
            + "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; "
            + "img-src 'self' data: https://images.unsplash.com; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        response.setHeader("Content-Security-Policy", POLITICA_CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=()");

        filterChain.doFilter(request, response);
    }
}
