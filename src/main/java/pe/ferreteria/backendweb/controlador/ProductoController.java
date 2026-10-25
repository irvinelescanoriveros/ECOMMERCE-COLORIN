
package pe.ferreteria.backendweb.controlador;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ProductoController {

    private final JdbcTemplate jdbcTemplate;

    public ProductoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> listarProductosDisponibles() {
        String sql = """
            SELECT
                p.id_producto AS id,
                p.codigo AS codigo,
                p.nombre AS nombre,
                c.nombre AS categoria,
                m.nombre AS marca,
                p.unidad AS unidad,
                p.descripcion AS descripcion,

                SUM(i.stock) AS stock,

                MIN(
                    CASE
                        WHEN i.stock > 0 AND i.descuento_activo = 1
                            THEN ROUND(
                                i.precio_venta
                                * (1 - i.descuento_porcentaje / 100),
                                2
                            )
                        WHEN i.stock > 0
                            THEN i.precio_venta
                        ELSE NULL
                    END
                ) AS precio,

                dt.descripcion_comercial AS descripcion_comercial,
                dt.caracteristicas AS caracteristicas,
                dt.ventajas AS ventajas,
                dt.donde_usarlo AS donde_usarlo,
                dt.recomendaciones_uso AS recomendaciones_uso,
                dt.advertencia_uso AS advertencia_uso,
                dt.observaciones AS observaciones,
                dt.observaciones_adicionales AS observaciones_adicionales,
                dt.garantia AS garantia,
                dt.ficha_tecnica AS ficha_tecnica

            FROM Producto p

            JOIN Categoria c
                ON c.id_categoria = p.id_categoria

            JOIN Marca m
                ON m.id_marca = p.id_marca

            JOIN Inventario i
                ON i.id_producto = p.id_producto

            LEFT JOIN Detalle_Tecnico_Producto dt
                ON dt.id_producto = p.id_producto
                AND dt.activo = 1

            WHERE p.activo = 1
              AND c.activo = 1
              AND m.activo = 1
              AND i.activo = 1

            GROUP BY
                p.id_producto,
                p.codigo,
                p.nombre,
                c.nombre,
                m.nombre,
                p.unidad,
                p.descripcion,
                dt.descripcion_comercial,
                dt.caracteristicas,
                dt.ventajas,
                dt.donde_usarlo,
                dt.recomendaciones_uso,
                dt.advertencia_uso,
                dt.observaciones,
                dt.observaciones_adicionales,
                dt.garantia,
                dt.ficha_tecnica

            HAVING SUM(i.stock) > 0

            ORDER BY p.nombre
            """;

        return jdbcTemplate.queryForList(sql);
    }
}
