import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;



public class App extends Thread {
    public static Integer num_cliente_emisor;
    public static Integer cant_mensajes_cliente;
    public static Integer num_filtros_spam;
    public static Integer num_servidores_entrega;
    public static Integer cap_max_buzon_entrada;
    public static Integer cap_max_buzon_entrega;
    

    public static void main(String[] args) {
        String inputFilePath = "Entry.txt";
        System.out.println("Leyendo archivo de entrada:" + inputFilePath);
        Path path = Paths.get(inputFilePath);
        try (BufferedReader reader = java.nio.file.Files.newBufferedReader(path)) {

            num_cliente_emisor = Integer.valueOf(reader.readLine());
            cant_mensajes_cliente = Integer.valueOf(reader.readLine());
            num_filtros_spam = Integer.valueOf(reader.readLine());
            num_servidores_entrega = Integer.valueOf(reader.readLine());
            cap_max_buzon_entrada = Integer.valueOf(reader.readLine());
            cap_max_buzon_entrega = Integer.valueOf(reader.readLine());

            ArrayList buzon_entrada = new ArrayList(cap_max_buzon_entrada);
            ArrayList buzon_entrega = new ArrayList(cap_max_buzon_entrega);


            Thread[] hilos_clientes = new Thread[num_cliente_emisor];
            for (int i = 0; i < hilos_clientes.length; i++) {
                hilos_clientes[i] = new Thread(new crearClientes(num_cliente_emisor, cant_mensajes_cliente));
                hilos_clientes[i].start();
                hilos_clientes[i].join();
            }

            Thread[] hilos_filtros = new Thread[num_filtros_spam];
            for (int i = 0; i < hilos_filtros.length; i++) {
                hilos_filtros[i] = new Thread(new crearFiltros(num_filtros_spam));
                hilos_filtros[i].start();
                hilos_filtros[i].join();
            }
            Thread[] hilos_servidores = new Thread[num_servidores_entrega];
            for (int i = 0; i < hilos_servidores.length; i++) {
                hilos_servidores[i] = new Thread(new crearServidores(num_servidores_entrega));
                hilos_servidores[i].start();
                hilos_servidores[i].join();
            }
            

            System.out.println(num_cliente_emisor);
            System.out.println(cant_mensajes_cliente);
            System.out.println(num_filtros_spam);
            System.out.println(num_servidores_entrega);
            System.out.println(cap_max_buzon_entrada);
            System.out.println(cap_max_buzon_entrega);

        } catch (Exception e) {
            System.err.println("Error al leer el archivo: " + e.getMessage());
        }

    }

    @Override
    public void run (){

    } 

    

    private static class crearFiltros  extends Thread {

        public crearFiltros(Integer num_filtros_spam) {
        }
        @Override
        public void run() {
        
        
        }
    }

    private static class crearClientes extends Thread {

        public crearClientes(Integer num_cliente_emisor, Integer cant_mensajes_cliente) {
        }
        @Override
        public void run() {
        }

    }
    private static class crearServidores extends Thread {

        public crearServidores(Integer num_servidores_entrega) {
        }
        @Override
        public void run() {
            
        }
    }

}
