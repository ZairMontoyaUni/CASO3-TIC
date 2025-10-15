import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

enum TipoMensaje {
    INICIO,
    FIN,
    CORREO
}

public class App extends Thread {
    public static Integer num_cliente_emisor;
    public static Integer cant_mensajes_cliente;
    public static Integer num_filtros_spam;
    public static Integer num_servidores_entrega;
    public static Integer cap_max_buzon_entrada;
    public static Integer cap_max_buzon_entrega;
    
    public static void main(String[] args) {
        String inputFilePath = "Entry.txt";
        System.out.println("Leyendo archivo de entrada: " + inputFilePath);
        Path path = Paths.get(inputFilePath);
        
        try (BufferedReader reader = java.nio.file.Files.newBufferedReader(path)) {
            num_cliente_emisor = readInt(reader);
            cant_mensajes_cliente = readInt(reader);
            num_filtros_spam = readInt(reader);
            num_servidores_entrega = readInt(reader);
            cap_max_buzon_entrada = readInt(reader);
            cap_max_buzon_entrega = readInt(reader);

            System.out.println("\n📊 CONFIGURACIÓN:");
            System.out.println("Clientes: " + num_cliente_emisor);
            System.out.println("Mensajes por cliente: " + cant_mensajes_cliente);
            System.out.println("Filtros: " + num_filtros_spam);
            System.out.println("Servidores: " + num_servidores_entrega);
            System.out.println("Capacidad entrada: " + cap_max_buzon_entrada);
            System.out.println("Capacidad entrega: " + cap_max_buzon_entrega);
            System.out.println();

            // CREAR LOS BUZONES (recursos compartidos)
            BuzonEntrada buzonEntrada = new BuzonEntrada(cap_max_buzon_entrada);
            BuzonCuarentena buzonCuarentena = new BuzonCuarentena();
            BuzonEntrega buzonEntrega = new BuzonEntrega(cap_max_buzon_entrega, num_servidores_entrega);

            // CREAR Y LANZAR MANEJADOR DE CUARENTENA
            ManejadorCuarentena manejador = new ManejadorCuarentena(buzonCuarentena, buzonEntrega);
            manejador.start();

            // CREAR Y LANZAR CLIENTES
            Thread[] hilos_clientes = new Thread[num_cliente_emisor];
            for (int i = 0; i < hilos_clientes.length; i++) {
                hilos_clientes[i] = new ClienteEmisor(i, cant_mensajes_cliente, buzonEntrada);
                hilos_clientes[i].start();
            }

            // CREAR Y LANZAR FILTROS
            Thread[] hilos_filtros = new Thread[num_filtros_spam];
            for (int i = 0; i < hilos_filtros.length; i++) {
                hilos_filtros[i] = new FiltroSpam(i, buzonEntrada, buzonCuarentena, buzonEntrega, num_cliente_emisor);
                hilos_filtros[i].start();
            }

            // CREAR Y LANZAR SERVIDORES
            Thread[] hilos_servidores = new Thread[num_servidores_entrega];
            for (int i = 0; i < hilos_servidores.length; i++) {
                hilos_servidores[i] = new ServidorEntrega(i, buzonEntrega);
                hilos_servidores[i].start();
            }

            // ESPERAR A QUE TERMINEN TODOS
            for (int i = 0; i < hilos_clientes.length; i++) {
                hilos_clientes[i].join();
            }
            System.out.println("\n✅ Todos los clientes han terminado");

            for (int i = 0; i < hilos_filtros.length; i++) {
                hilos_filtros[i].join();
            }
            System.out.println("✅ Todos los filtros han terminado");

            manejador.join();
            System.out.println("✅ Manejador de cuarentena ha terminado");

            for (int i = 0; i < hilos_servidores.length; i++) {
                hilos_servidores[i].join();
            }
            System.out.println("✅ Todos los servidores han terminado");

            System.out.println("\n🎉 SISTEMA COMPLETADO");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Integer readInt(BufferedReader reader) throws java.io.IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String trimmed = line.trim();
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException nfe) {
                throw new NumberFormatException("No se pudo parsear entero desde: '" + line + "'");
            }
        }
        throw new java.io.IOException("Archivo de entrada incompleto");
    }

    // ======================== CLASE MENSAJE ========================
    public static class Mensaje {
        private String id;
        private boolean esSpam;
        private TipoMensaje tipo;
        private int tiempoCuarentena;

        public Mensaje(TipoMensaje tipo, String idCliente) {
            this.tipo = tipo;
            this.id = idCliente;
            this.esSpam = false;
            this.tiempoCuarentena = 0;
        }

        public Mensaje(String id, boolean esSpam) {
            this.tipo = TipoMensaje.CORREO;
            this.id = id;
            this.esSpam = esSpam;
            this.tiempoCuarentena = 0;
        }

        public String getId() { return id; }
        public boolean isEsSpam() { return esSpam; }
        public TipoMensaje getTipo() { return tipo; }
        public int getTiempoCuarentena() { return tiempoCuarentena; }
        public void setTiempoCuarentena(int tiempo) { this.tiempoCuarentena = tiempo; }
    }

    // ======================== BUZON ENTRADA ========================
    public static class BuzonEntrada {
        private ArrayList<Mensaje> mensajes;
        private int capacidadMaxima;

        public BuzonEntrada(int capacidadMaxima) {
            this.capacidadMaxima = capacidadMaxima;
            this.mensajes = new ArrayList<>();
        }

        public synchronized void depositar(Mensaje m) throws InterruptedException {
            while (mensajes.size() >= capacidadMaxima) {
                wait();
            }
            mensajes.add(m);
            System.out.println("📬 [ENTRADA] Depositado: " + m.getId() + " (Total: " + mensajes.size() + ")");
            notify();
        }

        public synchronized Mensaje extraer() throws InterruptedException {
            while (mensajes.isEmpty()) {
                wait();
            }
            Mensaje m = mensajes.remove(0);
            System.out.println("📤 [ENTRADA] Extraído: " + m.getId() + " (Quedan: " + mensajes.size() + ")");
            notify();
            return m;
        }

        public synchronized boolean estaVacio() {
            return mensajes.isEmpty();
        }

        public synchronized int getTamanio() {
            return mensajes.size();
        }
    }

    // ======================== BUZON CUARENTENA ========================
    public static class BuzonCuarentena {
        private ArrayList<Mensaje> mensajes;
        private boolean finRecibido = false;

        public BuzonCuarentena() {
            this.mensajes = new ArrayList<>();
        }

        // ESPERA SEMI-ACTIVA: usa yield()
        public void depositar(Mensaje m) {
            while (true) {
                synchronized(this) {
                    mensajes.add(m);
                    System.out.println("🟡 [CUARENTENA] Depositado: " + m.getId() + 
                                     " (Tiempo: " + m.getTiempoCuarentena() + ")");
                    return;
                }
            }
        }

        public synchronized ArrayList<Mensaje> obtenerMensajes() {
            return new ArrayList<>(mensajes);
        }

        public synchronized void removerMensaje(Mensaje m) {
            mensajes.remove(m);
        }

        public synchronized boolean estaVacio() {
            return mensajes.isEmpty();
        }

        public synchronized void marcarFin() {
            finRecibido = true;
        }

        public synchronized boolean finRecibido() {
            return finRecibido;
        }
    }

    // ======================== BUZON ENTREGA ========================
    public static class BuzonEntrega {
        private ArrayList<Mensaje> mensajes;
        private int capacidadMaxima;
        private int numServidores;
        private boolean finEnviado = false;

        public BuzonEntrega(int capacidadMaxima, int numServidores) {
            this.capacidadMaxima = capacidadMaxima;
            this.numServidores = numServidores;
            this.mensajes = new ArrayList<>();
        }

        // ESPERA SEMI-ACTIVA: usa yield()
        public void depositar(Mensaje m) {
            while (true) {
                synchronized(this) {
                    if (mensajes.size() < capacidadMaxima) {
                        mensajes.add(m);
                        System.out.println("✉️ [ENTREGA] Depositado: " + m.getId() + 
                                         " (Total: " + mensajes.size() + ")");
                        notifyAll();
                        return;
                    }
                }
                Thread.yield();
            }
        }

        // ESPERA ACTIVA: los servidores leen constantemente
        public synchronized Mensaje extraer() throws InterruptedException {
            while (mensajes.isEmpty() && !finEnviado) {
                Thread.yield();
            }
            
            if (!mensajes.isEmpty()) {
                Mensaje m = mensajes.remove(0);
                System.out.println("📨 [ENTREGA] Extraído: " + m.getId() + " (Quedan: " + mensajes.size() + ")");
                notifyAll();
                return m;
            }
            
            return null;
        }

        public synchronized void enviarFinATodos() {
            if (!finEnviado) {
                finEnviado = true;
                for (int i = 0; i < numServidores; i++) {
                    Mensaje fin = new Mensaje(TipoMensaje.FIN, "FIN-SISTEMA");
                    mensajes.add(fin);
                }
                System.out.println("🔚 [ENTREGA] FIN enviado a todos los servidores");
                notifyAll();
            }
        }

        public synchronized boolean estaVacio() {
            return mensajes.isEmpty();
        }
    }

    // ======================== MANEJADOR CUARENTENA ========================
    public static class ManejadorCuarentena extends Thread {
        private BuzonCuarentena buzonCuarentena;
        private BuzonEntrega buzonEntrega;

        public ManejadorCuarentena(BuzonCuarentena cuarentena, BuzonEntrega entrega) {
            this.buzonCuarentena = cuarentena;
            this.buzonEntrega = entrega;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(1000); // Corre cada segundo

                    ArrayList<Mensaje> mensajes = buzonCuarentena.obtenerMensajes();
                    ArrayList<Mensaje> aRemover = new ArrayList<>();

                    for (Mensaje m : mensajes) {
                        // Disminuir tiempo
                        int nuevoTiempo = m.getTiempoCuarentena() - 1;
                        m.setTiempoCuarentena(nuevoTiempo);

                        // Generar número aleatorio entre 1 y 21
                        int numAleatorio = (int)(Math.random() * 21) + 1;

                        if (numAleatorio % 7 == 0) {
                            // DESCARTAR mensaje malicioso
                            System.out.println("❌ [CUARENTENA] Mensaje DESCARTADO (malicioso): " + m.getId());
                            aRemover.add(m);
                        } else if (nuevoTiempo <= 0) {
                            // MOVER a entrega
                            System.out.println("✅ [CUARENTENA] Mensaje aprobado: " + m.getId());
                            buzonEntrega.depositar(m);
                            aRemover.add(m);
                        }
                    }

                    // Remover mensajes procesados
                    for (Mensaje m : aRemover) {
                        buzonCuarentena.removerMensaje(m);
                    }

                    // Verificar si debe terminar
                    if (buzonCuarentena.finRecibido() && buzonCuarentena.estaVacio()) {
                        System.out.println("🏁 [CUARENTENA] Manejador finalizado");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ======================== FILTRO SPAM ========================
    public static class FiltroSpam extends Thread {
        private int idFiltro;
        private BuzonEntrada buzonEntrada;
        private BuzonCuarentena buzonCuarentena;
        private BuzonEntrega buzonEntrega;
        private static int contadorInicio = 0;
        private static int contadorFin = 0;
        private static Object lock = new Object();
        private int numClientesEsperados;

        public FiltroSpam(int id, BuzonEntrada entrada, BuzonCuarentena cuarentena, 
                         BuzonEntrega entrega, int numClientes) {
            this.idFiltro = id;
            this.buzonEntrada = entrada;
            this.buzonCuarentena = cuarentena;
            this.buzonEntrega = entrega;
            this.numClientesEsperados = numClientes;
        }

        @Override
        public void run() {
            try {
                boolean todosClientesTerminaron = false;

                while (true) {
                    Mensaje m = buzonEntrada.extraer();

                    if (m.getTipo() == TipoMensaje.INICIO) {
                        synchronized(lock) {
                            contadorInicio++;
                            System.out.println("🔵 [FILTRO-" + idFiltro + "] INICIO recibido (" + contadorInicio + "/" + numClientesEsperados + ")");
                        }
                    } 
                    else if (m.getTipo() == TipoMensaje.FIN) {
                        synchronized(lock) {
                            contadorFin++;
                            System.out.println("🔴 [FILTRO-" + idFiltro + "] FIN recibido (" + contadorFin + "/" + numClientesEsperados + ")");
                            
                            if (contadorFin == numClientesEsperados) {
                                todosClientesTerminaron = true;
                            }
                        }
                    } 
                    else if (m.getTipo() == TipoMensaje.CORREO) {
                        if (m.isEsSpam()) {
                            // Asignar tiempo de cuarentena: entre 10000 y 20000
                            int tiempo = 10000 + (int)(Math.random() * 10001);
                            m.setTiempoCuarentena(tiempo);
                            System.out.println("⚠️ [FILTRO-" + idFiltro + "] SPAM detectado: " + m.getId());
                            buzonCuarentena.depositar(m);
                        } else {
                            System.out.println("✅ [FILTRO-" + idFiltro + "] Mensaje válido: " + m.getId());
                            buzonEntrega.depositar(m);
                        }
                    }

                    // Verificar si debe enviar FIN final
                    if (todosClientesTerminaron && buzonEntrada.estaVacio() && buzonCuarentena.estaVacio()) {
                        synchronized(lock) {
                            // Solo un filtro envía el FIN
                            if (contadorFin == numClientesEsperados) {
                                buzonEntrega.enviarFinATodos();
                                buzonCuarentena.marcarFin();
                                contadorFin++; // Marca para que otros filtros no lo envíen
                            }
                        }
                        System.out.println("🏁 [FILTRO-" + idFiltro + "] Finalizado");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ======================== CLIENTE EMISOR ========================
    public static class ClienteEmisor extends Thread {
        private int idCliente;
        private int numMensajes;
        private BuzonEntrada buzonEntrada;

        public ClienteEmisor(int id, int numMensajes, BuzonEntrada buzon) {
            this.idCliente = id;
            this.numMensajes = numMensajes;
            this.buzonEntrada = buzon;
        }

        @Override
        public void run() {
            try {
                Mensaje inicio = new Mensaje(TipoMensaje.INICIO, "Cliente-" + idCliente);
                buzonEntrada.depositar(inicio);
                System.out.println("✅ Cliente " + idCliente + " INICIADO");

                for (int i = 0; i < numMensajes; i++) {
                    String id = "Cliente-" + idCliente + "-Msg-" + i;
                    boolean esSpam = Math.random() < 0.5;
                    Mensaje correo = new Mensaje(id, esSpam);
                    buzonEntrada.depositar(correo);
                    Thread.sleep(10);
                }

                Mensaje fin = new Mensaje(TipoMensaje.FIN, "Cliente-" + idCliente);
                buzonEntrada.depositar(fin);
                System.out.println("🏁 Cliente " + idCliente + " FINALIZADO");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ======================== SERVIDOR ENTREGA ========================
    public static class ServidorEntrega extends Thread {
        private int idServidor;
        private BuzonEntrega buzonEntrega;

        public ServidorEntrega(int id, BuzonEntrega buzon) {
            this.idServidor = id;
            this.buzonEntrega = buzon;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Mensaje m = buzonEntrega.extraer();

                    if (m == null || m.getTipo() == TipoMensaje.FIN) {
                        System.out.println("🏁 [SERVIDOR-" + idServidor + "] Finalizado");
                        break;
                    }

                    // Procesar mensaje con tiempo aleatorio
                    int tiempoProceso = 50 + (int)(Math.random() * 100);
                    Thread.sleep(tiempoProceso);
                    System.out.println("📧 [SERVIDOR-" + idServidor + "] Procesado: " + m.getId());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}