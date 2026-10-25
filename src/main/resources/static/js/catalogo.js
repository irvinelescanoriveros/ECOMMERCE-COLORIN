/* Búsqueda, filtros jerárquicos de categoría y presentación del catálogo. */
let filtrosCatalogo = { departamento: "", subcategoria: "", tipo: "", categoria: "" };

function normalizarCatalogo(valor) {
    return String(valor || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLocaleLowerCase("es");
}

function cambiarVista(tipo) {
    const grid = document.getElementById("productos_colorin_grid");
    const tabla = document.getElementById("vista_tabla");
    const botones = document.querySelectorAll(".vista button");
    if (!grid || !tabla) return;
    grid.style.display = tipo === "grid" ? "grid" : "none";
    tabla.style.display = tipo === "tabla" ? "block" : "none";
    botones.forEach((boton, indice) => boton.classList.toggle("activo", indice === (tipo === "grid" ? 0 : 1)));
    try { localStorage.setItem("vistaCatalogo", tipo); } catch { /* Usa la vista actual si el navegador bloquea el almacenamiento. */ }
}

function filtrar(categoria) {
    filtrosCatalogo.categoria = categoria === "all" ? "" : categoria;
    aplicarBusquedaCatalogo();
}

function aplicarBusquedaCatalogo() {
    const texto = normalizarCatalogo(document.getElementById("buscador")?.value || "");
    const productos = [...document.querySelectorAll(".catalogo_cantenido[data-id-producto], .productos_tabla_row")];
    let visibles = 0;
    productos.forEach(producto => {
        const coincideTexto = normalizarCatalogo(producto.dataset.busqueda || producto.textContent).includes(texto);
        const coincideDepartamento = !filtrosCatalogo.departamento || normalizarCatalogo(producto.dataset.departamento) === normalizarCatalogo(filtrosCatalogo.departamento);
        const coincideSubcategoria = !filtrosCatalogo.subcategoria || normalizarCatalogo(producto.dataset.subcategoria) === normalizarCatalogo(filtrosCatalogo.subcategoria);
        const coincideTipo = !filtrosCatalogo.tipo || normalizarCatalogo(producto.dataset.tipo) === normalizarCatalogo(filtrosCatalogo.tipo);
        const coincideCategoria = !filtrosCatalogo.categoria || normalizarCatalogo(producto.dataset.categoria).includes(normalizarCatalogo(filtrosCatalogo.categoria));
        const mostrar = coincideTexto && coincideDepartamento && coincideSubcategoria && coincideTipo && coincideCategoria;
        producto.style.display = mostrar ? "" : "none";
        if (mostrar && producto.classList.contains("catalogo_cantenido")) visibles++;
    });
    const vacio = document.getElementById("catalogo-sin-resultados");
    if (vacio) vacio.hidden = visibles > 0;
}

document.addEventListener("DOMContentLoaded", () => {
    const parametros = new URLSearchParams(window.location.search);
    filtrosCatalogo.departamento = parametros.get("departamento") || "";
    filtrosCatalogo.subcategoria = parametros.get("subcategoria") || "";
    filtrosCatalogo.tipo = parametros.get("tipo") || "";
    const categoriaAntigua = parametros.get("categoria") || "";
    if (!filtrosCatalogo.departamento && (normalizarCatalogo(categoriaAntigua) === "herramientas" || normalizarCatalogo(categoriaAntigua) === "herramientas manuales")) filtrosCatalogo.departamento = "Herramientas";
    if (!filtrosCatalogo.departamento && normalizarCatalogo(categoriaAntigua) === "pinturas") filtrosCatalogo.departamento = "Pinturas";

    const buscador = document.getElementById("buscador");
    const inputGlobal = document.getElementById("buscador-global");
    const consulta = parametros.get("buscar") || "";
    if (buscador) buscador.value = consulta;
    if (inputGlobal) inputGlobal.value = consulta;
    buscador?.addEventListener("input", aplicarBusquedaCatalogo);
    document.getElementById("busqueda-global")?.addEventListener("submit", event => {
        event.preventDefault();
        if (!buscador || !inputGlobal) return;
        buscador.value = inputGlobal.value;
        history.replaceState(null, "", `catalogo.html?buscar=${encodeURIComponent(inputGlobal.value)}`);
        filtrosCatalogo = { departamento: "", subcategoria: "", tipo: "", categoria: "" };
        renderizarRutaCatalogo();
        aplicarBusquedaCatalogo();
    });

    let vistaGuardada = "grid";
    try { vistaGuardada = localStorage.getItem("vistaCatalogo") || "grid"; } catch { /* Usa la vista de fichas por defecto. */ }
    cambiarVista(vistaGuardada === "tabla" ? "tabla" : "grid");
    renderizarRutaCatalogo();
    aplicarBusquedaCatalogo();
    window.productosColorinReady?.then(() => {
        renderizarRutaCatalogo();
        aplicarBusquedaCatalogo();
    });
});

function renderizarRutaCatalogo() {
    const ruta = document.getElementById("catalogo-ruta");
    const textoRuta = document.getElementById("catalogo-ruta-texto");
    if (!ruta || !textoRuta) return;
    const partes = [filtrosCatalogo.departamento, filtrosCatalogo.subcategoria, filtrosCatalogo.tipo].filter(Boolean);
    ruta.hidden = partes.length === 0;
    textoRuta.textContent = partes.join("  ›  ");
    const titulo = document.querySelector(".section_titulo_1_2");
    const descripcion = document.querySelector(".section_descripcion_1_2");
    if (!partes.length) {
        if (titulo) titulo.textContent = "Catálogo de Ferretería Colorín";
        if (descripcion) descripcion.textContent = "Encuentra herramientas, pinturas y artículos de ferretería con stock disponible.";
        return;
    }
    if (partes.length) {
        if (titulo) titulo.textContent = partes[partes.length - 1];
        if (descripcion) descripcion.textContent = `Productos disponibles en ${partes.join(" / ")}.`;
    }
}
