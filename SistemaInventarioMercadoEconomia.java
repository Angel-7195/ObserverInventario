import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.List;

/**
 * SISTEMA DE GESTIÓN Y CONTROL DE INVENTARIO - MERCADO LA ECONOMÍA
 * Institución Universitaria ITM - Diseño de Software (2026)
 *
 * Implementación completa en Java Swing (Archivo único autocontenido)
 * Incluye:
 * - Autenticación y control de roles con permisos (CU11, CU09, RNF26, RNF28)
 * - Contraseñas almacenadas cifradas con SHA-256 + sal (RNF27)
 * - Gestión de Productos: Registrar, Editar, Desactivar, Consultar (CU01 - CU04)
 * - Importación de productos desde CSV (RNF14)
 * - Movimientos de Mercancía: Entradas y Salidas con validación transaccional
 *   y reversión ante fallas (CU10, CU05, RNF23) mediante ControladorMovimiento
 * - Historial de Movimientos con filtros de producto, tipo y rango de fechas (CU06)
 * - Control de Niveles de Stock: Bajo / Normal / Exceso (CU07)
 * - Patrón de Diseño Observer para Alertas Automáticas en Tiempo Real (CU08),
 *   más verificación periódica mediante ControladorAlertas
 * - Exportación de reportes a Excel (CSV) y PDF (RNF13)
 * - Persistencia automática, respaldo y restauración de datos (RNF24, RNF25)
 * - Registro de auditoría de acciones (RNF29)
 *
 * Compilar:  javac -encoding UTF-8 SistemaInventarioMercadoEconomia.java
 * Ejecutar:  java SistemaInventarioMercadoEconomia
 */
public class SistemaInventarioMercadoEconomia {

    // =========================================================================
    // PALETA DE COLORES Y ESTILOS VISUALES (UI MODERNA EN SWING)
    // =========================================================================
    public static final Color COLOR_PRIMARIO = new Color(20, 83, 45);      // Verde Esmeralda Oscuro (Mercado)
    public static final Color COLOR_SECUNDARIO = new Color(22, 163, 74);   // Verde Fresco
    public static final Color COLOR_FONDO = new Color(248, 250, 252);      // Gris muy claro
    public static final Color COLOR_PANEL = Color.WHITE;
    public static final Color COLOR_TEXTO = new Color(30, 41, 59);
    public static final Color COLOR_ALERTA_BAJO = new Color(220, 38, 38);  // Rojo
    public static final Color COLOR_ALERTA_EXCESO = new Color(217, 119, 6);// Ámbar / Naranja
    public static final Color COLOR_NORMAL = new Color(21, 128, 61);       // Verde

    public static final Font FUENTE_TITULO = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FUENTE_SUBTITULO = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FUENTE_NORMAL = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FUENTE_NEGRITA = new Font("Segoe UI", Font.BOLD, 13);

