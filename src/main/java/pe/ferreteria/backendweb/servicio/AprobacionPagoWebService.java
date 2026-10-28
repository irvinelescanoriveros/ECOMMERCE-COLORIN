
package pe.ferreteria.backendweb.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ferreteria.backendweb.dao.AprobacionPagoWebDAO;

@Service
public class AprobacionPagoWebService {

    private final AprobacionPagoWebDAO aprobacionPagoWebDAO;

    public AprobacionPagoWebService(
            AprobacionPagoWebDAO aprobacionPagoWebDAO) {
        this.aprobacionPagoWebDAO = aprobacionPagoWebDAO;
    }

    // =========================
    // APROBAR PAGO
    // =========================

    @Transactional
    public ResponseEntity<?> simularAprobacion(
            int id, int idClienteWeb) {

        Map<String, Object> pedido;

        try {
            pedido = aprobacionPagoWebDAO.buscarPedidoParaAprobacion(
                    id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        Object idVentaExistente = pedido.get("id_venta");

        if ("Confirmado".equals(pedido.get("estado_pago"))
                && idVentaExistente instanceof Number) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Este pedido ya fue aprobado.",
                    "nroComprobante",
                    aprobacionPagoWebDAO.buscarComprobantePorIdVenta(
                            ((Number) idVentaExistente).intValue())));
        }

