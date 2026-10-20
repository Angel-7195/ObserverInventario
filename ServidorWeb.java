import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Version web de la interfaz del inventario.
 *
 * Levanta un servidor HTTP local (sin librerias externas) que usa las mismas
 * clases del patron Observer: Producto, GestorAlertasInventario, etc.
 * El navegador muestra index.html y se comunica con este servidor.
 *
 * Igual que la ventana Swing, el servidor tambien es un observador del
 * producto: cada vez que cambia la existencia, el producto lo notifica y
 * el servidor registra el movimiento en el historial.
 */
public class ServidorWeb implements ObservadorInventario {

    private static final int PUERTO_LOCAL = 8080;
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private Producto producto;
    private GestorAlertasInventario gestorAlertas;
    private final List<String> historial = new ArrayList<>();
    private int alertasRegistradas;

    public ServidorWeb() {
        crearProducto("P001", "Arroz", 10, 5, 20);
    }

    // ------------------------------------------------------------
    // Logica (la misma que usa la ventana Swing)
    // ------------------------------------------------------------

    private synchronized void crearProducto(String codigo, String nombre, int existencia, int minimo, int maximo) {
        producto = new Producto(codigo, nombre, existencia, minimo, maximo);
        gestorAlertas = new GestorAlertasInventario();

        // Registro de observadores (patron Observer).
        producto.agregarObservador(gestorAlertas);
        producto.agregarObservador(this);

        alertasRegistradas = 0;
        historial.clear();
        registrarHistorial("Producto creado: " + nombre + " (" + codigo + "), existencia " + existencia);
    }

    @Override
    public void actualizar(Producto producto) {
        registrarHistorial("  -> existencia actual: " + producto.getExistencia());
        List<AlertaInventario> alertas = gestorAlertas.getAlertas();
        while (alertasRegistradas < alertas.size()) {
            registrarHistorial("  !! ALERTA " + alertas.get(alertasRegistradas++).getTipo());
        }
    }

    private void registrarHistorial(String texto) {
        historial.add("[" + LocalDateTime.now().format(FORMATO_HORA) + "] " + texto);
    }

    private synchronized String procesar(String ruta, Map<String, String> datos) {
        switch (ruta) {
            case "/api/estado":
                return estadoJson(null);

            case "/api/producto": {
                String codigo = datos.getOrDefault("codigo", "").trim();
                String nombre = datos.getOrDefault("nombre", "").trim();
                int existencia = entero(datos.get("existencia"));
                int minimo = entero(datos.get("minimo"));
                int maximo = entero(datos.get("maximo"));
                if (codigo.isEmpty() || nombre.isEmpty()) {
                    return estadoJson("El código y el nombre son obligatorios.");
                }
                if (existencia < 0 || minimo < 0 || maximo < 0) {
                    return estadoJson("Los valores deben ser números positivos.");
                }
                if (minimo >= maximo) {
                    return estadoJson("El nivel mínimo debe ser menor que el nivel máximo.");
                }
                crearProducto(codigo, nombre, existencia, minimo, maximo);
                return estadoJson(null);
            }

            case "/api/entrada": {
                int cantidad = entero(datos.get("cantidad"));
                if (cantidad <= 0) {
                    return estadoJson("La cantidad debe ser mayor que 0.");
                }
                registrarHistorial("Entrada de " + cantidad + " unidades");
                producto.aumentarExistencia(cantidad);
                return estadoJson(null);
            }

            case "/api/salida": {
                int cantidad = entero(datos.get("cantidad"));
                if (cantidad <= 0) {
                    return estadoJson("La cantidad debe ser mayor que 0.");
                }
                if (cantidad > producto.getExistencia()) {
                    return estadoJson("No hay suficiente existencia. Disponible: "
                            + producto.getExistencia() + " unidades.");
                }
                registrarHistorial("Salida de " + cantidad + " unidades");
                producto.disminuirExistencia(cantidad);
                return estadoJson(null);
            }

            case "/api/limpiar-historial":
                historial.clear();
                return estadoJson(null);

            default:
                return null;
        }
    }

