/* Navegación jerárquica de categorías: departamento > subcategoría > tipo. */
document.addEventListener("DOMContentLoaded", () => {
    const catalogo = {
        "Herramientas": {
            icono: "fa-screwdriver-wrench",
            grupos: [
                { nombre: "Herramientas manuales", tipos: ["Llaves de ajuste", "Alicates", "Destornilladores", "Martillos", "Sierras y serruchos", "Cuchillas y cutters"] },
                { nombre: "Herramientas de medición", tipos: ["Winchas", "Niveles", "Escuadras", "Calibradores"] },
                { nombre: "Artículos de seguridad", tipos: ["Guantes", "Lentes de seguridad", "Mascarillas", "Cascos"] },
                { nombre: "Candados", tipos: ["Candados", "Cerraduras"] },
                { nombre: "Accesorios de fijación", tipos: ["Clavos", "Tornillos", "Tarugos", "Pernos"] },
                { nombre: "Otras herramientas", tipos: ["Taladros", "Amoladoras", "Accesorios para herramientas"] }
            ]
        },
        "Pinturas": {
            icono: "fa-paint-roller",
            grupos: [
                { nombre: "Marcas", tipos: ["Tekno", "CPP", "Anypsa", "Otras marcas"] },
                { nombre: "Herramientas para pintar", tipos: ["Brochas", "Rodillos", "Bandejas", "Extensiones"] },
                { nombre: "Esmaltes y solventes", tipos: ["Esmaltes", "Thinner", "Aguarrás"] },
                { nombre: "Pegamentos y adhesivos", tipos: ["Pegamentos", "Adhesivos", "Siliconas"] },
                { nombre: "Productos para resanar y empastar", tipos: ["Masillas", "Empastes", "Yeso"] },
                { nombre: "Acabados para madera", tipos: ["Barnices", "Lacas", "Selladores"] }
            ]
        }
    };
    const panel = document.getElementById("category-mega-menu");
    const toggle = document.getElementById("btn-categorias");
    if (!panel || !toggle) return;

    let departamentoActual = "Herramientas";
    let grupoActual = catalogo[departamentoActual].grupos[0];
    const crearRuta = (departamento, grupo, tipo = "") => {
        const params = new URLSearchParams({ departamento, subcategoria: grupo });
        if (tipo) params.set("tipo", tipo);
        return `catalogo.html?${params.toString()}`;
    };

    function renderizar() {
        const datos = catalogo[departamentoActual];
        panel.innerHTML = `
            <div class="mega-heading"><div><span class="section-kicker">ENCUENTRA LO QUE BUSCAS</span><h2>${departamentoActual}</h2><p>Explora nuestras subcategorías</p></div><button type="button" class="mega-close" aria-label="Cerrar categorías"><i class="fa-solid fa-xmark"></i></button></div>
            <div class="mega-columns">
                <div class="mega-main-list" aria-label="Categorías principales">${Object.keys(catalogo).map(nombre => `<button class="mega-main-item ${nombre === departamentoActual ? "activo" : ""}" type="button" data-mega-main="${nombre}"><i class="fa-solid ${catalogo[nombre].icono}"></i>${nombre}<i class="fa-solid fa-chevron-right mega-arrow"></i></button>`).join("")}</div>
                <div class="mega-sub-list" aria-label="Subcategorías">${datos.grupos.map(grupo => `<button class="mega-sub-item ${grupo.nombre === grupoActual.nombre ? "activo" : ""}" type="button" data-mega-sub="${grupo.nombre}">${grupo.nombre}<i class="fa-solid fa-chevron-right"></i></button>`).join("")}</div>
                <div class="mega-type-list" aria-label="Tipos de producto"><a class="mega-ver-todo" href="${crearRuta(departamentoActual, grupoActual.nombre)}">Ver todo ${grupoActual.nombre.toLocaleLowerCase("es")} <i class="fa-solid fa-arrow-right"></i></a>${grupoActual.tipos.map(tipo => `<a class="mega-type-link" href="${crearRuta(departamentoActual, grupoActual.nombre, tipo)}">${tipo}<i class="fa-solid fa-arrow-right"></i></a>`).join("")}</div>
            </div>`;
        panel.querySelector(".mega-close").addEventListener("click", cerrar);
        panel.querySelectorAll("[data-mega-main]").forEach(button => button.addEventListener("click", event => {
            event.stopPropagation();
            departamentoActual = button.dataset.megaMain;
            grupoActual = catalogo[departamentoActual].grupos[0];
            renderizar();
        }));
        panel.querySelectorAll("[data-mega-sub]").forEach(button => button.addEventListener("click", event => {
            event.stopPropagation();
            grupoActual = catalogo[departamentoActual].grupos.find(grupo => grupo.nombre === button.dataset.megaSub);
            renderizar();
        }));
    }

    function abrir() { renderizar(); panel.hidden = false; toggle.setAttribute("aria-expanded", "true"); }
    function cerrar() { panel.hidden = true; toggle.setAttribute("aria-expanded", "false"); }
    toggle.addEventListener("click", () => panel.hidden ? abrir() : cerrar());
    document.addEventListener("click", event => { if (!panel.contains(event.target) && !toggle.contains(event.target)) cerrar(); });
    document.addEventListener("keydown", event => { if (event.key === "Escape" && !panel.hidden) { cerrar(); toggle.focus(); } });
});