        if (!"Pendiente".equals(pedido.get("estado_pago"))) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "El pedido no está pendiente de pago.");
        }

        Integer idUsuarioWeb =
                aprobacionPagoWebDAO.obtenerIdUsuarioWeb();

        if (idUsuarioWeb == null) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "No existe el usuario activo OperacionWEB con rol WEB.");
        }

        List<Map<String, Object>> detalles =
                aprobacionPagoWebDAO.listarDetallesPedido(id);

        if (detalles.isEmpty()) {
            return respuesta(
                    HttpStatus.CONFLICT, "El pedido no tiene productos.");
        }

        List<AsignacionLote> asignaciones = new ArrayList<>();

        // Verificar el stock y asignar lotes por antigüedad.
        for (Map<String, Object> detalle : detalles) {
            int idProducto =
                    ((Number) detalle.get("id_producto")).intValue();
            int cantidadSolicitada =
                    ((Number) detalle.get("cantidad")).intValue();
            BigDecimal precioPedido =
                    (BigDecimal) detalle.get("precio_unitario");

            List<Map<String, Object>> lotes =
                    aprobacionPagoWebDAO.listarLotesDisponibles(idProducto);

            int disponible = lotes.stream()
                    .mapToInt(lote ->
                            ((Number) lote.get("stock")).intValue())
                    .sum();

            if (disponible < cantidadSolicitada) {
                return respuesta(
                        HttpStatus.CONFLICT,
                        "No hay stock suficiente para todos los productos. "
                        + "El pedido sigue pendiente.");
            }

            int porAsignar = cantidadSolicitada;

            for (Map<String, Object> lote : lotes) {
                if (porAsignar == 0) {
                    break;
                }

                int stockAntes =
                        ((Number) lote.get("stock")).intValue();
                int stockDefectuoso =
                        ((Number) lote.get("stock_defectuoso")).intValue();
                int cantidadLote = Math.min(stockAntes, porAsignar);
                int idInventario =
                        ((Number) lote.get("id_inventario")).intValue();

                asignaciones.add(new AsignacionLote(
                        idProducto,
                        idInventario,
                        cantidadLote,
                        stockAntes,
                        stockDefectuoso,
                        precioPedido));

                porAsignar -= cantidadLote;
            }
        }

        Map<String, Object> cuenta;

        try {
            cuenta = aprobacionPagoWebDAO.buscarCuentaWeb(idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.NOT_FOUND, "No encontramos la cuenta web.");
        }

        boolean esFactura =
                "Factura".equals(pedido.get("tipo_comprobante"));

        int idCliente = esFactura
                ? obtenerClienteJuridico(cuenta, pedido)
                : obtenerClienteNatural(cuenta, pedido, idClienteWeb);

        String serie = esFactura ? "F001" : "B001";

        Integer siguienteNumero =
                aprobacionPagoWebDAO.obtenerSiguienteNumero(serie);

        int numero = siguienteNumero != null ? siguienteNumero : 1;
        String nroComprobante =
                serie + "-" + String.format("%08d", numero);

        BigDecimal total = (BigDecimal) pedido.get("total");
        BigDecimal costoEnvio = (BigDecimal) pedido.get("costo_envio");

        BigDecimal subtotalVenta = total.divide(
                new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);

        BigDecimal igv = total.subtract(subtotalVenta)
                .setScale(2, RoundingMode.HALF_UP);

        String metodoPagoVenta =
                "app-sip".equals(pedido.get("metodo_pago"))
                ? "Transferencia" : "Tarjeta";

        aprobacionPagoWebDAO.insertarVenta(
                idCliente,
                idUsuarioWeb,
                subtotalVenta,
                igv,
                total,
                costoEnvio,
                String.valueOf(pedido.get("tipo_comprobante")),
                serie,
                numero,
                nroComprobante,
                metodoPagoVenta);

        Integer idVenta =
                aprobacionPagoWebDAO.buscarIdVentaPorComprobante(
                        nroComprobante);

        if (idVenta == null) {
            throw new IllegalStateException(
                    "No se pudo recuperar la venta registrada.");
        }

        // Descontar el inventario, registrar el detalle y el Kardex.
        for (AsignacionLote asignacion : asignaciones) {
            int stockDespues =
                    asignacion.stockAntes() - asignacion.cantidad();

            int reducido = aprobacionPagoWebDAO.descontarStock(
                    asignacion.idInventario(),
                    asignacion.cantidad());

            if (reducido != 1) {
                throw new IllegalStateException(
                        "El stock cambió durante la aprobación. "
                        + "Intenta nuevamente.");
            }

            aprobacionPagoWebDAO.insertarDetalleVenta(
                    idVenta,
                    asignacion.idInventario(),
                    asignacion.cantidad(),
                    asignacion.precioUnitario());

            aprobacionPagoWebDAO.insertarMovimientoKardex(
                    asignacion.idProducto(),
                    asignacion.idInventario(),
                    idUsuarioWeb,
                    asignacion.cantidad(),
                    asignacion.stockAntes(),
                    stockDespues,
                    asignacion.stockDefectuoso(),
                    asignacion.precioUnitario(),
                    idVenta,
                    nroComprobante,
                    "Aprobación web del pedido "
                    + pedido.get("numero_pedido"));
        }

        int pedidosActualizados =
                aprobacionPagoWebDAO.confirmarPedido(
                        id, idClienteWeb, idVenta);

        if (pedidosActualizados != 1) {
            throw new IllegalStateException(
                    "El estado del pedido cambió durante la aprobación.");
        }

        return ResponseEntity.ok(Map.of(
                "mensaje",
                "Aprobación simulada; la venta y el Kardex fueron registrados.",
                "numeroPedido", pedido.get("numero_pedido"),
                "idVenta", idVenta,
                "nroComprobante", nroComprobante,
                "estadoPago", "Confirmado",
                "estadoPedido", "En preparacion"));
    }

    // =========================
    // RECHAZAR PAGO
    // =========================

    @Transactional
    public ResponseEntity<?> simularRechazo(
            int id, int idClienteWeb) {

        Map<String, Object> pedido;

        try {
            pedido = aprobacionPagoWebDAO.buscarPedidoParaRechazo(
                    id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        if (!"Pendiente".equals(pedido.get("estado_pago"))
                || pedido.get("id_venta") != null) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "Solo se puede simular el rechazo de un pago pendiente.");
        }

        int actualizados = aprobacionPagoWebDAO.rechazarPedido(
                id, idClienteWeb);

        if (actualizados != 1) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "El estado del pedido cambió. "
                    + "Actualiza la página e inténtalo de nuevo.");
        }

        return ResponseEntity.ok(Map.of(
                "mensaje",
                "Rechazo de pago simulado. No se registró una venta "
                + "ni se modificó el stock.",
                "estadoPago", "Rechazado",
                "estadoPedido", "Cancelado"));
    }

    // =========================
    // DESPACHO
    // =========================

    @Transactional
    public ResponseEntity<?> simularDespacho(
            int id, int idClienteWeb) {

        return avanzarEstadoPedido(
                id,
                idClienteWeb,
                "En preparacion",
                "Enviado",
                "Despacho simulado. El pedido ahora figura como enviado.");
    }

    // =========================
    // ENTREGA
    // =========================

    @Transactional
    public ResponseEntity<?> simularEntrega(
            int id, int idClienteWeb) {

        return avanzarEstadoPedido(
                id,
                idClienteWeb,
                "Enviado",
                "Entregado",
                "Entrega simulada. El pedido ahora figura como entregado.");
    }

    private ResponseEntity<?> avanzarEstadoPedido(
            int id,
            int idClienteWeb,
            String estadoActual,
            String estadoSiguiente,
            String mensaje) {

        Map<String, Object> pedido;

        try {
            pedido = aprobacionPagoWebDAO.buscarPedidoParaCambioEstado(
                    id, idClienteWeb);
        } catch (EmptyResultDataAccessException e) {
            return respuesta(
                    HttpStatus.NOT_FOUND, "No encontramos ese pedido.");
        }

        if (!estadoActual.equals(pedido.get("estado_pedido"))
                || !"Confirmado".equals(pedido.get("estado_pago"))
                || pedido.get("id_venta") == null) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "El pedido no está en el estado necesario para esta acción.");
        }

        int actualizados =
                aprobacionPagoWebDAO.actualizarEstadoPedido(
                        id, idClienteWeb, estadoActual, estadoSiguiente);

        if (actualizados != 1) {
            return respuesta(
                    HttpStatus.CONFLICT,
                    "El estado del pedido cambió. "
                    + "Actualiza la página e inténtalo de nuevo.");
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "estadoPedido", estadoSiguiente));
    }

    // =========================
    // OBTENER O REGISTRAR CLIENTE NATURAL
    // =========================

    private int obtenerClienteNatural(
            Map<String, Object> cuenta,
            Map<String, Object> pedido,
            int idClienteWeb) {

        Object clienteVinculado = cuenta.get("id_cliente");

        if (clienteVinculado instanceof Number) {
            int idCliente = ((Number) clienteVinculado).intValue();

            Integer activo =
                    aprobacionPagoWebDAO.contarClienteActivo(idCliente);

            if (activo != null && activo > 0) {
                return idCliente;
            }
        }

        String dni = texto(cuenta.get("dni"));
        Integer existente = null;

        if (!dni.isBlank()) {
            existente =
                    aprobacionPagoWebDAO.buscarClienteNaturalPorDni(dni);
        }

        if (existente == null) {
            String direccion = limitarDireccion(
                    texto(pedido.get("direccion")));

            existente = aprobacionPagoWebDAO.insertarClienteNatural(
                    dni,
                    texto(cuenta.get("nombres")),
                    texto(cuenta.get("apellidos")),
                    texto(cuenta.get("telefono")),
                    direccion);
        }

        if (existente == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el cliente natural.");
        }

        aprobacionPagoWebDAO.vincularClienteWeb(
                existente, idClienteWeb);

        return existente;
    }

    // =========================
    // OBTENER O REGISTRAR CLIENTE JURÍDICO
    // =========================

    private int obtenerClienteJuridico(
            Map<String, Object> cuenta,
            Map<String, Object> pedido) {

        String ruc = texto(pedido.get("ruc_factura"));

        Integer existente =
                aprobacionPagoWebDAO.buscarClienteJuridicoPorRuc(ruc);

        if (existente != null) {
            return existente;
        }

        String direccion = limitarDireccion(
                texto(pedido.get("direccion")));

        String contacto = (
                texto(cuenta.get("nombres")) + " "
                + texto(cuenta.get("apellidos"))).trim();

        Integer idCliente =
                aprobacionPagoWebDAO.insertarClienteJuridico(
                        ruc,
                        texto(pedido.get("razon_social")),
                        contacto,
                        texto(cuenta.get("telefono")),
                        direccion);

        if (idCliente == null) {
            throw new IllegalStateException(
                    "No se pudo registrar el cliente jurídico.");
        }

        return idCliente;
    }

    // =========================
    // UTILIDADES
    // =========================

    private String limitarDireccion(String direccion) {
        return direccion.length() > 150
                ? direccion.substring(0, 150)
                : direccion;
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private ResponseEntity<Map<String, String>> respuesta(
            HttpStatus estado, String mensaje) {

        return ResponseEntity.status(estado)
                .body(Map.of("mensaje", mensaje));
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
