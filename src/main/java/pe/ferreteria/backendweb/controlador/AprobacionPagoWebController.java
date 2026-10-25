package pe.ferreteria.backendweb.controlador;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simula la aprobación del pago y convierte el pedido web en una venta.
 */
@RestController
@RequestMapping("/api/pedidos-web")
@CrossOrigin(origins = "*")
public class AprobacionPagoWebController {

    private final JdbcTemplate jdbcTemplate;

    public AprobacionPagoWebController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PutMapping("/{id}/simular-aprobacion")
    @Transactional
    public ResponseEntity<?> simularAprobacion(
            @PathVariable int id,
            HttpSession sesion) {

        Object idSesion = sesion.getAttribute("idClienteWeb");
        if (!(idSesion instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }

        int idClienteWeb = ((Number) idSesion).intValue();
        Map<String, Object> pedido;
        try {
            pedido = jdbcTemplate.queryForMap("""
                SELECT id_pedido_web, numero_pedido, id_cliente_web, id_venta,
                       estado_pedido, estado_pago, metodo_pago, metodo_entrega,
                       departamento, provincia, distrito, direccion,
                       piso_departamento, referencia, punto_recojo,
                       tipo_comprobante, ruc_factura, razon_social,
                       subtotal, costo_envio, total
                FROM Pedido_Web
                WHERE id_pedido_web = ? AND id_cliente_web = ?
                FOR UPDATE
                """, id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        Object idVentaExistente = pedido.get("id_venta");
        if ("Confirmado".equals(pedido.get("estado_pago")) && idVentaExistente instanceof Number) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Este pedido ya fue aprobado.",
                    "nroComprobante", comprobanteExistente(((Number) idVentaExistente).intValue())));
        }
        if (!"Pendiente".equals(pedido.get("estado_pago"))) {
            return respuesta(HttpStatus.CONFLICT, "El pedido no está pendiente de pago.");
        }

        Integer idUsuarioWeb = jdbcTemplate.query("""
            SELECT id_usuario FROM Usuario
            WHERE username = 'OperacionWEB' AND rol = 'WEB' AND activo = 1
            """, rs -> rs.next() ? rs.getInt(1) : null);
        if (idUsuarioWeb == null) {
            return respuesta(HttpStatus.CONFLICT,
                    "No existe el usuario activo OperacionWEB con rol WEB.");
        }

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList("""
            SELECT id_producto, cantidad, precio_unitario
            FROM Detalle_Pedido_Web
            WHERE id_pedido_web = ?
            ORDER BY id_detalle_pedido_web
            FOR UPDATE
            """, id);
        if (detalles.isEmpty()) {
            return respuesta(HttpStatus.CONFLICT, "El pedido no tiene productos.");
        }

        List<AsignacionLote> asignaciones = new ArrayList<>();
        for (Map<String, Object> detalle : detalles) {
            int idProducto = ((Number) detalle.get("id_producto")).intValue();
            int cantidadSolicitada = ((Number) detalle.get("cantidad")).intValue();
            BigDecimal precioPedido = (BigDecimal) detalle.get("precio_unitario");
            List<Map<String, Object>> lotes = jdbcTemplate.queryForList("""
                SELECT id_inventario, stock, stock_defectuoso
                FROM Inventario
                WHERE id_producto = ? AND activo = 1 AND stock > 0
                ORDER BY fecha_ingreso, id_inventario
                FOR UPDATE
                """, idProducto);

            int disponible = lotes.stream()
                    .mapToInt(lote -> ((Number) lote.get("stock")).intValue())
                    .sum();
            if (disponible < cantidadSolicitada) {
                return respuesta(HttpStatus.CONFLICT,
                        "No hay stock suficiente para todos los productos. El pedido sigue pendiente.");
            }

            int porAsignar = cantidadSolicitada;
            for (Map<String, Object> lote : lotes) {
                if (porAsignar == 0) {
                    break;
                }
                int stockAntes = ((Number) lote.get("stock")).intValue();
                int defectuoso = ((Number) lote.get("stock_defectuoso")).intValue();
                int cantidadLote = Math.min(stockAntes, porAsignar);
                asignaciones.add(new AsignacionLote(
                        idProducto,
                        ((Number) lote.get("id_inventario")).intValue(),
                        cantidadLote,
                        stockAntes,
                        defectuoso,
                        precioPedido));
                porAsignar -= cantidadLote;
            }
        }

        Map<String, Object> cuenta;
        try {
            cuenta = jdbcTemplate.queryForMap("""
                SELECT id_cliente, dni, nombres, apellidos, telefono
                FROM Cliente_Web
                WHERE id_cliente_web = ? AND activo = 1
                FOR UPDATE
                """, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos la cuenta web.");
        }

        boolean esFactura = "Factura".equals(pedido.get("tipo_comprobante"));
        int idCliente = esFactura
                ? obtenerClienteJuridico(cuenta, pedido)
                : obtenerClienteNatural(cuenta, pedido, idClienteWeb);

        String serie = esFactura ? "F001" : "B001";

        Integer siguienteNumero = jdbcTemplate.queryForObject(
                """
        SELECT COALESCE(MAX(numero), 0) + 1
        FROM Venta
        WHERE serie = ?
        """,
                Integer.class,
                serie
        );

        int numero = siguienteNumero != null ? siguienteNumero : 1;
        String nroComprobante = serie + "-" + String.format("%08d", numero);
        BigDecimal total = (BigDecimal) pedido.get("total");
        BigDecimal costoEnvio = (BigDecimal) pedido.get("costo_envio");
        BigDecimal subtotalVenta = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotalVenta).setScale(2, RoundingMode.HALF_UP);
        String metodoPagoVenta = "app-sip".equals(pedido.get("metodo_pago"))
                ? "Transferencia" : "Tarjeta";

        jdbcTemplate.update("""
            INSERT INTO Venta
                (id_cliente, id_usuario, subtotal, igv, total, costo_envio,
                 tipo_comprobante, serie, numero, nro_comprobante, metodo_pago,
                 monto_recibido, vuelto, estado_comprobante, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, 0.00, 'Pendiente', 1)
            """,
                idCliente, idUsuarioWeb, subtotalVenta, igv, total, costoEnvio,
                pedido.get("tipo_comprobante"), serie, numero, nroComprobante, metodoPagoVenta);

        Integer idVenta = jdbcTemplate.queryForObject(
                "SELECT id_venta FROM Venta WHERE nro_comprobante = ?",
                Integer.class, nroComprobante);

        for (AsignacionLote asignacion : asignaciones) {
            int stockDespues = asignacion.stockAntes() - asignacion.cantidad();
            int reducido = jdbcTemplate.update("""
                UPDATE Inventario
                SET stock = stock - ?
                WHERE id_inventario = ? AND stock >= ?
                """, asignacion.cantidad(), asignacion.idInventario(), asignacion.cantidad());
            if (reducido != 1) {
                throw new IllegalStateException("El stock cambió durante la aprobación. Intenta nuevamente.");
            }

            jdbcTemplate.update("""
                INSERT INTO Detalle_Venta
                    (id_venta, id_inventario, cantidad, precio_unitario, descuento)
                VALUES (?, ?, ?, ?, 0.00)
                """, idVenta, asignacion.idInventario(), asignacion.cantidad(), asignacion.precioUnitario());

            jdbcTemplate.update("""
                INSERT INTO Kardex
                    (id_producto, id_inventario, id_usuario, fecha_movimiento,
                     movimiento, motivo_movimiento, cantidad, stock_antes, stock_despues,
                     stock_defectuoso_antes, stock_defectuoso_despues, precio_unitario,
                     tipo_referencia, id_referencia, nro_comprobante, motivo)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'Salida', 'Venta', ?, ?, ?, ?, ?, ?,
                        'Venta', ?, ?, ?)
                """,
                    asignacion.idProducto(), asignacion.idInventario(), idUsuarioWeb,
                    asignacion.cantidad(), asignacion.stockAntes(), stockDespues,
                    asignacion.stockDefectuoso(), asignacion.stockDefectuoso(),
                    asignacion.precioUnitario(), idVenta, nroComprobante,
                    "Aprobación web del pedido " + pedido.get("numero_pedido"));
        }

        jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET id_venta = ?, estado_pago = 'Confirmado', estado_pedido = 'En preparacion'
            WHERE id_pedido_web = ? AND id_cliente_web = ? AND estado_pago = 'Pendiente'
            """, idVenta, id, idClienteWeb);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Aprobación simulada; la venta y el Kardex fueron registrados.",
                "numeroPedido", pedido.get("numero_pedido"),
                "idVenta", idVenta,
                "nroComprobante", nroComprobante,
                "estadoPago", "Confirmado",
                "estadoPedido", "En preparacion"));
    }

    @PutMapping("/{id}/simular-rechazo")
    @Transactional
    public ResponseEntity<?> simularRechazo(
            @PathVariable int id,
            HttpSession sesion) {

        Object idSesion = sesion.getAttribute("idClienteWeb");
        if (!(idSesion instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }

        int idClienteWeb = ((Number) idSesion).intValue();
        Map<String, Object> pedido;
        try {
            pedido = jdbcTemplate.queryForMap("""
                SELECT id_pedido_web, id_venta, estado_pago
                FROM Pedido_Web
                WHERE id_pedido_web = ? AND id_cliente_web = ?
                FOR UPDATE
                """, id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        if (!"Pendiente".equals(pedido.get("estado_pago"))
                || pedido.get("id_venta") != null) {
            return respuesta(HttpStatus.CONFLICT,
                    "Solo se puede simular el rechazo de un pago pendiente.");
        }

        int actualizados = jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET estado_pago = 'Rechazado', estado_pedido = 'Cancelado'
            WHERE id_pedido_web = ? AND id_cliente_web = ?
              AND estado_pago = 'Pendiente' AND id_venta IS NULL
            """, id, idClienteWeb);
        if (actualizados != 1) {
            return respuesta(HttpStatus.CONFLICT,
                    "El estado del pedido cambió. Actualiza la página e inténtalo de nuevo.");
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Rechazo de pago simulado. No se registró una venta ni se modificó el stock.",
                "estadoPago", "Rechazado",
                "estadoPedido", "Cancelado"));
    }

    @PutMapping("/{id}/simular-despacho")
    @Transactional
    public ResponseEntity<?> simularDespacho(
            @PathVariable int id,
            HttpSession sesion) {
        return avanzarEstadoPedido(id, sesion, "En preparacion", "Enviado",
                "Despacho simulado. El pedido ahora figura como enviado.");
    }

    @PutMapping("/{id}/simular-entrega")
    @Transactional
    public ResponseEntity<?> simularEntrega(
            @PathVariable int id,
            HttpSession sesion) {
        return avanzarEstadoPedido(id, sesion, "Enviado", "Entregado",
                "Entrega simulada. El pedido ahora figura como entregado.");
    }

    private ResponseEntity<?> avanzarEstadoPedido(
            int id,
            HttpSession sesion,
            String estadoActual,
            String estadoSiguiente,
            String mensaje) {
        Object idSesion = sesion.getAttribute("idClienteWeb");
        if (!(idSesion instanceof Number)) {
            return respuesta(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }

        int idClienteWeb = ((Number) idSesion).intValue();
        Map<String, Object> pedido;
        try {
            pedido = jdbcTemplate.queryForMap("""
                SELECT estado_pedido, estado_pago, id_venta
                FROM Pedido_Web
                WHERE id_pedido_web = ? AND id_cliente_web = ?
                FOR UPDATE
                """, id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        if (!estadoActual.equals(pedido.get("estado_pedido"))
                || !"Confirmado".equals(pedido.get("estado_pago"))
                || pedido.get("id_venta") == null) {
            return respuesta(HttpStatus.CONFLICT,
                    "El pedido no está en el estado necesario para esta acción.");
        }

        int actualizados = jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET estado_pedido = ?
            WHERE id_pedido_web = ? AND id_cliente_web = ?
              AND estado_pedido = ? AND estado_pago = 'Confirmado' AND id_venta IS NOT NULL
            """, estadoSiguiente, id, idClienteWeb, estadoActual);
        if (actualizados != 1) {
            return respuesta(HttpStatus.CONFLICT,
                    "El estado del pedido cambió. Actualiza la página e inténtalo de nuevo.");
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "estadoPedido", estadoSiguiente));
    }

    private int obtenerClienteNatural(Map<String, Object> cuenta, Map<String, Object> pedido, int idClienteWeb) {
        Object clienteVinculado = cuenta.get("id_cliente");
        if (clienteVinculado instanceof Number) {
            Integer activo = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Cliente WHERE id_cliente = ? AND activo = 1",
                    Integer.class, ((Number) clienteVinculado).intValue());
            if (activo != null && activo > 0) {
                return ((Number) clienteVinculado).intValue();
            }
        }

        String dni = texto(cuenta.get("dni"));
        Integer existente = null;
        if (!dni.isBlank()) {
            existente = jdbcTemplate.query("""
                SELECT id_cliente FROM Cliente WHERE dni = ? AND tipo_cliente = 'Natural' AND activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, dni);
        }
        if (existente == null) {
            String direccion = texto(pedido.get("direccion"));
            if (direccion.length() > 150) {
                direccion = direccion.substring(0, 150);
            }
            jdbcTemplate.update("""
                INSERT INTO Cliente (tipo_cliente, dni, nombre, apellidos, telefono, direccion)
                VALUES ('Natural', NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''))
                """, dni, texto(cuenta.get("nombres")), texto(cuenta.get("apellidos")),
                    texto(cuenta.get("telefono")), direccion);
            existente = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        }
        jdbcTemplate.update("UPDATE Cliente_Web SET id_cliente = ? WHERE id_cliente_web = ?",
                existente, idClienteWeb);
        return existente;
    }

    private int obtenerClienteJuridico(Map<String, Object> cuenta, Map<String, Object> pedido) {
        String ruc = texto(pedido.get("ruc_factura"));
        Integer existente = jdbcTemplate.query("""
            SELECT id_cliente FROM Cliente WHERE ruc = ? AND tipo_cliente = 'Juridica' AND activo = 1
            """, rs -> rs.next() ? rs.getInt(1) : null, ruc);
        if (existente != null) {
            return existente;
        }

        String direccion = texto(pedido.get("direccion"));
        if (direccion.length() > 150) {
            direccion = direccion.substring(0, 150);
        }
        String contacto = (texto(cuenta.get("nombres")) + " " + texto(cuenta.get("apellidos"))).trim();
        jdbcTemplate.update("""
            INSERT INTO Cliente (tipo_cliente, ruc, razon_social, contacto, telefono, direccion)
            VALUES ('Juridica', ?, ?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''))
            """, ruc, texto(pedido.get("razon_social")), contacto,
                texto(cuenta.get("telefono")), direccion);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    private String comprobanteExistente(int idVenta) {
        String comprobante = jdbcTemplate.query("""
            SELECT nro_comprobante FROM Venta WHERE id_venta = ?
            """, rs -> rs.next() ? rs.getString(1) : null, idVenta);
        return comprobante == null ? "" : comprobante;
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("mensaje", mensaje));
    }

    private record AsignacionLote(
            int idProducto,
            int idInventario,
            int cantidad,
            int stockAntes,
            int stockDefectuoso,
            BigDecimal precioUnitario) {

    }
}
