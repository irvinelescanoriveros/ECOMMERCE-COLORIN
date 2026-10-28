
package pe.ferreteria.backendweb.dao;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoWebDAO {

    private final JdbcTemplate jdbcTemplate;

    public PedidoWebDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listarPedidos(int idClienteWeb) {
        return jdbcTemplate.queryForList("""
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
    }

    public List<Map<String, Object>> listarProductosPedido(int idPedido) {
        return jdbcTemplate.queryForList("""
            SELECT nombre_producto AS nombre, cantidad, subtotal
            FROM Detalle_Pedido_Web
            WHERE id_pedido_web = ?
            ORDER BY id_detalle_pedido_web
            """, idPedido);
    }

    public Map<String, Object> buscarProductoDisponible(
            int idProducto, int cantidad) {

        return jdbcTemplate.queryForMap("""
            SELECT p.codigo, p.nombre, SUM(i.stock) AS stock,
                   MIN(CASE WHEN i.descuento_activo = 1
                       THEN ROUND(i.precio_venta * (1 - i.descuento_porcentaje / 100), 2)
                       ELSE i.precio_venta END) AS precio
            FROM Producto p
            JOIN Categoria c
                ON c.id_categoria = p.id_categoria AND c.activo = 1
            JOIN Marca m
                ON m.id_marca = p.id_marca AND m.activo = 1
            JOIN Inventario i
                ON i.id_producto = p.id_producto
                AND i.activo = 1 AND i.stock > 0
            WHERE p.id_producto = ? AND p.activo = 1
            GROUP BY p.id_producto, p.codigo, p.nombre
            HAVING SUM(i.stock) >= ?
            """, idProducto, cantidad);
    }

    public Map<String, Object> buscarProductoParaDetalle(int idProducto) {
        return jdbcTemplate.queryForMap("""
            SELECT p.codigo, p.nombre,
                   MIN(CASE WHEN i.descuento_activo = 1
                       THEN ROUND(i.precio_venta * (1 - i.descuento_porcentaje / 100), 2)
                       ELSE i.precio_venta END) AS precio
            FROM Producto p
            JOIN Inventario i
                ON i.id_producto = p.id_producto
                AND i.activo = 1 AND i.stock > 0
            WHERE p.id_producto = ? AND p.activo = 1
            GROUP BY p.id_producto, p.codigo, p.nombre
            """, idProducto);
    }

    public void insertarPedido(
            String numeroPedido,
            int idClienteWeb,
            String metodoPago,
            String metodoEntrega,
            String departamento,
            String provincia,
            String distrito,
            String direccion,
            String pisoDepartamento,
            String referencia,
            Date fechaEntrega,
            String puntoRecojo,
            String tipoComprobante,
            String rucFactura,
            String razonSocial,
            BigDecimal subtotal,
            BigDecimal costoEnvio,
            BigDecimal total) {

        jdbcTemplate.update("""
            INSERT INTO Pedido_Web
                (numero_pedido, id_cliente_web, estado_pedido, estado_pago,
                 metodo_pago, metodo_entrega, departamento, provincia, distrito,
                 direccion, piso_departamento, referencia, fecha_entrega,
                 punto_recojo, tipo_comprobante, ruc_factura, razon_social,
                 subtotal, costo_envio, total)
            VALUES (?, ?, 'Pendiente', 'Pendiente', ?, ?,
                    NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''),
                    NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?,
                    NULLIF(?, ''), ?, NULLIF(?, ''), NULLIF(?, ''),
                    ?, ?, ?)
            """,
                numeroPedido, idClienteWeb, metodoPago, metodoEntrega,
                departamento, provincia, distrito, direccion,
                pisoDepartamento, referencia, fechaEntrega, puntoRecojo,
                tipoComprobante, rucFactura, razonSocial,
                subtotal, costoEnvio, total);
    }

    public Integer obtenerIdPedido(String numeroPedido) {
        return jdbcTemplate.queryForObject("""
            SELECT id_pedido_web
            FROM Pedido_Web
            WHERE numero_pedido = ?
            """, Integer.class, numeroPedido);
    }

    public void insertarDetallePedido(
            int idPedido,
            int idProducto,
            String codigoProducto,
            String nombreProducto,
            int cantidad,
            BigDecimal precioUnitario) {

        jdbcTemplate.update("""
            INSERT INTO Detalle_Pedido_Web
                (id_pedido_web, id_producto, codigo_producto,
                 nombre_producto, cantidad, precio_unitario)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
                idPedido, idProducto, codigoProducto,
                nombreProducto, cantidad, precioUnitario);
    }
}
