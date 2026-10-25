
/* Renderizado de la ficha técnica con datos de la API de Ferretería Colorín. */
document.addEventListener("DOMContentLoaded", async () => {
    const contenedor = document.getElementById("product-detail");
    const id = new URLSearchParams(window.location.search).get("id");

    await window.productosColorinReady;

    const producto = window.productosColorin?.find(
        item => String(item.id) === id
    );

    const escapeHtml = valor =>
        String(valor ?? "").replace(/[&<>"']/g, caracter => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        })[caracter]);

    const precio = valor =>
        `S/ ${Number(valor).toLocaleString("es-PE", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        })}`;

    const textoDisponible = valor =>
        valor !== null && valor !== undefined && String(valor).trim() !== "";

    const apartado = (titulo, contenido) => {
        if (!textoDisponible(contenido)) return "";

        return `
            <details class="product-extra">
                <summary>${escapeHtml(titulo)}
                    <i class="fa-solid fa-chevron-down"></i>
                </summary>
                <p>${escapeHtml(contenido)}</p>
            </details>`;
    };

    if (!producto) {
        const mensaje = window.errorProductosColorin
            ? "No pudimos cargar los productos. Verifica que el backend esté ejecutándose."
            : "Vuelve al catálogo para elegir un producto disponible.";

        contenedor.innerHTML = `
            <section class="producto-no-encontrado">
                <i class="fa-solid fa-box-open"></i>
                <h1>No encontramos ese producto</h1>
                <p>${escapeHtml(mensaje)}</p>
                <a class="detalle-volver" href="catalogo.html">
                    Volver al catálogo
                </a>
            </section>`;
        return;
    }

    document.title = `${producto.nombre} | Ferretería Colorín`;

    document.getElementById("product-breadcrumb-name").textContent =
        producto.nombre;

    const stockTexto = producto.stock === 1
        ? "1 disponible"
        : `${producto.stock} disponibles`;

    // ficha_tecnica puede llegar desde MySQL como texto JSON.
    let ficha = {};

    if (producto.ficha_tecnica) {
        try {
            ficha = typeof producto.ficha_tecnica === "string"
                ? JSON.parse(producto.ficha_tecnica)
                : producto.ficha_tecnica;

            if (!ficha || typeof ficha !== "object" || Array.isArray(ficha)) {
                ficha = {};
            }
        } catch (error) {
            console.error("No se pudo interpretar la ficha técnica:", error);
            ficha = {};
        }
    }

    // Mostrar datos básicos si la ficha JSON está vacía.
    if (Object.keys(ficha).length === 0) {
        ficha = {
            "Código": producto.codigo,
            "Categoría": producto.categoria,
            "Marca": producto.marca,
            "Presentación": producto.unidad
        };
    }

    const especificaciones = Object.entries(ficha)
        .filter(([titulo, valor]) =>
            textoDisponible(titulo) &&
            valor !== null &&
            valor !== undefined &&
            String(valor).trim() !== ""
        )
        .map(([titulo, valor]) => {
            const valorVisible = typeof valor === "boolean"
                ? (valor ? "Sí" : "No")
                : String(valor);

            const tituloVisible = titulo
                .replace(/_/g, " ")
                .replace(/\b\w/g, letra => letra.toUpperCase());

            return `
                <div class="spec-item">
                    <dt>${escapeHtml(tituloVisible)}</dt>
                    <dd>${escapeHtml(valorVisible)}</dd>
                </div>`;
        })
        .join("");

    const descripcion = producto.descripcion_comercial ||
        producto.descripcion ||
        "Información del producto no disponible.";

    const caracteristicas = producto.caracteristicas;

    const seccionCaracteristicas = textoDisponible(caracteristicas)
        ? `
            <div class="product-description-copy">
                <h3>Características</h3>
                <p>${escapeHtml(caracteristicas)}</p>
            </div>`
        : "";

    const seccionFicha = `
        <div class="product-section-heading tech-heading">
            <span class="section-kicker">DETALLES Y ESPECIFICACIONES</span>
            <h2>Ficha técnica</h2>
        </div>
        <dl class="product-spec-grid">${especificaciones}</dl>`;

    const dondeUsarlo = apartado("Dónde utilizarlo", producto.donde_usarlo);
    const recomendaciones = apartado(
        "Recomendaciones de uso",
        producto.recomendaciones_uso
    );
    const ventajas = apartado("Ventajas", producto.ventajas);
    const advertencias = apartado(
        "Advertencias de uso",
        producto.advertencia_uso
    );
    const observaciones = apartado(
        "Observaciones",
        producto.observaciones
    );
    const observacionesAdicionales = apartado(
        "Observaciones adicionales",
        producto.observaciones_adicionales
    );
    const garantia = apartado("Garantía", producto.garantia);

    contenedor.innerHTML = `
        <article class="catalogo_cantenido product-detail-card"
                 data-id-producto="${Number(producto.id)}">

            <div class="product-gallery">
                <div class="product-image-frame">
                    <img class="catalogo_cantenido_imagen product-main-image"
                         src="${escapeHtml(producto.imagen)}"
                         alt="${escapeHtml(producto.nombre)}">
                </div>
                <span class="product-gallery-caption">
                    <i class="fa-regular fa-image"></i>
                    Imagen referencial del producto
                </span>
            </div>

            <div class="product-buy-panel">
                <span class="product-brand">${escapeHtml(producto.marca)}</span>
                <h1 class="catalogo_cantenido_TITULO">
                    ${escapeHtml(producto.nombre)}
                </h1>
                <p class="product-code">
                    Código: ${escapeHtml(producto.codigo)}
                    <span>·</span>
                    ${escapeHtml(producto.categoria)}
                </p>
                <p class="catalogo_cantenido_PRECIO product-detail-price">
                    ${precio(producto.precio)}
                </p>
                <p class="product-unit">${escapeHtml(producto.unidad)}</p>

                <div class="product-stock">
                    <span class="stock-dot"></span>
                    <div>
                        <strong>Disponible</strong>
                        <small>${escapeHtml(stockTexto)} en el inventario</small>
                    </div>
                </div>

                <div class="catalogo_cantenido_STOCK product-cart-stock">
                    Stock: ${Number(producto.stock)} disponibles
                </div>

                <button class="btn btn_agregar product-add-button"
                        type="button"
                        aria-label="Agregar ${escapeHtml(producto.nombre)} al carrito">
                    <i class="fa-solid fa-cart-plus"></i>
                    Agregar al carrito
                </button>

                <p class="product-demo-note">
                    <i class="fa-solid fa-circle-info"></i>
                    Precio y stock consultados del inventario actual.
                </p>
            </div>
        </article>

        <section class="product-info-section">
            <div class="product-section-heading">
                <span class="section-kicker">INFORMACIÓN DEL PRODUCTO</span>
                <h2>Descripción</h2>
            </div>

            <p class="product-intro">${escapeHtml(descripcion)}</p>

            ${seccionCaracteristicas}
            ${seccionFicha}
            ${dondeUsarlo}
            ${recomendaciones}
            ${ventajas}
            ${advertencias}
            ${observaciones}
            ${observacionesAdicionales}
            ${garantia}

            <a class="product-back-link" href="catalogo.html">
                <i class="fa-solid fa-arrow-left"></i>
                Volver al catálogo
            </a>
        </section>`;
});
