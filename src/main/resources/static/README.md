# Ferretería Colorín — frontend

Frontend estático adaptado desde TechStore. Las páginas ya están separadas para que cada sección pueda crecer por su cuenta:

- `index.html`: Inicio y productos destacados.
- `catalogo.html`: catálogo, búsqueda, filtros y vistas de fichas o tabla.
- `producto.html`: ficha individual abierta desde cualquier tarjeta o nombre de producto.
- `contacto.html`: formulario de contacto.
- `cuenta.html`: prototipo de cuenta, perfil, direcciones y pedidos.
- `checkout.html`: flujo de compra en tres pasos: datos personales, entrega y método de pago.
- `js/header.js`: cabecera, navegación, pie y panel común del carrito.
- `js/menu_categorias.js`: menú multinivel Herramientas/Pinturas y navegación por subcategorías y tipos.
- `js/catalogo_colorin.js`: consulta la API y renderiza productos del inventario.
- `js/producto.js`: ficha detallada de producto y especificaciones disponibles.
- `js/carro_de_compras.js`: cantidades, total y persistencia local del carrito entre páginas.
- `js/cuenta.js`: interfaz preparada para buscar clientes en `GET /api/clientes/dni/{dni}`.
- `js/checkout.js`: validación del flujo de compra y confirmación local de pedidos de demostración.

## Abrir

Abre `index.html` con un navegador. Los enlaces llevan a las otras páginas HTML del mismo proyecto. No requiere instalar dependencias. Para que el navegador comparta de manera fiable el carrito al pasar entre páginas, sirve la carpeta desde un mismo origen web; algunos navegadores aíslan o restringen el almacenamiento al abrir archivos directamente con `file://`.

## Datos actuales

El catálogo consulta al backend Spring Boot, que lee productos, precios, marcas, categorías y existencias del inventario compartido. Solo muestra productos activos con stock positivo. La imagen es un marcador referencial porque la API todavía no incluye fotografías ni fichas técnicas extendidas. Los filtros de subcategoría y tipo se asignan temporalmente a partir del nombre y la categoría del producto.

Inicio, catálogo y ficha de producto requieren que MySQL y el backend Spring Boot estén activos. El registro e inicio de sesión, búsqueda por DNI, perfil, direcciones, checkout y confirmación de pedidos siguen siendo demostrativos: no guardan cuentas ni pedidos en MySQL, no cobran y no modifican inventario. El carrito se conserva entre páginas en el almacenamiento del navegador. La aplicación de escritorio no se modificó.
