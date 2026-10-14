import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Interfaz grafica del sistema de inventario.
 *
 * La ventana tambien implementa ObservadorInventario: se registra en el
 * producto junto al GestorAlertasInventario, asi que cada vez que cambia
 * la existencia el producto notifica a ambos observadores. El gestor genera
 * las alertas y la ventana se actualiza para mostrarlas.
 */
public class InterfazInventario extends JFrame implements ObservadorInventario {

    private static final Color VERDE = new Color(46, 160, 67);
    private static final Color ROJO = new Color(207, 34, 46);
    private static final Color NARANJA = new Color(219, 120, 0);
    private static final Color AZUL = new Color(9, 105, 218);
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private Producto producto;
    private GestorAlertasInventario gestorAlertas;
    private int alertasMostradas;

    // Formulario del producto
    private final JTextField txtCodigo = new JTextField("P001", 10);
    private final JTextField txtNombre = new JTextField("Arroz", 10);
    private final JSpinner spnExistencia = new JSpinner(new SpinnerNumberModel(10, 0, 100000, 1));
    private final JSpinner spnMinimo = new JSpinner(new SpinnerNumberModel(5, 0, 100000, 1));
    private final JSpinner spnMaximo = new JSpinner(new SpinnerNumberModel(20, 1, 100000, 1));

    // Estado actual
    private final JLabel lblProducto = new JLabel("-", SwingConstants.CENTER);
    private final JLabel lblExistencia = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblEstado = new JLabel("-", SwingConstants.CENTER);
    private final JLabel lblLimites = new JLabel("-", SwingConstants.CENTER);
    private final BarraInventario barra = new BarraInventario();

