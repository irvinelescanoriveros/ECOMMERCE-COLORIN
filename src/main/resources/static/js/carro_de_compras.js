/* 
 CARRITO DE COMPRAS
*/

document.addEventListener("DOMContentLoaded", () => {

    const panelCarrito = document.getElementById("carrito_panel");
    const overlayCarrito = document.getElementById("carrito_overlay");
    const btnCerrarCarrito = document.getElementById("btn_cerrar_carrito");
    const carritoCuerpo = document.getElementById("carrito_cuerpo");
    const totalMonto = document.getElementById("total_monto");
    const btnAbrirCarritoMenu = document.getElementById("btn_abrir_carrito_menu");
    const cartBadge = document.getElementById("cart-badge");

    const modalConfirmacion = document.getElementById("modal-confirmacion");
    const modalGracias = document.getElementById("modal-gracias");
    const btnConfirmarPago = document.getElementById("modal-btn-confirmar");

    let carrito = [];
    try {
        carrito = JSON.parse(localStorage.getItem("carritoColorin") || "[]");
        if (!Array.isArray(carrito)) carrito = [];
    } catch {
        carrito = [];
    }

    function guardarCarrito() {
        try {
            localStorage.setItem("carritoColorin", JSON.stringify(carrito));
        } catch {
            // El carrito sigue funcionando durante la página actual si el navegador bloquea el almacenamiento.
        }
    }

    function abrirModal(modal) {
        if (!modal) return;
        modal.classList.add("active");
    }

    function cerrarModal(modal) {
        if (!modal) return;
        modal.classList.remove("active");
    }

    document.addEventListener("click", (e) => {
        const btnClose = e.target.closest("[data-close]");
        if (!btnClose) return;

        const modalId = btnClose.getAttribute("data-close");
        const modal = document.getElementById(modalId);
        if (modal) cerrarModal(modal);
    });

    document.addEventListener("click", (e) => {
        if (e.target.classList && e.target.classList.contains("modal")) {
            cerrarModal(e.target);
        }
    });

    document.addEventListener("keydown", (e) => {
        if (e.key !== "Escape") return;
        if (modalConfirmacion) cerrarModal(modalConfirmacion);
        if (modalGracias) cerrarModal(modalGracias);
    });

    if (btnAbrirCarritoMenu) {
        btnAbrirCarritoMenu.addEventListener("click", (e) => {
            e.preventDefault();
            abrirCarrito();
        });
    }

    function abrirCarrito() {
        panelCarrito.classList.add("active");
        overlayCarrito.classList.add("active");
    }

    function cerrarCarrito() {
        panelCarrito.classList.remove("active");
        overlayCarrito.classList.remove("active");
    }

    function convertirPrecio(precioTexto) {
        return Number(
            precioTexto
                .replace("S/", "")
                .replaceAll(",", "")
                .trim()
        );
    }

    function formatearPrecio(numero) {
        return "S/ " + numero.toLocaleString("es-PE", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function actualizarBadge() {
        const totalItems = carrito.reduce((sum, p) => sum + p.cantidad, 0);

        if (!cartBadge) return;

        cartBadge.textContent = totalItems;
        if (totalItems > 0) cartBadge.classList.add("visible");
        else cartBadge.classList.remove("visible");
    }

    function renderizarCarrito() {
        guardarCarrito();
        carritoCuerpo.innerHTML = "";

        if (carrito.length === 0) {
            carritoCuerpo.innerHTML = `<p class="carrito-vacio">Tu carrito está vacío</p>`;
            totalMonto.textContent = "S/ 0.00";
            actualizarBadge();
            return;
        }

        carrito.forEach((producto, index) => {
            carritoCuerpo.innerHTML += `
                <div class="carrito-producto">
                    <img 
                        src="${producto.imagen}" 
                        alt="${producto.nombre}" 
                        class="carrito-producto-img"
                    >

                    <div class="carrito-producto-info">
                        <h4>${producto.nombre}</h4>
                        <p class="carrito-producto-precio">
                            ${formatearPrecio(producto.precio)}
                        </p>

                        <div class="carrito-producto-cantidad">
                            <button class="btn-restar" data-index="${index}">-</button>
                            <span>${producto.cantidad}</span>
                            <button class="btn-sumar" data-index="${index}">+</button>
                        </div>
                    </div>

                    <button class="btn-eliminar-producto" data-index="${index}">
                        <i class="fa-solid fa-trash-can"></i>
                    </button>
                </div>
            `;
        });

        calcularTotal();
        actualizarBadge();
    }

    function calcularTotal() {
        const total = carrito.reduce((suma, producto) => {
            return suma + producto.precio * producto.cantidad;
        }, 0);

        totalMonto.textContent = formatearPrecio(total);
    }

    function agregarProductoAlCarrito(boton) {
        
        const cardProducto = boton.closest(".catalogo_cantenido");
        const filaTabla = boton.closest(".productos_tabla_row");

        let nombre, precioTexto, imagen, productoId, stockTexto;

        if (cardProducto) {
            nombre = cardProducto.querySelector(".catalogo_cantenido_TITULO").textContent;
            precioTexto = cardProducto.querySelector(".catalogo_cantenido_PRECIO").textContent;
            imagen = cardProducto.querySelector(".catalogo_cantenido_imagen").src;
            productoId = cardProducto.dataset.idProducto;
            stockTexto = cardProducto.querySelector(".catalogo_cantenido_STOCK").textContent;
        } else if (filaTabla) {
            nombre = filaTabla.querySelector(".productos_tabla_cell_NAME").textContent;
            precioTexto = filaTabla.querySelector(".productos_tabla_cell_PRECIO").textContent;
            imagen = filaTabla.querySelector(".productos_tabla_cell_imagen img").src;
            productoId = filaTabla.dataset.idProducto;
            stockTexto = filaTabla.querySelector(".productos_tabla_cell_STOCK").textContent;
        } else {
            return;
        }

        const precio = convertirPrecio(precioTexto);
        const stock = Number(stockTexto.match(/\d+/)?.[0] || 0);

        const productoExistente = carrito.find(p => p.id === productoId);

        if (productoExistente) {
            if (productoExistente.cantidad >= productoExistente.stock) {
                alert("No hay más unidades disponibles de este producto.");
                return;
            }
            productoExistente.cantidad++;
        } else {
            carrito.push({
                id: productoId,
                nombre,
                precio,
                imagen,
                cantidad: 1,
                stock
            });
        }

        renderizarCarrito();
        abrirCarrito();
    }

    document.addEventListener("click", (e) => {
        const botonAgregar = e.target.closest(".btn_agregar , .btn_agregar_tabla");
        if (botonAgregar) {
            agregarProductoAlCarrito(botonAgregar);
        }
    });

    carritoCuerpo.addEventListener("click", (e) => {
        const botonSumar = e.target.closest(".btn-sumar");
        const botonRestar = e.target.closest(".btn-restar");
        const botonEliminar = e.target.closest(".btn-eliminar-producto");

        if (botonSumar) {
            const index = Number(botonSumar.dataset.index);
            if (carrito[index].cantidad >= carrito[index].stock) {
                alert("No hay más unidades disponibles de este producto.");
                return;
            }
            carrito[index].cantidad++;
            renderizarCarrito();
        }

        if (botonRestar) {
            const index = Number(botonRestar.dataset.index);
            carrito[index].cantidad--;

            if (carrito[index].cantidad <= 0) {
                carrito.splice(index, 1);
            }

            renderizarCarrito();
        }

        if (botonEliminar) {
            const index = Number(botonEliminar.dataset.index);
            carrito.splice(index, 1);
            renderizarCarrito();
        }
    });

    btnCerrarCarrito.addEventListener("click", cerrarCarrito);
    overlayCarrito.addEventListener("click", cerrarCarrito);

    renderizarCarrito();

    const btnPago = document.querySelector(".btn-proceder-pago");

    if (btnPago) {
        btnPago.addEventListener("click", () => {
            if (carrito.length === 0) {
                alert("Tu carrito está vacío.");
                return;
            }
            if (!sessionStorage.getItem("colorinClienteDemo")) {
                sessionStorage.setItem("colorinCheckoutPendiente", "1");
                window.location.href = "cuenta.html?checkout=login";
                return;
            }
            window.location.href = "checkout.html";
        });
    }

    window.addEventListener("colorin-cart-updated", () => {
        try {
            const guardado = JSON.parse(localStorage.getItem("carritoColorin") || "[]");
            carrito = Array.isArray(guardado) ? guardado : [];
        } catch { carrito = []; }
        renderizarCarrito();
    });
});
