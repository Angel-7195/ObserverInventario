import java.util.ArrayList;
import java.util.List;

public class GestorAlertasInventario implements ObservadorInventario {

    private List<AlertaInventario> alertas;
    
    public GestorAlertasInventario() {
        this.alertas = new ArrayList<>();
    }

    @Override
    public void actualizar(Producto producto) {

        if (producto.getExistencia() <= producto.getNivelMinimo()) {

        generarAlerta(producto, "REABASTECIMIENTO");

        } else if (producto.getExistencia() >= producto.getNivelMaximo()) {
            
            generarAlerta(producto, "EXCESO");

        }

    }

    public void generarAlerta(Producto producto, String tipo) {
        
        String mensaje;

        if (tipo.equals("REABASTECIMIENTO")){

            mensaje = "El producto " + producto.getNombre() + " alcanzó el nivel minimo de inventario.";
        
        } else {
            
            mensaje = "El producto " + producto.getNombre() + " alcanzó el nivel máximo de inventario.";

        }

        AlertaInventario alerta = new AlertaInventario(tipo, mensaje);
        alertas.add(alerta);
        System.out.println(alerta.mostrarAlerta());

    }
}