    private String estadoJson(String error) {
        String estado;
        if (producto.getExistencia() <= producto.getNivelMinimo()) {
            estado = "REABASTECER";
        } else if (producto.getExistencia() >= producto.getNivelMaximo()) {
            estado = "EXCESO";
        } else {
            estado = "NORMAL";
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"producto\":{")
                .append("\"codigo\":").append(texto(producto.getCodigo())).append(',')
                .append("\"nombre\":").append(texto(producto.getNombre())).append(',')
                .append("\"existencia\":").append(producto.getExistencia()).append(',')
                .append("\"minimo\":").append(producto.getNivelMinimo()).append(',')
                .append("\"maximo\":").append(producto.getNivelMaximo())
                .append("},");
        json.append("\"estado\":").append(texto(estado)).append(',');

        json.append("\"alertas\":[");
        List<AlertaInventario> alertas = gestorAlertas.getAlertas();
        for (int i = alertas.size() - 1; i >= 0; i--) {
            AlertaInventario a = alertas.get(i);
            json.append("{\"tipo\":").append(texto(a.getTipo()))
                    .append(",\"mensaje\":").append(texto(a.getMensaje()))
                    .append(",\"fecha\":").append(texto(a.getFecha().format(FORMATO_FECHA)))
                    .append('}');
            if (i > 0) {
                json.append(',');
            }
        }
        json.append("],");

        json.append("\"historial\":[");
        for (int i = 0; i < historial.size(); i++) {
            json.append(texto(historial.get(i)));
            if (i < historial.size() - 1) {
                json.append(',');
            }
        }
        json.append("],");

        json.append("\"error\":").append(error == null ? "null" : texto(error));
        json.append('}');
        return json.toString();
    }

    // ------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------

    private static String texto(String s) {
        StringBuilder r = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': r.append("\\\""); break;
                case '\\': r.append("\\\\"); break;
                case '\n': r.append("\\n"); break;
                case '\r': r.append("\\r"); break;
                case '\t': r.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        r.append(String.format("\\u%04x", (int) c));
                    } else {
                        r.append(c);
                    }
            }
        }
        return r.append('"').toString();
    }

    private static int entero(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static Map<String, String> leerDatos(HttpExchange intercambio) throws IOException {
        String cuerpo;
        try (InputStream entrada = intercambio.getRequestBody()) {
            cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> datos = new HashMap<>();
        for (String par : cuerpo.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                datos.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return datos;
    }

    private static void responder(HttpExchange intercambio, int codigo, String tipo, byte[] cuerpo) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    // ------------------------------------------------------------
    // Servidor
    // ------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        ServidorWeb app = new ServidorWeb();
        // En un hosting (como Render) el puerto llega en la variable PORT y hay
        // que escuchar en todas las interfaces. En tu computador se usa localhost:8080.
        String puertoHosting = System.getenv("PORT");
        boolean enHosting = puertoHosting != null;
        int puerto = enHosting ? Integer.parseInt(puertoHosting) : PUERTO_LOCAL;
        String host = enHosting ? "0.0.0.0" : "localhost";
        HttpServer servidor = HttpServer.create(new InetSocketAddress(host, puerto), 0);

        servidor.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();

            if (ruta.equals("/") || ruta.equals("/index.html")) {
                Path pagina = Path.of("index.html");
                if (Files.exists(pagina)) {
                    responder(intercambio, 200, "text/html; charset=utf-8", Files.readAllBytes(pagina));
                } else {
                    responder(intercambio, 404, "text/plain; charset=utf-8",
                            "No se encontró index.html. Ejecuta el servidor desde la carpeta del proyecto."
                                    .getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (ruta.startsWith("/api/")) {
                Map<String, String> datos = intercambio.getRequestMethod().equals("POST")
                        ? leerDatos(intercambio)
                        : new HashMap<>();
                String json = app.procesar(ruta, datos);
                if (json != null) {
                    responder(intercambio, 200, "application/json; charset=utf-8",
                            json.getBytes(StandardCharsets.UTF_8));
                    return;
                }
            }

            responder(intercambio, 404, "text/plain; charset=utf-8",
                    "No encontrado".getBytes(StandardCharsets.UTF_8));
        });

        servidor.start();
        String direccion = "http://localhost:" + puerto;
        System.out.println("Servidor iniciado en " + direccion);
        System.out.println("Presiona Ctrl+C para detenerlo.");

        if (enHosting) {
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(direccion));
            }
        } catch (Exception e) {
            System.out.println("Abre " + direccion + " en tu navegador.");
        }
    }
}
