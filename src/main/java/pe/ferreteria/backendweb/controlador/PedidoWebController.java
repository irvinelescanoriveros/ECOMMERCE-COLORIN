package pe.ferreteria.backendweb.controlador;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos-web")
@CrossOrigin(origins = "*")
public class PedidoWebController {

    private static final BigDecimal COSTO_ENVIO = new BigDecimal("4.99");
    private static final Set<String> METODOS_PAGO = Set.of(
            "sip-oh-credito", "sip-oh-debito", "app-sip", "interbank", "otras-tarjetas");

    private final JdbcTemplate jdbcTemplate;

    public PedidoWebController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listarPedidos(HttpSession sesion) {
        Object idSesion = sesion.getAttribute("idClienteWeb");
        if (!(idSesion instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para ver tus pedidos.");
        }

        int idClienteWeb = ((Number) idSesion).intValue();
        List<Map<String, Object>> pedidos = jdbcTemplate.queryForList("""
            SELECT id_pedido_web AS id,
                   numero_pedido AS numero,
                   DATE_FORMAT(fecha_pedido, '%Y-%m-%dT%H:%i:%s') AS fecha,
                   estado_pedido AS estado,
                   estado_pago AS estadoPago,
                   metodo_pago AS metodoPago,
                   total
            FROM Pedido_Web
            WHERE id_cliente_web = ?
            ORDER BY fecha_pedido DESC, id_pedido_web DESC
            """, idClienteWeb);

        List<Map<String, Object>> respuestaPedidos = new java.util.ArrayList<>();
        for (Map<String, Object> pedido : pedidos) {
            Map<String, Object> resultado = new LinkedHashMap<>(pedido);
            Integer idPedido = ((Number) pedido.get("id")).intValue();
            resultado.put("productos", jdbcTemplate.queryForList("""
                SELECT nombre_producto AS nombre, cantidad, subtotal
                FROM Detalle_Pedido_Web
                WHERE id_pedido_web = ?
                ORDER BY id_detalle_pedido_web
                """, idPedido));
            respuestaPedidos.add(resultado);
        }

        return ResponseEntity.ok(respuestaPedidos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> registrarPedido(
            @RequestBody PedidoRequest datos,
            HttpSession sesion) {

        Object idSesion = sesion.getAttribute("idClienteWeb");
        if (!(idSesion instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para registrar el pedido.");
        }
        if (datos == null || datos.productos() == null || datos.productos().isEmpty()) {
            return respuesta(HttpStatus.BAD_REQUEST, "El carrito está vacío.");
        }

        String metodoPago = limpiar(datos.metodoPago());
        String metodoEntrega = limpiar(datos.metodoEntrega());
        String tipoComprobante = limpiar(datos.tipoComprobante());
        if (!METODOS_PAGO.contains(metodoPago)) {
            return respuesta(HttpStatus.BAD_REQUEST, "Selecciona un método de pago válido.");
        }
        if (!metodoEntrega.equals("Domicilio") && !metodoEntrega.equals("Recojo")) {
            return respuesta(HttpStatus.BAD_REQUEST, "Selecciona un método de entrega válido.");
        }
        if (!tipoComprobante.equals("Boleta") && !tipoComprobante.equals("Factura")) {
            return respuesta(HttpStatus.BAD_REQUEST, "Selecciona Boleta o Factura.");
        }
        if (metodoEntrega.equals("Domicilio")
                && (vacio(datos.departamento()) || vacio(datos.provincia())
                || vacio(datos.distrito()) || vacio(datos.direccion()))) {
            return respuesta(HttpStatus.BAD_REQUEST, "Completa los datos obligatorios de entrega.");
        }
        if (metodoEntrega.equals("Recojo") && vacio(datos.puntoRecojo())) {
            return respuesta(HttpStatus.BAD_REQUEST, "Selecciona el punto de recojo.");
        }
        String ruc = limpiar(datos.rucFactura());
        String razonSocial = limpiar(datos.razonSocial());
        if (tipoComprobante.equals("Factura")
                && (!ruc.matches("\\d{11}") || razonSocial.isBlank())) {
            return respuesta(HttpStatus.BAD_REQUEST, "Para emitir factura ingresa RUC y razón social.");
        }

        Date fechaEntrega = null;
        if (!vacio(datos.fechaEntrega())) {
            try {
                fechaEntrega = Date.valueOf(LocalDate.parse(datos.fechaEntrega()));
            } catch (DateTimeParseException e) {
                return respuesta(HttpStatus.BAD_REQUEST, "La fecha de entrega no es válida.");
            }
        }

        Set<Integer> ids = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : datos.productos()) {
            if (item == null || item.idProducto() <= 0 || item.cantidad() <= 0
                    || !ids.add(item.idProducto())) {
                return respuesta(HttpStatus.BAD_REQUEST, "Hay productos o cantidades inválidas en el carrito.");
            }
            try {
                Map<String, Object> producto = jdbcTemplate.queryForMap("""
                    SELECT p.codigo, p.nombre, SUM(i.stock) AS stock,
                           MIN(CASE WHEN i.descuento_activo = 1
                               THEN ROUND(i.precio_venta * (1 - i.descuento_porcentaje / 100), 2)
                               ELSE i.precio_venta END) AS precio
                    FROM Producto p
                    JOIN Categoria c ON c.id_categoria = p.id_categoria AND c.activo = 1
                    JOIN Marca m ON m.id_marca = p.id_marca AND m.activo = 1
                    JOIN Inventario i ON i.id_producto = p.id_producto AND i.activo = 1 AND i.stock > 0
                    WHERE p.id_producto = ? AND p.activo = 1
                    GROUP BY p.id_producto, p.codigo, p.nombre
                    HAVING SUM(i.stock) >= ?
                    """, item.idProducto(), item.cantidad());
                BigDecimal precio = (BigDecimal) producto.get("precio");
                subtotal = subtotal.add(precio.multiply(BigDecimal.valueOf(item.cantidad())));
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                return respuesta(HttpStatus.CONFLICT,
                        "Un producto ya no tiene stock suficiente. Actualiza el carrito e inténtalo de nuevo.");
            }
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoEnvio = metodoEntrega.equals("Domicilio") ? COSTO_ENVIO : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(costoEnvio).setScale(2, RoundingMode.HALF_UP);
        int idClienteWeb = ((Number) idSesion).intValue();
        String numeroPedido = "FCW-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT);

        jdbcTemplate.update("""
            INSERT INTO Pedido_Web
                (numero_pedido, id_cliente_web, estado_pedido, estado_pago, metodo_pago,
                 metodo_entrega, departamento, provincia, distrito, direccion,
                 piso_departamento, referencia, fecha_entrega, punto_recojo,
                 tipo_comprobante, ruc_factura, razon_social, subtotal, costo_envio, total)
            VALUES (?, ?, 'Pendiente', 'Pendiente', ?, ?, NULLIF(?, ''), NULLIF(?, ''),
                    NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?,
                    NULLIF(?, ''), ?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?)
            """,
                numeroPedido, idClienteWeb, metodoPago, metodoEntrega,
                limpiar(datos.departamento()), limpiar(datos.provincia()), limpiar(datos.distrito()),
                limpiar(datos.direccion()), limpiar(datos.pisoDepartamento()), limpiar(datos.referencia()),
                fechaEntrega, limpiar(datos.puntoRecojo()), tipoComprobante,
                tipoComprobante.equals("Factura") ? ruc : "",
                tipoComprobante.equals("Factura") ? razonSocial : "",
                subtotal, costoEnvio, total);

        Integer idPedido = jdbcTemplate.queryForObject(
                "SELECT id_pedido_web FROM Pedido_Web WHERE numero_pedido = ?",
                Integer.class, numeroPedido);

        for (ItemPedido item : datos.productos()) {
            Map<String, Object> producto = jdbcTemplate.queryForMap("""
                SELECT p.codigo, p.nombre,
                       MIN(CASE WHEN i.descuento_activo = 1
                           THEN ROUND(i.precio_venta * (1 - i.descuento_porcentaje / 100), 2)
                           ELSE i.precio_venta END) AS precio
                FROM Producto p
                JOIN Inventario i ON i.id_producto = p.id_producto AND i.activo = 1 AND i.stock > 0
                WHERE p.id_producto = ? AND p.activo = 1
                GROUP BY p.id_producto, p.codigo, p.nombre
                """, item.idProducto());
            jdbcTemplate.update("""
                INSERT INTO Detalle_Pedido_Web
                    (id_pedido_web, id_producto, codigo_producto, nombre_producto, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                    idPedido, item.idProducto(), producto.get("codigo"), producto.get("nombre"),
                    item.cantidad(), producto.get("precio"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Pedido registrado y pendiente de pago.",
                "idPedido", idPedido,
                "numeroPedido", numeroPedido,
                "subtotal", subtotal,
                "costoEnvio", costoEnvio,
                "total", total,
                "estadoPago", "Pendiente"));
    }

    private ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("mensaje", mensaje));
    }

    private boolean vacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public record ItemPedido(int idProducto, int cantidad) { }

    public record PedidoRequest(
            List<ItemPedido> productos,
            String metodoPago,
            String metodoEntrega,
            String departamento,
            String provincia,
            String distrito,
            String direccion,
            String pisoDepartamento,
            String referencia,
            String fechaEntrega,
            String puntoRecojo,
            String tipoComprobante,
            String rucFactura,
            String razonSocial) { }
}
