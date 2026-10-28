
package pe.ferreteria.backendweb.servicio;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.ferreteria.backendweb.dao.ProductoDAO;

@Service
public class ProductoService {

    private final ProductoDAO productoDAO;

    public ProductoService(ProductoDAO productoDAO) {
        this.productoDAO = productoDAO;
    }

    public List<Map<String, Object>> listarProductosDisponibles() {
        return productoDAO.listarProductosDisponibles();
    }
}