    // =========================================================================
    // PUNTO DE ENTRADA DE LA APLICACIÓN
    // =========================================================================
    public static void main(String[] args) {
        try {
            // Intentar aplicar Nimbus o el Look & Feel nativo del sistema operativo
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            // Carga los datos guardados (RNF24/RNF25) o crea los datos de ejemplo
            RepositorioSistema repositorio = RepositorioSistema.cargarOCrear();
            VentanaLogin login = new VentanaLogin(repositorio);
            login.setVisible(true);
        });
    }

    // =========================================================================
    // 1. PATRÓN DE DISEÑO OBSERVER (SEGÚN DIAGRAMA DE CLASES PÁG. 28-30)
    // =========================================================================

    /**
     * Interfaz ObservadorInventario.
     * Extiende Serializable para que el gestor de alertas se guarde junto con
     * los productos que lo tienen registrado como observador (RNF24).
     */
    public interface ObservadorInventario extends Serializable {
        void actualizar(Producto producto);
    }

    /**
     * Clase AlertaInventario (Representa las alertas generadas por umbrales).
     * Estados según el diagrama de estados (pág. 36):
     * Activa: Reabastecimiento, Activa: Exceso e Inactiva.
     */
    public static class AlertaInventario implements Serializable {
        private static final long serialVersionUID = 1L;
        private static int contadorId = 1;
        private int idAlerta;
        private String codigoProducto;
        private String nombreProducto;
        private String tipo; // "REABASTECIMIENTO" o "EXCESO"
        private String mensaje;
        private LocalDateTime fecha;
        private boolean activa;

        public AlertaInventario(Producto producto, String tipo, String mensaje) {
            this.idAlerta = contadorId++;
            this.codigoProducto = producto.getCodigo();
            this.nombreProducto = producto.getNombre();
            this.tipo = tipo;
            this.mensaje = mensaje;
            this.fecha = LocalDateTime.now();
            this.activa = true;
        }

        /** generar(): crea una alerta nueva (diagrama de clases y secuencia CU08). */
        public static AlertaInventario generar(Producto producto, String tipo, String mensaje) {
            return new AlertaInventario(producto, tipo, mensaje);
        }

        /**
         * actualizar(): actualiza la alerta activa en lugar de duplicarla (CU08).
         * La fecha solo cambia si cambió el motivo o el mensaje, para que la
         * verificación periódica no "refresque" alertas que siguen iguales.
         */
        public void actualizar(Producto producto, String nuevoTipo, String nuevoMensaje) {
            boolean cambio = !nuevoTipo.equals(tipo) || !nuevoMensaje.equals(mensaje);
            this.nombreProducto = producto.getNombre();
            this.tipo = nuevoTipo;
            this.mensaje = nuevoMensaje;
            if (cambio) this.fecha = LocalDateTime.now();
            this.activa = true;
        }

        /** desactivar(): pasa la alerta al estado Inactiva. */
        public void desactivar() {
            this.activa = false;
        }

        public String mostrarAlerta() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            return String.format("[%s] %s (%s) - %s [%s]",
                    tipo, nombreProducto, codigoProducto, mensaje, fecha.format(fmt));
        }

        /** Mantiene la numeración consecutiva al cargar datos guardados. */
        static void ajustarContador(int siguiente) {
            if (siguiente > contadorId) contadorId = siguiente;
        }

        // Getters
        public int getIdAlerta() { return idAlerta; }
        public String getCodigoProducto() { return codigoProducto; }
        public String getNombreProducto() { return nombreProducto; }
        public String getTipo() { return tipo; }
        public String getMensaje() { return mensaje; }
        public LocalDateTime getFecha() { return fecha; }
        public boolean isActiva() { return activa; }
    }

    /**
     * Observador Concreto: GestorAlertasInventario
     * Implementa la lógica del Diagrama de Actividades CU08 y Diagrama de Estados (Pág. 35-36)
     */
    public static class GestorAlertasInventario implements ObservadorInventario {
        private static final long serialVersionUID = 1L;
        private List<AlertaInventario> alertas = new ArrayList<>();
        // Los listeners de la interfaz no se guardan en disco
        private transient List<Runnable> listenersUI;

        private List<Runnable> listeners() {
            if (listenersUI == null) listenersUI = new ArrayList<>();
            return listenersUI;
        }

        public void agregarListenerUI(Runnable listener) {
            listeners().add(listener);
        }

        public void eliminarListenerUI(Runnable listener) {
            listeners().remove(listener);
        }

        private void notificarUI() {
            for (Runnable r : new ArrayList<>(listeners())) {
                r.run();
            }
        }

        @Override
        public void actualizar(Producto producto) {
            if (!producto.isActivo()) {
                desactivarAlertaDeProducto(producto.getCodigo());
                notificarUI();
                return;
            }

            int exist = producto.getExistenciaActual();
            int min = producto.getNivelMinimo();
            int max = producto.getNivelMaximo();

            // Verificar si cruza umbrales (CU08)
            if (exist <= min) {
                String msg = String.format("Stock crítico: %d %s (Mínimo requerido: %d)",
                        exist, producto.getUnidadMedida(), min);
                generarOActualizarAlerta(producto, "REABASTECIMIENTO", msg);
            } else if (exist >= max) {
                String msg = String.format("Sobre-stock: %d %s (Máximo permitido: %d)",
                        exist, producto.getUnidadMedida(), max);
                generarOActualizarAlerta(producto, "EXCESO", msg);
            } else {
                // Nivel NORMAL: desactivar alerta previa si existe (Diagrama de estados pág. 36)
                desactivarAlertaDeProducto(producto.getCodigo());
            }
            notificarUI();
        }

        public void generarOActualizarAlerta(Producto producto, String tipo, String mensaje) {
            // Evitar duplicados: si ya existe alerta activa para el producto, actualizarla
            for (AlertaInventario a : alertas) {
                if (a.getCodigoProducto().equals(producto.getCodigo()) && a.isActiva()) {
                    a.actualizar(producto, tipo, mensaje);
                    return;
                }
            }
            // Generar nueva alerta
            alertas.add(AlertaInventario.generar(producto, tipo, mensaje));
        }

        private void desactivarAlertaDeProducto(String codigoProducto) {
            for (AlertaInventario a : alertas) {
                if (a.getCodigoProducto().equals(codigoProducto) && a.isActiva()) {
                    a.desactivar();
                }
            }
        }

        public List<AlertaInventario> getAlertasActivas() {
            List<AlertaInventario> activas = new ArrayList<>();
            for (AlertaInventario a : alertas) {
                if (a.isActiva()) activas.add(a);
            }
            return activas;
        }

        public AlertaInventario getAlertaActivaDe(String codigoProducto) {
            for (AlertaInventario a : alertas) {
                if (a.isActiva() && a.getCodigoProducto().equals(codigoProducto)) return a;
            }
            return null;
        }

        public List<AlertaInventario> getTodasLasAlertas() {
            return alertas;
        }
    }

    // =========================================================================
    // 2. MODELO DEL DOMINIO (SEGÚN DIAGRAMA DE CLASES PÁG. 27)
    // =========================================================================

    /**
     * Utilidad de seguridad: las contraseñas se guardan como hash SHA-256 con
     * sal aleatoria; nunca en texto plano (RNF27).
     */
    public static final class Seguridad {
        private static final SecureRandom RANDOM = new SecureRandom();

        private Seguridad() {}

        static String generarSal() {
            byte[] sal = new byte[16];
            RANDOM.nextBytes(sal);
            return Base64.getEncoder().encodeToString(sal);
        }

        static String hash(String contrasena, String sal) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(Base64.getDecoder().decode(sal));
                byte[] digest = md.digest(contrasena.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(digest);
            } catch (Exception e) {
                throw new IllegalStateException("No fue posible cifrar la contraseña.", e);
            }
        }
    }

    public static class Rol implements Serializable {
        private static final long serialVersionUID = 1L;
        // Permisos (una por módulo del sistema) - RF10 / RNF28
        public static final String P_DASHBOARD = "DASHBOARD";
        public static final String P_PRODUCTOS = "PRODUCTOS";
        public static final String P_MOVIMIENTOS = "MOVIMIENTOS";
        public static final String P_HISTORIAL = "HISTORIAL";
        public static final String P_ALERTAS = "ALERTAS";
        public static final String P_USUARIOS = "USUARIOS";

        private int idRol;
        private String nombre; // "ADMINISTRADOR" o "ENCARGADO DE INVENTARIO"
        private String descripcion;
        private Set<String> permisos;

        public Rol(int idRol, String nombre, String descripcion, String... permisos) {
            this.idRol = idRol;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.permisos = new LinkedHashSet<>(Arrays.asList(permisos));
        }

        /** obtenerPermisos() según el diagrama de clases. */
        public String obtenerPermisos() {
            return String.join(", ", permisos);
        }

        public boolean tienePermiso(String permiso) {
            return permisos.contains(permiso);
        }

        public int getIdRol() { return idRol; }
        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }

        @Override
        public String toString() { return nombre; }
    }

    public static class Usuario implements Serializable {
        private static final long serialVersionUID = 1L;
        private int idUsuario;
        private String nombreUsuario;
        private String nombresCompletos;
        private String hashContrasena;
        private String sal;
        private Rol rol;
        private boolean activo;

        public Usuario(int idUsuario, String nombreUsuario, String nombresCompletos, String contrasena, Rol rol, boolean activo) {
            this.idUsuario = idUsuario;
            this.nombreUsuario = nombreUsuario;
            this.nombresCompletos = nombresCompletos;
            this.rol = rol;
            this.activo = activo;
            setContrasena(contrasena);
        }

        /** iniciarSesion(nombreUsuario, contraseña) según el diagrama de clases (CU11). */
        public boolean iniciarSesion(String usuario, String contrasena) {
            return activo
                    && nombreUsuario.equalsIgnoreCase(usuario.trim())
                    && verificarContrasena(contrasena);
        }

        public boolean verificarContrasena(String contrasena) {
            return Seguridad.hash(contrasena, sal).equals(hashContrasena);
        }

        /** Cifra y guarda la contraseña (RNF27). */
        public void setContrasena(String contrasena) {
            this.sal = Seguridad.generarSal();
            this.hashContrasena = Seguridad.hash(contrasena, sal);
        }

        public int getIdUsuario() { return idUsuario; }
        public String getNombreUsuario() { return nombreUsuario; }
        public String getNombresCompletos() { return nombresCompletos; }
        public void setNombresCompletos(String nombresCompletos) { this.nombresCompletos = nombresCompletos; }
        public Rol getRol() { return rol; }
        public void setRol(Rol rol) { this.rol = rol; }
        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }
    }

    /** Clase Categoria (diagrama de clases pág. 27). */
    public static class Categoria implements Serializable {
        private static final long serialVersionUID = 1L;
        private int idCategoria;
        private String nombre;

        public Categoria(int idCategoria, String nombre) {
            this.idCategoria = idCategoria;
            this.nombre = nombre;
        }

        public int getIdCategoria() { return idCategoria; }
        public String getNombre() { return nombre; }

        @Override
        public String toString() { return nombre; }
    }

    /** Clase Proveedor (diagrama de clases pág. 27): suministra las entradas. */
    public static class Proveedor implements Serializable {
        private static final long serialVersionUID = 1L;
        private int idProveedor;
        private String nombre;

        public Proveedor(int idProveedor, String nombre) {
            this.idProveedor = idProveedor;
            this.nombre = nombre;
        }

        public int getIdProveedor() { return idProveedor; }
        public String getNombre() { return nombre; }

        @Override
        public String toString() { return nombre; }
    }

    /**
     * Clase Producto (Sujeto Observado en el patrón Observer)
     */
    public static class Producto implements Serializable {
        private static final long serialVersionUID = 1L;
        private String codigo;
        private String nombre;
        private Categoria categoria;
        private String unidadMedida;
        private double precio;
        private int existenciaActual;
        private int nivelMinimo;
        private int nivelMaximo;
        private boolean activo;
        private List<ObservadorInventario> observadores = new ArrayList<>();

        public Producto(String codigo, String nombre, Categoria categoria, String unidadMedida,
                        double precio, int existenciaInicial, int nivelMinimo, int nivelMaximo) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.categoria = categoria;
            this.unidadMedida = unidadMedida;
            this.precio = precio;
            this.existenciaActual = existenciaInicial;
            this.nivelMinimo = nivelMinimo;
            this.nivelMaximo = nivelMaximo;
            this.activo = true;
        }

        // Métodos del Patrón Observer
        public void agregarObservador(ObservadorInventario observador) {
            if (!observadores.contains(observador)) {
                observadores.add(observador);
            }
        }

        public void eliminarObservador(ObservadorInventario observador) {
            observadores.remove(observador);
        }

        public void notificarObservadores() {
            for (ObservadorInventario obs : observadores) {
                obs.actualizar(this);
            }
        }

        // Operaciones de negocio
        public void aumentarExistencia(int cantidad) {
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
            this.existenciaActual += cantidad;
            notificarObservadores();
        }

        public void disminuirExistencia(int cantidad) {
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
            if (cantidad > this.existenciaActual) throw new IllegalArgumentException("Stock insuficiente.");
            this.existenciaActual -= cantidad;
            notificarObservadores();
        }

        /** consultarStock() según el diagrama de clases y de secuencia. */
        public int consultarStock() {
            return existenciaActual;
        }

        /** Devuelve la existencia a su valor anterior cuando falla un movimiento (RNF23). */
        void restaurarExistencia(int existenciaAnterior) {
            this.existenciaActual = existenciaAnterior;
            try {
                notificarObservadores();
            } catch (RuntimeException ex) {
                // La existencia ya quedó restaurada aunque un observador falle
                System.err.println("Aviso: un observador falló al restaurar la existencia: " + ex.getMessage());
            }
        }

        /**
         * Estados según Diagrama de Estados (Pág. 36): Inactivo, Stock bajo, Exceso, Normal
         */
        public String verificarNivelInventario() {
            if (!activo) return "INACTIVO";
            if (existenciaActual <= nivelMinimo) return "STOCK BAJO";
            if (existenciaActual >= nivelMaximo) return "EXCESO";
            return "NORMAL";
        }

        public void desactivar() {
            this.activo = false;
            notificarObservadores();
        }

        public void activar() {
            this.activo = true;
            notificarObservadores();
        }

        /** Cambia ambos umbrales a la vez y notifica una sola vez (evita alertas transitorias). */
        public void setNiveles(int nivelMinimo, int nivelMaximo) {
            if (nivelMinimo < 0 || nivelMaximo <= 0 || nivelMinimo >= nivelMaximo) {
                throw new IllegalArgumentException("El nivel mínimo debe ser estrictamente menor al nivel máximo.");
            }
            this.nivelMinimo = nivelMinimo;
            this.nivelMaximo = nivelMaximo;
            notificarObservadores();
        }

        // Getters y Setters
        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getCategoria() { return categoria == null ? "" : categoria.getNombre(); }
        public Categoria getCategoriaObj() { return categoria; }
        public void setCategoria(Categoria categoria) { this.categoria = categoria; }
        public String getUnidadMedida() { return unidadMedida; }
        public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
        public double getPrecio() { return precio; }
        public void setPrecio(double precio) { this.precio = precio; }
        public int getExistenciaActual() { return existenciaActual; }
        public int getNivelMinimo() { return nivelMinimo; }
        public int getNivelMaximo() { return nivelMaximo; }
        public boolean isActivo() { return activo; }

        @Override
        public String toString() {
            return codigo + " - " + nombre + " (Disp: " + existenciaActual + " " + unidadMedida + ")";
        }
    }

    /**
     * Clase Abstracta MovimientoInventario (Segun Diagrama de Clases Pág. 27)
     */
    public static abstract class MovimientoInventario implements Serializable {
        private static final long serialVersionUID = 1L;
        private static int secuencia = 1000;
        protected int idMovimiento;
        protected LocalDateTime fecha;
        protected Producto producto;
        protected int cantidad;
        protected int existenciaResultante;
        protected Usuario responsable;

        public MovimientoInventario(Producto producto, int cantidad, LocalDateTime fecha, Usuario responsable) {
            this.idMovimiento = ++secuencia;
            this.fecha = fecha;
            this.producto = producto;
            this.cantidad = cantidad;
            this.existenciaResultante = producto.consultarStock();
            this.responsable = responsable;
        }

        /** registrar(): agrega el movimiento al historial (diagramas de secuencia CU05/CU10). */
        public boolean registrar(List<MovimientoInventario> historial) {
            historial.add(0, this);
            return true;
        }

        public abstract String getTipoMovimiento();
        public abstract String getDetalle();

        /** Mantiene la numeración consecutiva al cargar datos guardados. */
        static void ajustarSecuencia(int ultimoId) {
            if (ultimoId > secuencia) secuencia = ultimoId;
        }

        public int getIdMovimiento() { return idMovimiento; }
        public LocalDateTime getFecha() { return fecha; }
        public Producto getProducto() { return producto; }
        public int getCantidad() { return cantidad; }
        public int getExistenciaResultante() { return existenciaResultante; }
        public Usuario getResponsable() { return responsable; }
    }

    public static class Entrada extends MovimientoInventario {
        private static final long serialVersionUID = 1L;
        private Proveedor proveedor;
        private String numeroDocumento;

        public Entrada(Producto producto, int cantidad, LocalDateTime fecha, Usuario responsable, Proveedor proveedor, String numeroDocumento) {
            super(producto, cantidad, fecha, responsable);
            this.proveedor = proveedor;
            this.numeroDocumento = numeroDocumento == null ? "" : numeroDocumento;
        }

        /** incrementarExistencia() según el diagrama de clases y la secuencia CU10. */
        public void incrementarExistencia() {
            producto.aumentarExistencia(cantidad);
            existenciaResultante = producto.consultarStock();
        }

        @Override
        public String getTipoMovimiento() { return "ENTRADA"; }

        @Override
        public String getDetalle() {
            return "Proveedor: " + proveedor.getNombre() + (numeroDocumento.isEmpty() ? "" : " | Doc: " + numeroDocumento);
        }

        public Proveedor getProveedor() { return proveedor; }
        public String getNumeroDocumento() { return numeroDocumento; }
    }

    public static class Salida extends MovimientoInventario {
        private static final long serialVersionUID = 1L;
        private String motivo;

        public Salida(Producto producto, int cantidad, LocalDateTime fecha, Usuario responsable, String motivo) {
            super(producto, cantidad, fecha, responsable);
            this.motivo = motivo;
        }

        /** validarExistencia(): 0 < cantidad <= existencia (secuencia CU05). */
        public boolean validarExistencia() {
            int existenciaActual = producto.consultarStock();
            return cantidad > 0 && cantidad <= existenciaActual;
        }

        /** descontarExistencia() según el diagrama de clases y la secuencia CU05. */
        public void descontarExistencia() {
            if (!validarExistencia()) {
                throw new IllegalArgumentException("La cantidad supera la existencia disponible.");
            }
            producto.disminuirExistencia(cantidad);
            existenciaResultante = producto.consultarStock();
        }

        @Override
        public String getTipoMovimiento() { return "SALIDA"; }

        @Override
        public String getDetalle() {
            return "Motivo: " + motivo;
        }

        public String getMotivo() { return motivo; }
    }

    /**
     * Registro de Auditoría (RNF29 - Trazabilidad de acciones)
     */
    public static class RegistroAccion implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime fecha;
        private String usuario;
        private String modulo;
        private String accion;

        public RegistroAccion(String usuario, String modulo, String accion) {
            this.fecha = LocalDateTime.now();
            this.usuario = usuario;
            this.modulo = modulo;
            this.accion = accion;
        }

        public LocalDateTime getFecha() { return fecha; }
        public String getUsuario() { return usuario; }
        public String getModulo() { return modulo; }
        public String getAccion() { return accion; }
    }

    /** Se lanza cuando el código buscado no corresponde a ningún producto (CU10: ofrecer CU01). */
    public static class ProductoNoEncontradoException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String codigo;

        public ProductoNoEncontradoException(String codigo) {
            super("No existe ningún producto con el código: " + codigo);
            this.codigo = codigo;
        }

        public String getCodigo() { return codigo; }
    }

    /** Resultado de una importación de productos desde archivo (RNF14). */
    public static class ResultadoImportacion {
        public int importados = 0;
        public List<String> errores = new ArrayList<>();
    }

    // =========================================================================
    // 3. CONTROLADORES (SEGÚN DIAGRAMAS DE SECUENCIA PÁG. 37-39)
    // =========================================================================

    /**
     * ControladorMovimiento: coordina CU10 y CU05 tal como en los diagramas de
     * secuencia. Descontar/incrementar y registrar se aplican como una sola
     * transacción: si una parte falla, se revierte todo (RNF23).
     */
    public static class ControladorMovimiento {
        private final RepositorioSistema repo;

        public ControladorMovimiento(RepositorioSistema repo) {
            this.repo = repo;
        }

        /** Localizar el producto (CU05 y CU10 «include» CU04). */
        public Producto localizarProducto(String codigo) {
            if (codigo == null || codigo.trim().isEmpty()) {
                throw new IllegalArgumentException("Ingrese o seleccione el código del producto.");
            }
            Producto p = repo.buscarProductoPorCodigo(codigo);
            if (p == null) throw new ProductoNoEncontradoException(codigo.trim().toUpperCase());
            return p;
        }

        private LocalDateTime validarFecha(LocalDate fecha) {
            if (fecha == null) throw new IllegalArgumentException("Ingrese la fecha del movimiento.");
            if (fecha.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("La fecha del movimiento no puede ser posterior a hoy.");
            }
            return fecha.isEqual(LocalDate.now()) ? LocalDateTime.now() : fecha.atTime(LocalTime.now());
        }

        /** CU10 - Registrar entrada de mercancía. */
        public synchronized Entrada registrarEntrada(String codigo, int cantidad, String nombreProveedor,
                                                     String documento, LocalDate fecha, Usuario usuario) {
            Producto p = localizarProducto(codigo);
            if (!p.isActivo()) {
                throw new IllegalArgumentException("El producto " + p.getCodigo() + " está inactivo y no admite movimientos.");
            }
            if (cantidad <= 0) throw new IllegalArgumentException("La cantidad recibida debe ser mayor a 0.");
            if (nombreProveedor == null || nombreProveedor.trim().isEmpty()) {
                throw new IllegalArgumentException("Debe ingresar el nombre del proveedor.");
            }
            LocalDateTime fechaMovimiento = validarFecha(fecha);
            Proveedor proveedor = repo.obtenerOCrearProveedor(nombreProveedor.trim());

            int existenciaAnterior = p.consultarStock();
            Entrada entrada = new Entrada(p, cantidad, fechaMovimiento, usuario, proveedor, documento == null ? "" : documento.trim());
            try {
                entrada.incrementarExistencia();
                if (!entrada.registrar(repo.getHistorialMovimientos())) {
                    throw new IllegalStateException("No se pudo registrar el movimiento en el historial.");
                }
            } catch (RuntimeException ex) {
                // Regla de negocio: ambas operaciones se aplican juntas; si falla una, no se aplica ninguna
                repo.getHistorialMovimientos().remove(entrada);
                if (p.consultarStock() != existenciaAnterior) p.restaurarExistencia(existenciaAnterior);
                throw new IllegalStateException("Falló el registro de la entrada; no se aplicó el incremento. Detalle: " + ex.getMessage(), ex);
            }
            repo.registrarLog(usuario.getNombreUsuario(), "Entradas",
                    "Entrada +" + cantidad + " de " + p.getNombre() + " (Nuevo stock: " + p.getExistenciaActual() + ")");
            return entrada;
        }

        /** CU05 - Registrar salida de productos. */
        public synchronized Salida registrarSalida(String codigo, int cantidad, String motivo,
                                                   LocalDate fecha, Usuario usuario) {
            Producto p = localizarProducto(codigo);
            if (!p.isActivo()) {
                throw new IllegalArgumentException("El producto " + p.getCodigo() + " está inactivo y no admite movimientos.");
            }
            if (p.consultarStock() <= 0) {
                throw new IllegalArgumentException("El producto " + p.getNombre() + " no tiene existencias disponibles.");
            }
            LocalDateTime fechaMovimiento = validarFecha(fecha);

            int existenciaAnterior = p.consultarStock();
            Salida salida = new Salida(p, cantidad, fechaMovimiento, usuario, motivo);
            if (!salida.validarExistencia()) {
                throw new IllegalArgumentException(cantidad <= 0
                        ? "La cantidad debe ser mayor a 0."
                        : "La cantidad (" + cantidad + ") supera la existencia disponible (" + existenciaAnterior + ").");
            }
            try {
                salida.descontarExistencia();
                if (!salida.registrar(repo.getHistorialMovimientos())) {
                    throw new IllegalStateException("No se pudo registrar el movimiento en el historial.");
                }
            } catch (RuntimeException ex) {
                repo.getHistorialMovimientos().remove(salida);
                if (p.consultarStock() != existenciaAnterior) p.restaurarExistencia(existenciaAnterior);
                throw new IllegalStateException("Falló el registro de la salida; no se aplicó el descuento. Detalle: " + ex.getMessage(), ex);
            }
            repo.registrarLog(usuario.getNombreUsuario(), "Salidas",
                    "Salida -" + cantidad + " de " + p.getNombre() + " (Nuevo stock: " + p.getExistenciaActual() + ")");
            return salida;
        }
    }

    /**
     * ControladorAlertas (secuencia CU08): verifica los niveles de cada
     * producto activo tras cada movimiento (vía Observer) y de forma periódica.
     */
    public static class ControladorAlertas {
        private final RepositorioSistema repo;

        public ControladorAlertas(RepositorioSistema repo) {
            this.repo = repo;
        }

        /** verificarNiveles(): loop [para cada producto activo] -> notificar al observador. */
        public synchronized void verificarNiveles() {
            for (Producto p : new ArrayList<>(repo.getProductos())) {
                if (p.isActivo()) p.notificarObservadores();
            }
        }

        /** mostrarAlertasActivas() (producto y motivo). */
        public List<AlertaInventario> mostrarAlertasActivas() {
            return repo.getGestorAlertas().getAlertasActivas();
        }
    }

    // =========================================================================
    // 4. REPOSITORIO CENTRAL, PERSISTENCIA Y DATOS DE PRUEBA (MERCADO LA ECONOMÍA)
    // =========================================================================
    public static class RepositorioSistema implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final String ARCHIVO_DATOS = "inventario_datos.dat";
        public static final String CARPETA_RESPALDOS = "respaldos";

        private List<Rol> roles = new ArrayList<>();
        private List<Usuario> usuarios = new ArrayList<>();
        private List<Categoria> categorias = new ArrayList<>();
        private List<Proveedor> proveedores = new ArrayList<>();
        private List<Producto> productos = new ArrayList<>();
        private List<MovimientoInventario> historialMovimientos = new ArrayList<>();
        private List<RegistroAccion> logsAuditoria = new ArrayList<>();
        private GestorAlertasInventario gestorAlertas = new GestorAlertasInventario();

        private transient ControladorMovimiento controladorMovimiento;
        private transient ControladorAlertas controladorAlertas;
        private transient boolean persistenciaActiva;

        public RepositorioSistema() {
            inicializarDatos();
            persistenciaActiva = true;
            guardar();
        }

        private void inicializarDatos() {
            // Roles con sus permisos (RF10 / RNF28)
            Rol rolAdmin = new Rol(1, "ADMINISTRADOR", "Acceso total: Gestión de usuarios, roles, auditoría e inventario.",
                    Rol.P_DASHBOARD, Rol.P_PRODUCTOS, Rol.P_MOVIMIENTOS, Rol.P_HISTORIAL, Rol.P_ALERTAS, Rol.P_USUARIOS);
            Rol rolEncargado = new Rol(2, "ENCARGADO DE INVENTARIO", "Gestión de productos, entradas, salidas, consultas y alertas.",
                    Rol.P_DASHBOARD, Rol.P_PRODUCTOS, Rol.P_MOVIMIENTOS, Rol.P_HISTORIAL, Rol.P_ALERTAS);
            roles.add(rolAdmin);
            roles.add(rolEncargado);

            // Usuarios por defecto (las contraseñas se guardan cifradas)
            usuarios.add(new Usuario(1, "admin", "Carlos Mendoza (Administrador)", "1234", rolAdmin, true));
            usuarios.add(new Usuario(2, "encargado", "Laura Gómez (Encargada)", "1234", rolEncargado, true));
            usuarios.add(new Usuario(3, "julian", "Julián Bermeo", "1234", rolEncargado, true));

            // Categorías
            for (String c : new String[]{"Granos y Cereales", "Lácteos", "Aceites y Grasas", "Bebidas",
                    "Aseo Hogar", "Endulzantes", "Huevos y Lácteos", "Otros"}) {
                obtenerOCrearCategoria(c);
            }

            // Proveedores
            obtenerOCrearProveedor("Distribuidora Diana S.A.");
            obtenerOCrearProveedor("Colanta");
            obtenerOCrearProveedor("Grasas y Aceites del Valle");

            // Productos típicos de la canasta familiar - Mercado La Economía
            registrarProductoInicial(new Producto("P001", "Arroz Diana 500g", obtenerOCrearCategoria("Granos y Cereales"), "Unidad", 2400, 45, 15, 80));
            registrarProductoInicial(new Producto("P002", "Aceite Vegetal 1000ml", obtenerOCrearCategoria("Aceites y Grasas"), "Botella", 8900, 8, 10, 40)); // Stock bajo inicial
            registrarProductoInicial(new Producto("P003", "Leche Entera Colanta 1L", obtenerOCrearCategoria("Lácteos"), "Bolsa", 4200, 62, 20, 60)); // Exceso inicial
            registrarProductoInicial(new Producto("P004", "Frijol Cargamanto 500g", obtenerOCrearCategoria("Granos y Cereales"), "Bolsa", 5600, 25, 10, 50));
            registrarProductoInicial(new Producto("P005", "Azúcar Morena Incauca 1kg", obtenerOCrearCategoria("Endulzantes"), "Bolsa", 4800, 12, 12, 45)); // En el límite mínimo
            registrarProductoInicial(new Producto("P006", "Huevos AA x30", obtenerOCrearCategoria("Huevos y Lácteos"), "Cubeta", 16500, 18, 8, 30));
            registrarProductoInicial(new Producto("P007", "Café Sello Rojo 250g", obtenerOCrearCategoria("Bebidas"), "Paquete", 7200, 30, 10, 50));
            registrarProductoInicial(new Producto("P008", "Jabón Rey Barra 300g", obtenerOCrearCategoria("Aseo Hogar"), "Unidad", 3100, 5, 10, 40)); // Stock bajo inicial

            // Movimientos iniciales de ejemplo
            Usuario uEnc = usuarios.get(1);
            Producto p1 = buscarProductoPorCodigo("P001");
            new Entrada(p1, 45, LocalDateTime.now().minusDays(1), uEnc,
                    obtenerOCrearProveedor("Distribuidora Diana S.A."), "FAC-8821").registrar(historialMovimientos);
            Producto p2 = buscarProductoPorCodigo("P002");
            new Salida(p2, 5, LocalDateTime.now().minusHours(3), uEnc, "Venta diaria mostrador").registrar(historialMovimientos);

            registrarLog("SISTEMA", "Inicialización", "Carga inicial de datos del inventario Mercado La Economía.");
        }

        private void registrarProductoInicial(Producto p) {
            p.agregarObservador(gestorAlertas);
            productos.add(p);
            p.notificarObservadores(); // Evaluar estado inicial
        }

        // ------------------------- Persistencia (RNF24 / RNF25) -------------------------

        /** Carga los datos guardados; si no existen (o están dañados) crea los datos de ejemplo. */
        public static RepositorioSistema cargarOCrear() {
            File archivo = new File(ARCHIVO_DATOS);
            if (archivo.exists()) {
                try {
                    RepositorioSistema r = leerDesdeArchivo(archivo);
                    r.registrarLog("SISTEMA", "Persistencia", "Datos cargados desde " + archivo.getName());
                    return r;
                } catch (Exception ex) {
                    File danado = new File(ARCHIVO_DATOS + ".danado");
                    archivo.renameTo(danado);
                    JOptionPane.showMessageDialog(null,
                            "No se pudo leer el archivo de datos (" + ex.getMessage() + ").\n"
                                    + "Se conservó una copia como '" + danado.getName() + "' y se iniciará con los datos de ejemplo.\n"
                                    + "Puede restaurar un respaldo desde el módulo de Usuarios (administrador).",
                            "Persistencia de datos", JOptionPane.WARNING_MESSAGE);
                }
            }
            return new RepositorioSistema();
        }

        private static RepositorioSistema leerDesdeArchivo(File archivo) throws IOException, ClassNotFoundException {
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(archivo)))) {
                RepositorioSistema r = (RepositorioSistema) in.readObject();
                r.despuesDeCargar();
                return r;
            }
        }

        private void despuesDeCargar() {
            persistenciaActiva = true;
            int maxAlerta = 0;
            for (AlertaInventario a : gestorAlertas.getTodasLasAlertas()) maxAlerta = Math.max(maxAlerta, a.getIdAlerta());
            AlertaInventario.ajustarContador(maxAlerta + 1);
            int maxMov = 0;
            for (MovimientoInventario m : historialMovimientos) maxMov = Math.max(maxMov, m.getIdMovimiento());
            MovimientoInventario.ajustarSecuencia(maxMov);
        }

        /** Guarda todo el estado en disco (se invoca automáticamente tras cada acción registrada). */
        public synchronized void guardar() {
            if (!persistenciaActiva) return;
            File destino = new File(ARCHIVO_DATOS);
            File temporal = new File(ARCHIVO_DATOS + ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(temporal)))) {
                out.writeObject(this);
            } catch (IOException ex) {
                System.err.println("No se pudieron guardar los datos: " + ex.getMessage());
                return;
            }
            try {
                Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("No se pudo reemplazar el archivo de datos: " + ex.getMessage());
            }
        }

        /** Crea una copia de respaldo con fecha y hora en la carpeta 'respaldos' (RNF24). */
        public File crearRespaldo(Usuario usuario) throws IOException {
            String sello = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path carpeta = Paths.get(CARPETA_RESPALDOS);
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve("inventario_" + sello + ".dat");
            registrarLog(usuario.getNombreUsuario(), "Respaldo", "Creó respaldo: " + destino.getFileName());
            Files.copy(Paths.get(ARCHIVO_DATOS), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toFile();
        }

        /** Restaura la información a partir de un respaldo (RNF25). */
        public static RepositorioSistema restaurarRespaldo(File archivo, Usuario usuario) throws IOException, ClassNotFoundException {
            RepositorioSistema r = leerDesdeArchivo(archivo);
            r.registrarLog(usuario.getNombreUsuario(), "Respaldo", "Restauró la información desde: " + archivo.getName());
            return r;
        }

        // ------------------------- Autenticación (CU11) -------------------------

        public Usuario autenticar(String username, String password) {
            for (Usuario u : usuarios) {
                if (u.iniciarSesion(username, password)) {
                    registrarLog(u.getNombreUsuario(), "Autenticación", "Inicio de sesión exitoso (" + u.getRol().getNombre() + ")");
                    return u;
                }
            }
            registrarLog(username.trim(), "Autenticación", "Intento de inicio de sesión fallido.");
            return null;
        }

        // ------------------------- Productos (CU01 - CU04) -------------------------

        public Producto buscarProductoPorCodigo(String codigo) {
            for (Producto p : productos) {
                if (p.getCodigo().equalsIgnoreCase(codigo.trim())) return p;
            }
            return null;
        }

        /** Validaciones comunes de CU01/CU02 (RNF19, RNF34). */
        public static void validarDatosProducto(String codigo, String nombre, double precio, int existencia, int min, int max) {
            if (codigo == null || codigo.trim().isEmpty() || nombre == null || nombre.trim().isEmpty()) {
                throw new IllegalArgumentException("El código y el nombre son obligatorios.");
            }
            if (precio < 0 || existencia < 0 || min < 0 || max <= 0) {
                throw new IllegalArgumentException("Los valores numéricos no pueden ser negativos (y el máximo debe ser mayor a 0).");
            }
            if (min >= max) {
                throw new IllegalArgumentException("El nivel mínimo debe ser estrictamente menor al nivel máximo.");
            }
        }

        public void agregarProducto(Producto p, Usuario usuario) {
            validarDatosProducto(p.getCodigo(), p.getNombre(), p.getPrecio(), p.getExistenciaActual(), p.getNivelMinimo(), p.getNivelMaximo());
            if (buscarProductoPorCodigo(p.getCodigo()) != null) {
                throw new IllegalArgumentException("Ya existe un producto con el código: " + p.getCodigo());
            }
            p.agregarObservador(gestorAlertas);
            productos.add(p);
            p.notificarObservadores();
            registrarLog(usuario.getNombreUsuario(), "Productos", "Registró producto: " + p.getCodigo() + " - " + p.getNombre());
        }

        /**
         * Importa productos desde un archivo CSV (RNF14).
         * Formato por línea: codigo;nombre;categoria;unidad;precio;existencia;minimo;maximo
         */
        public ResultadoImportacion importarProductosCSV(File archivo, Usuario usuario) {
            ResultadoImportacion resultado = new ResultadoImportacion();
            List<String> lineas;
            try {
                try {
                    lineas = Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);
                } catch (CharacterCodingException ex) {
                    lineas = Files.readAllLines(archivo.toPath(), Charset.forName("windows-1252"));
                }
            } catch (IOException ex) {
                resultado.errores.add("No se pudo leer el archivo: " + ex.getMessage());
                return resultado;
            }

            int numero = 0;
            for (String original : lineas) {
                numero++;
                String linea = original.replace("﻿", "").trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                String separador = linea.contains(";") ? ";" : ",";
                String[] c = linea.split(java.util.regex.Pattern.quote(separador), -1);
                String primera = c[0].trim().toLowerCase();
                if (primera.equals("codigo") || primera.equals("código")) continue; // encabezado
                if (c.length < 8) {
                    resultado.errores.add("Línea " + numero + ": se esperaban 8 columnas (codigo;nombre;categoria;unidad;precio;existencia;minimo;maximo).");
                    continue;
                }
                try {
                    String codigo = c[0].trim().toUpperCase();
                    String nombre = c[1].trim();
                    String categoria = c[2].trim().isEmpty() ? "Otros" : c[2].trim();
                    String unidad = c[3].trim().isEmpty() ? "Unidad" : c[3].trim();
                    double precio = Double.parseDouble(c[4].trim().replace(",", "."));
                    int existencia = Integer.parseInt(c[5].trim());
                    int min = Integer.parseInt(c[6].trim());
                    int max = Integer.parseInt(c[7].trim());
                    validarDatosProducto(codigo, nombre, precio, existencia, min, max);
                    if (buscarProductoPorCodigo(codigo) != null) {
                        throw new IllegalArgumentException("ya existe un producto con el código " + codigo + ".");
                    }
                    Producto p = new Producto(codigo, nombre, obtenerOCrearCategoria(categoria), unidad, precio, existencia, min, max);
                    p.agregarObservador(gestorAlertas);
                    productos.add(p);
                    p.notificarObservadores();
                    resultado.importados++;
                } catch (NumberFormatException ex) {
                    resultado.errores.add("Línea " + numero + ": precio, existencia y niveles deben ser números válidos.");
                } catch (IllegalArgumentException ex) {
                    resultado.errores.add("Línea " + numero + ": " + ex.getMessage());
                }
            }
            registrarLog(usuario.getNombreUsuario(), "Productos", "Importó " + resultado.importados + " producto(s) desde "
                    + archivo.getName() + (resultado.errores.isEmpty() ? "" : " (" + resultado.errores.size() + " línea(s) con error)"));
            return resultado;
        }

        public Categoria obtenerOCrearCategoria(String nombre) {
            String n = nombre.trim();
            for (Categoria c : categorias) {
                if (c.getNombre().equalsIgnoreCase(n)) return c;
            }
            Categoria nueva = new Categoria(categorias.size() + 1, n);
            categorias.add(nueva);
            return nueva;
        }

        public Proveedor obtenerOCrearProveedor(String nombre) {
            String n = nombre.trim();
            for (Proveedor p : proveedores) {
                if (p.getNombre().equalsIgnoreCase(n)) return p;
            }
            Proveedor nuevo = new Proveedor(proveedores.size() + 1, n);
            proveedores.add(nuevo);
            return nuevo;
        }

        // ------------------------- Movimientos (CU10 / CU05) -------------------------

        public Entrada registrarEntrada(String codigoProducto, int cantidad, String proveedor, String documento,
                                        LocalDate fecha, Usuario usuario) {
            return getControladorMovimiento().registrarEntrada(codigoProducto, cantidad, proveedor, documento, fecha, usuario);
        }

        public Salida registrarSalida(String codigoProducto, int cantidad, String motivo, LocalDate fecha, Usuario usuario) {
            return getControladorMovimiento().registrarSalida(codigoProducto, cantidad, motivo, fecha, usuario);
        }

        // ------------------------- Auditoría (RNF29) -------------------------

        public void registrarLog(String usuario, String modulo, String accion) {
            logsAuditoria.add(0, new RegistroAccion(usuario, modulo, accion));
            guardar(); // Cada acción registrada queda persistida
        }

        // Getters
        public List<Rol> getRoles() { return roles; }
        public List<Usuario> getUsuarios() { return usuarios; }
        public List<Categoria> getCategorias() { return categorias; }
        public List<Proveedor> getProveedores() { return proveedores; }
        public List<Producto> getProductos() { return productos; }
        public List<MovimientoInventario> getHistorialMovimientos() { return historialMovimientos; }
        public List<RegistroAccion> getLogsAuditoria() { return logsAuditoria; }
        public GestorAlertasInventario getGestorAlertas() { return gestorAlertas; }

        public ControladorMovimiento getControladorMovimiento() {
            if (controladorMovimiento == null) controladorMovimiento = new ControladorMovimiento(this);
            return controladorMovimiento;
        }

        public ControladorAlertas getControladorAlertas() {
            if (controladorAlertas == null) controladorAlertas = new ControladorAlertas(this);
            return controladorAlertas;
        }
    }

    // =========================================================================
    // 5. INTERFAZ DE INICIO DE SESIÓN (CU11 - INICIAR SESIÓN)
    // =========================================================================
    public static class VentanaLogin extends JFrame {
        private RepositorioSistema repo;
        private JTextField txtUsuario;
        private JPasswordField txtPassword;
        private JLabel lblMensajeError;

        public VentanaLogin(RepositorioSistema repo) {
            this.repo = repo;
            configurarVentana();
            construirUI();
        }

        private void configurarVentana() {
            setTitle("Mercado La Economía - Control de Inventario (Acceso)");
            setSize(450, 520);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setResizable(false);
        }

        private void construirUI() {
            JPanel panelPrincipal = new JPanel(new BorderLayout());
            panelPrincipal.setBackground(COLOR_FONDO);

            // Cabecera corporativa
            JPanel header = new JPanel();
            header.setBackground(COLOR_PRIMARIO);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setBorder(new EmptyBorder(30, 20, 30, 20));

            JLabel lblIcono = new JLabel(new Icono("CARRITO", 48, Color.WHITE), SwingConstants.CENTER);
            lblIcono.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblTitulo = new JLabel("MERCADO LA ECONOMÍA");
            lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
            lblTitulo.setForeground(Color.WHITE);
            lblTitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblSubtitulo = new JLabel("Sistema de Gestión y Control de Inventario");
            lblSubtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblSubtitulo.setForeground(new Color(187, 247, 208));
            lblSubtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

            header.add(lblIcono);
            header.add(Box.createVerticalStrut(8));
            header.add(lblTitulo);
            header.add(Box.createVerticalStrut(4));
            header.add(lblSubtitulo);

            // Formulario central
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBackground(COLOR_PANEL);
            formPanel.setBorder(new CompoundBorder(
                    new EmptyBorder(25, 35, 25, 35),
                    new CompoundBorder(
                            new LineBorder(new Color(226, 232, 240), 1, true),
                            new EmptyBorder(20, 25, 20, 25)
                    )
            ));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(6, 0, 6, 0);
            gbc.gridx = 0;

            JLabel lblBienvenida = new JLabel("Iniciar Sesión");
            lblBienvenida.setFont(FUENTE_SUBTITULO);
            lblBienvenida.setForeground(COLOR_TEXTO);
            gbc.gridy = 0;
            formPanel.add(lblBienvenida, gbc);

            JLabel lblUser = new JLabel("Usuario:");
            lblUser.setFont(FUENTE_NEGRITA);
            gbc.gridy = 1;
            formPanel.add(lblUser, gbc);

            txtUsuario = new JTextField("encargado");
            txtUsuario.setFont(FUENTE_NORMAL);
            txtUsuario.setPreferredSize(new Dimension(250, 34));
            gbc.gridy = 2;
            formPanel.add(txtUsuario, gbc);

            JLabel lblPass = new JLabel("Contraseña:");
            lblPass.setFont(FUENTE_NEGRITA);
            gbc.gridy = 3;
            formPanel.add(lblPass, gbc);

            txtPassword = new JPasswordField("1234");
            txtPassword.setFont(FUENTE_NORMAL);
            txtPassword.setPreferredSize(new Dimension(250, 34));
            gbc.gridy = 4;
            formPanel.add(txtPassword, gbc);

            lblMensajeError = new JLabel(" ");
            lblMensajeError.setForeground(COLOR_ALERTA_BAJO);
            lblMensajeError.setFont(new Font("Segoe UI", Font.BOLD, 12));
            gbc.gridy = 5;
            formPanel.add(lblMensajeError, gbc);

            JButton btnIngresar = crearBotonEstilizado("Ingresar al Sistema", COLOR_SECUNDARIO, Color.WHITE);
            btnIngresar.setPreferredSize(new Dimension(250, 38));
            gbc.gridy = 6;
            formPanel.add(btnIngresar, gbc);

            // Panel de ayuda rápida para pruebas
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(COLOR_FONDO);
            footer.setBorder(new EmptyBorder(0, 35, 15, 35));
            JLabel lblCredenciales = new JLabel("<html><center><font color='#64748b'><b>Credenciales de prueba:</b><br>" +
                    "Admin: <code>admin / 1234</code> &nbsp;|&nbsp; Encargado: <code>encargado / 1234</code></font></center></html>", SwingConstants.CENTER);
            lblCredenciales.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            footer.add(lblCredenciales, BorderLayout.CENTER);

            panelPrincipal.add(header, BorderLayout.NORTH);
            panelPrincipal.add(formPanel, BorderLayout.CENTER);
            panelPrincipal.add(footer, BorderLayout.SOUTH);

            add(panelPrincipal);

            // Eventos
            ActionListener loginAction = e -> procesarLogin();
            btnIngresar.addActionListener(loginAction);
            txtPassword.addActionListener(loginAction);
        }

        private void procesarLogin() {
            String user = txtUsuario.getText();
            String pass = new String(txtPassword.getPassword());

            if (user.trim().isEmpty() || pass.trim().isEmpty()) {
                ponerEstado(lblMensajeError, "ALERTA", "Ingrese usuario y contraseña.");
                return;
            }

            Usuario autenticado = repo.autenticar(user, pass);
            if (autenticado != null) {
                dispose();
                VentanaPrincipal principal = new VentanaPrincipal(repo, autenticado);
                principal.setVisible(true);
            } else {
                ponerEstado(lblMensajeError, "ERROR", "Credenciales inválidas o usuario inactivo.");
            }
        }
    }

    // =========================================================================
    // 6. VENTANA PRINCIPAL (DASHBOARD + MÓDULOS DE INVENTARIO)
    // =========================================================================
    public static class VentanaPrincipal extends JFrame {
        /** Intervalo de la verificación periódica de niveles (CU08). */
        public static final int INTERVALO_VERIFICACION_MS = 60_000;

        private RepositorioSistema repo;
        private Usuario usuarioActual;

        private CardLayout cardLayout;
        private JPanel contenedorVistas;
        private JButton btnBadgeAlertas;
        private final Set<String> vistasPermitidas = new HashSet<>();
        private final Runnable listenerAlertas = this::actualizarBadgeAlertas;
        private javax.swing.Timer timerVerificacion;

        // Referencias a los paneles para refrescar datos en tiempo real
        private PanelDashboard panelDashboard;
        private PanelProductos panelProductos;
        private PanelMovimientos panelMovimientos;
        private PanelHistorial panelHistorial;
        private PanelNivelesAlertas panelNivelesAlertas;
        private PanelUsuarios panelUsuarios;

        public VentanaPrincipal(RepositorioSistema repo, Usuario usuarioActual) {
            this.repo = repo;
            this.usuarioActual = usuarioActual;

            configurarVentana();
            construirInterfaz();

            // Suscribir actualización de badge de alertas cuando el Observer detecte cambios
            repo.getGestorAlertas().agregarListenerUI(listenerAlertas);
            actualizarBadgeAlertas();

            // Verificación periódica de niveles (CU08: "tras cada movimiento y de forma periódica")
            timerVerificacion = new javax.swing.Timer(INTERVALO_VERIFICACION_MS, e -> {
                repo.getControladorAlertas().verificarNiveles();
                panelDashboard.refrescar();
                panelNivelesAlertas.refrescarDatos();
                actualizarBadgeAlertas();
            });
            timerVerificacion.start();
        }

        /** Detiene el temporizador y deja de escuchar al Observer al cerrar esta ventana. */
        private void liberarRecursos() {
            if (timerVerificacion != null) timerVerificacion.stop();
            repo.getGestorAlertas().eliminarListenerUI(listenerAlertas);
        }

        private void configurarVentana() {
            setTitle("Mercado La Economía | Sistema de Gestión y Control de Inventario");
            setSize(1220, 760);
            setMinimumSize(new Dimension(1050, 680));
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
        }

        private void construirInterfaz() {
            setLayout(new BorderLayout());

            // 1. BARRA SUPERIOR (HEADER)
            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setBackground(COLOR_PRIMARIO);
            topBar.setBorder(new EmptyBorder(12, 20, 12, 20));

            JLabel lblMarca = new JLabel("MERCADO LA ECONOMÍA  |  Control de Inventario");
            lblMarca.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblMarca.setIcon(new Icono("CARRITO", 22));
            lblMarca.setIconTextGap(8);
            lblMarca.setForeground(Color.WHITE);

            JPanel panelDerechoHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
            panelDerechoHeader.setOpaque(false);

            btnBadgeAlertas = new JButton("Alertas Activas: 0");
            btnBadgeAlertas.setFont(FUENTE_NEGRITA);
            btnBadgeAlertas.setIcon(new Icono("CAMPANA"));
            btnBadgeAlertas.setBackground(COLOR_ALERTA_BAJO);
            btnBadgeAlertas.setForeground(Color.WHITE);
            btnBadgeAlertas.setFocusPainted(false);
            btnBadgeAlertas.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnBadgeAlertas.addActionListener(e -> mostrarVista("ALERTAS"));

            JLabel lblUsuarioInfo = new JLabel(usuarioActual.getNombresCompletos() +
                    " [" + usuarioActual.getRol().getNombre() + "]");
            lblUsuarioInfo.setFont(FUENTE_NORMAL);
            lblUsuarioInfo.setIcon(new Icono("USUARIO"));
            lblUsuarioInfo.setForeground(new Color(220, 252, 231));

            JButton btnCerrarSesion = new JButton("Cerrar Sesión");
            btnCerrarSesion.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnCerrarSesion.setBackground(new Color(15, 61, 34));
            btnCerrarSesion.setForeground(Color.WHITE);
            btnCerrarSesion.setFocusPainted(false);
            btnCerrarSesion.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnCerrarSesion.addActionListener(e -> cerrarSesion());

            panelDerechoHeader.add(btnBadgeAlertas);
            panelDerechoHeader.add(lblUsuarioInfo);
            panelDerechoHeader.add(btnCerrarSesion);

            topBar.add(lblMarca, BorderLayout.WEST);
            topBar.add(panelDerechoHeader, BorderLayout.EAST);

            // 2. MENÚ LATERAL (SIDEBAR DE NAVEGACIÓN POR CASOS DE USO)
            JPanel sideBar = new JPanel();
            sideBar.setBackground(new Color(241, 245, 249));
            sideBar.setPreferredSize(new Dimension(245, 0));
            sideBar.setLayout(new BoxLayout(sideBar, BoxLayout.Y_AXIS));
            sideBar.setBorder(new MatteBorder(0, 0, 0, 1, new Color(203, 213, 225)));

            JLabel lblMenuTitulo = new JLabel("MÓDULOS DEL SISTEMA");
            lblMenuTitulo.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblMenuTitulo.setForeground(new Color(100, 116, 139));
            lblMenuTitulo.setBorder(new EmptyBorder(18, 18, 10, 10));
            lblMenuTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);
            sideBar.add(lblMenuTitulo);

            // Restricción de acceso por permisos del Rol (RF10 / RNF28 / CU09 / CU11)
            Rol rol = usuarioActual.getRol();
            if (rol.tienePermiso(Rol.P_DASHBOARD)) agregarBotonMenu(sideBar, "GRAFICA", "Resumen General", "DASHBOARD");
            if (rol.tienePermiso(Rol.P_PRODUCTOS)) agregarBotonMenu(sideBar, "CAJA", "Productos", "PRODUCTOS");
            if (rol.tienePermiso(Rol.P_MOVIMIENTOS)) agregarBotonMenu(sideBar, "ACTUALIZAR", "Entradas y Salidas", "MOVIMIENTOS");
            if (rol.tienePermiso(Rol.P_HISTORIAL)) agregarBotonMenu(sideBar, "HISTORIAL", "Historial Movimientos", "HISTORIAL");
            if (rol.tienePermiso(Rol.P_ALERTAS)) agregarBotonMenu(sideBar, "ALERTA", "Niveles y Alertas", "ALERTAS");

            if (rol.tienePermiso(Rol.P_USUARIOS)) {
                sideBar.add(Box.createVerticalStrut(15));
                JLabel lblAdminTitulo = new JLabel("ADMINISTRACIÓN");
                lblAdminTitulo.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lblAdminTitulo.setForeground(new Color(100, 116, 139));
                lblAdminTitulo.setBorder(new EmptyBorder(10, 18, 10, 10));
                lblAdminTitulo.setAlignmentX(Component.LEFT_ALIGNMENT);
                sideBar.add(lblAdminTitulo);

                agregarBotonMenu(sideBar, "USUARIOS", "Usuarios y Roles", "USUARIOS");
            }

            sideBar.add(Box.createVerticalGlue());

            JLabel lblPie = new JLabel("<html><font color='#64748b'>ITM - Diseño de Software 2026<br>Patrón Observer Activo<br>Verificación automática cada "
                    + (INTERVALO_VERIFICACION_MS / 1000) + " s</font></html>");
            lblPie.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblPie.setBorder(new EmptyBorder(15, 18, 15, 15));
            lblPie.setAlignmentX(Component.LEFT_ALIGNMENT);
            sideBar.add(lblPie);

            // 3. CONTENEDOR CENTRAL CON CARDLAYOUT
            cardLayout = new CardLayout();
            contenedorVistas = new JPanel(cardLayout);
            contenedorVistas.setBackground(COLOR_FONDO);

            panelDashboard = new PanelDashboard(repo, this::mostrarVista);
            panelProductos = new PanelProductos(repo, usuarioActual, this::refrescarTodo);
            // CU10: si el producto no existe, se ofrece registrarlo mediante el formulario de CU01
            panelMovimientos = new PanelMovimientos(repo, usuarioActual, this::refrescarTodo,
                    codigo -> panelProductos.abrirDialogoProducto(null, codigo));
            panelHistorial = new PanelHistorial(repo, usuarioActual);
            panelNivelesAlertas = new PanelNivelesAlertas(repo, usuarioActual, this::refrescarTodo);
            if (rol.tienePermiso(Rol.P_USUARIOS)) {
                panelUsuarios = new PanelUsuarios(repo, usuarioActual, this::reiniciarConRepositorio);
            }

            agregarVista(Rol.P_DASHBOARD, panelDashboard);
            agregarVista(Rol.P_PRODUCTOS, panelProductos);
            agregarVista(Rol.P_MOVIMIENTOS, panelMovimientos);
            agregarVista(Rol.P_HISTORIAL, panelHistorial);
            agregarVista(Rol.P_ALERTAS, panelNivelesAlertas);
            if (panelUsuarios != null) agregarVista(Rol.P_USUARIOS, panelUsuarios);

            add(topBar, BorderLayout.NORTH);
            add(sideBar, BorderLayout.WEST);
            add(contenedorVistas, BorderLayout.CENTER);
        }

        /** Agrega la vista solo si el rol tiene el permiso correspondiente (RNF28). */
        private void agregarVista(String permiso, JPanel vista) {
            if (usuarioActual.getRol().tienePermiso(permiso)) {
                contenedorVistas.add(vista, permiso);
                vistasPermitidas.add(permiso);
            }
        }

        private void agregarBotonMenu(JPanel sideBar, String icono, String texto, String vistaDestino) {
            JButton btn = new JButton(texto, new Icono(icono));
            btn.setIconTextGap(10);
            btn.setFont(FUENTE_NEGRITA);
            btn.setForeground(COLOR_TEXTO);
            btn.setBackground(new Color(241, 245, 249));
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setBorder(new EmptyBorder(12, 18, 12, 15));
            btn.setMaximumSize(new Dimension(245, 45));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(220, 252, 231));
                    btn.setForeground(COLOR_PRIMARIO);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(new Color(241, 245, 249));
                    btn.setForeground(COLOR_TEXTO);
                }
            });

            btn.addActionListener(e -> mostrarVista(vistaDestino));
            sideBar.add(btn);
        }

        public void mostrarVista(String nombreVista) {
            if (!vistasPermitidas.contains(nombreVista)) {
                JOptionPane.showMessageDialog(this, "Su rol no tiene acceso a este módulo (RNF28).",
                        "Acceso restringido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            refrescarTodo();
            cardLayout.show(contenedorVistas, nombreVista);
        }

        public void refrescarTodo() {
            panelDashboard.refrescar();
            panelProductos.refrescarTabla();
            panelMovimientos.refrescarCombos();
            panelHistorial.refrescarTabla();
            panelNivelesAlertas.refrescarDatos();
            if (panelUsuarios != null) panelUsuarios.refrescar();
            actualizarBadgeAlertas();
        }

        private void actualizarBadgeAlertas() {
            int count = repo.getGestorAlertas().getAlertasActivas().size();
            btnBadgeAlertas.setText("Alertas Activas: " + count);
            btnBadgeAlertas.setBackground(count > 0 ? COLOR_ALERTA_BAJO : COLOR_SECUNDARIO);
        }

        private void cerrarSesion() {
            repo.registrarLog(usuarioActual.getNombreUsuario(), "Autenticación", "Cierre de sesión.");
            liberarRecursos();
            dispose();
            new VentanaLogin(repo).setVisible(true);
        }

        /** Tras restaurar un respaldo (RNF25) se reinicia la sesión con los datos restaurados. */
        private void reiniciarConRepositorio(RepositorioSistema restaurado) {
            restaurado.guardar();
            liberarRecursos();
            dispose();
            new VentanaLogin(restaurado).setVisible(true);
        }
    }

    // =========================================================================
    // 7. PANEL DASHBOARD (RESUMEN EJECUTIVO EN TIEMPO REAL)
    // =========================================================================
    public static class PanelDashboard extends JPanel {
        private RepositorioSistema repo;
        private java.util.function.Consumer<String> navegar;

        private JLabel lblTotalProductos;
        private JLabel lblStockBajo;
        private JLabel lblExcesoStock;
        private JLabel lblMovimientosHoy;
        private DefaultTableModel modeloAlertasRecientes;

        public PanelDashboard(RepositorioSistema repo, java.util.function.Consumer<String> navegar) {
            this.repo = repo;
            this.navegar = navegar;
            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescar();
        }

        private void construirUI() {
            JLabel lblTitulo = new JLabel("Panel General del Inventario - Mercado La Economía");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            // Tarjetas KPI
            JPanel gridKPI = new JPanel(new GridLayout(1, 4, 15, 0));
            gridKPI.setOpaque(false);

            lblTotalProductos = new JLabel("0");
            lblStockBajo = new JLabel("0");
            lblExcesoStock = new JLabel("0");
            lblMovimientosHoy = new JLabel("0");

            gridKPI.add(crearTarjetaKPI("Productos Activos", lblTotalProductos, COLOR_PRIMARIO, "CAJA"));
            gridKPI.add(crearTarjetaKPI("Stock Bajo (Reabastecer)", lblStockBajo, COLOR_ALERTA_BAJO, "BAJA"));
            gridKPI.add(crearTarjetaKPI("Exceso de Inventario", lblExcesoStock, COLOR_ALERTA_EXCESO, "SUBE"));
            gridKPI.add(crearTarjetaKPI("Movimientos Registrados", lblMovimientosHoy, new Color(37, 99, 235), "ACTUALIZAR"));

            JPanel topWrapper = new JPanel(new BorderLayout(0, 15));
            topWrapper.setOpaque(false);
            topWrapper.add(lblTitulo, BorderLayout.NORTH);
            topWrapper.add(gridKPI, BorderLayout.CENTER);

            // Panel central con accesos rápidos y alertas generadas por Observer
            JPanel centro = new JPanel(new BorderLayout(15, 15));
            centro.setOpaque(false);

            // Accesos rápidos
            JPanel panelAccesos = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
            panelAccesos.setBackground(COLOR_PANEL);
            panelAccesos.setBorder(new TitledBorder(new LineBorder(new Color(226, 232, 240)), "Acciones Rápidas (Flujo Operativo)"));

            JButton btnNuevaEntrada = crearBotonEstilizado("+ Registrar Entrada", COLOR_SECUNDARIO, Color.WHITE);
            JButton btnNuevaSalida = crearBotonEstilizado("- Registrar Salida / Venta", new Color(37, 99, 235), Color.WHITE);
            JButton btnNuevoProd = crearBotonEstilizado("CAJA", "Nuevo Producto", COLOR_PRIMARIO, Color.WHITE);

            btnNuevaEntrada.addActionListener(e -> navegar.accept("MOVIMIENTOS"));
            btnNuevaSalida.addActionListener(e -> navegar.accept("MOVIMIENTOS"));
            btnNuevoProd.addActionListener(e -> navegar.accept("PRODUCTOS"));

            panelAccesos.add(btnNuevaEntrada);
            panelAccesos.add(btnNuevaSalida);
            panelAccesos.add(btnNuevoProd);

            // Tabla de alertas activas del Patrón Observer
            JPanel panelAlertas = new JPanel(new BorderLayout(10, 10));
            panelAlertas.setBackground(COLOR_PANEL);
            panelAlertas.setBorder(new CompoundBorder(
                    new TitledBorder(new LineBorder(new Color(226, 232, 240)), "Alertas Automáticas Activas (Patrón Observer)"),
                    new EmptyBorder(10, 10, 10, 10)
            ));

            String[] cols = {"ID Alerta", "Código", "Producto", "Tipo de Alerta", "Detalle / Motivo", "Fecha Generación"};
            modeloAlertasRecientes = new DefaultTableModel(cols, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable tablaAlertas = new JTable(modeloAlertasRecientes);
            estilizarTabla(tablaAlertas);

            panelAlertas.add(new JScrollPane(tablaAlertas), BorderLayout.CENTER);

            centro.add(panelAccesos, BorderLayout.NORTH);
            centro.add(panelAlertas, BorderLayout.CENTER);

            add(topWrapper, BorderLayout.NORTH);
            add(centro, BorderLayout.CENTER);
        }

        private JPanel crearTarjetaKPI(String titulo, JLabel lblValor, Color colorAcento, String icono) {
            JPanel card = new JPanel(new BorderLayout(10, 5));
            card.setBackground(COLOR_PANEL);
            card.setBorder(new CompoundBorder(
                    new MatteBorder(0, 5, 0, 0, colorAcento),
                    new EmptyBorder(15, 15, 15, 15)
            ));

            JLabel lblTit = new JLabel(titulo);
            lblTit.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblTit.setForeground(new Color(100, 116, 139));

            lblValor.setFont(new Font("Segoe UI", Font.BOLD, 28));
            lblValor.setForeground(colorAcento);

            JLabel lblIco = new JLabel(new Icono(icono, 32, colorAcento));

            card.add(lblTit, BorderLayout.NORTH);
            card.add(lblValor, BorderLayout.CENTER);
            card.add(lblIco, BorderLayout.EAST);
            return card;
        }

        public void refrescar() {
            int activos = 0;
            int bajos = 0;
            int excesos = 0;

            for (Producto p : repo.getProductos()) {
                if (p.isActivo()) {
                    activos++;
                    String estado = p.verificarNivelInventario();
                    if ("STOCK BAJO".equals(estado)) bajos++;
                    else if ("EXCESO".equals(estado)) excesos++;
                }
            }

            lblTotalProductos.setText(String.valueOf(activos));
            lblStockBajo.setText(String.valueOf(bajos));
            lblExcesoStock.setText(String.valueOf(excesos));
            lblMovimientosHoy.setText(String.valueOf(repo.getHistorialMovimientos().size()));

            modeloAlertasRecientes.setRowCount(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (AlertaInventario a : repo.getGestorAlertas().getAlertasActivas()) {
                modeloAlertasRecientes.addRow(new Object[]{
                        "ALT-" + a.getIdAlerta(),
                        a.getCodigoProducto(),
                        a.getNombreProducto(),
                        a.getTipo(),
                        a.getMensaje(),
                        a.getFecha().format(fmt)
                });
            }
        }
    }

    // =========================================================================
    // 8. PANEL GESTIÓN DE PRODUCTOS (CU01, CU02, CU03, CU04)
    // =========================================================================
    public static class PanelProductos extends JPanel {
        private RepositorioSistema repo;
        private Usuario usuarioActual;
        private Runnable onCambio;

        private JTextField txtBuscar;
        private JComboBox<String> cmbFiltroEstado;
        private DefaultTableModel modeloTabla;
        private JTable tablaProductos;
        private JLabel lblSinResultados;

        public PanelProductos(RepositorioSistema repo, Usuario usuarioActual, Runnable onCambio) {
            this.repo = repo;
            this.usuarioActual = usuarioActual;
            this.onCambio = onCambio;

            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescarTabla();
        }

        private void construirUI() {
            // Cabecera y barra de búsqueda (CU04)
            JPanel header = new JPanel(new BorderLayout(10, 10));
            header.setOpaque(false);

            JLabel lblTitulo = new JLabel("Catálogo y Gestión de Productos");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            JPanel barraHerramientas = new JPanel(new BorderLayout(10, 0));
            barraHerramientas.setBackground(COLOR_PANEL);
            barraHerramientas.setBorder(new EmptyBorder(12, 15, 12, 15));

            JPanel panelBusqueda = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            panelBusqueda.setOpaque(false);
            panelBusqueda.add(new JLabel("Buscar (Código, Nombre o Categoría):", new Icono("BUSCAR"), SwingConstants.LEFT));
            txtBuscar = new JTextField(20);
            txtBuscar.setFont(FUENTE_NORMAL);
            txtBuscar.addKeyListener(new KeyAdapter() {
                @Override public void keyReleased(KeyEvent e) { refrescarTabla(); }
            });

            cmbFiltroEstado = new JComboBox<>(new String[]{"Solo Activos", "Todos", "Inactivos"});
            cmbFiltroEstado.addActionListener(e -> refrescarTabla());

            panelBusqueda.add(txtBuscar);
            panelBusqueda.add(new JLabel("Estado:"));
            panelBusqueda.add(cmbFiltroEstado);

            JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            panelBotones.setOpaque(false);

            JButton btnNuevo = crearBotonEstilizado("+ Nuevo Producto", COLOR_SECUNDARIO, Color.WHITE);
            JButton btnEditar = crearBotonEstilizado("EDITAR", "Editar", new Color(37, 99, 235), Color.WHITE);
            JButton btnDesactivar = crearBotonEstilizado("BLOQUEAR", "Activar/Desactivar", COLOR_ALERTA_BAJO, Color.WHITE);
            JButton btnImportar = crearBotonEstilizado("IMPORTAR", "Importar CSV (RNF14)", new Color(71, 85, 105), Color.WHITE);
            btnImportar.setToolTipText("<html>Formato por línea (separador ; o ,):<br>codigo;nombre;categoria;unidad;precio;existencia;minimo;maximo</html>");

            btnNuevo.addActionListener(e -> abrirDialogoProducto(null, null));
            btnEditar.addActionListener(e -> editarSeleccionado());
            btnDesactivar.addActionListener(e -> cambiarEstadoSeleccionado());
            btnImportar.addActionListener(e -> importarCSV());

            panelBotones.add(btnNuevo);
            panelBotones.add(btnEditar);
            panelBotones.add(btnDesactivar);
            panelBotones.add(btnImportar);

            barraHerramientas.setLayout(new BorderLayout(10, 10));
            barraHerramientas.add(panelBusqueda, BorderLayout.NORTH);
            barraHerramientas.add(panelBotones, BorderLayout.CENTER);

            header.add(lblTitulo, BorderLayout.NORTH);
            header.add(barraHerramientas, BorderLayout.CENTER);

            // Tabla de productos
            String[] columnas = {"Código", "Nombre", "Categoría", "Unidad", "Precio ($)", "Existencia", "Mínimo", "Máximo", "Nivel Stock", "Estado"};
            modeloTabla = new DefaultTableModel(columnas, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tablaProductos = new JTable(modeloTabla);
            estilizarTabla(tablaProductos);

            // Renderizador de colores para la columna "Nivel Stock"
            tablaProductos.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    String val = String.valueOf(value);
                    setFont(FUENTE_NEGRITA);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    if (!isSelected) {
                        if ("STOCK BAJO".equals(val)) {
                            c.setForeground(COLOR_ALERTA_BAJO);
                            c.setBackground(new Color(254, 226, 226));
                        } else if ("EXCESO".equals(val)) {
                            c.setForeground(COLOR_ALERTA_EXCESO);
                            c.setBackground(new Color(254, 243, 199));
                        } else if ("NORMAL".equals(val)) {
                            c.setForeground(COLOR_NORMAL);
                            c.setBackground(new Color(220, 252, 231));
                        } else {
                            c.setForeground(Color.GRAY);
                            c.setBackground(Color.WHITE);
                        }
                    }
                    return c;
                }
            });

            lblSinResultados = new JLabel(" ");
            lblSinResultados.setFont(FUENTE_NEGRITA);
            lblSinResultados.setBorder(new EmptyBorder(4, 2, 0, 0));

            add(header, BorderLayout.NORTH);
            add(new JScrollPane(tablaProductos), BorderLayout.CENTER);
            add(lblSinResultados, BorderLayout.SOUTH);
        }

        /** RNF14: importa productos desde un archivo CSV. */
        private void importarCSV() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Importar productos (codigo;nombre;categoria;unidad;precio;existencia;minimo;maximo)");
            fc.setFileFilter(new FileNameExtensionFilter("Archivos CSV / texto (*.csv, *.txt)", "csv", "txt"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            ResultadoImportacion r = repo.importarProductosCSV(fc.getSelectedFile(), usuarioActual);
            onCambio.run();

            StringBuilder sb = new StringBuilder("Productos importados: " + r.importados);
            if (!r.errores.isEmpty()) {
                sb.append("\nLíneas con error: ").append(r.errores.size()).append("\n\n");
                for (int i = 0; i < Math.min(15, r.errores.size()); i++) sb.append("• ").append(r.errores.get(i)).append('\n');
                if (r.errores.size() > 15) sb.append("... y ").append(r.errores.size() - 15).append(" más.");
            }
            JTextArea area = new JTextArea(sb.toString(), Math.min(20, 4 + r.errores.size()), 60);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Importación de productos (RNF14)",
                    r.errores.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }

        public void refrescarTabla() {
            modeloTabla.setRowCount(0);
            String filtro = txtBuscar.getText().trim().toLowerCase();
            String estadoFiltro = (String) cmbFiltroEstado.getSelectedItem();

            for (Producto p : repo.getProductos()) {
                if ("Solo Activos".equals(estadoFiltro) && !p.isActivo()) continue;
                if ("Inactivos".equals(estadoFiltro) && p.isActivo()) continue;

                boolean coincide = filtro.isEmpty()
                        || p.getCodigo().toLowerCase().contains(filtro)
                        || p.getNombre().toLowerCase().contains(filtro)
                        || p.getCategoria().toLowerCase().contains(filtro);

                if (coincide) {
                    modeloTabla.addRow(new Object[]{
                            p.getCodigo(),
                            p.getNombre(),
                            p.getCategoria(),
                            p.getUnidadMedida(),
                            String.format("%,.0f", p.getPrecio()),
                            p.getExistenciaActual(),
                            p.getNivelMinimo(),
                            p.getNivelMaximo(),
                            p.verificarNivelInventario(),
                            p.isActivo() ? "ACTIVO" : "INACTIVO"
                    });
                }
            }

            // Flujo alternativo CU04: informar cuando no hay coincidencias
            if (modeloTabla.getRowCount() == 0) {
                lblSinResultados.setForeground(COLOR_ALERTA_EXCESO);
                lblSinResultados.setIcon(new Icono("INFO"));
                lblSinResultados.setText(filtro.isEmpty()
                        ? "No hay productos para el estado seleccionado."
                        : "No se encontraron productos que coincidan con \"" + txtBuscar.getText().trim() + "\".");
            } else {
                lblSinResultados.setForeground(new Color(100, 116, 139));
                lblSinResultados.setIcon(null);
                lblSinResultados.setText(modeloTabla.getRowCount() + " producto(s) encontrados.");
            }
        }

        private void editarSeleccionado() {
            int fila = tablaProductos.getSelectedRow();
            if (fila < 0) {
                JOptionPane.showMessageDialog(this, "Seleccione un producto de la tabla para editar.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String codigo = (String) modeloTabla.getValueAt(fila, 0);
            Producto p = repo.buscarProductoPorCodigo(codigo);
            if (p == null) {
                JOptionPane.showMessageDialog(this, "El producto ya no se encuentra en el sistema.", "Editar Producto", JOptionPane.WARNING_MESSAGE);
                return;
            }
            abrirDialogoProducto(p, null);
        }

        private void cambiarEstadoSeleccionado() {
            int fila = tablaProductos.getSelectedRow();
            if (fila < 0) {
                JOptionPane.showMessageDialog(this, "Seleccione un producto de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String codigo = (String) modeloTabla.getValueAt(fila, 0);
            Producto p = repo.buscarProductoPorCodigo(codigo);

            if (p.isActivo()) {
                // Flujo alternativo CU03: advertir si tiene existencias > 0
                String msg = "¿Desea desactivar el producto '" + p.getNombre() + "'?";
                if (p.getExistenciaActual() > 0) {
                    msg = "ADVERTENCIA: El producto aún tiene " + p.getExistenciaActual() + " unidades en inventario.\n" + msg;
                }
                int op = JOptionPane.showConfirmDialog(this, msg, "Confirmar Desactivación", JOptionPane.YES_NO_OPTION);
                if (op == JOptionPane.YES_OPTION) {
                    p.desactivar();
                    repo.registrarLog(usuarioActual.getNombreUsuario(), "Productos", "Desactivó producto: " + p.getCodigo());
                    onCambio.run();
                }
            } else {
                p.activar();
                repo.registrarLog(usuarioActual.getNombreUsuario(), "Productos", "Reactivó producto: " + p.getCodigo());
                onCambio.run();
            }
        }

        /** Siguiente código libre con formato P001, P002, ... */
        private String siguienteCodigo() {
            int n = repo.getProductos().size() + 1;
            String codigo = String.format("P%03d", n);
            while (repo.buscarProductoPorCodigo(codigo) != null) codigo = String.format("P%03d", ++n);
            return codigo;
        }

        /**
         * Formulario modal para CU01 (Registrar) y CU02 (Editar).
         * codigoSugerido se usa cuando CU10 ofrece registrar un producto inexistente.
         */
        public void abrirDialogoProducto(Producto productoEditar, String codigoSugerido) {
            boolean esEdicion = (productoEditar != null);
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    esEdicion ? "Editar Producto" : "Registrar Nuevo Producto", true);
            dialog.setSize(440, 480);
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout());

            JPanel form = new JPanel(new GridLayout(8, 2, 10, 12));
            form.setBorder(new EmptyBorder(20, 20, 20, 20));

            JTextField txtCod = new JTextField(esEdicion ? productoEditar.getCodigo()
                    : (codigoSugerido != null ? codigoSugerido : siguienteCodigo()));
            txtCod.setEnabled(!esEdicion); // Código no editable en CU02
            JTextField txtNom = new JTextField(esEdicion ? productoEditar.getNombre() : "");
            // Categorías registradas (clase Categoria); se puede escribir una nueva
            JComboBox<String> cmbCat = new JComboBox<>();
            for (Categoria c : repo.getCategorias()) cmbCat.addItem(c.getNombre());
            cmbCat.setEditable(true);
            if (esEdicion) cmbCat.setSelectedItem(productoEditar.getCategoria());

            JComboBox<String> cmbUni = new JComboBox<>(new String[]{"Unidad", "Bolsa", "Botella", "Paquete", "Cubeta", "Caja", "Kilogramo"});
            if (esEdicion) cmbUni.setSelectedItem(productoEditar.getUnidadMedida());

            JTextField txtPrecio = new JTextField(!esEdicion ? ""
                    : (productoEditar.getPrecio() % 1 == 0 ? String.valueOf((long) productoEditar.getPrecio())
                    : String.valueOf(productoEditar.getPrecio())));
            JTextField txtExistencia = new JTextField(esEdicion ? String.valueOf(productoEditar.getExistenciaActual()) : "0");
            txtExistencia.setEnabled(!esEdicion); // Postcondición CU02: La existencia solo se modifica mediante CU05 y CU10

            JTextField txtMin = new JTextField(esEdicion ? String.valueOf(productoEditar.getNivelMinimo()) : "10");
            JTextField txtMax = new JTextField(esEdicion ? String.valueOf(productoEditar.getNivelMaximo()) : "50");

            form.add(new JLabel("Código Único:*")); form.add(txtCod);
            form.add(new JLabel("Nombre del Producto:*")); form.add(txtNom);
            form.add(new JLabel("Categoría:*")); form.add(cmbCat);
            form.add(new JLabel("Unidad de Medida:*")); form.add(cmbUni);
            form.add(new JLabel("Precio Venta ($):*")); form.add(txtPrecio);
            form.add(new JLabel(esEdicion ? "Existencia Actual (Solo Mov.):" : "Existencia Inicial:*")); form.add(txtExistencia);
            form.add(new JLabel("Nivel Mínimo (Alerta Bajo):*")); form.add(txtMin);
            form.add(new JLabel("Nivel Máximo (Alerta Exceso):*")); form.add(txtMax);

            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnCancelar = new JButton("Cancelar");
            JButton btnGuardar = crearBotonEstilizado("Guardar Producto", COLOR_SECUNDARIO, Color.WHITE);

            btnCancelar.addActionListener(e -> dialog.dispose());
            btnGuardar.addActionListener(e -> {
                try {
                    String cod = txtCod.getText().trim().toUpperCase();
                    String nom = txtNom.getText().trim();
                    Object catSel = cmbCat.getEditor().getItem();
                    String categoria = catSel == null ? "" : catSel.toString().trim();
                    if (categoria.isEmpty()) throw new IllegalArgumentException("La categoría es obligatoria.");
                    double precio = Double.parseDouble(txtPrecio.getText().trim().replace(",", "."));
                    int exist = Integer.parseInt(txtExistencia.getText().trim());
                    int min = Integer.parseInt(txtMin.getText().trim());
                    int max = Integer.parseInt(txtMax.getText().trim());

                    // Validaciones comunes CU01/CU02 (campos vacíos, negativos, mínimo >= máximo)
                    RepositorioSistema.validarDatosProducto(cod, nom, precio, exist, min, max);

                    if (esEdicion) {
                        productoEditar.setNombre(nom);
                        productoEditar.setCategoria(repo.obtenerOCrearCategoria(categoria));
                        productoEditar.setUnidadMedida((String) cmbUni.getSelectedItem());
                        productoEditar.setPrecio(precio);
                        productoEditar.setNiveles(min, max);
                        repo.registrarLog(usuarioActual.getNombreUsuario(), "Productos", "Editó producto: " + cod);
                        JOptionPane.showMessageDialog(dialog, "Producto actualizado correctamente.");
                    } else {
                        Producto nuevo = new Producto(cod, nom, repo.obtenerOCrearCategoria(categoria),
                                (String) cmbUni.getSelectedItem(), precio, exist, min, max);
                        repo.agregarProducto(nuevo, usuarioActual);
                        JOptionPane.showMessageDialog(dialog, "Producto registrado correctamente.");
                    }
                    onCambio.run();
                    dialog.dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(dialog, "Verifique que precio, existencia y niveles sean números válidos.", "Error de Validación", JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error de Validación", JOptionPane.ERROR_MESSAGE);
                }
            });

            botones.add(btnCancelar);
            botones.add(btnGuardar);

            dialog.add(form, BorderLayout.CENTER);
            dialog.add(botones, BorderLayout.SOUTH);
            dialog.setVisible(true);
        }
    }

    // =========================================================================
    // 9. PANEL ENTRADAS Y SALIDAS DE MERCANCÍA (CU10 Y CU05)
    // =========================================================================
    public static class PanelMovimientos extends JPanel {
        private static final DateTimeFormatter FMT_FECHA =
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

        private RepositorioSistema repo;
        private Usuario usuarioActual;
        private Runnable onCambio;
        private java.util.function.Consumer<String> registrarProductoNuevo; // abre CU01 con el código sugerido

        // Componentes Entrada (CU10)
        private JTextField txtCodigoEntrada;
        private JComboBox<Producto> cmbProdEntrada;
        private JLabel lblStockActualEntrada;
        private JTextField txtCantEntrada;
        private JComboBox<String> cmbProveedor;
        private JTextField txtDocumento;
        private JTextField txtFechaEntrada;

        // Componentes Salida (CU05)
        private JTextField txtCodigoSalida;
        private JComboBox<Producto> cmbProdSalida;
        private JLabel lblStockActualSalida;
        private JTextField txtCantSalida;
        private JComboBox<String> cmbMotivoSalida;
        private JTextField txtFechaSalida;

        public PanelMovimientos(RepositorioSistema repo, Usuario usuarioActual, Runnable onCambio,
                                java.util.function.Consumer<String> registrarProductoNuevo) {
            this.repo = repo;
            this.usuarioActual = usuarioActual;
            this.onCambio = onCambio;
            this.registrarProductoNuevo = registrarProductoNuevo;

            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescarCombos();
        }

        private void construirUI() {
            JLabel lblTitulo = new JLabel("Registro de Entradas y Salidas de Mercancía");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            JPanel gridDosColumnas = new JPanel(new GridLayout(1, 2, 20, 0));
            gridDosColumnas.setOpaque(false);

            gridDosColumnas.add(crearPanelEntrada());
            gridDosColumnas.add(crearPanelSalida());

            add(lblTitulo, BorderLayout.NORTH);
            add(gridDosColumnas, BorderLayout.CENTER);
        }

        /** Campo de código + botón Buscar (CU05/CU10 «include» CU04). */
        private JPanel crearBuscador(JTextField campo, Runnable accionBuscar) {
            JPanel p = new JPanel(new BorderLayout(6, 0));
            p.setOpaque(false);
            campo.setToolTipText("Escriba el código (ej. P001) y presione Enter o Buscar");
            campo.addActionListener(e -> accionBuscar.run());
            JButton btn = new JButton("Buscar", new Icono("BUSCAR"));
            btn.setFocusPainted(false);
            btn.addActionListener(e -> accionBuscar.run());
            p.add(campo, BorderLayout.CENTER);
            p.add(btn, BorderLayout.EAST);
            return p;
        }

        private JPanel crearPanelEntrada() {
            JPanel card = new JPanel(new BorderLayout(10, 15));
            card.setBackground(COLOR_PANEL);
            card.setBorder(new CompoundBorder(
                    new MatteBorder(4, 0, 0, 0, COLOR_SECUNDARIO),
                    new EmptyBorder(20, 20, 20, 20)
            ));

            JLabel lblSub = new JLabel("Registrar Entrada de Mercancía", new Icono("IMPORTAR"), SwingConstants.LEFT);
            lblSub.setFont(FUENTE_SUBTITULO);
            lblSub.setForeground(COLOR_PRIMARIO);

            JPanel form = new JPanel(new GridLayout(7, 2, 10, 12));
            form.setOpaque(false);

            txtCodigoEntrada = new JTextField();
            cmbProdEntrada = new JComboBox<>();
            lblStockActualEntrada = new JLabel("0");
            lblStockActualEntrada.setFont(FUENTE_NEGRITA);
            lblStockActualEntrada.setForeground(COLOR_PRIMARIO);

            txtCantEntrada = new JTextField();
            cmbProveedor = new JComboBox<>();
            cmbProveedor.setEditable(true);
            cmbProveedor.setToolTipText("Seleccione un proveedor o escriba uno nuevo");
            txtDocumento = new JTextField();
            txtFechaEntrada = new JTextField(LocalDate.now().format(FMT_FECHA));
            txtFechaEntrada.setToolTipText("Formato dd/MM/aaaa. No se permiten fechas futuras.");

            cmbProdEntrada.addActionListener(e -> actualizarEtiquetasStock());

            form.add(new JLabel("Buscar por código:")); form.add(crearBuscador(txtCodigoEntrada, this::buscarProductoEntrada));
            form.add(new JLabel("Seleccionar Producto:*")); form.add(cmbProdEntrada);
            form.add(new JLabel("Existencia Actual:")); form.add(lblStockActualEntrada);
            form.add(new JLabel("Cantidad Recibida:*")); form.add(txtCantEntrada);
            form.add(new JLabel("Proveedor:*")); form.add(cmbProveedor);
            form.add(new JLabel("N° Factura / Remisión:")); form.add(txtDocumento);
            form.add(new JLabel("Fecha (dd/MM/aaaa):*")); form.add(txtFechaEntrada);

            JButton btnConfirmarEntrada = crearBotonEstilizado("Confirmar Entrada (+)", COLOR_SECUNDARIO, Color.WHITE);
            btnConfirmarEntrada.setPreferredSize(new Dimension(0, 40));
            btnConfirmarEntrada.addActionListener(e -> procesarEntrada());

            card.add(lblSub, BorderLayout.NORTH);
            card.add(form, BorderLayout.CENTER);
            card.add(btnConfirmarEntrada, BorderLayout.SOUTH);
            return card;
        }

        private JPanel crearPanelSalida() {
            JPanel card = new JPanel(new BorderLayout(10, 15));
            card.setBackground(COLOR_PANEL);
            card.setBorder(new CompoundBorder(
                    new MatteBorder(4, 0, 0, 0, new Color(37, 99, 235)),
                    new EmptyBorder(20, 20, 20, 20)
            ));

            JLabel lblSub = new JLabel("Registrar Salida por Venta", new Icono("EXPORTAR"), SwingConstants.LEFT);
            lblSub.setFont(FUENTE_SUBTITULO);
            lblSub.setForeground(new Color(30, 58, 138));

            JPanel form = new JPanel(new GridLayout(7, 2, 10, 12));
            form.setOpaque(false);

            txtCodigoSalida = new JTextField();
            cmbProdSalida = new JComboBox<>();
            lblStockActualSalida = new JLabel("0");
            lblStockActualSalida.setFont(FUENTE_NEGRITA);
            lblStockActualSalida.setForeground(new Color(37, 99, 235));

            txtCantSalida = new JTextField();
            cmbMotivoSalida = new JComboBox<>(new String[]{"Venta en mostrador", "Pedido domicilio", "Merma / Producto vencido", "Devolución a proveedor"});
            txtFechaSalida = new JTextField(LocalDate.now().format(FMT_FECHA));
            txtFechaSalida.setToolTipText("Formato dd/MM/aaaa. No se permiten fechas futuras.");

            cmbProdSalida.addActionListener(e -> actualizarEtiquetasStock());

            form.add(new JLabel("Buscar por código:")); form.add(crearBuscador(txtCodigoSalida, this::buscarProductoSalida));
            form.add(new JLabel("Seleccionar Producto:*")); form.add(cmbProdSalida);
            form.add(new JLabel("Existencia Disponible:")); form.add(lblStockActualSalida);
            form.add(new JLabel("Cantidad Vendida / Salida:*")); form.add(txtCantSalida);
            form.add(new JLabel("Motivo de Salida:*")); form.add(cmbMotivoSalida);
            form.add(new JLabel("Fecha (dd/MM/aaaa):*")); form.add(txtFechaSalida);
            form.add(new JLabel("")); form.add(new JLabel("<html><font color='#64748b' size='2'>*Descuenta inventario y evalúa alertas.</font></html>"));

            JButton btnConfirmarSalida = crearBotonEstilizado("Confirmar Salida (-)", new Color(37, 99, 235), Color.WHITE);
            btnConfirmarSalida.setPreferredSize(new Dimension(0, 40));
            btnConfirmarSalida.addActionListener(e -> procesarSalida());

            card.add(lblSub, BorderLayout.NORTH);
            card.add(form, BorderLayout.CENTER);
            card.add(btnConfirmarSalida, BorderLayout.SOUTH);
            return card;
        }

        private void actualizarEtiquetasStock() {
            if (lblStockActualEntrada == null || lblStockActualSalida == null) return;
            Producto in = (Producto) cmbProdEntrada.getSelectedItem();
            lblStockActualEntrada.setText(in == null ? "-" : in.getExistenciaActual() + " " + in.getUnidadMedida()
                    + " (Máx: " + in.getNivelMaximo() + ")");
            Producto out = (Producto) cmbProdSalida.getSelectedItem();
            lblStockActualSalida.setText(out == null ? "-" : out.getExistenciaActual() + " " + out.getUnidadMedida()
                    + " (Mín: " + out.getNivelMinimo() + ")");
        }

        // ---------- Búsqueda por código (CU04) ----------

        private void buscarProductoEntrada() {
            String codigo = txtCodigoEntrada.getText().trim();
            try {
                Producto p = repo.getControladorMovimiento().localizarProducto(codigo);
                if (!p.isActivo()) {
                    JOptionPane.showMessageDialog(this, "El producto " + p.getCodigo() + " está inactivo y no admite nuevos movimientos.",
                            "Producto inactivo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                cmbProdEntrada.setSelectedItem(p);
            } catch (ProductoNoEncontradoException ex) {
                ofrecerRegistroProducto(ex.getCodigo());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Buscar producto", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void buscarProductoSalida() {
            String codigo = txtCodigoSalida.getText().trim();
            try {
                Producto p = repo.getControladorMovimiento().localizarProducto(codigo);
                if (!p.isActivo()) {
                    JOptionPane.showMessageDialog(this, "El producto " + p.getCodigo() + " está inactivo; no se puede registrar la salida.",
                            "Producto inactivo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                cmbProdSalida.setSelectedItem(p);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage() + "\nNo se puede registrar la salida.",
                        "Buscar producto", JOptionPane.WARNING_MESSAGE);
            }
        }

        /** Flujo alternativo CU10: si el producto no existe, ofrecer registrarlo (CU01). */
        private void ofrecerRegistroProducto(String codigo) {
            int op = JOptionPane.showConfirmDialog(this,
                    "No existe ningún producto con el código '" + codigo + "'.\n¿Desea registrarlo ahora?",
                    "Producto no encontrado", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (op == JOptionPane.YES_OPTION && registrarProductoNuevo != null) {
                registrarProductoNuevo.accept(codigo);
                refrescarCombos();
                Producto nuevo = repo.buscarProductoPorCodigo(codigo);
                if (nuevo != null) {
                    cmbProdEntrada.setSelectedItem(nuevo);
                    txtCantEntrada.requestFocusInWindow();
                }
            }
        }

        // ---------- Validaciones de formulario ----------

        private int leerCantidad(JTextField campo) {
            String t = campo.getText().trim();
            if (t.isEmpty()) throw new IllegalArgumentException("Ingrese la cantidad.");
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Ingrese una cantidad entera válida mayor a 0.");
            }
        }

        private LocalDate leerFecha(JTextField campo) {
            try {
                return LocalDate.parse(campo.getText().trim(), FMT_FECHA);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Fecha inválida. Use el formato dd/MM/aaaa (ej. "
                        + LocalDate.now().format(FMT_FECHA) + ").");
            }
        }

        private String mensajeAlerta(Producto p) {
            AlertaInventario a = repo.getGestorAlertas().getAlertaActivaDe(p.getCodigo());
            if (a == null) return "";
            return "\n\nALERTA - " + a.getTipo() + ":\n" + a.getMensaje();
        }

        private void procesarEntrada() {
            try {
                Producto sel = (Producto) cmbProdEntrada.getSelectedItem();
                if (sel == null) throw new IllegalArgumentException("Seleccione un producto.");
                int cant = leerCantidad(txtCantEntrada);
                Object provSel = cmbProveedor.getEditor().getItem();
                String prov = provSel == null ? "" : provSel.toString().trim();
                LocalDate fecha = leerFecha(txtFechaEntrada);

                repo.registrarEntrada(sel.getCodigo(), cant, prov, txtDocumento.getText(), fecha, usuarioActual);

                txtCantEntrada.setText("");
                txtDocumento.setText("");
                txtCodigoEntrada.setText("");
                txtFechaEntrada.setText(LocalDate.now().format(FMT_FECHA));
                onCambio.run();
                cmbProveedor.getEditor().setItem("");

                JOptionPane.showMessageDialog(this,
                        "Entrada registrada exitosamente.\nNueva existencia de " + sel.getNombre() + ": " + sel.getExistenciaActual() + mensajeAlerta(sel),
                        "Entrada Confirmada", JOptionPane.INFORMATION_MESSAGE);
            } catch (ProductoNoEncontradoException ex) {
                ofrecerRegistroProducto(ex.getCodigo());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void procesarSalida() {
            try {
                Producto sel = (Producto) cmbProdSalida.getSelectedItem();
                if (sel == null) throw new IllegalArgumentException("Seleccione un producto.");
                int cant = leerCantidad(txtCantSalida);
                String motivo = (String) cmbMotivoSalida.getSelectedItem();
                LocalDate fecha = leerFecha(txtFechaSalida);

                repo.registrarSalida(sel.getCodigo(), cant, motivo, fecha, usuarioActual);

                txtCantSalida.setText("");
                txtCodigoSalida.setText("");
                txtFechaSalida.setText(LocalDate.now().format(FMT_FECHA));
                onCambio.run();

                JOptionPane.showMessageDialog(this,
                        "Salida registrada exitosamente.\nNueva existencia de " + sel.getNombre() + ": " + sel.getExistenciaActual() + mensajeAlerta(sel),
                        "Salida Confirmada", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void refrescarCombos() {
            Producto selIn = (Producto) cmbProdEntrada.getSelectedItem();
            Producto selOut = (Producto) cmbProdSalida.getSelectedItem();

            cmbProdEntrada.removeAllItems();
            cmbProdSalida.removeAllItems();

            for (Producto p : repo.getProductos()) {
                if (p.isActivo()) {
                    cmbProdEntrada.addItem(p);
                    cmbProdSalida.addItem(p);
                }
            }
            if (selIn != null && selIn.isActivo()) cmbProdEntrada.setSelectedItem(selIn);
            if (selOut != null && selOut.isActivo()) cmbProdSalida.setSelectedItem(selOut);

            // Proveedores registrados (conserva lo que el usuario esté escribiendo)
            Object textoProveedor = cmbProveedor.getEditor().getItem();
            cmbProveedor.removeAllItems();
            for (Proveedor pr : repo.getProveedores()) cmbProveedor.addItem(pr.getNombre());
            cmbProveedor.getEditor().setItem(textoProveedor == null ? "" : textoProveedor);

            actualizarEtiquetasStock();
        }
    }

    // =========================================================================
    // 10. PANEL HISTORIAL DE MOVIMIENTOS (CU06)
    // =========================================================================
    public static class PanelHistorial extends JPanel {
        private static final DateTimeFormatter FMT_FECHA =
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

        private RepositorioSistema repo;
        private Usuario usuarioActual;
        private JComboBox<String> cmbTipo;
        private JTextField txtFiltroProducto;
        private JTextField txtDesde;
        private JTextField txtHasta;
        private JLabel lblEstado;
        private DefaultTableModel modeloTabla;

        public PanelHistorial(RepositorioSistema repo, Usuario usuarioActual) {
            this.repo = repo;
            this.usuarioActual = usuarioActual;
            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescarTabla();
        }

        private void construirUI() {
            JLabel lblTitulo = new JLabel("Historial de Movimientos del Inventario");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            JPanel filtros = new JPanel(new GridLayout(2, 1, 0, 0));
            filtros.setBackground(COLOR_PANEL);
            JPanel filaFiltros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
            filaFiltros.setOpaque(false);
            JPanel filaAcciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            filaAcciones.setOpaque(false);
            filtros.add(filaFiltros);
            filtros.add(filaAcciones);

            cmbTipo = new JComboBox<>(new String[]{"Todos", "ENTRADA", "SALIDA"});
            cmbTipo.addActionListener(e -> refrescarTabla());

            KeyAdapter alEscribir = new KeyAdapter() {
                @Override public void keyReleased(KeyEvent e) { refrescarTabla(); }
            };
            txtFiltroProducto = new JTextField(12);
            txtFiltroProducto.addKeyListener(alEscribir);
            txtDesde = new JTextField(8);
            txtDesde.setToolTipText("Fecha inicial dd/MM/aaaa (vacío = sin límite)");
            txtDesde.addKeyListener(alEscribir);
            txtHasta = new JTextField(8);
            txtHasta.setToolTipText("Fecha final dd/MM/aaaa (vacío = sin límite)");
            txtHasta.addKeyListener(alEscribir);

            JButton btnLimpiar = new JButton("Limpiar filtros");
            btnLimpiar.setFocusPainted(false);
            btnLimpiar.addActionListener(e -> {
                cmbTipo.setSelectedIndex(0);
                txtFiltroProducto.setText("");
                txtDesde.setText("");
                txtHasta.setText("");
                refrescarTabla();
            });

            JButton btnExportar = crearBotonEstilizado("EXPORTAR", "Exportar Excel / PDF (RNF13)", COLOR_PRIMARIO, Color.WHITE);
            btnExportar.addActionListener(e -> exportar());

            filaFiltros.add(new JLabel("Tipo:"));
            filaFiltros.add(cmbTipo);
            filaFiltros.add(new JLabel("Producto:"));
            filaFiltros.add(txtFiltroProducto);
            filaFiltros.add(new JLabel("Desde (dd/MM/aaaa):"));
            filaFiltros.add(txtDesde);
            filaFiltros.add(new JLabel("Hasta:"));
            filaFiltros.add(txtHasta);
            filaAcciones.add(btnLimpiar);
            filaAcciones.add(btnExportar);

            JPanel top = new JPanel(new BorderLayout(0, 10));
            top.setOpaque(false);
            top.add(lblTitulo, BorderLayout.NORTH);
            top.add(filtros, BorderLayout.CENTER);

            String[] cols = {"ID Mov.", "Fecha y Hora", "Tipo", "Código", "Producto", "Cantidad", "Existencia Resultante", "Detalle / Proveedor / Motivo", "Responsable"};
            modeloTabla = new DefaultTableModel(cols, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable tabla = new JTable(modeloTabla);
            estilizarTabla(tabla);

            tabla.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    setFont(FUENTE_NEGRITA);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    if (!isSelected) {
                        if ("ENTRADA".equals(value)) {
                            c.setForeground(COLOR_NORMAL);
                        } else {
                            c.setForeground(new Color(37, 99, 235));
                        }
                    }
                    return c;
                }
            });

            lblEstado = new JLabel(" ");
            lblEstado.setFont(FUENTE_NEGRITA);
            lblEstado.setBorder(new EmptyBorder(4, 2, 0, 0));

            add(top, BorderLayout.NORTH);
            add(new JScrollPane(tabla), BorderLayout.CENTER);
            add(lblEstado, BorderLayout.SOUTH);
        }

        /** Devuelve la fecha del campo, null si está vacío; lanza excepción si es inválida. */
        private LocalDate leerFecha(JTextField campo, String nombre) {
            String t = campo.getText().trim();
            if (t.isEmpty()) return null;
            try {
                return LocalDate.parse(t, FMT_FECHA);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Fecha '" + nombre + "' incompleta o inválida (use dd/MM/aaaa).");
            }
        }

        public void refrescarTabla() {
            modeloTabla.setRowCount(0);
            String tipoFiltro = (String) cmbTipo.getSelectedItem();
            String prodFiltro = txtFiltroProducto.getText().trim().toLowerCase();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

            LocalDate desde;
            LocalDate hasta;
            try {
                desde = leerFecha(txtDesde, "desde");
                hasta = leerFecha(txtHasta, "hasta");
            } catch (IllegalArgumentException ex) {
                lblEstado.setForeground(COLOR_ALERTA_EXCESO);
                ponerEstado(lblEstado, "ALERTA", ex.getMessage());
                return;
            }
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                lblEstado.setForeground(COLOR_ALERTA_EXCESO);
                ponerEstado(lblEstado, "ALERTA", "La fecha 'desde' no puede ser posterior a la fecha 'hasta'.");
                return;
            }

            for (MovimientoInventario m : repo.getHistorialMovimientos()) {
                if (!"Todos".equals(tipoFiltro) && !m.getTipoMovimiento().equals(tipoFiltro)) continue;
                if (!prodFiltro.isEmpty() &&
                        !m.getProducto().getNombre().toLowerCase().contains(prodFiltro) &&
                        !m.getProducto().getCodigo().toLowerCase().contains(prodFiltro)) continue;
                LocalDate dia = m.getFecha().toLocalDate();
                if (desde != null && dia.isBefore(desde)) continue;
                if (hasta != null && dia.isAfter(hasta)) continue;

                modeloTabla.addRow(new Object[]{
                        "MOV-" + m.getIdMovimiento(),
                        m.getFecha().format(fmt),
                        m.getTipoMovimiento(),
                        m.getProducto().getCodigo(),
                        m.getProducto().getNombre(),
                        (m.getTipoMovimiento().equals("ENTRADA") ? "+" : "-") + m.getCantidad(),
                        m.getExistenciaResultante(),
                        m.getDetalle(),
                        m.getResponsable().getNombreUsuario()
                });
            }

            if (modeloTabla.getRowCount() == 0) {
                // Flujo alternativo CU06
                lblEstado.setForeground(COLOR_ALERTA_EXCESO);
                ponerEstado(lblEstado, "INFO", "No hay movimientos registrados para los filtros / rango de fechas seleccionados.");
            } else {
                lblEstado.setForeground(new Color(100, 116, 139));
                lblEstado.setIcon(null);
                lblEstado.setText(modeloTabla.getRowCount() + " movimiento(s) encontrados.");
            }
        }

        private void exportar() {
            String filtro = "Tipo: " + cmbTipo.getSelectedItem()
                    + (txtFiltroProducto.getText().trim().isEmpty() ? "" : " | Producto: " + txtFiltroProducto.getText().trim())
                    + (txtDesde.getText().trim().isEmpty() ? "" : " | Desde: " + txtDesde.getText().trim())
                    + (txtHasta.getText().trim().isEmpty() ? "" : " | Hasta: " + txtHasta.getText().trim());
            boolean ok = ExportadorReportes.exportarTabla(this, "Historial de movimientos del inventario",
                    filtro, "historial_movimientos", modeloTabla);
            if (ok) repo.registrarLog(usuarioActual.getNombreUsuario(), "Historial", "Exportó el historial de movimientos (" + filtro + ")");
        }
    }

    // =========================================================================
    // 11. PANEL NIVELES DE INVENTARIO Y ALERTAS (CU07 Y CU08)
    // =========================================================================
    public static class PanelNivelesAlertas extends JPanel {
        private RepositorioSistema repo;
        private Usuario usuarioActual;
        private Runnable onCambio;
        private DefaultTableModel modeloBajoStock;
        private DefaultTableModel modeloExcesoStock;
        private DefaultTableModel modeloHistorialAlertas;
        private JLabel lblEstadoNiveles;

        public PanelNivelesAlertas(RepositorioSistema repo, Usuario usuarioActual, Runnable onCambio) {
            this.repo = repo;
            this.usuarioActual = usuarioActual;
            this.onCambio = onCambio;
            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescarDatos();
        }

        private DefaultTableModel modeloNoEditable(String[] columnas) {
            return new DefaultTableModel(columnas, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
        }

        private void construirUI() {
            JLabel lblTitulo = new JLabel("Control de Niveles de Inventario y Alertas");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            JPanel barra = new JPanel(new BorderLayout(10, 0));
            barra.setBackground(COLOR_PANEL);
            barra.setBorder(new EmptyBorder(8, 12, 8, 12));

            lblEstadoNiveles = new JLabel(" ");
            lblEstadoNiveles.setFont(FUENTE_NEGRITA);

            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            botones.setOpaque(false);
            JButton btnVerificar = crearBotonEstilizado("ACTUALIZAR", "Verificar niveles ahora", COLOR_SECUNDARIO, Color.WHITE);
            JButton btnExportar = crearBotonEstilizado("EXPORTAR", "Exportar reporte de abastecimiento", COLOR_PRIMARIO, Color.WHITE);
            btnVerificar.addActionListener(e -> {
                repo.getControladorAlertas().verificarNiveles();
                repo.registrarLog(usuarioActual.getNombreUsuario(), "Alertas", "Verificación manual de niveles de inventario.");
                onCambio.run();
                JOptionPane.showMessageDialog(this, "Verificación completada. Alertas activas: "
                        + repo.getControladorAlertas().mostrarAlertasActivas().size(), "Verificar niveles", JOptionPane.INFORMATION_MESSAGE);
            });
            btnExportar.addActionListener(e -> exportarReporteAbastecimiento());
            botones.add(btnVerificar);
            botones.add(btnExportar);

            barra.add(lblEstadoNiveles, BorderLayout.CENTER);
            barra.add(botones, BorderLayout.EAST);

            JPanel norte = new JPanel(new BorderLayout(0, 10));
            norte.setOpaque(false);
            norte.add(lblTitulo, BorderLayout.NORTH);
            norte.add(barra, BorderLayout.CENTER);

            // Dos listas comparativas según CU07: Productos por reabastecer vs Exceso
            JPanel panelListas = new JPanel(new GridLayout(1, 2, 15, 0));
            panelListas.setOpaque(false);

            // Lista 1: Stock Bajo
            JPanel cardBajo = new JPanel(new BorderLayout(5, 5));
            cardBajo.setBackground(COLOR_PANEL);
            cardBajo.setBorder(new CompoundBorder(
                    new TitledBorder(new LineBorder(COLOR_ALERTA_BAJO), "Productos por Reabastecer (Stock <= Mínimo)"),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            modeloBajoStock = modeloNoEditable(new String[]{"Código", "Producto", "Actual", "Mínimo", "Sugerido"});
            JTable tablaBajo = new JTable(modeloBajoStock);
            estilizarTabla(tablaBajo);
            cardBajo.add(new JScrollPane(tablaBajo), BorderLayout.CENTER);

            // Lista 2: Exceso de Inventario
            JPanel cardExceso = new JPanel(new BorderLayout(5, 5));
            cardExceso.setBackground(COLOR_PANEL);
            cardExceso.setBorder(new CompoundBorder(
                    new TitledBorder(new LineBorder(COLOR_ALERTA_EXCESO), "Productos con Exceso (Stock >= Máximo)"),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            modeloExcesoStock = modeloNoEditable(new String[]{"Código", "Producto", "Actual", "Máximo", "Excedente"});
            JTable tablaExceso = new JTable(modeloExcesoStock);
            estilizarTabla(tablaExceso);
            cardExceso.add(new JScrollPane(tablaExceso), BorderLayout.CENTER);

            panelListas.add(cardBajo);
            panelListas.add(cardExceso);

            // Panel inferior: Log de Alertas generadas por el Observer
            JPanel cardAlertas = new JPanel(new BorderLayout(5, 5));
            cardAlertas.setBackground(COLOR_PANEL);
            cardAlertas.setBorder(new CompoundBorder(
                    new TitledBorder(new LineBorder(new Color(203, 213, 225)), "Registro de Notificaciones Observer (AlertaInventario)"),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            modeloHistorialAlertas = modeloNoEditable(new String[]{"ID", "Estado", "Tipo Alerta", "Producto", "Mensaje Generado", "Fecha/Hora"});
            JTable tablaAlertas = new JTable(modeloHistorialAlertas);
            estilizarTabla(tablaAlertas);
            cardAlertas.add(new JScrollPane(tablaAlertas), BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelListas, cardAlertas);
            split.setResizeWeight(0.52);
            split.setBorder(null);

            add(norte, BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);
        }

        public void refrescarDatos() {
            modeloBajoStock.setRowCount(0);
            modeloExcesoStock.setRowCount(0);
            modeloHistorialAlertas.setRowCount(0);

            for (Producto p : repo.getProductos()) {
                if (!p.isActivo()) continue;
                if (p.getExistenciaActual() <= p.getNivelMinimo()) {
                    int sugerido = p.getNivelMinimo() - p.getExistenciaActual() + 5;
                    modeloBajoStock.addRow(new Object[]{
                            p.getCodigo(), p.getNombre(), p.getExistenciaActual(), p.getNivelMinimo(), "Pedir +" + sugerido
                    });
                } else if (p.getExistenciaActual() >= p.getNivelMaximo()) {
                    int exceso = p.getExistenciaActual() - p.getNivelMaximo();
                    modeloExcesoStock.addRow(new Object[]{
                            p.getCodigo(), p.getNombre(), p.getExistenciaActual(), p.getNivelMaximo(), "+" + exceso + " und"
                    });
                }
            }

            // Flujo alternativo CU07: ningún producto fuera de rango
            if (modeloBajoStock.getRowCount() == 0 && modeloExcesoStock.getRowCount() == 0) {
                lblEstadoNiveles.setForeground(COLOR_NORMAL);
                ponerEstado(lblEstadoNiveles, "OK", "Todos los productos activos están dentro del rango definido.");
            } else {
                lblEstadoNiveles.setForeground(COLOR_TEXTO);
                lblEstadoNiveles.setIcon(null);
                lblEstadoNiveles.setText(modeloBajoStock.getRowCount() + " por reabastecer  |  "
                        + modeloExcesoStock.getRowCount() + " con exceso");
                lblEstadoNiveles.setToolTipText("Verificación automática cada "
                        + (VentanaPrincipal.INTERVALO_VERIFICACION_MS / 1000) + " s");
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            List<AlertaInventario> todas = new ArrayList<>(repo.getGestorAlertas().getTodasLasAlertas());
            Collections.reverse(todas); // más recientes primero
            for (AlertaInventario a : todas) {
                modeloHistorialAlertas.addRow(new Object[]{
                        "ALT-" + a.getIdAlerta(),
                        a.isActiva() ? "ACTIVA" : "RESUELTA / INACTIVA",
                        a.getTipo(),
                        a.getNombreProducto() + " (" + a.getCodigoProducto() + ")",
                        a.getMensaje(),
                        a.getFecha().format(fmt)
                });
            }
        }

        /** Reporte de abastecimiento (CU07, flujo alternativo: exportar). */
        private void exportarReporteAbastecimiento() {
            String[] encabezados = {"Estado", "Código", "Producto", "Existencia", "Umbral", "Acción sugerida"};
            List<Object[]> filas = new ArrayList<>();
            for (int i = 0; i < modeloBajoStock.getRowCount(); i++) {
                filas.add(new Object[]{"STOCK BAJO", modeloBajoStock.getValueAt(i, 0), modeloBajoStock.getValueAt(i, 1),
                        modeloBajoStock.getValueAt(i, 2), "Mín: " + modeloBajoStock.getValueAt(i, 3), modeloBajoStock.getValueAt(i, 4)});
            }
            for (int i = 0; i < modeloExcesoStock.getRowCount(); i++) {
                filas.add(new Object[]{"EXCESO", modeloExcesoStock.getValueAt(i, 0), modeloExcesoStock.getValueAt(i, 1),
                        modeloExcesoStock.getValueAt(i, 2), "Máx: " + modeloExcesoStock.getValueAt(i, 3),
                        "Excedente " + modeloExcesoStock.getValueAt(i, 4)});
            }
            if (filas.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Ningún producto se encuentra fuera de rango; no hay datos para el reporte.",
                        "Reporte de abastecimiento", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            boolean ok = ExportadorReportes.exportar(this, "Reporte de abastecimiento - Niveles de inventario",
                    "Productos por reabastecer y con exceso de inventario", "reporte_abastecimiento", encabezados, filas);
            if (ok) repo.registrarLog(usuarioActual.getNombreUsuario(), "Niveles", "Exportó el reporte de abastecimiento.");
        }
    }

    // =========================================================================
    // 12. PANEL GESTIÓN DE USUARIOS, AUDITORÍA Y RESPALDOS (CU09 - SOLO ADMINISTRADOR)
    // =========================================================================
    public static class PanelUsuarios extends JPanel {
        private RepositorioSistema repo;
        private Usuario adminActual;
        private java.util.function.Consumer<RepositorioSistema> onRestaurar;
        private DefaultTableModel modeloUsuarios;
        private DefaultTableModel modeloLogs;
        private JTable tablaUsuarios;

        public PanelUsuarios(RepositorioSistema repo, Usuario adminActual,
                             java.util.function.Consumer<RepositorioSistema> onRestaurar) {
            this.repo = repo;
            this.adminActual = adminActual;
            this.onRestaurar = onRestaurar;
            setLayout(new BorderLayout(15, 15));
            setBackground(COLOR_FONDO);
            setBorder(new EmptyBorder(20, 25, 20, 25));

            construirUI();
            refrescar();
        }

        private void construirUI() {
            JLabel lblTitulo = new JLabel("Administración de Usuarios, Roles y Auditoría (RNF29)");
            lblTitulo.setFont(FUENTE_TITULO);
            lblTitulo.setForeground(COLOR_TEXTO);

            // Panel superior: Gestión de usuarios
            JPanel panelSup = new JPanel(new BorderLayout(10, 10));
            panelSup.setBackground(COLOR_PANEL);
            panelSup.setBorder(new CompoundBorder(
                    new TitledBorder("Cuentas de Usuario Registradas"),
                    new EmptyBorder(10, 10, 10, 10)
            ));

            JPanel barraBotones = new JPanel(new BorderLayout(0, 8));
            barraBotones.setOpaque(false);

            JPanel botonesUsuarios = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            botonesUsuarios.setOpaque(false);
            JButton btnCrearUsuario = crearBotonEstilizado("+ Nuevo Usuario", COLOR_SECUNDARIO, Color.WHITE);
            JButton btnEditarUsuario = crearBotonEstilizado("EDITAR", "Modificar Usuario", new Color(37, 99, 235), Color.WHITE);
            JButton btnAlternarEstado = crearBotonEstilizado("Activar / Desactivar Cuenta", COLOR_ALERTA_EXCESO, Color.WHITE);
            btnCrearUsuario.addActionListener(e -> dialogoCrearUsuario());
            btnEditarUsuario.addActionListener(e -> dialogoEditarUsuario());
            btnAlternarEstado.addActionListener(e -> toggleUsuario(tablaUsuarios));
            botonesUsuarios.add(btnCrearUsuario);
            botonesUsuarios.add(btnEditarUsuario);
            botonesUsuarios.add(btnAlternarEstado);

            JPanel botonesRespaldo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            botonesRespaldo.setOpaque(false);
            JButton btnRespaldo = crearBotonEstilizado("GUARDAR", "Crear Respaldo (RNF24)", COLOR_PRIMARIO, Color.WHITE);
            JButton btnRestaurar = crearBotonEstilizado("RESTAURAR", "Restaurar Respaldo (RNF25)", new Color(71, 85, 105), Color.WHITE);
            btnRespaldo.addActionListener(e -> crearRespaldo());
            btnRestaurar.addActionListener(e -> restaurarRespaldo());
            botonesRespaldo.add(btnRespaldo);
            botonesRespaldo.add(btnRestaurar);

            barraBotones.add(botonesUsuarios, BorderLayout.NORTH);
            barraBotones.add(botonesRespaldo, BorderLayout.CENTER);

            modeloUsuarios = new DefaultTableModel(new String[]{"ID", "Usuario", "Nombres Completos", "Rol Asignado", "Permisos", "Estado"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tablaUsuarios = new JTable(modeloUsuarios);
            estilizarTabla(tablaUsuarios);

            panelSup.add(barraBotones, BorderLayout.NORTH);
            panelSup.add(new JScrollPane(tablaUsuarios), BorderLayout.CENTER);

            // Panel inferior: Log de trazabilidad (RNF29)
            JPanel panelInf = new JPanel(new BorderLayout(10, 10));
            panelInf.setBackground(COLOR_PANEL);
            panelInf.setBorder(new CompoundBorder(
                    new TitledBorder("Historial de Auditoría del Sistema (Trazabilidad RNF29)"),
                    new EmptyBorder(10, 10, 10, 10)
            ));
            modeloLogs = new DefaultTableModel(new String[]{"Fecha y Hora", "Usuario", "Módulo", "Acción Ejecutada"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            JTable tablaLogs = new JTable(modeloLogs);
            estilizarTabla(tablaLogs);
            panelInf.add(new JScrollPane(tablaLogs), BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelSup, panelInf);
            split.setResizeWeight(0.5);
            split.setBorder(null);

            add(lblTitulo, BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);
        }

        private Usuario usuarioSeleccionado() {
            int fila = tablaUsuarios.getSelectedRow();
            if (fila < 0) {
                JOptionPane.showMessageDialog(this, "Seleccione un usuario de la tabla.");
                return null;
            }
            String username = (String) modeloUsuarios.getValueAt(fila, 1);
            for (Usuario u : repo.getUsuarios()) {
                if (u.getNombreUsuario().equals(username)) return u;
            }
            return null;
        }

        private void toggleUsuario(JTable tabla) {
            Usuario u = usuarioSeleccionado();
            if (u == null) return;
            // Flujo alternativo CU09: No permitir desactivar la propia cuenta
            if (u.getNombreUsuario().equalsIgnoreCase(adminActual.getNombreUsuario())) {
                JOptionPane.showMessageDialog(this, "No puede desactivar su propia cuenta de administrador.", "Acción Restringida", JOptionPane.WARNING_MESSAGE);
                return;
            }
            u.setActivo(!u.isActivo());
            repo.registrarLog(adminActual.getNombreUsuario(), "Usuarios",
                    (u.isActivo() ? "Activó" : "Desactivó") + " al usuario: " + u.getNombreUsuario());
            refrescar();
        }

        private void dialogoCrearUsuario() {
            JDialog d = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Registrar Nuevo Usuario", true);
            d.setSize(420, 320);
            d.setLocationRelativeTo(this);
            d.setLayout(new BorderLayout());

            JPanel form = new JPanel(new GridLayout(4, 2, 10, 12));
            form.setBorder(new EmptyBorder(20, 20, 20, 20));

            JTextField txtUser = new JTextField();
            JTextField txtNombres = new JTextField();
            JPasswordField txtPass = new JPasswordField();
            JComboBox<Rol> cmbRol = new JComboBox<>(repo.getRoles().toArray(new Rol[0]));

            form.add(new JLabel("Nombre de Usuario:*")); form.add(txtUser);
            form.add(new JLabel("Nombres Completos:*")); form.add(txtNombres);
            form.add(new JLabel("Contraseña (mín. 4):*")); form.add(txtPass);
            form.add(new JLabel("Rol del Sistema:*")); form.add(cmbRol);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnCancelar = new JButton("Cancelar");
            btnCancelar.addActionListener(e -> d.dispose());
            JButton btnGuardar = crearBotonEstilizado("Crear Usuario", COLOR_SECUNDARIO, Color.WHITE);
            btnGuardar.addActionListener(e -> {
                String u = txtUser.getText().trim();
                String n = txtNombres.getText().trim();
                String p = new String(txtPass.getPassword());

                if (u.isEmpty() || n.isEmpty() || p.length() < 4) {
                    JOptionPane.showMessageDialog(d, "Complete todos los campos (contraseña mín. 4 caracteres).");
                    return;
                }
                if (u.contains(" ")) {
                    JOptionPane.showMessageDialog(d, "El nombre de usuario no puede contener espacios.");
                    return;
                }
                for (Usuario existente : repo.getUsuarios()) {
                    if (existente.getNombreUsuario().equalsIgnoreCase(u)) {
                        JOptionPane.showMessageDialog(d, "El nombre de usuario ya existe.");
                        return;
                    }
                }
                int nuevoId = 1;
                for (Usuario existente : repo.getUsuarios()) nuevoId = Math.max(nuevoId, existente.getIdUsuario() + 1);
                Rol rol = (Rol) cmbRol.getSelectedItem();
                repo.getUsuarios().add(new Usuario(nuevoId, u, n, p, rol, true));
                repo.registrarLog(adminActual.getNombreUsuario(), "Usuarios", "Creó usuario: " + u + " (rol: " + rol.getNombre() + ")");
                refrescar();
                d.dispose();
            });
            btns.add(btnCancelar);
            btns.add(btnGuardar);

            d.add(form, BorderLayout.CENTER);
            d.add(btns, BorderLayout.SOUTH);
            d.setVisible(true);
        }

        /** CU09 - Modificar usuario: nombres, rol y (opcional) contraseña. */
        private void dialogoEditarUsuario() {
            Usuario usuario = usuarioSeleccionado();
            if (usuario == null) return;

            JDialog d = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Modificar Usuario", true);
            d.setSize(460, 340);
            d.setLocationRelativeTo(this);
            d.setLayout(new BorderLayout());

            JPanel form = new JPanel(new GridLayout(4, 2, 10, 12));
            form.setBorder(new EmptyBorder(20, 20, 20, 20));

            JTextField txtUser = new JTextField(usuario.getNombreUsuario());
            txtUser.setEnabled(false);
            JTextField txtNombres = new JTextField(usuario.getNombresCompletos());
            JPasswordField txtPass = new JPasswordField();
            txtPass.setToolTipText("Déjelo vacío para conservar la contraseña actual");
            JComboBox<Rol> cmbRol = new JComboBox<>(repo.getRoles().toArray(new Rol[0]));
            cmbRol.setSelectedItem(usuario.getRol());

            form.add(new JLabel("Nombre de Usuario:")); form.add(txtUser);
            form.add(new JLabel("Nombres Completos:*")); form.add(txtNombres);
            form.add(new JLabel("Nueva contraseña (opcional):")); form.add(txtPass);
            form.add(new JLabel("Rol del Sistema:*")); form.add(cmbRol);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnCancelar = new JButton("Cancelar");
            btnCancelar.addActionListener(e -> d.dispose());
            JButton btnGuardar = crearBotonEstilizado("Guardar Cambios", COLOR_SECUNDARIO, Color.WHITE);
            btnGuardar.addActionListener(e -> {
                String n = txtNombres.getText().trim();
                String p = new String(txtPass.getPassword());
                Rol rol = (Rol) cmbRol.getSelectedItem();
                if (n.isEmpty()) {
                    JOptionPane.showMessageDialog(d, "Los nombres completos son obligatorios.");
                    return;
                }
                if (!p.isEmpty() && p.length() < 4) {
                    JOptionPane.showMessageDialog(d, "La contraseña no cumple los requisitos (mínimo 4 caracteres).");
                    return;
                }
                boolean esPropio = usuario.getNombreUsuario().equalsIgnoreCase(adminActual.getNombreUsuario());
                if (esPropio && !rol.tienePermiso(Rol.P_USUARIOS)) {
                    JOptionPane.showMessageDialog(d, "No puede quitarse a sí mismo el rol de administrador.",
                            "Acción Restringida", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                StringBuilder cambios = new StringBuilder();
                if (!n.equals(usuario.getNombresCompletos())) cambios.append(" nombres;");
                if (rol != usuario.getRol()) cambios.append(" rol ").append(usuario.getRol().getNombre()).append(" -> ").append(rol.getNombre()).append(";");
                if (!p.isEmpty()) cambios.append(" contraseña;");

                usuario.setNombresCompletos(n);
                usuario.setRol(rol);
                if (!p.isEmpty()) usuario.setContrasena(p);
                repo.registrarLog(adminActual.getNombreUsuario(), "Usuarios", "Modificó usuario: " + usuario.getNombreUsuario()
                        + (cambios.length() == 0 ? " (sin cambios)" : " (" + cambios.toString().trim() + ")"));
                refrescar();
                d.dispose();
                JOptionPane.showMessageDialog(this, "Usuario actualizado. Los permisos aplican en el siguiente inicio de sesión.",
                        "Modificar Usuario", JOptionPane.INFORMATION_MESSAGE);
            });
            btns.add(btnCancelar);
            btns.add(btnGuardar);

            d.add(form, BorderLayout.CENTER);
            d.add(btns, BorderLayout.SOUTH);
            d.setVisible(true);
        }

        private void crearRespaldo() {
            try {
                File f = repo.crearRespaldo(adminActual);
                refrescar();
                JOptionPane.showMessageDialog(this, "Respaldo creado correctamente:\n" + f.getAbsolutePath(),
                        "Respaldo (RNF24)", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "No se pudo crear el respaldo: " + ex.getMessage(),
                        "Respaldo (RNF24)", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void restaurarRespaldo() {
            File carpeta = new File(RepositorioSistema.CARPETA_RESPALDOS);
            JFileChooser fc = new JFileChooser(carpeta.exists() ? carpeta : new File("."));
            fc.setDialogTitle("Seleccione el respaldo a restaurar");
            fc.setFileFilter(new FileNameExtensionFilter("Respaldos del inventario (*.dat)", "dat"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            int op = JOptionPane.showConfirmDialog(this,
                    "Se reemplazará TODA la información actual por la del respaldo:\n" + fc.getSelectedFile().getName()
                            + "\n\n¿Desea continuar? Deberá iniciar sesión nuevamente.",
                    "Restaurar respaldo (RNF25)", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (op != JOptionPane.YES_OPTION) return;
            try {
                RepositorioSistema restaurado = RepositorioSistema.restaurarRespaldo(fc.getSelectedFile(), adminActual);
                JOptionPane.showMessageDialog(this, "Información restaurada correctamente. Inicie sesión nuevamente.",
                        "Restaurar respaldo (RNF25)", JOptionPane.INFORMATION_MESSAGE);
                onRestaurar.accept(restaurado);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "El archivo seleccionado no es un respaldo válido: " + ex.getMessage(),
                        "Restaurar respaldo (RNF25)", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void refrescar() {
            modeloUsuarios.setRowCount(0);
            for (Usuario u : repo.getUsuarios()) {
                modeloUsuarios.addRow(new Object[]{
                        u.getIdUsuario(),
                        u.getNombreUsuario(),
                        u.getNombresCompletos(),
                        u.getRol().getNombre(),
                        u.getRol().obtenerPermisos(),
                        u.isActivo() ? "ACTIVO" : "INACTIVO"
                });
            }

            modeloLogs.setRowCount(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            for (RegistroAccion log : repo.getLogsAuditoria()) {
                modeloLogs.addRow(new Object[]{
                        log.getFecha().format(fmt),
                        log.getUsuario(),
                        log.getModulo(),
                        log.getAccion()
                });
            }
        }
    }

    // =========================================================================
    // 13. EXPORTACIÓN DE REPORTES A EXCEL (CSV) Y PDF (RNF13)
    // =========================================================================
    public static final class ExportadorReportes {
        private static final int MAX_CARACTERES_LINEA = 168; // Courier 7.5 pt en hoja horizontal
        private static final int LINEAS_POR_PAGINA = 52;

        private ExportadorReportes() {}

        /** Exporta todas las filas visibles de un modelo de tabla. */
        public static boolean exportarTabla(Component padre, String titulo, String subtitulo, String nombreBase, TableModel modelo) {
            String[] enc = new String[modelo.getColumnCount()];
            for (int c = 0; c < enc.length; c++) enc[c] = modelo.getColumnName(c);
            List<Object[]> filas = new ArrayList<>();
            for (int r = 0; r < modelo.getRowCount(); r++) {
                Object[] fila = new Object[enc.length];
                for (int c = 0; c < enc.length; c++) fila[c] = modelo.getValueAt(r, c);
                filas.add(fila);
            }
            return exportar(padre, titulo, subtitulo, nombreBase, enc, filas);
        }

        /** Pide el destino (CSV para Excel o PDF) y escribe el reporte. Devuelve true si se exportó. */
        public static boolean exportar(Component padre, String titulo, String subtitulo, String nombreBase,
                                       String[] encabezados, List<Object[]> filas) {
            if (filas.isEmpty()) {
                JOptionPane.showMessageDialog(padre, "No hay datos para exportar con los filtros actuales.",
                        "Exportar reporte", JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
            final String sello = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            final JFileChooser fc = new JFileChooser();
            final FileNameExtensionFilter filtroExcel = new FileNameExtensionFilter("Excel - valores separados por ; (*.csv)", "csv");
            final FileNameExtensionFilter filtroPdf = new FileNameExtensionFilter("Documento PDF (*.pdf)", "pdf");
            fc.setDialogTitle("Exportar reporte (RNF13)");
            fc.setAcceptAllFileFilterUsed(false);
            fc.addChoosableFileFilter(filtroExcel);
            fc.addChoosableFileFilter(filtroPdf);
            fc.setFileFilter(filtroExcel);
            fc.setSelectedFile(new File(nombreBase + "_" + sello + ".csv"));
            fc.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, ev -> {
                String ext = fc.getFileFilter() == filtroPdf ? ".pdf" : ".csv";
                fc.setSelectedFile(new File(nombreBase + "_" + sello + ext));
            });

            if (fc.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) return false;
            File archivo = fc.getSelectedFile();
            boolean pdf = fc.getFileFilter() == filtroPdf || archivo.getName().toLowerCase().endsWith(".pdf");
            String extension = pdf ? ".pdf" : ".csv";
            if (!archivo.getName().toLowerCase().endsWith(extension)) {
                archivo = new File(archivo.getParentFile(), archivo.getName() + extension);
            }
            if (archivo.exists()) {
                int op = JOptionPane.showConfirmDialog(padre, "El archivo ya existe. ¿Desea reemplazarlo?",
                        "Exportar reporte", JOptionPane.YES_NO_OPTION);
                if (op != JOptionPane.YES_OPTION) return false;
            }
            try {
                if (pdf) escribirPDF(archivo, titulo, subtitulo, encabezados, filas);
                else escribirCSV(archivo, titulo, subtitulo, encabezados, filas);
                JOptionPane.showMessageDialog(padre, "Reporte exportado correctamente:\n" + archivo.getAbsolutePath(),
                        "Exportar reporte (RNF13)", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(padre, "No se pudo exportar el reporte: " + ex.getMessage(),
                        "Exportar reporte", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        // ---------------- CSV (se abre directamente en Excel) ----------------

        static void escribirCSV(File archivo, String titulo, String subtitulo, String[] encabezados, List<Object[]> filas) throws IOException {
            try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
                w.write('﻿'); // BOM: Excel reconoce tildes y eñes
                w.write(campoCSV(titulo) + "\r\n");
                if (subtitulo != null && !subtitulo.isEmpty()) w.write(campoCSV(subtitulo) + "\r\n");
                w.write(campoCSV("Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))) + "\r\n\r\n");
                w.write(lineaCSV(encabezados));
                for (Object[] fila : filas) w.write(lineaCSV(fila));
            }
        }

        private static String lineaCSV(Object[] valores) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < valores.length; i++) {
                if (i > 0) sb.append(';');
                sb.append(campoCSV(valores[i]));
            }
            return sb.append("\r\n").toString();
        }

        private static String campoCSV(Object valor) {
            String s = valor == null ? "" : String.valueOf(valor);
            if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains(",")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }

        // ---------------- PDF (generado sin librerías externas) ----------------

        static void escribirPDF(File archivo, String titulo, String subtitulo, String[] encabezados, List<Object[]> filas) throws IOException {
            int n = encabezados.length;
            int[] ancho = new int[n];
            for (int c = 0; c < n; c++) ancho[c] = Math.min(40, encabezados[c].length());
            for (Object[] fila : filas) {
                for (int c = 0; c < n; c++) ancho[c] = Math.min(40, Math.max(ancho[c], texto(fila[c]).length()));
            }
            // Ajustar al ancho de la hoja reduciendo la columna más ancha
            while (anchoTotal(ancho) > MAX_CARACTERES_LINEA) {
                int mayor = 0;
                for (int c = 1; c < n; c++) if (ancho[c] > ancho[mayor]) mayor = c;
                if (ancho[mayor] <= 6) break;
                ancho[mayor]--;
            }

            String encabezado = filaTexto(encabezados, ancho);
            String separador = repetir('-', Math.min(MAX_CARACTERES_LINEA, encabezado.length()));
            List<String> cuerpo = new ArrayList<>();
            for (Object[] fila : filas) cuerpo.add(filaTexto(fila, ancho));

            // Paginación: título solo en la primera página, encabezado de tabla en todas
            List<List<String>> paginas = new ArrayList<>();
            List<String> actual = new ArrayList<>();
            actual.add("MERCADO LA ECONOMÍA - " + titulo);
            if (subtitulo != null && !subtitulo.isEmpty()) actual.add(subtitulo);
            actual.add("Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    + "   |   Registros: " + filas.size());
            actual.add("");
            actual.add(encabezado);
            actual.add(separador);
            for (String linea : cuerpo) {
                if (actual.size() >= LINEAS_POR_PAGINA) {
                    paginas.add(actual);
                    actual = new ArrayList<>();
                    actual.add(encabezado);
                    actual.add(separador);
                }
                actual.add(linea);
            }
            paginas.add(actual);

            Charset cp1252 = Charset.forName("windows-1252");
            CharsetEncoder codificador = cp1252.newEncoder();
            int totalPaginas = paginas.size();
            int totalObjetos = 3 + 2 * totalPaginas;
            List<byte[]> objetos = new ArrayList<>();

            objetos.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < totalPaginas; i++) kids.append(4 + 2 * i).append(" 0 R ");
            objetos.add(ascii("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + totalPaginas + " >>"));
            objetos.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>"));

            for (int i = 0; i < totalPaginas; i++) {
                StringBuilder cs = new StringBuilder();
                cs.append("BT /F1 7.5 Tf 9.5 TL 30 560 Td\n");
                boolean primeraLinea = true;
                for (String linea : paginas.get(i)) {
                    if (!primeraLinea) cs.append("T*\n");
                    cs.append('(').append(escaparPDF(linea, codificador)).append(") Tj\n");
                    primeraLinea = false;
                }
                cs.append("ET\n");
                cs.append("BT /F1 7 Tf 30 20 Td (").append(escaparPDF("Página " + (i + 1) + " de " + totalPaginas
                        + "  -  Sistema de Gestión y Control de Inventario", codificador)).append(") Tj ET\n");
                byte[] contenido = cs.toString().getBytes(cp1252);

                int idContenido = 5 + 2 * i;
                objetos.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                        + idContenido + " 0 R >>"));
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                stream.write(ascii("<< /Length " + contenido.length + " >>\nstream\n"));
                stream.write(contenido);
                stream.write(ascii("\nendstream"));
                objetos.add(stream.toByteArray());
            }

            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(archivo))) {
                ByteArrayOutputStream pdf = new ByteArrayOutputStream();
                pdf.write(ascii("%PDF-1.4\n"));
                pdf.write(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});
                long[] posiciones = new long[totalObjetos + 1];
                for (int i = 0; i < objetos.size(); i++) {
                    posiciones[i + 1] = pdf.size();
                    pdf.write(ascii((i + 1) + " 0 obj\n"));
                    pdf.write(objetos.get(i));
                    pdf.write(ascii("\nendobj\n"));
                }
                long inicioXref = pdf.size();
                StringBuilder xref = new StringBuilder();
                xref.append("xref\n0 ").append(totalObjetos + 1).append("\n0000000000 65535 f \n");
                for (int i = 1; i <= totalObjetos; i++) xref.append(String.format("%010d 00000 n \n", posiciones[i]));
                xref.append("trailer\n<< /Size ").append(totalObjetos + 1).append(" /Root 1 0 R >>\nstartxref\n")
                        .append(inicioXref).append("\n%%EOF\n");
                pdf.write(ascii(xref.toString()));
                pdf.writeTo(out);
            }
        }

        private static int anchoTotal(int[] ancho) {
            int total = 0;
            for (int a : ancho) total += a;
            return total + 3 * (ancho.length - 1);
        }

        private static String texto(Object v) {
            return v == null ? "" : String.valueOf(v).replace('\n', ' ').replace('\r', ' ');
        }

        private static String filaTexto(Object[] valores, int[] ancho) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < ancho.length; c++) {
                if (c > 0) sb.append(" | ");
                String s = texto(c < valores.length ? valores[c] : "");
                if (s.length() > ancho[c]) s = s.substring(0, Math.max(0, ancho[c] - 1)) + "~";
                sb.append(s);
                for (int k = s.length(); k < ancho[c]; k++) sb.append(' ');
            }
            return sb.toString();
        }

        private static String repetir(char ch, int veces) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < veces; i++) sb.append(ch);
            return sb.toString();
        }

        private static String escaparPDF(String s, CharsetEncoder codificador) {
            StringBuilder sb = new StringBuilder();
            for (char ch : s.toCharArray()) {
                if (ch == '\\' || ch == '(' || ch == ')') sb.append('\\').append(ch);
                else if (codificador.canEncode(ch)) sb.append(ch);
                else sb.append('?');
            }
            return sb.toString();
        }

        private static byte[] ascii(String s) {
            return s.getBytes(StandardCharsets.US_ASCII);
        }
    }

    // =========================================================================
    // 14. MÉTODOS UTILITARIOS DE ESTILO UI
    // =========================================================================
    private static JButton crearBotonEstilizado(String texto, Color fondo, Color textoColor) {
        JButton btn = new JButton(texto);
        btn.setFont(FUENTE_NEGRITA);
        btn.setBackground(fondo);
        btn.setForeground(textoColor);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        return btn;
    }

    private static JButton crearBotonEstilizado(String icono, String texto, Color fondo, Color textoColor) {
        JButton btn = crearBotonEstilizado(texto, fondo, textoColor);
        btn.setIcon(new Icono(icono));
        btn.setIconTextGap(8);
        return btn;
    }

    private static void ponerEstado(JLabel lbl, String icono, String texto) {
        lbl.setIcon(icono == null ? null : new Icono(icono));
        lbl.setText(texto);
    }

    /**
     * Iconos vectoriales dibujados con Java2D. Swing no muestra emojis,
     * asi que cada icono se pinta con lineas y figuras simples.
     * Si no se indica color, toma el color de texto del componente.
     */
    static class Icono implements Icon {
        private final String tipo;
        private final int tam;
        private final Color color;

        Icono(String tipo) { this(tipo, 16, null); }
        Icono(String tipo, int tam) { this(tipo, tam, null); }
        Icono(String tipo, int tam, Color color) {
            this.tipo = tipo;
            this.tam = tam;
            this.color = color;
        }

        @Override public int getIconWidth() { return tam; }
        @Override public int getIconHeight() { return tam; }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(tam / 16.0, tam / 16.0);
            g.setColor(color != null ? color : (c != null ? c.getForeground() : Color.DARK_GRAY));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            dibujar(g, tipo);
            g.dispose();
        }

        private static void dibujar(Graphics2D g, String tipo) {
            Path2D p = new Path2D.Double();
            switch (tipo) {
                case "CARRITO":
                    p.moveTo(1, 2); p.lineTo(3.5, 2); p.lineTo(5.5, 10.5); p.lineTo(13, 10.5);
                    p.lineTo(14.5, 4.5); p.lineTo(4.3, 4.5);
                    g.draw(p);
                    g.fill(new Ellipse2D.Double(5, 12, 2.6, 2.6));
                    g.fill(new Ellipse2D.Double(11, 12, 2.6, 2.6));
                    break;
                case "CAMPANA":
                    p.moveTo(2.8, 12); p.lineTo(4.2, 10.3); p.lineTo(4.2, 7);
                    p.curveTo(4.2, 3.6, 11.8, 3.6, 11.8, 7);
                    p.lineTo(11.8, 10.3); p.lineTo(13.2, 12); p.closePath();
                    g.fill(p);
                    g.fill(new Ellipse2D.Double(6.6, 12.4, 2.8, 2.6));
                    g.fill(new Ellipse2D.Double(7.2, 1.6, 1.6, 1.6));
                    break;
                case "USUARIO":
                    g.fill(new Ellipse2D.Double(5, 1.5, 6, 6));
                    p.moveTo(2, 15); p.curveTo(2, 9, 14, 9, 14, 15); p.closePath();
                    g.fill(p);
                    break;
                case "USUARIOS":
                    g.fill(new Ellipse2D.Double(1.8, 3, 4.6, 4.6));
                    p.moveTo(0.3, 13); p.curveTo(0.3, 8.3, 8, 8.3, 8, 13); p.closePath();
                    g.fill(p);
                    g.fill(new Ellipse2D.Double(8.8, 2, 5, 5));
                    Path2D p2 = new Path2D.Double();
                    p2.moveTo(7, 15); p2.curveTo(7, 8.8, 15.8, 8.8, 15.8, 15); p2.closePath();
                    g.fill(p2);
                    break;
                case "GRAFICA":
                    g.fill(new RoundRectangle2D.Double(1.5, 9, 3.4, 5.5, 1, 1));
                    g.fill(new RoundRectangle2D.Double(6.3, 5, 3.4, 9.5, 1, 1));
                    g.fill(new RoundRectangle2D.Double(11.1, 1.5, 3.4, 13, 1, 1));
                    break;
                case "CAJA":
                    g.draw(new Rectangle2D.Double(2, 5.5, 12, 9));
                    p.moveTo(2, 5.5); p.lineTo(4, 2); p.lineTo(12, 2); p.lineTo(14, 5.5);
                    p.moveTo(8, 2); p.lineTo(8, 5.5);
                    p.moveTo(6, 9); p.lineTo(10, 9);
                    g.draw(p);
                    break;
                case "RESTAURAR":
                    g.translate(16, 0);
                    g.scale(-1, 1);
                    // continua igual que ACTUALIZAR, en espejo
                case "ACTUALIZAR":
                    g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 40, 280, Arc2D.OPEN));
                    p.moveTo(14.8, 1.8); p.lineTo(14.8, 6.6); p.lineTo(10, 6.6); p.closePath();
                    g.fill(p);
                    break;
                case "HISTORIAL":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                    p.moveTo(8, 4.5); p.lineTo(8, 8); p.lineTo(11, 10);
                    g.draw(p);
                    break;
                case "ALERTA":
                    p.moveTo(8, 1.3); p.lineTo(15, 14.3); p.lineTo(1, 14.3); p.closePath();
                    g.draw(p);
                    g.draw(new Line2D.Double(8, 6, 8, 9.5));
                    g.fill(new Ellipse2D.Double(7.1, 11, 1.8, 1.8));
                    break;
                case "BAJA":
                case "SUBE":
                    if ("SUBE".equals(tipo)) {
                        g.translate(0, 16);
                        g.scale(1, -1);
                    }
                    p.moveTo(1, 3.5); p.lineTo(6, 8.5); p.lineTo(9, 5.5); p.lineTo(14, 10.5);
                    g.draw(p);
                    Path2D f = new Path2D.Double();
                    f.moveTo(15.3, 12.8); f.lineTo(10.8, 12.8); f.lineTo(15.3, 8.3); f.closePath();
                    g.fill(f);
                    break;
                case "BUSCAR":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 9, 9));
                    g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(9.4, 9.4, 14.3, 14.3));
                    break;
                case "EDITAR":
                    p.moveTo(11, 1.5); p.lineTo(14.5, 5); p.lineTo(5.5, 14); p.lineTo(1.5, 14.5);
                    p.lineTo(2, 10.5); p.closePath();
                    p.moveTo(9.3, 3.2); p.lineTo(12.8, 6.7);
                    g.draw(p);
                    break;
                case "BLOQUEAR":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                    g.draw(new Line2D.Double(3.4, 12.6, 12.6, 3.4));
                    break;
                case "IMPORTAR":
                case "EXPORTAR":
                    p.moveTo(1.5, 10); p.lineTo(1.5, 14.5); p.lineTo(14.5, 14.5); p.lineTo(14.5, 10);
                    p.moveTo(8, 1.5); p.lineTo(8, 10);
                    if ("IMPORTAR".equals(tipo)) {
                        p.moveTo(4.5, 6.5); p.lineTo(8, 10); p.lineTo(11.5, 6.5);
                    } else {
                        p.moveTo(4.5, 5); p.lineTo(8, 1.5); p.lineTo(11.5, 5);
                    }
                    g.draw(p);
                    break;
                case "INFO":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                    g.fill(new Ellipse2D.Double(7.1, 4, 1.8, 1.8));
                    g.draw(new Line2D.Double(8, 7.3, 8, 11.8));
                    break;
                case "OK":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                    p.moveTo(4.8, 8.2); p.lineTo(7, 10.5); p.lineTo(11.3, 5.6);
                    g.draw(p);
                    break;
                case "ERROR":
                    g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                    g.draw(new Line2D.Double(5.5, 5.5, 10.5, 10.5));
                    g.draw(new Line2D.Double(10.5, 5.5, 5.5, 10.5));
                    break;
                case "GUARDAR":
                    p.moveTo(1.5, 1.5); p.lineTo(12, 1.5); p.lineTo(14.5, 4); p.lineTo(14.5, 14.5);
                    p.lineTo(1.5, 14.5); p.closePath();
                    g.draw(p);
                    g.draw(new Rectangle2D.Double(4.5, 1.5, 6, 3.8));
                    g.draw(new Rectangle2D.Double(4, 9, 8, 5.5));
                    break;
                default:
                    break;
            }
        }
    }

    private static void estilizarTabla(JTable tabla) {
        tabla.setFont(FUENTE_NORMAL);
        tabla.setRowHeight(30);
        tabla.setSelectionBackground(new Color(220, 252, 231));
        tabla.setSelectionForeground(COLOR_TEXTO);
        tabla.setGridColor(new Color(226, 232, 240));
        tabla.setShowVerticalLines(false);

        JTableHeader header = tabla.getTableHeader();
        header.setFont(FUENTE_NEGRITA);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(COLOR_TEXTO);
        header.setPreferredSize(new Dimension(0, 34));
    }
}
