/* Elementos que comparten las tres páginas: navegación, pie y carrito. */
document.addEventListener("DOMContentLoaded", () => {
    const pagina = document.body.dataset.page || "inicio";
    const encabezado = document.getElementById("site-header");
    const pie = document.getElementById("site-footer");
    const carrito = document.getElementById("site-cart");
    const emailCliente = () => sessionStorage.getItem("colorinClienteDemo");

    if (encabezado) {
        encabezado.innerHTML = `
            <div class="top-strip">Ferretería Colorín <span>·</span> Todo para construir, reparar y renovar</div>
            <header class="site-header">
                <a href="index.html" class="brand-lockup" aria-label="Ferretería Colorín, inicio"><span class="brand-mark"><i class="fa-solid fa-house"></i></span><span class="brand-name">Ferretería <strong>Colorín</strong></span></a>
                <form class="header-search" id="busqueda-global" action="catalogo.html" method="get" role="search"><input id="buscador-global" name="buscar" type="search" placeholder="¿Qué estás buscando hoy?" aria-label="Buscar productos"><button type="submit" aria-label="Buscar"><i class="fa-solid fa-magnifying-glass"></i></button></form>
                <div class="header-actions"><div id="account-slot"></div><a href="#carrito" class="header-cart" id="btn_abrir_carrito_menu" aria-label="Abrir carrito"><i class="fa-solid fa-cart-shopping"></i><span>Carrito</span><b id="cart-badge" class="cart-badge">0</b></a></div>
            </header>
            <nav class="category-nav" aria-label="Navegación principal"><button type="button" class="category-menu" id="btn-categorias" aria-expanded="false" aria-controls="category-mega-menu"><i class="fa-solid fa-bars"></i> Categorías <i class="fa-solid fa-chevron-down category-chevron"></i></button><a href="index.html" class="nav-link ${pagina === "inicio" ? "active" : ""}">Inicio</a><a href="contacto.html" class="nav-link ${pagina === "contacto" ? "active" : ""}">Contacto</a><span class="nav-promise"><i class="fa-solid fa-screwdriver-wrench"></i> Soluciones para tus proyectos</span></nav>
            <div id="category-mega-menu" class="category-mega-menu" hidden></div>`;
    }

    function actualizarMenuCuenta() {
        const slot = document.getElementById("account-slot");
        if (!slot) return;
        const email = emailCliente();
        if (!email) {
            slot.innerHTML = '<a class="header-account" href="cuenta.html"><i class="fa-regular fa-user"></i><span><small>Bienvenido</small><strong>Mi cuenta</strong></span></a>';
            return;
        }
        slot.innerHTML = `<div class="account-menu-wrap"><a class="header-account" href="cuenta.html#perfil"><i class="fa-regular fa-user"></i><span><small>Hola,</small><strong>${email.replace(/[&<>"']/g, "")}</strong></span></a><button class="account-menu-toggle" type="button" aria-label="Abrir menú de cuenta" aria-expanded="false"><i class="fa-solid fa-chevron-down"></i></button><div class="account-dropdown" hidden><p class="dropdown-email">${email.replace(/[&<>"']/g, "")}</p><a href="cuenta.html#perfil"><i class="fa-regular fa-id-card"></i> Mi perfil</a><a href="cuenta.html#pedidos"><i class="fa-solid fa-box"></i> Mis pedidos</a><a href="cuenta.html#direcciones"><i class="fa-solid fa-location-dot"></i> Direcciones</a><button type="button" class="dropdown-logout"><i class="fa-solid fa-arrow-right-from-bracket"></i> Cerrar sesión</button></div></div>`;
        const toggle = slot.querySelector(".account-menu-toggle");
        const dropdown = slot.querySelector(".account-dropdown");
        toggle.addEventListener("click", () => { dropdown.hidden = !dropdown.hidden; toggle.setAttribute("aria-expanded", String(!dropdown.hidden)); });
        slot.querySelector(".dropdown-logout").addEventListener("click", () => { sessionStorage.removeItem("colorinClienteDemo"); window.location.href = "cuenta.html"; });
    }
    actualizarMenuCuenta();
    window.addEventListener("colorin-auth-changed", actualizarMenuCuenta);
    document.addEventListener("click", event => {
        const wrap = document.querySelector(".account-menu-wrap");
        if (wrap && !wrap.contains(event.target)) {
            const menu = wrap.querySelector(".account-dropdown");
            if (menu) menu.hidden = true;
            wrap.querySelector(".account-menu-toggle")?.setAttribute("aria-expanded", "false");
        }
    });

    if (pie) {
        pie.innerHTML = `
            <footer class="site_footer">
                <div class="footer_container"><div class="footer_grid">
                    <div class="footer_col"><div class="footer_logo"><div class="logo_icon"><img src="img/Logo_Chip.png" alt=""></div><h3>Ferretería <span>Colorín</span></h3></div><p class="footer_desc">Productos para el hogar, el taller y la construcción.</p></div>
                    <div class="footer_col"><h4 class="footer_titulo">NAVEGACIÓN</h4><ul class="footer_links"><li><a href="index.html">Inicio</a></li><li><a href="catalogo.html">Catálogo</a></li><li><a href="contacto.html">Contacto</a></li><li><a href="cuenta.html">Mi cuenta</a></li></ul></div>
                    <div class="footer_col"><h4 class="footer_titulo">CATEGORÍAS</h4><ul class="footer_links"><li><a href="catalogo.html">Herramientas manuales</a></li><li><a href="catalogo.html">Pinturas</a></li><li><a href="catalogo.html">Ferretería general</a></li></ul></div>
                    <div class="footer-col"><h4 class="footer_titulo">CONTACTO</h4><ul class="footer_contacto"><li><span>📍</span> Ferretería Colorín</li><li><span>📞</span> Contáctanos para consultas y pedidos</li><li><span>✉️</span> Atención al cliente</li></ul></div>
                </div><div class="footer_bottom"><p class="footer_copy">&copy; 2026 Ferretería Colorín</p></div></div>
            </footer>`;
    }

    if (carrito) {
        carrito.innerHTML = `
            <div id="carrito_panel" class="carrito-side-panel">
                <div class="carrito-header"><div class="header-title"><i class="fa-solid fa-cart-shopping"></i><h2>Carrito de Compras</h2></div><button id="btn_cerrar_carrito" class="close-btn" type="button" aria-label="Cerrar carrito">&times;</button></div>
                <div class="carrito-cuerpo" id="carrito_cuerpo"></div>
                <div class="carrito-footer"><div class="carrito-total"><span>Total</span><span id="total_monto">S/ 0.00</span></div><button class="btn-proceder-pago" type="button">CONTINUAR COMPRA</button><p class="transaccion-segura">Pedido de demostración</p></div>
            </div>
            <div id="carrito_overlay" class="carrito-overlay"></div>
            <div id="modal-confirmacion" class="modal"><div class="modal-content"><div class="modal-header"><h3>Confirmar pedido</h3><button class="modal-close" data-close="modal-confirmacion">&times;</button></div><p>¿Deseas continuar con este pedido de demostración?</p><div class="modal-actions"><button class="btn-modal btn-cancel" data-close="modal-confirmacion">Volver</button><button id="modal-btn-confirmar" class="btn-modal btn-confirmar">Confirmar</button></div></div></div>
            <div id="modal-gracias" class="modal"><div class="modal-content"><div class="modal-header"><h3>¡Gracias!</h3><button class="modal-close" data-close="modal-gracias">&times;</button></div><p>Pedido de demostración completado.</p><div class="modal-actions"><button class="btn-modal btn-confirmar" data-close="modal-gracias">Aceptar</button></div></div></div>`;
    }

});
