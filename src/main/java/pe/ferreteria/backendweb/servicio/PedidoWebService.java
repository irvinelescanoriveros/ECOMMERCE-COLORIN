
package pe.ferreteria.backendweb.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ferreteria.backendweb.dao.PedidoWebDAO;

@Service
public class PedidoWebService {

    private static final BigDecimal COSTO_ENVIO = new BigDecimal("4.99");

    private static final Set<String> METODOS_PAGO = Set.of(
            "sip-oh-credito",
            "sip-oh-debito",
            "app-sip",
            "interbank",
            "otras-tarjetas"
    );

    private final PedidoWebDAO pedidoWebDAO;

    public PedidoWebService(PedidoWebDAO pedidoWebDAO) {
        this.pedidoWebDAO = pedidoWebDAO;
    }

    public List<Map<String, Object>> listarPedidos(int idClienteWeb) {
        List<Map<String, Object>> pedidos =
                pedidoWebDAO.listarPedidos(idClienteWeb);

        List<Map<String, Object>> respuestaPedidos = new ArrayList<>();

        for (Map<String, Object> pedido : pedidos) {
            Map<String, Object> resultado = new LinkedHashMap<>(pedido);
            int idPedido = ((Number) pedido.get("id")).intValue();

            resultado.put(
                    "productos",
                    pedidoWebDAO.listarProductosPedido(idPedido)
            );

            respuestaPedidos.add(resultado);
        }

        return respuestaPedidos;
    }

    @Transactional
    public ResponseEntity<?> registrarPedido(
            PedidoRequest datos, int idClienteWeb) {

        if (datos == null || datos.productos() == null
                || datos.productos().isEmpty()) {
            return respuesta(HttpStatus.BAD_REQUEST, "El carrito está vacío.");
        }

        String metodoPago = limpiar(datos.metodoPago());
        String metodoEntrega = limpiar(datos.metodoEntrega());
        String tipoComprobante = limpiar(datos.tipoComprobante());

        if (!METODOS_PAGO.contains(metodoPago)) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un método de pago válido."
            );
        }

        if (!metodoEntrega.equals("Domicilio")
                && !metodoEntrega.equals("Recojo")) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un método de entrega válido."
            );
        }

        if (!tipoComprobante.equals("Boleta")
                && !tipoComprobante.equals("Factura")) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona Boleta o Factura."
            );
        }

        if (metodoEntrega.equals("Domicilio")
                && (vacio(datos.departamento())
                || vacio(datos.provincia())
                || vacio(datos.distrito())
                || vacio(datos.direccion()))) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Completa los datos obligatorios de entrega."
            );
        }

        if (metodoEntrega.equals("Recojo") && vacio(datos.puntoRecojo())) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona el punto de recojo."
            );
        }

        String ruc = limpiar(datos.rucFactura());
        String razonSocial = limpiar(datos.razonSocial());

        if (tipoComprobante.equals("Factura")
                && (!ruc.matches("\\d{11}") || razonSocial.isBlank())) {
            return respuesta(
                    HttpStatus.BAD_REQUEST,
                    "Para emitir factura ingresa RUC y razón social."
            );
        }

        Date fechaEntrega = null;

        if (!vacio(datos.fechaEntrega())) {
            try {
                fechaEntrega = Date.valueOf(
                        LocalDate.parse(datos.fechaEntrega())
                );
            } catch (DateTimeParseException e) {
                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "La fecha de entrega no es válida."
                );
            }
        }

        Set<Integer> ids = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemPedido item : datos.productos()) {
            if (item == null || item.idProducto() <= 0
                    || item.cantidad() <= 0
                    || !ids.add(item.idProducto())) {
                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Hay productos o cantidades inválidas en el carrito."
                );
            }

            try {
                Map<String, Object> producto =
                        pedidoWebDAO.buscarProductoDisponible(
                                item.idProducto(),
                                item.cantidad()
                        );

                BigDecimal precio = (BigDecimal) producto.get("precio");

                subtotal = subtotal.add(
                        precio.multiply(
                                BigDecimal.valueOf(item.cantidad())
                        )
                );

            } catch (EmptyResultDataAccessException e) {
                return respuesta(
                        HttpStatus.CONFLICT,
                        "Un producto ya no tiene stock suficiente. "
                        + "Actualiza el carrito e inténtalo de nuevo."
                );
            }
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoEnvio = metodoEntrega.equals("Domicilio")
                ? COSTO_ENVIO : BigDecimal.ZERO;

        BigDecimal total = subtotal.add(costoEnvio)
                .setScale(2, RoundingMode.HALF_UP);

        String numeroPedido = "FCW-"
                + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase(Locale.ROOT);

        pedidoWebDAO.insertarPedido(
                numeroPedido,
                idClienteWeb,
                metodoPago,
                metodoEntrega,
                limpiar(datos.departamento()),
                limpiar(datos.provincia()),
                limpiar(datos.distrito()),
                limpiar(datos.direccion()),
                limpiar(datos.pisoDepartamento()),
                limpiar(datos.referencia()),
                fechaEntrega,
                limpiar(datos.puntoRecojo()),
                tipoComprobante,
                tipoComprobante.equals("Factura") ? ruc : "",
                tipoComprobante.equals("Factura") ? razonSocial : "",
                subtotal,
                costoEnvio,
                total
        );

        Integer idPedido = pedidoWebDAO.obtenerIdPedido(numeroPedido);

        if (idPedido == null) {
            throw new IllegalStateException(
                    "No se pudo recuperar el pedido registrado."
            );
        }

        for (ItemPedido item : datos.productos()) {
            Map<String, Object> producto =
                    pedidoWebDAO.buscarProductoParaDetalle(
                            item.idProducto()
                    );

            pedidoWebDAO.insertarDetallePedido(
                    idPedido,
                    item.idProducto(),
                    String.valueOf(producto.get("codigo")),
                    String.valueOf(producto.get("nombre")),
                    item.cantidad(),
                    (BigDecimal) producto.get("precio")
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Pedido registrado y pendiente de pago.",
                "idPedido", idPedido,
                "numeroPedido", numeroPedido,
                "subtotal", subtotal,
                "costoEnvio", costoEnvio,
                "total", total,
                "estadoPago", "Pendiente"
        ));
    }

    private ResponseEntity<Map<String, String>> respuesta(
            HttpStatus estado, String mensaje) {
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
