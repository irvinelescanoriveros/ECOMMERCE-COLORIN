/* Registro y validación del inicio de sesión mediante el backend. */
document.addEventListener("DOMContentLoaded", () => {
    const accessSection = document.getElementById("cuenta-acceso");
    const dashboard = document.getElementById("cuenta-panel-cliente");
    const emailKey = "colorinClienteDemo";
    let perfil = {};
    let direcciones = [];

    function perfilKeyActual() {
        const correo = sessionStorage.getItem(emailKey);
        return correo ? `colorinPerfilDemo:${correo.toLowerCase()}` : null;
    }

    async function cargarPerfilDemo() {
        const key = perfilKeyActual();
        try {
            if (key) {
                const guardado = JSON.parse(sessionStorage.getItem(key) || "{}");
                perfil = guardado && typeof guardado === "object" ? guardado : {};
                aplicarPerfilEnFormulario(perfil);
            }
        } catch { perfil = {}; }

        try {
            const respuesta = await fetch("/api/clientes-web/perfil", { headers: { Accept: "application/json" } });
            if (!respuesta.ok) return;
            perfil = await respuesta.json();
            aplicarPerfilEnFormulario(perfil);
        } catch (error) {
            console.error("No se pudo cargar el perfil desde el backend:", error);
        }
    }

    async function cargarDirecciones() {
        const lista = document.getElementById("direcciones-lista");
        if (!lista) return;
        try {
            const respuesta = await fetch("/api/clientes-web/direcciones", {
                headers: { Accept: "application/json" }
            });
            if (!respuesta.ok) throw new Error("No se pudieron cargar tus direcciones.");
            direcciones = await respuesta.json();
            dibujarDirecciones();
        } catch (error) {
            console.error(error);
            setMensaje("direcciones-mensaje", "No pudimos cargar las direcciones. Revisa que el backend esté actualizado.", true);
        }
    }

    function dibujarDirecciones() {
        const lista = document.getElementById("direcciones-lista");
        if (!lista) return;
        lista.replaceChildren();
        if (!direcciones.length) {
            const vacio = document.createElement("div");
            vacio.className = "direcciones-vacias";
            vacio.innerHTML = '<i class="fa-solid fa-location-dot"></i><p>Aún no agregaste una dirección.</p><small>Podrás guardarla para usarla en tus próximos pedidos.</small>';
            lista.append(vacio);
            return;
        }
        direcciones.forEach(item => {
            const card = document.createElement("article");
            card.className = "direccion-card";
            const heading = document.createElement("h2");
            heading.textContent = item.etiqueta;
            const detail = document.createElement("p");
            detail.textContent = `${item.direccion}, ${item.distrito}, ${item.provincia}, ${item.departamento}`;
            const reference = document.createElement("small");
            const extras = [item.pisoDepartamento, item.referencia].filter(Boolean).join(" · ");
            reference.textContent = extras || "Dirección guardada";
            card.append(heading, detail, reference);
            const acciones = document.createElement("div");
            acciones.className = "direccion-acciones";
            const esPredeterminada = item.predeterminada === true
                || Number(item.predeterminada) === 1;
            if (esPredeterminada) {
                const etiquetaPrincipal = document.createElement("span");
                etiquetaPrincipal.className = "direccion-predeterminada";
                etiquetaPrincipal.textContent = "Dirección predeterminada";
                acciones.append(etiquetaPrincipal);
            } else {
                const botonPredeterminada = document.createElement("button");
                botonPredeterminada.type = "button";
                botonPredeterminada.className = "cuenta-boton cuenta-boton-secundario";
                botonPredeterminada.textContent = "Usar como predeterminada";
                botonPredeterminada.addEventListener("click", async () => {
                    botonPredeterminada.disabled = true;
                    setMensaje("direcciones-mensaje", "Actualizando dirección predeterminada…");
                    try {
                        const respuesta = await fetch(`/api/clientes-web/direcciones/${encodeURIComponent(item.id)}/predeterminada`, {
                            method: "PUT",
                            headers: { Accept: "application/json" }
                        });
                        const resultado = await respuesta.json().catch(() => ({}));
                        if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo actualizar la dirección.");
                        setMensaje("direcciones-mensaje", "Dirección predeterminada actualizada.");
                        await cargarDirecciones();
                    } catch (error) {
                        setMensaje("direcciones-mensaje", error.message || "No pudimos conectar con el backend.", true);
                    } finally {
                        botonPredeterminada.disabled = false;
                    }
                });
                acciones.append(botonPredeterminada);
            }
            card.append(acciones);
            lista.append(card);
        });
    }

    function aplicarPerfilEnFormulario(datos) {
        const idsPerfil = { dni: "perfil-dni", nombre: "perfil-nombre", apellidos: "perfil-apellidos", genero: "perfil-genero", fechaNacimiento: "perfil-nacimiento", telefono: "perfil-telefono" };
        Object.entries(idsPerfil).forEach(([campo, id]) => {
            const input = document.getElementById(id);
            const valor = datos?.[campo];
            if (input && valor != null) input.value = String(valor);
        });
    }

    function setMensaje(id, texto, error = false) {
        const elemento = document.getElementById(id);
        if (!elemento) return;
        elemento.textContent = texto;
        elemento.classList.toggle("error", error);
    }

    function mostrarDashboard(email) {
        sessionStorage.setItem(emailKey, email);
        accessSection.hidden = true;
        dashboard.hidden = false;
        document.getElementById("cliente-email-resumen").textContent = email;
        cargarPerfilDemo();
        cargarDirecciones();
        cargarPedidosDesdeBackend();
        window.dispatchEvent(new CustomEvent("colorin-auth-changed"));
        mostrarVista(location.hash.replace("#", "") || "perfil");
        if (sessionStorage.getItem("colorinCheckoutPendiente") === "1") {
            sessionStorage.removeItem("colorinCheckoutPendiente");
            window.location.href = "checkout.html";
        }
    }

    function mostrarAcceso(tab = "ingresar") {
        sessionStorage.removeItem(emailKey);
        document.getElementById("form-iniciar-sesion")?.reset();
        setMensaje("login-mensaje", "");
        dashboard.hidden = true;
        accessSection.hidden = false;
        elegirPestana(tab);
        window.dispatchEvent(new CustomEvent("colorin-auth-changed"));
    }

    function elegirPestana(nombre) {
        document.querySelectorAll("[data-cuenta-tab]").forEach(tab => {
            const activo = tab.dataset.cuentaTab === nombre;
            tab.classList.toggle("activo", activo);
            tab.setAttribute("aria-selected", String(activo));
        });
        document.querySelectorAll("[data-cuenta-panel]").forEach(panel => {
            panel.hidden = panel.dataset.cuentaPanel !== nombre;
        });
    }

    document.querySelectorAll("[data-cuenta-tab]").forEach(tab => tab.addEventListener("click", () => elegirPestana(tab.dataset.cuentaTab)));
    document.querySelector("[data-ir-registro]")?.addEventListener("click", () => elegirPestana("registrar"));
    document.querySelector("[data-ir-login]")?.addEventListener("click", () => elegirPestana("ingresar"));

    document.getElementById("form-registrar-cliente")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const correo = document.getElementById("registro-correo").value.trim().toLowerCase();
        const password = document.getElementById("registro-password").value;
        const confirmacion = document.getElementById("registro-password-confirmar").value;
        if (password.length < 8) return setMensaje("registro-mensaje", "La contraseña debe tener al menos 8 caracteres.", true);
        if (password !== confirmacion) return setMensaje("registro-mensaje", "Las contraseñas no coinciden. Revísalas y vuelve a intentar.", true);
        if (!document.getElementById("registro-acepta").checked) return setMensaje("registro-mensaje", "Acepta las condiciones para continuar.", true);
        const boton = form.querySelector('button[type="submit"]');
        boton.disabled = true;
        setMensaje("registro-mensaje", "Creando tu cuenta...");
        try {
            const respuesta = await fetch("http://localhost:8080/api/clientes-web/registro", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify({ correo, password })
            });
            const resultado = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) {
                throw new Error(resultado.mensaje || "No se pudo crear la cuenta.");
            }
            form.reset();
            document.getElementById("login-correo").value = correo;
            elegirPestana("ingresar");
            setMensaje("registro-mensaje", "Cuenta creada en la base de datos.");
            setMensaje("login-mensaje", "Tu cuenta ya está creada. El inicio de sesión con base de datos es el siguiente paso.");
        } catch (error) {
            setMensaje("registro-mensaje", error.message || "No pudimos conectar con el backend.", true);
        } finally {
            boton.disabled = false;
        }
    });

    document.getElementById("form-iniciar-sesion")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const correo = document.getElementById("login-correo").value.trim().toLowerCase();
        const password = document.getElementById("login-password").value;
        const boton = form.querySelector('button[type="submit"]');
        boton.disabled = true;
        setMensaje("login-mensaje", "Verificando tus datos...");
        try {
            const respuesta = await fetch("http://localhost:8080/api/clientes-web/login", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify({ correo, password })
            });
            const resultado = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) throw new Error(resultado.mensaje || "Correo o contraseña incorrectos.");
            setMensaje("login-mensaje", "Inicio de sesión correcto.");
            mostrarDashboard(correo);
        } catch (error) {
            setMensaje("login-mensaje", error.message || "No pudimos conectar con el backend.", true);
        } finally {
            boton.disabled = false;
        }
    });

    document.getElementById("btn-olvido")?.addEventListener("click", () => {
        setMensaje("login-mensaje", "La recuperación por correo estará disponible cuando conectemos el backend.");
    });
    document.querySelector("[data-google-demo]")?.addEventListener("click", () => {
        setMensaje("login-mensaje", "El acceso con Google requiere configurar un servicio de autenticación en el backend.");
    });
    document.getElementById("link-condiciones")?.addEventListener("click", event => {
        event.preventDefault();
        document.getElementById("modal-condiciones")?.classList.add("active");
    });
    document.querySelectorAll("[data-close='modal-condiciones']").forEach(button => button.addEventListener("click", () => document.getElementById("modal-condiciones")?.classList.remove("active")));

    function mostrarVista(nombre) {
        const vistaValida = ["perfil", "pedidos", "direcciones"].includes(nombre) ? nombre : "perfil";
        document.querySelectorAll("[data-cliente-vista]").forEach(button => button.classList.toggle("activo", button.dataset.clienteVista === vistaValida));
        document.querySelectorAll("[data-cliente-panel]").forEach(panel => { panel.hidden = panel.dataset.clientePanel !== vistaValida; });
        history.replaceState(null, "", `#${vistaValida}`);
    }
    document.querySelectorAll("[data-cliente-vista]").forEach(button => button.addEventListener("click", () => mostrarVista(button.dataset.clienteVista)));
    document.getElementById("btn-cerrar-sesion")?.addEventListener("click", async () => {
        try {
            await fetch("http://localhost:8080/api/clientes-web/logout", { method: "POST" });
        } catch (error) {
            console.error("No se pudo cerrar la sesión del backend:", error);
        }
        mostrarAcceso("ingresar");
    });

    document.getElementById("btn-buscar-dni")?.addEventListener("click", async () => {
        const dni = document.getElementById("perfil-dni").value.trim();
        if (!/^\d{8}$/.test(dni)) return setMensaje("perfil-dni-mensaje", "Ingresa un DNI de 8 dígitos.", true);
        setMensaje("perfil-dni-mensaje", "Buscando nombres y apellidos…");
        try {
            const response = await fetch(`/api/clientes-web/dni/${encodeURIComponent(dni)}`, { headers: { Accept: "application/json" } });
            if (!response.ok) throw new Error("No se encontraron datos para ese DNI.");
            const cliente = await response.json();
            document.getElementById("perfil-nombre").value = cliente.nombre || "";
            document.getElementById("perfil-apellidos").value = cliente.apellidos || "";
            setMensaje("perfil-dni-mensaje", "Completamos los nombres desde tu ficha de cliente.");
        } catch {
            setMensaje("perfil-dni-mensaje", "No encontramos ese DNI en los clientes registrados o no se pudo consultar. Puedes completar los nombres manualmente.");
        }
    });

    document.getElementById("form-perfil")?.addEventListener("submit", async event => {
        event.preventDefault();
        perfil = Object.fromEntries(new FormData(event.currentTarget).entries());
        try {
            const respuesta = await fetch("/api/clientes-web/perfil", {
                method: "PUT",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(perfil)
            });
            const resultado = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo guardar el perfil.");
            const key = perfilKeyActual();
            if (key) sessionStorage.setItem(key, JSON.stringify(perfil));
            setMensaje("perfil-mensaje", "Perfil guardado en la base de datos.");
        } catch (error) {
            setMensaje("perfil-mensaje", error.message || "No pudimos conectar con el backend.", true);
        }
    });

    const addressForm = document.getElementById("form-direccion");
    document.getElementById("btn-agregar-direccion")?.addEventListener("click", () => { addressForm.hidden = !addressForm.hidden; });
    document.getElementById("btn-cancelar-direccion")?.addEventListener("click", () => { addressForm.reset(); addressForm.hidden = true; });
    addressForm?.addEventListener("submit", async event => {
        event.preventDefault();
        const boton = addressForm.querySelector('button[type="submit"]');
        const item = {
            etiqueta: document.getElementById("direccion-etiqueta").value.trim(),
            direccion: document.getElementById("direccion-detalle").value.trim(),
            departamento: document.getElementById("direccion-departamento").value.trim(),
            provincia: document.getElementById("direccion-provincia").value.trim(),
            distrito: document.getElementById("direccion-distrito").value.trim(),
            pisoDepartamento: document.getElementById("direccion-piso").value.trim(),
            referencia: document.getElementById("direccion-referencia").value.trim()
        };
        boton.disabled = true;
        setMensaje("direcciones-mensaje", "Guardando dirección…");
        try {
            const respuesta = await fetch("/api/clientes-web/direcciones", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(item)
            });
            const resultado = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo guardar la dirección.");
            addressForm.reset();
            addressForm.hidden = true;
            setMensaje("direcciones-mensaje", "Dirección guardada en la base de datos.");
            await cargarDirecciones();
        } catch (error) {
            setMensaje("direcciones-mensaje", error.message || "No pudimos conectar con el backend.", true);
        } finally {
            boton.disabled = false;
        }
    });

    const pedidosVacios = document.getElementById("pedidos-vacios");
    const pedidosLista = document.getElementById("pedidos-lista");
    function mostrarPedidos(filtro = "todos") {
        let pedidos = [];
        try {
            const guardados = JSON.parse(sessionStorage.getItem("colorinPedidosDemo") || "[]");
            pedidos = Array.isArray(guardados) ? guardados : [];
        } catch { pedidos = []; }
        const estadosEnCurso = ["Pendiente", "Confirmado", "En preparación", "En preparacion", "Enviado", "En curso"];
        const filtrados = pedidos.filter(pedido => filtro === "todos"
            || (filtro === "curso" && estadosEnCurso.includes(pedido.estado))
            || (filtro === "entregados" && pedido.estado === "Entregado")
            || (filtro === "cancelados" && pedido.estado === "Cancelado"));
        pedidosLista.replaceChildren();
        filtrados.forEach(pedido => {
            const tarjeta = document.createElement("article");
            tarjeta.className = "pedido-card";
            const encabezado = document.createElement("div");
            encabezado.className = "pedido-card-heading";
            const numero = document.createElement("strong");
            numero.textContent = `Pedido ${pedido.numero || ""}`;
            const estado = document.createElement("span");
            estado.className = "pedido-estado";
            estado.textContent = pedido.estado === "En preparacion"
                ? "En preparación"
                : pedido.estado || "Registrado";
            encabezado.append(numero, estado);
            const fecha = document.createElement("p");
            const fechaPedido = pedido.fecha ? new Date(pedido.fecha) : new Date();
            fecha.textContent = `Realizado el ${fechaPedido.toLocaleDateString("es-PE", { day: "numeric", month: "long", year: "numeric" })}`;
            if (pedido.estadoPago === "Rechazado") {
                const avisoPago = document.createElement("p");
                avisoPago.className = "pedido-pago-rechazado";
                avisoPago.textContent = "Pago rechazado (simulación). No se registró una venta ni se descontó stock.";
                tarjeta.append(avisoPago);
            }
            const productos = document.createElement("ul");
            (Array.isArray(pedido.productos) ? pedido.productos : []).forEach(producto => {
                const linea = document.createElement("li");
                linea.textContent = `${producto.cantidad} × ${producto.nombre}`;
                productos.append(linea);
            });
            const total = document.createElement("strong");
            total.className = "pedido-total";
            total.textContent = `Total: S/ ${Number(pedido.total || 0).toFixed(2)}`;
            tarjeta.append(encabezado, fecha, productos, total);
            if (filtro === "curso" && pedido.estadoPago === "Pendiente" && pedido.id) {
                const botonAprobar = document.createElement("button");
                botonAprobar.type = "button";
                botonAprobar.className = "cuenta-boton pedido-aprobar-demo";
                botonAprobar.textContent = "Simular aprobación por banco";
                const botonRechazar = document.createElement("button");
                botonRechazar.type = "button";
                botonRechazar.className = "cuenta-boton pedido-rechazar-demo";
                botonRechazar.textContent = "Simular pago rechazado";
                botonAprobar.addEventListener("click", async () => {
                    const confirma = window.confirm("Simular la aprobación del pago? Se registrará la venta y se descontará el stock del inventario.");
                    if (!confirma) return;
                    botonAprobar.disabled = true;
                    botonRechazar.disabled = true;
                    setMensaje("pedidos-mensaje", "Confirmando pago y registrando la venta…");
                    try {
                        const respuesta = await fetch(`/api/pedidos-web/${encodeURIComponent(pedido.id)}/simular-aprobacion`, {
                            method: "PUT",
                            headers: { Accept: "application/json" }
                        });
                        const resultado = await respuesta.json().catch(() => ({}));
                        if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo aprobar el pedido.");
                        setMensaje("pedidos-mensaje", `Aprobación simulada. Venta ${resultado.nroComprobante} registrada.`);
                        await cargarPedidosDesdeBackend();
                    } catch (error) {
                        setMensaje("pedidos-mensaje", error.message || "No pudimos conectar con el backend.", true);
                        botonAprobar.disabled = false;
                        botonRechazar.disabled = false;
                    }
                });
                botonRechazar.addEventListener("click", async () => {
                    const confirma = window.confirm("Simular que el banco rechazó el pago? El pedido quedará cancelado y no se creará una venta ni se descontará stock.");
                    if (!confirma) return;
                    botonAprobar.disabled = true;
                    botonRechazar.disabled = true;
                    setMensaje("pedidos-mensaje", "Registrando el rechazo simulado…");
                    try {
                        const respuesta = await fetch(`/api/pedidos-web/${encodeURIComponent(pedido.id)}/simular-rechazo`, {
                            method: "PUT",
                            headers: { Accept: "application/json" }
                        });
                        const resultado = await respuesta.json().catch(() => ({}));
                        if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo registrar el rechazo.");
                        setMensaje("pedidos-mensaje", "Pago rechazado. El pedido pasó a Cancelados; el stock no se modificó.");
                        await cargarPedidosDesdeBackend();
                    } catch (error) {
                        setMensaje("pedidos-mensaje", error.message || "No pudimos conectar con el backend.", true);
                        botonAprobar.disabled = false;
                        botonRechazar.disabled = false;
                    }
                });
                tarjeta.append(botonAprobar, botonRechazar);
            }
            if (filtro === "curso" && pedido.id
                    && ["En preparacion", "Enviado"].includes(pedido.estado)) {
                const esDespacho = pedido.estado === "En preparacion";
                const botonAvance = document.createElement("button");
                botonAvance.type = "button";
                botonAvance.className = "cuenta-boton pedido-avance-demo";
                botonAvance.textContent = esDespacho ? "Simular despacho" : "Simular entrega";
                botonAvance.addEventListener("click", async () => {
                    const accion = esDespacho ? "despacho" : "entrega";
                    const siguiente = esDespacho ? "Enviado" : "Entregado";
                    const confirma = window.confirm(`Simular ${accion} del pedido? Su estado cambiará a ${siguiente}.`);
                    if (!confirma) return;
                    botonAvance.disabled = true;
                    setMensaje("pedidos-mensaje", `Actualizando el pedido a ${siguiente}…`);
                    try {
                        const endpoint = esDespacho ? "simular-despacho" : "simular-entrega";
                        const respuesta = await fetch(`/api/pedidos-web/${encodeURIComponent(pedido.id)}/${endpoint}`, {
                            method: "PUT",
                            headers: { Accept: "application/json" }
                        });
                        const resultado = await respuesta.json().catch(() => ({}));
                        if (!respuesta.ok) throw new Error(resultado.mensaje || "No se pudo actualizar el pedido.");
                        setMensaje("pedidos-mensaje", resultado.mensaje || `Estado actualizado a ${siguiente}.`);
                        await cargarPedidosDesdeBackend();
                    } catch (error) {
                        setMensaje("pedidos-mensaje", error.message || "No pudimos conectar con el backend.", true);
                        botonAvance.disabled = false;
                    }
                });
                const notaDemo = document.createElement("small");
                notaDemo.className = "pedido-avance-nota";
                notaDemo.textContent = "Control simulado para la demostración.";
                tarjeta.append(botonAvance, notaDemo);
            }
            pedidosLista.append(tarjeta);
        });
        pedidosVacios.hidden = filtrados.length > 0;
        pedidosLista.hidden = filtrados.length === 0;
        if (!pedidos.length) {
            pedidosVacios.querySelector("h2").textContent = "Aún no tienes pedidos";
            pedidosVacios.querySelector("p").textContent = "Cuando realices una compra, podrás seguirla desde aquí.";
        } else if (!filtrados.length) {
            pedidosVacios.querySelector("h2").textContent = "No hay pedidos en esta categoría";
            pedidosVacios.querySelector("p").textContent = "Prueba con otro filtro para consultar tus compras.";
        }
    }

    async function cargarPedidosDesdeBackend() {
        setMensaje("pedidos-mensaje", "Cargando tus pedidos…");
        try {
            const respuesta = await fetch("/api/pedidos-web", {
                headers: { Accept: "application/json" }
            });
            const resultado = await respuesta.json().catch(() => ([]));
            if (!respuesta.ok) throw new Error(resultado.mensaje || "No pudimos consultar tus pedidos.");
            sessionStorage.setItem("colorinPedidosDemo", JSON.stringify(resultado));
            setMensaje("pedidos-mensaje", "");
        } catch (error) {
            console.error("No se pudieron cargar los pedidos desde el backend:", error);
            setMensaje("pedidos-mensaje", "No pudimos cargar el historial desde la base de datos. Comprueba que el backend esté actualizado.", true);
        }
        const filtroActivo = document.querySelector("[data-pedido-filtro].activo")?.dataset.pedidoFiltro || "todos";
        mostrarPedidos(filtroActivo);
    }

    document.querySelectorAll("[data-pedido-filtro]").forEach(button => button.addEventListener("click", () => {
        document.querySelectorAll("[data-pedido-filtro]").forEach(item => item.classList.toggle("activo", item === button));
        mostrarPedidos(button.dataset.pedidoFiltro);
    }));

    if (new URLSearchParams(location.search).get("checkout") === "login") {
        document.getElementById("checkout-login-notice").hidden = false;
    }

    const currentEmail = sessionStorage.getItem(emailKey);
    if (currentEmail) {
        fetch("http://localhost:8080/api/clientes-web/sesion", { headers: { Accept: "application/json" } })
            .then(async respuesta => {
                if (!respuesta.ok) throw new Error("La sesión del backend ya no está activa.");
                return respuesta.json();
            })
            .then(datos => mostrarDashboard(datos.correo))
            .catch(() => mostrarAcceso("ingresar"));
    }
});
