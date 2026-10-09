public class Main {

    public static void main(String[] args) {

        // ----------------------------------------------------
        // 1. CREAR EL PRODUCTO
        // ----------------------------------------------------
        // Creamos un producto llamado "Arroz".
        //
        // Los valores enviados al constructor son:
        // codigo = "P001"
        // nombre = "Arroz"
        // existencia inicial = 10
        // nivel minimo = 5
        // nivel maximo = 20
        //
        // Esto significa que:
        // - Si la existencia baja hasta 5 o menos, debe generarse
        //   una alerta de reabastecimiento.
        // - Si la existencia llega a 20 o mas, debe generarse
        //   una alerta por exceso de inventario.
        Producto producto = new Producto(
                "P001",
                "Arroz",
                10,
                5,
                20
        );


        // ----------------------------------------------------
        // 2. CREAR EL OBSERVADOR
        // ----------------------------------------------------
        // Creamos el GestorAlertasInventario.
        //
        // Esta clase implementa la interfaz ObservadorInventario,
        // por lo tanto puede ser notificada cuando cambie
        // la existencia de un producto.
        GestorAlertasInventario gestorAlertas =
                new GestorAlertasInventario();


        // ----------------------------------------------------
        // 3. REGISTRAR EL OBSERVADOR EN EL PRODUCTO
        // ----------------------------------------------------
        // Aquí se establece la relación principal del patrón Observer.
        //
        // Le estamos diciendo al producto:
        // "Este gestor quiere ser notificado cuando cambie tu estado".
        //
        // Desde este momento, cada vez que cambie la existencia,
        // Producto llamará al método actualizar() del observador.
        producto.agregarObservador(gestorAlertas);


        // ----------------------------------------------------
        // 4. MOSTRAR LA INFORMACION INICIAL
        // ----------------------------------------------------
        System.out.println("=== SISTEMA DE INVENTARIO ===");

        System.out.println(
                "Producto: " + producto.getNombre()
        );

        System.out.println(
                "Existencia inicial: " + producto.getExistencia()
        );

        System.out.println(
                "Nivel minimo: " + producto.getNivelMinimo()
        );

        System.out.println(
                "Nivel maximo: " + producto.getNivelMaximo()
        );


        // ----------------------------------------------------
        // 5. PRIMERA SALIDA
        // ----------------------------------------------------
        // El producto comienza con 10 unidades.
        //
        // 10 - 3 = 7
        //
        // Como 7 es mayor que el nivel minimo (5),
        // no debe generarse ninguna alerta.
        System.out.println("\n--- Salida de 3 unidades ---");

        producto.disminuirExistencia(3);

        System.out.println(
                "Existencia actual: " + producto.getExistencia()
        );


        // ----------------------------------------------------
        // 6. SEGUNDA SALIDA
        // ----------------------------------------------------
        // Antes de esta salida tenemos 7 unidades.
        //
        // 7 - 3 = 4
        //
        // Ahora la existencia es 4.
        //
        // Como 4 es menor o igual que el nivel minimo (5),
        // Producto notificará al GestorAlertasInventario.
        //
        // El gestor detectará que existe poco inventario
        // y generará una alerta de REABASTECIMIENTO.
        System.out.println("\n--- Salida de 3 unidades ---");

        producto.disminuirExistencia(3);

        System.out.println(
                "Existencia actual: " + producto.getExistencia()
        );


        // ----------------------------------------------------
        // 7. PRIMERA ENTRADA
        // ----------------------------------------------------
        // Actualmente tenemos 4 unidades.
        //
        // 4 + 10 = 14
        //
        // Como 14 está entre el nivel minimo (5)
        // y el nivel maximo (20),
        // no se genera ninguna alerta.
        System.out.println("\n--- Entrada de 10 unidades ---");

        producto.aumentarExistencia(10);

        System.out.println(
                "Existencia actual: " + producto.getExistencia()
        );


        // ----------------------------------------------------
        // 8. SEGUNDA ENTRADA
        // ----------------------------------------------------
        // Actualmente tenemos 14 unidades.
        //
        // 14 + 7 = 21
        //
        // Como 21 es mayor o igual que el nivel maximo (20),
        // Producto notificará nuevamente al observador.
        //
        // GestorAlertasInventario detectará el exceso
        // y generará una alerta de EXCESO DE INVENTARIO.
        System.out.println("\n--- Entrada de 7 unidades ---");

        producto.aumentarExistencia(7);

        System.out.println(
                "Existencia actual: " + producto.getExistencia()
        );
    }
}