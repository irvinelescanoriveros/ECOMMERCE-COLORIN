
package pe.ferreteria.backendweb.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AprobacionPagoWebDAO {

    private final JdbcTemplate jdbcTemplate;

    public AprobacionPagoWebDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================
    // PEDIDOS
    // =========================

    public Map<String, Object> buscarPedidoParaAprobacion(
            int idPedido, int idClienteWeb) {

        return jdbcTemplate.queryForMap("""
            SELECT id_pedido_web, numero_pedido, id_cliente_web, id_venta,
                   estado_pedido, estado_pago, metodo_pago, metodo_entrega,
                   departamento, provincia, distrito, direccion,
                   piso_departamento, referencia, punto_recojo,
                   tipo_comprobante, ruc_factura, razon_social,
                   subtotal, costo_envio, total
            FROM Pedido_Web
            WHERE id_pedido_web = ? AND id_cliente_web = ?
            FOR UPDATE
            """, idPedido, idClienteWeb);
    }

    public Map<String, Object> buscarPedidoParaRechazo(
            int idPedido, int idClienteWeb) {

        return jdbcTemplate.queryForMap("""
            SELECT id_pedido_web, id_venta, estado_pago
            FROM Pedido_Web
            WHERE id_pedido_web = ? AND id_cliente_web = ?
            FOR UPDATE
            """, idPedido, idClienteWeb);
    }

    public Map<String, Object> buscarPedidoParaCambioEstado(
            int idPedido, int idClienteWeb) {

        return jdbcTemplate.queryForMap("""
            SELECT estado_pedido, estado_pago, id_venta
            FROM Pedido_Web
            WHERE id_pedido_web = ? AND id_cliente_web = ?
            FOR UPDATE
            """, idPedido, idClienteWeb);
    }

    public int confirmarPedido(
            int idPedido,
            int idClienteWeb,
            int idVenta) {

        return jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET id_venta = ?, estado_pago = 'Confirmado',
                estado_pedido = 'En preparacion'
            WHERE id_pedido_web = ? AND id_cliente_web = ?
              AND estado_pago = 'Pendiente'
            """, idVenta, idPedido, idClienteWeb);
    }

    public int rechazarPedido(int idPedido, int idClienteWeb) {
        return jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET estado_pago = 'Rechazado', estado_pedido = 'Cancelado'
            WHERE id_pedido_web = ? AND id_cliente_web = ?
              AND estado_pago = 'Pendiente' AND id_venta IS NULL
            """, idPedido, idClienteWeb);
    }

    public int actualizarEstadoPedido(
            int idPedido,
            int idClienteWeb,
            String estadoActual,
            String estadoSiguiente) {

        return jdbcTemplate.update("""
            UPDATE Pedido_Web
            SET estado_pedido = ?
            WHERE id_pedido_web = ? AND id_cliente_web = ?
              AND estado_pedido = ?
              AND estado_pago = 'Confirmado'
              AND id_venta IS NOT NULL
            """, estadoSiguiente, idPedido, idClienteWeb, estadoActual);
    }

    // =========================
    // USUARIO DE OPERACIONES WEB
    // =========================

    public Integer obtenerIdUsuarioWeb() {
        return jdbcTemplate.query("""
            SELECT id_usuario
            FROM Usuario
            WHERE username = 'OperacionWEB'
              AND rol = 'WEB'
              AND activo = 1
            """, rs -> rs.next() ? rs.getInt(1) : null);
    }

    // =========================
    // DETALLES DEL PEDIDO
    // =========================

    public List<Map<String, Object>> listarDetallesPedido(int idPedido) {
        return jdbcTemplate.queryForList("""
            SELECT id_producto, cantidad, precio_unitario
            FROM Detalle_Pedido_Web
            WHERE id_pedido_web = ?
            ORDER BY id_detalle_pedido_web
            FOR UPDATE
            """, idPedido);
    }

    // =========================
    // INVENTARIO
    // =========================

    public List<Map<String, Object>> listarLotesDisponibles(int idProducto) {
        return jdbcTemplate.queryForList("""
            SELECT id_inventario, stock, stock_defectuoso
            FROM Inventario
            WHERE id_producto = ? AND activo = 1 AND stock > 0
            ORDER BY fecha_ingreso, id_inventario
            FOR UPDATE
            """, idProducto);
    }

    public int descontarStock(int idInventario, int cantidad) {
        return jdbcTemplate.update("""
            UPDATE Inventario
            SET stock = stock - ?
            WHERE id_inventario = ? AND stock >= ?
            """, cantidad, idInventario, cantidad);
    }

    // =========================
    // CLIENTE WEB Y CLIENTES
    // =========================

    public Map<String, Object> buscarCuentaWeb(int idClienteWeb) {
        return jdbcTemplate.queryForMap("""
            SELECT id_cliente, dni, nombres, apellidos, telefono
            FROM Cliente_Web
            WHERE id_cliente_web = ? AND activo = 1
            FOR UPDATE
            """, idClienteWeb);
    }

    public Integer contarClienteActivo(int idCliente) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM Cliente
            WHERE id_cliente = ? AND activo = 1
            """, Integer.class, idCliente);
    }

    public Integer buscarClienteNaturalPorDni(String dni) {
        return jdbcTemplate.query("""
            SELECT id_cliente
            FROM Cliente
            WHERE dni = ? AND tipo_cliente = 'Natural' AND activo = 1
            """, rs -> rs.next() ? rs.getInt(1) : null, dni);
    }

    public Integer insertarClienteNatural(
            String dni,
            String nombres,
            String apellidos,
            String telefono,
            String direccion) {

        jdbcTemplate.update("""
            INSERT INTO Cliente
                (tipo_cliente, dni, nombre, apellidos, telefono, direccion)
            VALUES ('Natural', NULLIF(?, ''), NULLIF(?, ''),
                    NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''))
            """, dni, nombres, apellidos, telefono, direccion);

        return jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Integer.class);
    }

    public Integer buscarClienteJuridicoPorRuc(String ruc) {
        return jdbcTemplate.query("""
            SELECT id_cliente
            FROM Cliente
            WHERE ruc = ? AND tipo_cliente = 'Juridica' AND activo = 1
            """, rs -> rs.next() ? rs.getInt(1) : null, ruc);
    }

    public Integer insertarClienteJuridico(
            String ruc,
            String razonSocial,
            String contacto,
            String telefono,
            String direccion) {

        jdbcTemplate.update("""
            INSERT INTO Cliente
                (tipo_cliente, ruc, razon_social, contacto, telefono, direccion)
            VALUES ('Juridica', ?, ?, NULLIF(?, ''),
                    NULLIF(?, ''), NULLIF(?, ''))
            """, ruc, razonSocial, contacto, telefono, direccion);

        return jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Integer.class);
    }

    public int vincularClienteWeb(int idCliente, int idClienteWeb) {
        return jdbcTemplate.update("""
            UPDATE Cliente_Web
            SET id_cliente = ?
            WHERE id_cliente_web = ?
            """, idCliente, idClienteWeb);
    }

    // =========================
    // VENTAS Y COMPROBANTES
    // =========================

    public Integer obtenerSiguienteNumero(String serie) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(numero), 0) + 1
            FROM Venta
            WHERE serie = ?
            """, Integer.class, serie);
    }

    public int insertarVenta(
            int idCliente,
            int idUsuario,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total,
            BigDecimal costoEnvio,
            String tipoComprobante,
            String serie,
            int numero,
            String nroComprobante,
            String metodoPago) {

        return jdbcTemplate.update("""
            INSERT INTO Venta
                (id_cliente, id_usuario, subtotal, igv, total, costo_envio,
                 tipo_comprobante, serie, numero, nro_comprobante, metodo_pago,
                 monto_recibido, vuelto, estado_comprobante, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, 0.00, 'Pendiente', 1)
            """,
                idCliente, idUsuario, subtotal, igv, total, costoEnvio,
                tipoComprobante, serie, numero, nroComprobante, metodoPago);
    }

    public Integer buscarIdVentaPorComprobante(String nroComprobante) {
        return jdbcTemplate.queryForObject("""
            SELECT id_venta
            FROM Venta
            WHERE nro_comprobante = ?
            """, Integer.class, nroComprobante);
    }

    public String buscarComprobantePorIdVenta(int idVenta) {
        String comprobante = jdbcTemplate.query("""
            SELECT nro_comprobante
            FROM Venta
            WHERE id_venta = ?
            """, rs -> rs.next() ? rs.getString(1) : null, idVenta);

        return comprobante == null ? "" : comprobante;
    }

    // =========================
    // DETALLE DE VENTA Y KARDEX
    // =========================

    public int insertarDetalleVenta(
            int idVenta,
            int idInventario,
            int cantidad,
            BigDecimal precioUnitario) {

        return jdbcTemplate.update("""
            INSERT INTO Detalle_Venta
                (id_venta, id_inventario, cantidad,
                 precio_unitario, descuento)
            VALUES (?, ?, ?, ?, 0.00)
            """, idVenta, idInventario, cantidad, precioUnitario);
    }

    public int insertarMovimientoKardex(
            int idProducto,
            int idInventario,
            int idUsuario,
            int cantidad,
            int stockAntes,
            int stockDespues,
            int stockDefectuoso,
            BigDecimal precioUnitario,
            int idVenta,
            String nroComprobante,
            String motivo) {

        return jdbcTemplate.update("""
            INSERT INTO Kardex
                (id_producto, id_inventario, id_usuario, fecha_movimiento,
                 movimiento, motivo_movimiento, cantidad, stock_antes,
                 stock_despues, stock_defectuoso_antes,
                 stock_defectuoso_despues, precio_unitario,
                 tipo_referencia, id_referencia, nro_comprobante, motivo)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'Salida', 'Venta',
                    ?, ?, ?, ?, ?, ?, 'Venta', ?, ?, ?)
            """,
                idProducto, idInventario, idUsuario, cantidad,
                stockAntes, stockDespues, stockDefectuoso, stockDefectuoso,
                precioUnitario, idVenta, nroComprobante, motivo);
    }
}