    // Movimientos
    private final JSpinner spnCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));

    // Alertas e historial
    private final DefaultTableModel modeloAlertas =
            new DefaultTableModel(new Object[] {"Tipo", "Mensaje", "Fecha"}, 0) {
                @Override
                public boolean isCellEditable(int fila, int columna) {
                    return false;
                }
            };
    private final DefaultListModel<String> modeloHistorial = new DefaultListModel<>();

    public InterfazInventario() {
        super("Sistema de Inventario - Patrón Observer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(10, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel superior = new JPanel(new GridLayout(1, 3, 10, 0));
        superior.add(crearPanelProducto());
        superior.add(crearPanelEstado());
        superior.add(crearPanelMovimientos());

        JSplitPane inferior = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, crearPanelAlertas(), crearPanelHistorial());
        inferior.setResizeWeight(0.7);
        inferior.setBorder(null);

        contenido.add(superior, BorderLayout.NORTH);
        contenido.add(inferior, BorderLayout.CENTER);
        setContentPane(contenido);

        crearProducto();

        setMinimumSize(new Dimension(1100, 620));
        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------
    // Paneles
    // ------------------------------------------------------------

    private JPanel crearPanelProducto() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(titulo("1. Producto"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        agregarCampo(panel, c, 0, "Código:", txtCodigo);
        agregarCampo(panel, c, 1, "Nombre:", txtNombre);
        agregarCampo(panel, c, 2, "Existencia inicial:", spnExistencia);
        agregarCampo(panel, c, 3, "Nivel mínimo:", spnMinimo);
        agregarCampo(panel, c, 4, "Nivel máximo:", spnMaximo);

        JButton btnCrear = new JButton("Crear producto");
        btnCrear.addActionListener(e -> crearProducto());
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.insets = new Insets(10, 4, 3, 4);
        panel.add(btnCrear, c);

        return panel;
    }

    private void agregarCampo(JPanel panel, GridBagConstraints c, int fila, String etiqueta, Component campo) {
        c.gridx = 0;
        c.gridy = fila;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(campo, c);
    }

    private JPanel crearPanelEstado() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(titulo("2. Estado del inventario"));

        lblProducto.setFont(lblProducto.getFont().deriveFont(Font.BOLD, 15f));
        lblExistencia.setFont(lblExistencia.getFont().deriveFont(Font.BOLD, 44f));
        lblEstado.setFont(lblEstado.getFont().deriveFont(Font.BOLD, 13f));
        lblEstado.setOpaque(true);
        lblEstado.setForeground(Color.WHITE);
        lblEstado.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        for (JLabel l : new JLabel[] {lblProducto, lblExistencia, lblEstado, lblLimites}) {
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        barra.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(lblProducto);
        panel.add(lblExistencia);
        JLabel lblUnidades = new JLabel("unidades en existencia", SwingConstants.CENTER);
        lblUnidades.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(lblUnidades);
        panel.add(Box.createVerticalStrut(6));
        panel.add(lblEstado);
        panel.add(Box.createVerticalStrut(8));
        panel.add(barra);
        panel.add(Box.createVerticalStrut(4));
        panel.add(lblLimites);
        return panel;
    }

    private JPanel crearPanelMovimientos() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(titulo("3. Movimientos"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Cantidad:"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(spnCantidad, c);

        JButton btnEntrada = new JButton("Registrar entrada (+)");
        btnEntrada.setForeground(VERDE.darker());
        btnEntrada.addActionListener(e -> registrarEntrada());

        JButton btnSalida = new JButton("Registrar salida (-)");
        btnSalida.setForeground(ROJO);
        btnSalida.addActionListener(e -> registrarSalida());

        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = 1;
        panel.add(btnEntrada, c);
        c.gridy = 2;
        panel.add(btnSalida, c);

        JLabel ayuda = new JLabel("<html><div style='width:210px;color:#555'>"
                + "Cada movimiento cambia la existencia y el producto notifica a sus "
                + "observadores: el gestor de alertas y esta ventana.</div></html>");
        c.gridy = 3;
        c.insets = new Insets(10, 4, 4, 4);
        panel.add(ayuda, c);
        return panel;
    }

    private JPanel crearPanelAlertas() {
        JTable tabla = new JTable(modeloAlertas);
        tabla.setRowHeight(24);
        tabla.setFillsViewportHeight(true);
        tabla.getColumnModel().getColumn(0).setMinWidth(150);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(380);
        tabla.getColumnModel().getColumn(2).setMinWidth(140);
        tabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                    boolean foco, int fila, int col) {
                super.getTableCellRendererComponent(t, valor, sel, foco, fila, col);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!sel) {
                    setForeground("EXCESO".equals(valor) ? NARANJA : ROJO);
                }
                return this;
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(titulo("Alertas generadas por GestorAlertasInventario"));
        panel.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelHistorial() {
        JList<String> lista = new JList<>(modeloHistorial);
        lista.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton btnLimpiar = new JButton("Limpiar");
        btnLimpiar.addActionListener(e -> modeloHistorial.clear());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        botones.add(btnLimpiar);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(titulo("Historial de movimientos"));
        panel.add(new JScrollPane(lista), BorderLayout.CENTER);
        panel.add(botones, BorderLayout.SOUTH);
        return panel;
    }

    private TitledBorder titulo(String texto) {
        TitledBorder borde = BorderFactory.createTitledBorder(texto);
        borde.setTitleFont(borde.getTitleFont() == null
                ? new Font(Font.SANS_SERIF, Font.BOLD, 12)
                : borde.getTitleFont().deriveFont(Font.BOLD));
        return borde;
    }

    // ------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------

    private void crearProducto() {
        String codigo = txtCodigo.getText().trim();
        String nombre = txtNombre.getText().trim();
        int existencia = (Integer) spnExistencia.getValue();
        int minimo = (Integer) spnMinimo.getValue();
        int maximo = (Integer) spnMaximo.getValue();

        if (codigo.isEmpty() || nombre.isEmpty()) {
            error("El código y el nombre son obligatorios.");
            return;
        }
        if (minimo >= maximo) {
            error("El nivel mínimo debe ser menor que el nivel máximo.");
            return;
        }

        producto = new Producto(codigo, nombre, existencia, minimo, maximo);
        gestorAlertas = new GestorAlertasInventario();

        // Registro de observadores (patron Observer).
        // El gestor va primero para que, cuando la ventana sea notificada,
        // la alerta correspondiente ya exista.
        producto.agregarObservador(gestorAlertas);
        producto.agregarObservador(this);

        alertasMostradas = 0;
        modeloAlertas.setRowCount(0);
        modeloHistorial.clear();
        registrarHistorial("Producto creado: " + nombre + " (" + codigo + "), existencia " + existencia);
        refrescarEstado();
    }

    private void registrarEntrada() {
        int cantidad = (Integer) spnCantidad.getValue();
        registrarHistorial("Entrada de " + cantidad + " unidades");
        producto.aumentarExistencia(cantidad);
    }

    private void registrarSalida() {
        int cantidad = (Integer) spnCantidad.getValue();
        if (cantidad > producto.getExistencia()) {
            error("No hay suficiente existencia. Disponible: " + producto.getExistencia() + " unidades.");
            return;
        }
        registrarHistorial("Salida de " + cantidad + " unidades");
        producto.disminuirExistencia(cantidad);
    }

    // ------------------------------------------------------------
    // Observer: la ventana es notificada por el producto
    // ------------------------------------------------------------

    @Override
    public void actualizar(Producto producto) {
        registrarHistorial("  -> existencia actual: " + producto.getExistencia());
        refrescarEstado();
        mostrarAlertasNuevas();
    }

    private void mostrarAlertasNuevas() {
        List<AlertaInventario> alertas = gestorAlertas.getAlertas();
        while (alertasMostradas < alertas.size()) {
            AlertaInventario alerta = alertas.get(alertasMostradas++);
            modeloAlertas.insertRow(0, new Object[] {
                alerta.getTipo(), alerta.getMensaje(), alerta.getFecha().format(FORMATO_FECHA)
            });
            registrarHistorial("  !! ALERTA " + alerta.getTipo());
        }
    }

    private void refrescarEstado() {
        int existencia = producto.getExistencia();
        int minimo = producto.getNivelMinimo();
        int maximo = producto.getNivelMaximo();

        lblProducto.setText(producto.getNombre() + "  (" + producto.getCodigo() + ")");
        lblExistencia.setText(String.valueOf(existencia));
        lblLimites.setText("Mínimo: " + minimo + "   |   Máximo: " + maximo);

        Color color;
        if (existencia <= minimo) {
            lblEstado.setText("REABASTECER");
            color = ROJO;
        } else if (existencia >= maximo) {
            lblEstado.setText("EXCESO");
            color = NARANJA;
        } else {
            lblEstado.setText("NORMAL");
            color = VERDE;
        }
        lblEstado.setBackground(color);
        lblExistencia.setForeground(color);
        barra.actualizar(existencia, minimo, maximo, color);
    }

    private void registrarHistorial(String texto) {
        String hora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        modeloHistorial.addElement("[" + hora + "] " + texto);
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------
    // Barra que dibuja la existencia con las marcas de minimo y maximo
    // ------------------------------------------------------------

    private static class BarraInventario extends JPanel {
        private int existencia;
        private int minimo;
        private int maximo;
        private Color color = AZUL;

        BarraInventario() {
            setPreferredSize(new Dimension(240, 26));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            setOpaque(false);
        }

        void actualizar(int existencia, int minimo, int maximo, Color color) {
            this.existencia = existencia;
            this.minimo = minimo;
            this.maximo = maximo;
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = 10;
            int ancho = getWidth() - 20;
            int alto = 16;
            int y = (getHeight() - alto) / 2;

            // La escala llega un 25% por encima del maximo para que el exceso se vea.
            double tope = Math.max(maximo * 1.25, existencia);
            if (tope <= 0) {
                tope = 1;
            }

            g2.setColor(new Color(225, 228, 232));
            g2.fillRoundRect(x, y, ancho, alto, alto, alto);

            int lleno = (int) Math.round(ancho * Math.min(existencia, tope) / tope);
            g2.setColor(color);
            g2.fillRoundRect(x, y, Math.max(lleno, 0), alto, alto, alto);

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Color.DARK_GRAY);
            for (int marca : new int[] {minimo, maximo}) {
                int mx = x + (int) Math.round(ancho * marca / tope);
                g2.drawLine(mx, y - 4, mx, y + alto + 4);
            }
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // Si falla se usa el aspecto por defecto de Swing.
        }
        SwingUtilities.invokeLater(() -> new InterfazInventario().setVisible(true));
    }
}
