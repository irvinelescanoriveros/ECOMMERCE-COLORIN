# Ferretería Colorín — Sistema Web

## 1. Descripción

Sistema web para la Ferretería Colorín que permite consultar productos, buscar artículos, gestionar una cuenta de cliente, registrar direcciones y realizar pedidos en línea.

El frontend está integrado con un backend desarrollado en Java y Spring Boot, conectado a una base de datos MySQL.

El proyecto incluye un prototipo funcional de ventas en línea con simulación de aprobación y rechazo de pagos.

## 2. Tecnologías utilizadas

- **Frontend:** HTML5, CSS3 y JavaScript.
- **Backend:** Java y Spring Boot.
- **Acceso a datos:** JdbcTemplate.
- **Base de datos:** MySQL.
- **Comunicación:** API REST mediante HTTP.
- **Almacenamiento del navegador:** localStorage y sessionStorage.

## 3. Estructura del frontend

### Páginas HTML

- `index.html`: página de inicio y productos destacados.
- `catalogo.html`: catálogo, búsqueda, filtros y visualización de productos.
- `producto.html`: ficha individual del producto.
- `contacto.html`: formulario de contacto.
- `cuenta.html`: registro, inicio de sesión, perfil, direcciones y pedidos.
- `checkout.html`: formulario para completar el pedido.

### Archivos JavaScript

- `header.js`: funcionalidades de la cabecera.
- `menu_categorias.js`: navegación por categorías.
- `catalogo_colorin.js`: carga y visualización del catálogo.
- `producto.js`: información detallada de los productos.
- `carro_de_compras.js`: gestión del carrito de compras.
- `cuenta.js`: funcionalidades de la cuenta del cliente.
- `checkout.js`: validación y registro de pedidos.

## 4. Funcionalidades principales

- Consulta de productos, precios, marcas, categorías y stock disponible.
- Búsqueda y navegación por el catálogo.
- Visualización de fichas individuales de productos.
- Carrito de compras con control de cantidades.
- Registro, inicio y cierre de sesión.
- Consulta y actualización del perfil del cliente.
- Registro de direcciones y selección de dirección predeterminada.
- Registro y consulta de pedidos.
- Selección de modalidad de entrega y método de pago.
- Selección de boleta o factura.
- Simulación de aprobación o rechazo del pago.
- Actualización del estado del pedido hasta su entrega.
- Registro de ventas, movimientos de Kardex y descuento del inventario cuando se aprueba el pago simulado.

Las categorías pueden incluir información definida en JavaScript, mientras que los productos, precios y existencias se consultan mediante el backend.

## 5. API REST

| Método | Endpoint | Función |
|---|---|---|
| GET | `/api/productos` | Consultar productos, precios y stock. |
| POST | `/api/clientes-web/registro` | Registrar una cuenta de cliente. |
| POST | `/api/clientes-web/login` | Iniciar sesión. |
| GET | `/api/clientes-web/sesion` | Consultar el estado de la sesión. |
| POST | `/api/clientes-web/logout` | Cerrar sesión. |
| GET | `/api/clientes-web/perfil` | Consultar el perfil del cliente. |
| PUT | `/api/clientes-web/perfil` | Actualizar el perfil. |
| GET | `/api/clientes-web/dni/{dni}` | Consultar datos de un cliente por DNI. |
| GET | `/api/clientes-web/direcciones` | Consultar direcciones registradas. |
| POST | `/api/clientes-web/direcciones` | Registrar una dirección. |
| PUT | `/api/clientes-web/direcciones/{id}/predeterminada` | Establecer la dirección predeterminada. |
| GET | `/api/pedidos-web` | Consultar los pedidos del cliente autenticado. |
| POST | `/api/pedidos-web` | Registrar un pedido. |
| PUT | `/api/pedidos-web/{id}/simular-aprobacion` | Simular la aprobación del pago. |
| PUT | `/api/pedidos-web/{id}/simular-rechazo` | Simular el rechazo del pago. |
| PUT | `/api/pedidos-web/{id}/simular-despacho` | Simular el despacho del pedido. |
| PUT | `/api/pedidos-web/{id}/simular-entrega` | Simular la entrega del pedido. |

Las operaciones que requieren autenticación utilizan la sesión del backend para identificar al cliente.

## 6. Flujo de compra

1. El cliente selecciona productos y los agrega al carrito.
2. Inicia sesión o crea una cuenta.
3. Completa los datos de entrega, pago y comprobante.
4. El sistema registra el pedido en estado pendiente.
5. Se simula la respuesta de la entidad financiera:
   - **Pago aprobado:** el sistema registra la venta, descuenta las cantidades correspondientes del inventario y genera los movimientos de salida en el Kardex.
   - **Pago rechazado:** el pedido se cancela sin registrar la venta ni modificar el stock.
6. Si el pago es aprobado, el pedido pasa por los estados de preparación, envío y entrega.

La aprobación y el rechazo del pago son simulaciones académicas. El sistema no se conecta realmente con bancos ni verifica el saldo de una cuenta. El despacho y la entrega también se simulan.

El backend valida el stock y calcula los importes del pedido antes de registrarlo; los totales mostrados por el frontend son informativos.

## 7. Almacenamiento de datos

### MySQL

Almacena la información persistente de las cuentas web, perfiles, direcciones y pedidos. Cuando se aprueba el pago simulado, también se registra la venta y se actualizan el inventario y el Kardex.

### localStorage

Se utiliza para conservar el carrito de compras en el navegador.

### sessionStorage

Se utiliza para información temporal de la interfaz, como datos auxiliares del cliente y resúmenes de pedidos.

La autenticación real se gestiona mediante la sesión del backend. Los datos almacenados en el navegador sirven como apoyo para la interfaz y no sustituyen la autenticación del servidor.

## 8. Ejecución del proyecto

1. Configurar MySQL y verificar las credenciales de conexión del backend.
2. Ejecutar la aplicación Spring Boot en el puerto `8080`.
3. Servir el frontend mediante un servidor web local.
4. Abrir `index.html` desde el servidor y probar las funcionalidades.

**Importante:** el frontend contiene solicitudes a `http://localhost:8080` y otras que utilizan rutas relativas como `/api/...`. Si el frontend y el backend se ejecutan en orígenes diferentes, se debe configurar correctamente CORS, el envío de cookies de sesión y las rutas de la API. Las solicitudes relativas necesitan que exista un origen común o un proxy configurado.

Las funciones que dependen de la API requieren que el backend y la base de datos estén disponibles.

## 9. Limitaciones

- Los pagos no se procesan mediante una entidad financiera real.
- El despacho y la entrega son simulados.
- No se incluye inicio de sesión con Google ni recuperación de contraseña.
- Las imágenes de productos son referenciales y pueden requerir recursos estáticos adicionales.
- Las funcionalidades que dependen de datos persistentes requieren conexión con el backend y MySQL.

## 10. Alcance del proyecto

El proyecto comprende un sitio web informativo y un prototipo funcional de comercio electrónico para Ferretería Colorín. Permite consultar productos, administrar una cuenta de cliente, registrar pedidos y demostrar el flujo de ventas integrado con el inventario del sistema.

La integración con bancos, los pagos reales y la logística automatizada quedan fuera del alcance actual.

## 11. Base de datos

El script SQL de la base de datos se encuentra en el repositorio del proyecto principal:

- [Ver archivos SQL de Ferretería Colorín](https://github.com/irvinelescanoriveros/FERRETERIA-COLORIN/tree/main/database)