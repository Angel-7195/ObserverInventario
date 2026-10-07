public class Main {

    public static void main(String[] args) {

        // Crear un producto
        Producto producto = new Producto(
                "P001",
                "Arroz",
                10,
                5,
                20
        );

        // Crear el observador encargado de las alertas
        GestorAlertasInventario gestorAlertas = new GestorAlertasInventario();

        // Registrar el observador en el producto
        producto.agregarObservador(gestorAlertas);

        System.out.println("=== SISTEMA DE INVENTARIO ===");
        System.out.println("Producto: " + producto.getNombre());
        System.out.println("Existencia inicial: " + producto.getExistencia());
        System.out.println("Nivel minimo: " + producto.getNivelMinimo());
        System.out.println("Nivel maximo: " + producto.getNivelMaximo());

        System.out.println("\n--- Salida de 3 unidades ---");
        producto.disminuirExistencia(3);
        System.out.println("Existencia actual: " + producto.getExistencia());

        System.out.println("\n--- Salida de 3 unidades ---");
        producto.disminuirExistencia(3);
        System.out.println("Existencia actual: " + producto.getExistencia());

        System.out.println("\n--- Entrada de 10 unidades ---");
        producto.aumentarExistencia(10);
        System.out.println("Existencia actual: " + producto.getExistencia());

        System.out.println("\n--- Entrada de 7 unidades ---");
        producto.aumentarExistencia(7);
        System.out.println("Existencia actual: " + producto.getExistencia());
    }
}