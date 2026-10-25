/* Carga y renderiza productos vigentes del inventario por medio de Spring Boot. */
(() => {
    const API_PRODUCTOS = "http://localhost:8080/api/productos";
    const IMAGEN_REFERENCIAL = "img/producto-sin-imagen.svg";
    const normalizar = valor => String(valor || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLocaleLowerCase("es");
    const escapar = valor => String(valor ?? "").replace(/[&<>"']/g, caracter => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"})[caracter]);
    const moneda = valor => `S/ ${Number(valor).toLocaleString("es-PE", {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;

    function clasificar(p) {
        const texto = normalizar(`${p.categoria} ${p.nombre} ${p.descripcion}`);
        if (/pint|esmalte|thinner|disolvente|solvente|brocha|rodillo|barniz|madera|silicona|pegamento|adhesivo|empaste|masilla/.test(texto)) {
            if (/brocha/.test(texto))
                return ["Pinturas", "Herramientas para pintar", "Brochas"];
            if (/rodillo/.test(texto))
                return ["Pinturas", "Herramientas para pintar", "Rodillos"];
            if (/bandeja/.test(texto))
                return ["Pinturas", "Herramientas para pintar", "Bandejas"];
            if (/esmalte/.test(texto))
                return ["Pinturas", "Esmaltes y solventes", "Esmaltes"];
            if (/thinner/.test(texto))
                return ["Pinturas", "Esmaltes y solventes", "Thinner"];
            if (/disolvente|solvente|aguarras/.test(texto))
                return ["Pinturas", "Esmaltes y solventes", "Aguarrás"];
            if (/pegamento/.test(texto))
                return ["Pinturas", "Pegamentos y adhesivos", "Pegamentos"];
            if (/adhesivo|silicona/.test(texto))
                return ["Pinturas", "Pegamentos y adhesivos", "Siliconas"];
            if (/empaste|masilla|resanar/.test(texto))
                return ["Pinturas", "Productos para resanar y empastar", "Masillas"];
            if (/madera|barniz|laca|sellador/.test(texto))
                return ["Pinturas", "Acabados para madera", "Barnices"];
            return ["Pinturas", "Marcas", p.marca || "Otras marcas"];
        }
        if (/wincha|cinta metrica/.test(texto))
            return ["Herramientas", "Herramientas de medición", "Winchas"];
        if (/nivel/.test(texto))
            return ["Herramientas", "Herramientas de medición", "Niveles"];
        if (/guante|lentes|mascarilla|casco/.test(texto))
            return ["Herramientas", "Artículos de seguridad", "Guantes"];
        if (/candado|cerradura/.test(texto))
            return ["Herramientas", "Candados", "Candados"];
        if (/tornillo/.test(texto))
            return ["Herramientas", "Accesorios de fijación", "Tornillos"];
        if (/clavo/.test(texto))
            return ["Herramientas", "Accesorios de fijación", "Clavos"];
        if (/tarugo/.test(texto))
            return ["Herramientas", "Accesorios de fijación", "Tarugos"];
        if (/perno/.test(texto))
            return ["Herramientas", "Accesorios de fijación", "Pernos"];
        if (/alicate/.test(texto))
            return ["Herramientas", "Herramientas manuales", "Alicates"];
        if (/destornillador/.test(texto))
            return ["Herramientas", "Herramientas manuales", "Destornilladores"];
        if (/martillo/.test(texto))
            return ["Herramientas", "Herramientas manuales", "Martillos"];
        if (/llave/.test(texto))
            return ["Herramientas", "Herramientas manuales", "Llaves de ajuste"];
        return ["Herramientas", "Otras herramientas", "Taladros"];
    }

    function adaptar(p) {
        const [departamento, subcategoria, tipo] = clasificar(p);
        return {
            id: Number(p.id), codigo: p.codigo || "", nombre: p.nombre || "Producto",
            categoria: p.categoria || "Ferretería", departamento, subcategoria, tipo,
            marca: p.marca || "Sin marca", unidad: p.unidad || "Unidad",
            descripcion: p.descripcion || "Producto disponible en Ferretería Colorín.",
            precio: Number(p.precio || 0),
            stock: Number(p.stock || 0),
// Datos de la ficha técnica
            descripcion_comercial: p.descripcion_comercial || "",
            caracteristicas: p.caracteristicas || "",
            ventajas: p.ventajas || "",
            donde_usarlo: p.donde_usarlo || "",
            recomendaciones_uso: p.recomendaciones_uso || "",
            advertencia_uso: p.advertencia_uso || "",
            observaciones: p.observaciones || "",
            observaciones_adicionales: p.observaciones_adicionales || "",
            garantia: p.garantia || "",
            ficha_tecnica: p.ficha_tecnica || null,

            imagen: p.codigo
                    ? `/imagenes/${encodeURIComponent(p.codigo)}.jpg`
                    : IMAGEN_REFERENCIAL
        };
    }

    function atributos(p) {
        const busqueda = `${p.nombre} ${p.codigo} ${p.categoria} ${p.subcategoria} ${p.tipo} ${p.marca}`;
        return `data-id-producto="${p.id}" data-categoria="${escapar(p.categoria)}" data-departamento="${escapar(p.departamento)}" data-subcategoria="${escapar(p.subcategoria)}" data-tipo="${escapar(p.tipo)}" data-busqueda="${escapar(busqueda.toLowerCase())}"`;
    }

    function tarjeta(p) {
        return `
            <article class="catalogo_cantenido" ${atributos(p)}>
                <a class="catalogo_cantenido_ver" href="producto.html?id=${p.id}" aria-label="Ver detalles de ${escapar(p.nombre)}"></a>
                <div class="catalogo_cantenido_imagen_wrapper"><img class="catalogo_cantenido_imagen" src="${escapar(p.imagen)}" alt="${escapar(p.nombre)}" loading="lazy"></div>
                <div class="catalogo_cantenido_body">
                    <span class="catalogo_cantenido_badge">${escapar(p.marca)}</span>
                    <h3 class="catalogo_cantenido_TITULO"><a class="producto-titulo-link" href="producto.html?id=${p.id}">${escapar(p.nombre)}</a></h3>
                    <p class="catalogo_cantenido_DESCRIPCION">${escapar(p.descripcion)} · ${escapar(p.unidad)} · Código ${escapar(p.codigo)}</p>
                    <p class="catalogo_cantenido_PRECIO">${moneda(p.precio)}</p>
                    <p class="catalogo_cantenido_STOCK">Stock: ${p.stock} disponibles</p>
                </div>
                <div class="catalogo_cantenido_acciones"><button class="btn btn_agregar" type="button" aria-label="Agregar ${escapar(p.nombre)} al carrito"><i class="fa-solid fa-cart-plus"></i> Agregar</button></div>
            </article>`;
    }

    function renderizar(productos) {
        const grid = document.getElementById("productos_colorin_grid");
        const tabla = document.getElementById("productos_colorin_tabla");
        const destacados = document.getElementById("productos_destacados_colorin");
        if (grid)
            grid.innerHTML = productos.map(tarjeta).join("");
        if (destacados)
            destacados.innerHTML = productos.slice(0, 3).map(tarjeta).join("");
        if (tabla)
            tabla.innerHTML = productos.map(p => `
            <tr class="productos_tabla_row" ${atributos(p)}>
                <td class="productos_tabla_cell productos_tabla_cell_imagen"><div class="categoria_icon"><img class="categoria_icon_img" src="${escapar(p.imagen)}" alt="${escapar(p.nombre)}" loading="lazy"></div></td>
                <td class="productos_tabla_cell productos_tabla_cell_NAME"><a class="producto-titulo-link" href="producto.html?id=${p.id}">${escapar(p.nombre)}</a> <small>(${escapar(p.codigo)})</small></td>
                <td class="productos_tabla_cell productos_tabla_cell_CATEGORIA">${escapar(p.categoria)} · ${escapar(p.marca)}</td>
                <td class="productos_tabla_cell productos_tabla_cell_PRECIO">${moneda(p.precio)}</td>
                <td class="productos_tabla_cell productos_tabla_cell_STOCK">${p.stock} disponibles</td>
                <td class="productos_tabla_cell productos_tabla_cell_AGREGAR"><button class="btn btn_agregar_tabla" type="button" aria-label="Agregar ${escapar(p.nombre)} al carrito"><i class="fa-solid fa-cart-plus"></i></button></td>
            </tr>`).join("");
    }

    function mostrarError() {
        const aviso = "No pudimos cargar el catálogo. Verifica que Spring Boot esté ejecutándose.";
        const grid = document.getElementById("productos_colorin_grid");
        const destacados = document.getElementById("productos_destacados_colorin");
        const tabla = document.getElementById("productos_colorin_tabla");
        if (grid)
            grid.textContent = aviso;
        if (destacados)
            destacados.textContent = aviso;
        if (tabla)
            tabla.innerHTML = `<tr><td colspan="6">${escapar(aviso)}</td></tr>`;
    }

    window.productosColorin = [];
    window.errorProductosColorin = false;
    window.productosColorinReady = fetch(API_PRODUCTOS)
            .then(respuesta => {
                if (!respuesta.ok)
                    throw new Error(`La API respondió ${respuesta.status}`);
                return respuesta.json();
            })
            .then(datos => {
                if (!Array.isArray(datos))
                    throw new Error("La respuesta del catálogo no es una lista.");
                window.productosColorin = datos.map(adaptar);
                renderizar(window.productosColorin);
                window.dispatchEvent(new Event("colorin-products-loaded"));
                return window.productosColorin;
            })
            .catch(error => {
                console.error("No se pudo cargar el catálogo de Ferretería Colorín:", error);
                window.errorProductosColorin = true;
                mostrarError();
                window.dispatchEvent(new Event("colorin-products-loaded"));
                return [];
            });
})();
