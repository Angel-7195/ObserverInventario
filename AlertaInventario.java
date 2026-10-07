import java.time.LocalDateTime;

public class AlertaInventario {

    private String tipo;
    private String mensaje;
    private LocalDateTime fecha;

    public AlertaInventario(String tipo, String mensaje) {
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.fecha = LocalDateTime.now();
    }

    public String mostrarAlerta() {
        return "ALERTA: " + tipo
                + "\n" + mensaje
                + "\nFecha: " + fecha;
    }

    public String getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}