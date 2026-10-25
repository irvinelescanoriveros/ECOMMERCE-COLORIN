/* Checkout: registra un pedido pendiente en el backend; no procesa cobros. */
document.addEventListener("DOMContentLoaded", () => {
    const cartKey = "carritoColorin";
    const ordersKey = "colorinPedidosDemo";
    const readCart = () => {
        try {
            const cart = JSON.parse(localStorage.getItem(cartKey) || "[]");
            return Array.isArray(cart) ? cart : [];
        } catch { return []; }
    };
    const cart = readCart();
    const layout = document.querySelector(".checkout-layout");
    const stepNav = document.querySelector(".checkout-steps");
    const summary = document.querySelector(".checkout-summary");
    let step = 1;
    let envio = 0;
    let fechaEntrega = "";
    let metodoEntrega = "domicilio";
    let datosPersonales = {};
    let datosEntrega = {};
    let direccionesGuardadas = [];

    const precio = valor => `S/ ${Number(valor || 0).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    const subtotal = cart.reduce((sum, producto) => sum + Number(producto.precio || 0) * Number(producto.cantidad || 0), 0);

    if (!cart.length) {
        layout.innerHTML = `<section class="checkout-empty"><i class="fa-solid fa-cart-shopping"></i><h1>Tu carrito está vacío</h1><p>Agrega productos antes de continuar con tu compra.</p><a href="catalogo.html" class="checkout-primary">Ir al catálogo</a></section>`;
        stepNav.hidden = true;
        return;
    }

    if (!sessionStorage.getItem("colorinClienteDemo")) {
        sessionStorage.setItem("colorinCheckoutPendiente", "1");
        window.location.replace("cuenta.html?checkout=login");
        return;
    }

    function renderSummary() {
        const productsContainer = document.getElementById("summary-products");
        productsContainer.replaceChildren();
        cart.forEach(producto => {
            const row = document.createElement("div"); row.className = "summary-product";
            const image = document.createElement("img"); image.src = producto.imagen || "img/Logo_Chip.png"; image.alt = "";
            const info = document.createElement("div"); info.className = "summary-product-info";
            const name = document.createElement("strong"); name.textContent = producto.nombre;
            const quantity = document.createElement("small"); quantity.textContent = `Cantidad: ${producto.cantidad}`;
            info.append(name, quantity);
            const itemPrice = document.createElement("span"); itemPrice.className = "summary-product-price"; itemPrice.textContent = precio(Number(producto.precio) * Number(producto.cantidad));
            row.append(image, info, itemPrice); productsContainer.append(row);
        });
        const units = cart.reduce((sum, producto) => sum + Number(producto.cantidad || 0), 0);
        document.getElementById("summary-item-count").textContent = `${units} ${units === 1 ? "producto" : "productos"}`;
        document.getElementById("summary-subtotal").textContent = precio(subtotal);
        document.getElementById("summary-shipping").textContent = envio ? precio(envio) : (metodoEntrega === "tienda" ? "Gratis" : precio(0));
        document.getElementById("summary-total").textContent = precio(subtotal + envio);
    }

    function mostrarPaso(numero) {
        step = numero;
        document.querySelectorAll("[data-checkout-step]").forEach(panel => { panel.hidden = Number(panel.dataset.checkoutStep) !== step; });
        document.querySelectorAll("[data-step-indicator]").forEach(item => {
            const itemStep = Number(item.dataset.stepIndicator);
            item.classList.toggle("activo", itemStep === step);
            item.classList.toggle("completado", itemStep < step);
            const number = item.querySelector("span");
            if (number) number.innerHTML = itemStep < step ? '<i class="fa-solid fa-check"></i>' : String(itemStep);
        });
        renderSummary();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function validar(form) {
        let primeroInvalido = null;
        form.querySelectorAll("input, select, textarea").forEach(campo => {
            const invalido = !campo.disabled && !campo.checkValidity();
            campo.classList.toggle("field-invalid", invalido);
            campo.setAttribute("aria-invalid", String(invalido));
            if (invalido && !primeroInvalido) primeroInvalido = campo;
        });
        if (primeroInvalido) {
            primeroInvalido.focus();
            primeroInvalido.reportValidity();
            return false;
        }
        return true;
    }

    const email = document.getElementById("checkout-email");
    const savedEmail = sessionStorage.getItem("colorinClienteDemo");
    if (email && savedEmail) {
        email.value = savedEmail;
        try {
            const profile = JSON.parse(sessionStorage.getItem(`colorinPerfilDemo:${savedEmail.toLowerCase()}`) || "{}");
            const profileFields = {
                nombre: "checkout-nombre",
                apellidos: "checkout-apellidos",
                telefono: "checkout-telefono",
                dni: "checkout-documento"
            };
            Object.entries(profileFields).forEach(([key, id]) => {
                const field = document.getElementById(id);
                if (field && profile[key]) field.value = profile[key];
            });
        } catch { /* Si no hay perfil guardado, el cliente completa los datos manualmente. */ }

        fetch("/api/clientes-web/perfil", { headers: { Accept: "application/json" } })
            .then(async respuesta => {
                if (!respuesta.ok) throw new Error("No se pudo consultar el perfil guardado.");
                return respuesta.json();
            })
            .then(profile => {
                const profileFields = {
                    nombre: "checkout-nombre",
                    apellidos: "checkout-apellidos",
                    telefono: "checkout-telefono",
                    dni: "checkout-documento"
                };
                Object.entries(profileFields).forEach(([key, id]) => {
                    const field = document.getElementById(id);
                    if (field && profile[key]) field.value = profile[key];
                });
            })
            .catch(error => console.warn("No se pudieron cargar los datos de perfil en checkout:", error));
    }

    const factura = document.getElementById("checkout-factura");
    const invoiceFields = document.getElementById("checkout-empresa");
    const invoiceRuc = document.getElementById("checkout-ruc");
    const invoiceBusiness = document.getElementById("checkout-razon-social");
    factura.addEventListener("change", () => {
        invoiceFields.hidden = !factura.checked;
        invoiceRuc.required = factura.checked;
        invoiceBusiness.required = factura.checked;
        if (!factura.checked) { invoiceRuc.value = ""; invoiceBusiness.value = ""; }
    });

    document.querySelectorAll("[data-terms-open]").forEach(link => link.addEventListener("click", event => {
        event.preventDefault(); document.getElementById("modal-politicas-checkout").classList.add("active");
    }));
    document.querySelectorAll("[data-checkout-close]").forEach(button => button.addEventListener("click", () => document.getElementById("modal-politicas-checkout").classList.remove("active")));

    document.getElementById("form-checkout-personal").addEventListener("submit", event => {
        event.preventDefault();
        const form = event.currentTarget;
        const name = document.getElementById("checkout-nombre");
        const nameError = document.querySelector('[data-error-for="checkout-nombre"]');
        nameError.hidden = Boolean(name.value.trim());
        name.setCustomValidity(name.value.trim() ? "" : "Este campo es obligatorio");
        if (!validar(form)) return;
        datosPersonales = Object.fromEntries(new FormData(form).entries());
        document.getElementById("personal-message").textContent = "";
        mostrarPaso(2);
    });

    const department = document.getElementById("delivery-departamento");
    const province = document.getElementById("delivery-provincia");
    const district = document.getElementById("delivery-distrito");
    const provinciasPorDepartamento = { Lima: ["Lima", "Huarochirí"], Callao: ["Callao"], Arequipa: ["Arequipa"] };
    const distritosPorProvincia = {
        Lima: ["Lima", "Chaclacayo", "Miraflores", "San Isidro", "San Juan de Lurigancho", "Santiago de Surco"],
        Huarochirí: ["Matucana", "Santa Eulalia"],
        Callao: ["Callao", "Bellavista", "La Punta"],
        Arequipa: ["Arequipa", "Cayma", "Cerro Colorado"]
    };
    function poblarSelect(select, opciones, placeholder) {
        select.replaceChildren(new Option(placeholder, ""));
        opciones.forEach(valor => select.add(new Option(valor, valor)));
        select.disabled = opciones.length === 0;
        select.value = "";
    }

    const savedAddressSelect = document.getElementById("saved-address-select");
    function aplicarDireccionGuardada(direccion) {
        const asignarOpcion = (select, valor) => {
            if (!valor) return;
            if (![...select.options].some(opcion => opcion.value === valor)) {
                select.add(new Option(valor, valor));
            }
            select.disabled = false;
            select.value = valor;
        };
        asignarOpcion(department, direccion.departamento);
        asignarOpcion(province, direccion.provincia);
        asignarOpcion(district, direccion.distrito);
        document.getElementById("delivery-direccion").value = direccion.direccion || "";
        document.getElementById("delivery-piso").value = direccion.pisoDepartamento || "";
        document.getElementById("delivery-referencia").value = direccion.referencia || "";
        actualizarEntrega();
    }

    if (savedAddressSelect) {
        savedAddressSelect.addEventListener("change", () => {
            const direccion = direccionesGuardadas.find(item => String(item.id) === savedAddressSelect.value);
            if (direccion) aplicarDireccionGuardada(direccion);
        });
        fetch("/api/clientes-web/direcciones", { headers: { Accept: "application/json" } })
            .then(async respuesta => {
                if (!respuesta.ok) throw new Error("No se pudieron cargar las direcciones guardadas.");
                direccionesGuardadas = await respuesta.json();
                direccionesGuardadas.forEach(item => {
                    const opcion = new Option(`${item.etiqueta}: ${item.direccion}, ${item.distrito}`, item.id);
                    savedAddressSelect.add(opcion);
                });
                const predeterminada = direccionesGuardadas.find(item => item.predeterminada === true || Number(item.predeterminada) === 1);
                if (predeterminada) {
                    savedAddressSelect.value = String(predeterminada.id);
                    aplicarDireccionGuardada(predeterminada);
                } else if (!direccionesGuardadas.length) {
                    document.getElementById("saved-address-help").textContent = "Aún no tienes direcciones guardadas. Puedes completar una nueva aquí.";
                }
            })
            .catch(error => {
                console.error("No se pudieron cargar las direcciones de entrega:", error);
                document.getElementById("saved-address-help").textContent = "No pudimos cargar las direcciones guardadas. Puedes completar una nueva.";
            });
    }

    department.addEventListener("change", () => {
        poblarSelect(province, provinciasPorDepartamento[department.value] || [], "Selecciona una provincia");
        poblarSelect(district, [], "Selecciona un distrito");
        actualizarEntrega();
    });
    province.addEventListener("change", () => {
        poblarSelect(district, distritosPorProvincia[province.value] || [], "Selecciona un distrito");
        actualizarEntrega();
    });
    district.addEventListener("change", actualizarEntrega);

    const deliveryAddress = document.getElementById("delivery-address-fields");
    const pickupFields = document.getElementById("delivery-pickup-fields");
    const pickupSelect = document.getElementById("pickup-store");
    const addressRequiredIds = ["delivery-departamento", "delivery-provincia", "delivery-distrito", "delivery-direccion", "delivery-referencia"];
    document.querySelectorAll('input[name="metodoEntrega"]').forEach(radio => radio.addEventListener("change", () => {
        metodoEntrega = radio.value;
        document.querySelectorAll(".delivery-option").forEach(label => label.classList.toggle("activo", label.contains(radio) && radio.checked));
        const domicilio = metodoEntrega === "domicilio";
        deliveryAddress.hidden = !domicilio;
        pickupFields.hidden = domicilio;
        addressRequiredIds.forEach(id => { document.getElementById(id).required = domicilio; });
        [department, province, district].forEach(select => { select.disabled = domicilio && select !== department && !select.options.length; });
        pickupSelect.required = !domicilio;
        envio = domicilio ? 4.99 : 0;
        actualizarEntrega();
    }));
    const continueDelivery = document.getElementById("delivery-continue");
    function actualizarEntrega() {
        const form = document.getElementById("form-checkout-entrega");
        continueDelivery.disabled = !form.checkValidity();
        renderSummary();
    }
    document.getElementById("form-checkout-entrega").addEventListener("input", actualizarEntrega);
    document.getElementById("form-checkout-entrega").addEventListener("change", actualizarEntrega);
    document.getElementById("form-checkout-entrega").addEventListener("submit", event => {
        event.preventDefault();
        if (!validar(event.currentTarget)) return;
        datosEntrega = { metodo: metodoEntrega, departamento: department.value, provincia: province.value, distrito: district.value, direccion: document.getElementById("delivery-direccion").value.trim(), piso: document.getElementById("delivery-piso").value.trim(), referencia: document.getElementById("delivery-referencia").value.trim(), puntoRecojo: pickupSelect.value, fecha: fechaEntrega };
        document.getElementById("checkout-factura-resumen").textContent = factura.checked ? "Factura" : "Boleta";
        document.getElementById("delivery-message").textContent = "";
        mostrarPaso(3);
    });
    document.querySelectorAll("[data-checkout-back]").forEach(button => button.addEventListener("click", () => mostrarPaso(Number(button.dataset.checkoutBack))));

    const dateButton = document.getElementById("delivery-change-date");
    const dateInput = document.getElementById("delivery-date");
    dateInput.min = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    dateButton.addEventListener("click", () => { dateInput.hidden = !dateInput.hidden; if (!dateInput.hidden) dateInput.focus(); });
    dateInput.addEventListener("change", () => {
        fechaEntrega = dateInput.value;
        if (!fechaEntrega) return;
        const [year, month, day] = fechaEntrega.split("-").map(Number);
        document.getElementById("delivery-date-label").textContent = `Fecha elegida: ${new Date(year, month - 1, day).toLocaleDateString("es-PE", { day: "numeric", month: "long" })}`;
        dateButton.textContent = "Cambiar fecha";
    });

    document.getElementById("form-checkout-pago").addEventListener("submit", async event => {
        event.preventDefault();
        if (!validar(event.currentTarget)) return;
        const pago = new FormData(event.currentTarget).get("metodoPago");
        const boton = event.currentTarget.querySelector('button[type="submit"]');
        boton.disabled = true;
        const mensajePago = document.getElementById("payment-message");
        mensajePago.textContent = "Registrando tu pedido…";
        const solicitud = {
            productos: cart.map(item => ({ idProducto: Number(item.id), cantidad: Number(item.cantidad) })),
            metodoPago: pago,
            metodoEntrega: datosEntrega.metodo === "domicilio" ? "Domicilio" : "Recojo",
            departamento: datosEntrega.departamento,
            provincia: datosEntrega.provincia,
            distrito: datosEntrega.distrito,
            direccion: datosEntrega.direccion,
            pisoDepartamento: datosEntrega.piso,
            referencia: datosEntrega.referencia,
            fechaEntrega: datosEntrega.fecha || null,
            puntoRecojo: datosEntrega.puntoRecojo,
            tipoComprobante: factura.checked ? "Factura" : "Boleta",
            rucFactura: factura.checked ? invoiceRuc.value.trim() : null,
            razonSocial: factura.checked ? invoiceBusiness.value.trim() : null
        };
        let pedido;
        try {
            const respuesta = await fetch("/api/pedidos-web", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(solicitud)
            });
            pedido = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) throw new Error(pedido.mensaje || "No se pudo registrar el pedido.");
        } catch (error) {
            mensajePago.textContent = error.message || "No pudimos conectar con el backend.";
            mensajePago.classList.add("error");
            boton.disabled = false;
            return;
        }

        const ahora = new Date();
        const order = { numero: pedido.numeroPedido, fecha: ahora.toISOString(), estado: "Pendiente", total: Number(pedido.total), cantidad: cart.reduce((sum, item) => sum + Number(item.cantidad || 0), 0), productos: cart.map(item => ({ nombre: item.nombre, cantidad: item.cantidad, subtotal: Number(item.precio) * Number(item.cantidad) })), metodoPago: pago };
        try {
            const orders = JSON.parse(sessionStorage.getItem(ordersKey) || "[]");
            sessionStorage.setItem(ordersKey, JSON.stringify([order, ...(Array.isArray(orders) ? orders : [])]));
        } catch { /* La confirmación se muestra aunque el navegador no permita la sesión de demostración. */ }
        localStorage.removeItem(cartKey);
        window.dispatchEvent(new Event("colorin-cart-updated"));
        document.getElementById("finish-order-number").textContent = order.numero;
        document.getElementById("finish-message").textContent = `Tu pedido tiene ${order.cantidad} ${order.cantidad === 1 ? "producto" : "productos"} y un total de ${precio(order.total)}. El pago está pendiente.`;
        stepNav.hidden = true;
        summary.hidden = true;
        layout.classList.add("checkout-completed");
        document.querySelectorAll("[data-checkout-step]").forEach(panel => { panel.hidden = true; });
        document.getElementById("checkout-finish").hidden = false;
        window.scrollTo({ top: 0, behavior: "smooth" });
    });

    function validarFactura() {
        invoiceRuc.setCustomValidity(factura.checked && !/^\d{11}$/.test(invoiceRuc.value.trim()) ? "Ingresa un RUC de 11 dígitos." : "");
    }
    invoiceRuc.addEventListener("input", validarFactura);
    actualizarEntrega();
    renderSummary();
});
