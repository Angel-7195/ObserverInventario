import java.util.ArrayList;
import java.util.List;

public class Producto {

    private String codigo;
    private String nombre;
    private int existencia;
    private int nivelMinimo;
    private int nivelMaximo;

    private List<ObservadorInventario> observadores;
    
    public Producto(String codigo, String nombre, int existencia, int nivelMinimo, int nivelMaximo){
        this.codigo = codigo;
        this.nombre = nombre;
        this.existencia = existencia;
        this.nivelMinimo = nivelMinimo;
        this.nivelMaximo = nivelMaximo;
        this.observadores = new ArrayList<>();
    }

    public void agregarObservador(ObservadorInventario observador){
        observadores.add(observador);
    }

    public void eliminarObservador(ObservadorInventario observador){
        observadores.remove(observador);
    }

    public void notificarObservadores(){
        for(ObservadorInventario observador : observadores){
            observador.actualizar(this);
        }
    }

    public void aumentarExistencia(int cantidad){
        existencia += cantidad;
        notificarObservadores();
    }

    public void disminuirExistencia(int cantidad){
        existencia -= cantidad;
        notificarObservadores();
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getExistencia() {
        return existencia;
    }

    public int getNivelMinimo() {
        return nivelMinimo;
    }

    public int getNivelMaximo() {
        return nivelMaximo;
    }
}