import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;

/* Enumeración de tipos de mensajes utilizados por los componentes del sistema. */
enum TipoMensaje {
    INICIO,
    FIN,
    CORREO
}

/* Clase principal que agrupa las estructuras (buzones) y las clases de hilo usadas
   en la simulación. Contiene el método main que inicializa y coordina los hilos. */
public class App extends Thread {
    public static Integer num_cliente_emisor;
    public static Integer cant_mensajes_cliente;
    public static Integer num_filtros_spam;
    public static Integer num_servidores_entrega;
    public static Integer cap_max_buzon_entrada;
    public static Integer cap_max_buzon_entrega;
    
    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
    String inputFilePath = "Entry.txt";
    System.out.println("INFO: Leyendo archivo de entrada: " + inputFilePath);
        Path path = Paths.get(inputFilePath);
        
        try (BufferedReader reader = java.nio.file.Files.newBufferedReader(path)) {
            num_cliente_emisor = readInt(reader);
            cant_mensajes_cliente = readInt(reader);
            num_filtros_spam = readInt(reader);
            num_servidores_entrega = readInt(reader);
            cap_max_buzon_entrada = readInt(reader);
            cap_max_buzon_entrega = readInt(reader);

            System.out.println();
            System.out.println("CONFIGURACIÓN DEL SISTEMA:");
            System.out.println("  - número_clientes_emisores=" + num_cliente_emisor);
            System.out.println("  - mensajes_por_cliente=" + cant_mensajes_cliente);
            System.out.println("  - numero_filtros_spam=" + num_filtros_spam);
            System.out.println("  - numero_servidores_entrega=" + num_servidores_entrega);
            System.out.println("  - capacidad_max_buzon_entrada=" + cap_max_buzon_entrada);
            System.out.println("  - capacidad_max_buzon_entrega=" + cap_max_buzon_entrega);
            System.out.println();

            // Barrier utilizada para sincronizar el comienzo de envío de mensajes por parte de los clientes
            CyclicBarrier inicioBarrier = new CyclicBarrier(num_cliente_emisor);
            /* Inicialización de los buzones compartidos entre productores, filtros, cuarentena y servidores. */
            BuzonEntrada buzonEntrada = new BuzonEntrada(cap_max_buzon_entrada);
            BuzonCuarentena buzonCuarentena = new BuzonCuarentena();
            BuzonEntrega buzonEntrega = new BuzonEntrega(cap_max_buzon_entrega, num_servidores_entrega);
            // Lanzamiento del hilo encargado de procesar mensajes en cuarentena
            ManejadorCuarentena manejador = new ManejadorCuarentena(buzonCuarentena, buzonEntrega);
            manejador.start();
            // Creación y lanzamiento de los hilos clientes
            Thread[] hilos_clientes = new Thread[num_cliente_emisor];
            for (int i = 0; i < hilos_clientes.length; i++) {
                hilos_clientes[i] = new ClienteEmisor(i, cant_mensajes_cliente, buzonEntrada, inicioBarrier);
                hilos_clientes[i].start();
            }

            // Creación y lanzamiento de los hilos filtros de spam
            Thread[] hilos_filtros = new Thread[num_filtros_spam];
            for (int i = 0; i < hilos_filtros.length; i++) {
                hilos_filtros[i] = new FiltroSpam(i, buzonEntrada, buzonCuarentena, buzonEntrega, num_cliente_emisor);
                hilos_filtros[i].start();
            }

            // Creación y lanzamiento de los hilos servidores de entrega
            Thread[] hilos_servidores = new Thread[num_servidores_entrega];
            for (int i = 0; i < hilos_servidores.length; i++) {
                hilos_servidores[i] = new ServidorEntrega(i, buzonEntrega);
                hilos_servidores[i].start();
            }

            // Espera activa del hilo principal hasta la terminación de todos los subprocesos
            for (Thread hilos_cliente : hilos_clientes) {
                hilos_cliente.join();
            }
            System.out.println("LOG: todos_clientes_finalizados=true");

            for (Thread hilos_filtro : hilos_filtros) {
                hilos_filtro.join();
            }
            System.out.println("LOG: todos_filtros_finalizados=true");

            manejador.join();
            System.out.println("LOG: manejador_cuarentena_finalizado=true");

            for (Thread hilos_servidore : hilos_servidores) {
                hilos_servidore.join();
            }
            System.out.println("LOG: todos_servidores_finalizados=true");

            System.out.println("RESULTADO: ejecucion_completada=OK");

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

    /**
     * Representa un mensaje que circula por el sistema.
     * Contiene identificador, indicación de spam, tipo y tiempo en cuarentena.
     */
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

    /**
     * Buzón de entrada usado por los clientes emisores y consumido por los filtros.
     * Implementa bloqueo con wait()/notifyAll() para productores/consumidores.
     */
    public static class BuzonEntrada {
        private ArrayList<Mensaje> mensajes;
        private int capacidadMaxima;

        public BuzonEntrada(int capacidadMaxima) {
            this.capacidadMaxima = capacidadMaxima;
            this.mensajes = new ArrayList<>();
        }

        public synchronized void depositar(Mensaje m) throws InterruptedException {
            while (mensajes.size() >= capacidadMaxima) {
                System.out.println("ENTRADA: espera_por_espacio en buzonEntrada (size=" + mensajes.size() + ", capacidad=" + capacidadMaxima + ")");
                wait();
            }
            mensajes.add(m);
            System.out.println("ENTRADA: depositado id=" + m.getId() + " tipo=" + m.getTipo() + " total=" + mensajes.size() + "/" + capacidadMaxima);
            notifyAll();
        }

        public synchronized Mensaje extraer() throws InterruptedException {
            while (mensajes.isEmpty()) {
                wait();
            }
            Mensaje m = mensajes.remove(0);
            System.out.println("ENTRADA: extraido id=" + m.getId() + " tipo=" + m.getTipo() + " quedan=" + mensajes.size() + "/" + capacidadMaxima);
            notifyAll();
            return m;
        }

        public synchronized boolean estaVacio() {
            return mensajes.isEmpty();
        }

        public synchronized int getTamanio() {
            return mensajes.size();
        }
    }

    /**
     * Buzón de cuarentena que almacena temporalmente mensajes marcados como spam.
     * Permite marcar fin y notificar al manejador de cuarentena.
     */
    public static class BuzonCuarentena {
        private ArrayList<Mensaje> mensajes;
        private boolean finRecibido = false;

        public BuzonCuarentena() {
            this.mensajes = new ArrayList<>();
        }

        public synchronized void depositar(Mensaje m) {
            mensajes.add(m);
            System.out.println("CUARENTENA: depositado id=" + m.getId() + " tiempo=" + m.getTiempoCuarentena() + " total=" + mensajes.size());
            notifyAll();
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
            notifyAll();
        }

        public synchronized boolean finRecibido() {
            return finRecibido;
        }
    }

    /**
     * Buzón de entrega consumido por los servidores.
     * Implementa espera con wait()/notifyAll() y permite enviar mensajes FIN prioritarios.
     */
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

        public synchronized void depositar(Mensaje m) throws InterruptedException {
            while (mensajes.size() >= capacidadMaxima) {
                wait();
            }
            mensajes.add(m);
            System.out.println("ENTREGA: depositado id=" + m.getId() + " tipo=" + m.getTipo() + " total=" + mensajes.size() + "/" + capacidadMaxima);
            notifyAll();
        }

        public synchronized Mensaje extraer() throws InterruptedException {
            while (mensajes.isEmpty() && !finEnviado) {
                wait();
            }
            
            if (mensajes.isEmpty() && finEnviado) {
                return null;
            }
            
            Mensaje m = mensajes.remove(0);
            System.out.println("ENTREGA: extraido id=" + m.getId() + " tipo=" + m.getTipo() + " quedan=" + mensajes.size() + "/" + capacidadMaxima);
            notifyAll();
            return m;
        }

        // CAMBIO 2e: enviarFinATodos permite exceder capacidad para FINs (prioritarios)
        public synchronized void enviarFinATodos() {
            if (!finEnviado) {
                finEnviado = true;
                for (int i = 0; i < numServidores; i++) {
                    Mensaje fin = new Mensaje(TipoMensaje.FIN, "FIN-SISTEMA-" + i);
                    mensajes.add(fin);
                }
                System.out.println("ENTREGA: fin_enviado=true servidores=" + numServidores);
                notifyAll();
            }
        }

        public synchronized boolean estaVacio() {
            return mensajes.isEmpty();
        }
    }

    /**
     * Hilo encargado de procesar periódicamente los mensajes en cuarentena
     * y decidir si son descartados o movidos al buzón de entrega.
     */
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
                    synchronized(buzonCuarentena) {
                        while (buzonCuarentena.estaVacio() && !buzonCuarentena.finRecibido()) {
                            buzonCuarentena.wait(1000);
                        }
                        
                        if (buzonCuarentena.finRecibido() && buzonCuarentena.estaVacio()) {
                            System.out.println("CUARENTENA: manejador_finalizado=true");
                            break;
                        }
                    }

                    ArrayList<Mensaje> mensajes = buzonCuarentena.obtenerMensajes();
                    ArrayList<Mensaje> aRemover = new ArrayList<>();

                    for (Mensaje m : mensajes) {
                        int nuevoTiempo = m.getTiempoCuarentena() - 1;
                        m.setTiempoCuarentena(nuevoTiempo);

                        int numAleatorio = (int)(Math.random() * 21) + 1;

                        if (numAleatorio % 7 == 0) {
                            System.out.println("CUARENTENA: mensaje_descartado id=" + m.getId() + " motivo=malicioso aleatorio=" + numAleatorio);
                            aRemover.add(m);
                        } else if (nuevoTiempo <= 0) {
                            System.out.println("CUARENTENA: mensaje_aprobado id=" + m.getId() + " motivo=tiempo_agotado");
                            buzonEntrega.depositar(m);
                            aRemover.add(m);
                        }
                    }

                    for (Mensaje m : aRemover) {
                        buzonCuarentena.removerMensaje(m);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * Componente que consume mensajes del buzón de entrada, aplica detección de spam
     * y redirige mensajes a cuarentena o al buzón de entrega según corresponda.
     * Gestiona la señalización de fin de sistema de forma coordinada entre filtros.
     */
    public static class FiltroSpam extends Thread {
        private int idFiltro;
        private BuzonEntrada buzonEntrada;
        private BuzonCuarentena buzonCuarentena;
        private BuzonEntrega buzonEntrega;
        private static int contadorInicio = 0;
        private static int contadorFin = 0;
        private static boolean finEnviadoPorFiltro = false;
        private static final Object lock = new Object();
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
                            System.out.println("FILTRO: idFiltro=" + idFiltro + " evento=INICIO recibido contadorInicio=" + contadorInicio + "/" + numClientesEsperados);
                        }
                        // reenviar la señal de inicio al buzón de entrega para que los servidores la reciban
                        buzonEntrega.depositar(m);
                    } else if (m.getTipo() == TipoMensaje.FIN) {
                        synchronized(lock) {
                            contadorFin++;
                            System.out.println("FILTRO: idFiltro=" + idFiltro + " evento=FIN recibido contadorFin=" + contadorFin + "/" + numClientesEsperados);
                            if (contadorFin == numClientesEsperados) {
                                todosClientesTerminaron = true;
                            }
                        }
                    } else if (m.getTipo() == TipoMensaje.CORREO) {
                        if (m.isEsSpam()) {
                            int tiempo = 10000 + (int)(Math.random() * 10001);
                            m.setTiempoCuarentena(tiempo);
                            System.out.println("FILTRO: idFiltro=" + idFiltro + " evento=SPAM_detectado idMensaje=" + m.getId() + " tiempoCuarentena=" + tiempo);
                            buzonCuarentena.depositar(m);
                        } else {
                            System.out.println("FILTRO: idFiltro=" + idFiltro + " evento=mensaje_valido idMensaje=" + m.getId());
                            buzonEntrega.depositar(m);
                        }
                    }

                    // CAMBIO 2c y 2g: Verificación atómica de todas las condiciones
                    if (todosClientesTerminaron) {
                        synchronized(lock) {
                            if (contadorFin == numClientesEsperados && 
                                buzonEntrada.estaVacio() && 
                                buzonCuarentena.estaVacio() && 
                                !finEnviadoPorFiltro) {
                                
                                buzonEntrega.enviarFinATodos();
                                buzonCuarentena.marcarFin();
                                finEnviadoPorFiltro = true;
                                System.out.println("FILTRO: idFiltro=" + idFiltro + " evento=FIN_final_enviado");
                            }
                        }
                        
                        synchronized(lock) {
                            if (finEnviadoPorFiltro) {
                                System.out.println("FILTRO: idFiltro=" + idFiltro + " estado=finalizado");
                                break;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // ======================== CLIENTE EMISOR ========================
    // CAMBIO 2h: CyclicBarrier para sincronizar inicio de todos los clientes
    public static class ClienteEmisor extends Thread {
        private int idCliente;
        private int numMensajes;
        private BuzonEntrada buzonEntrada;
        private CyclicBarrier barrier;

        public ClienteEmisor(int id, int numMensajes, BuzonEntrada buzon, CyclicBarrier barrier) {
            this.idCliente = id;
            this.numMensajes = numMensajes;
            this.buzonEntrada = buzon;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                // Enviar mensaje de INICIO
                Mensaje inicio = new Mensaje(TipoMensaje.INICIO, "Cliente-" + idCliente);
                buzonEntrada.depositar(inicio);
                System.out.println("✅ Cliente " + idCliente + " INICIADO - esperando a otros clientes...");

                // CAMBIO 2h: Esperar a que TODOS los clientes envíen INICIO
                barrier.await();
                System.out.println("🚀 Cliente " + idCliente + " comenzando a generar mensajes (todos sincronizados)");

                // Generar mensajes
                for (int i = 0; i < numMensajes; i++) {
                    String id = "Cliente-" + idCliente + "-Msg-" + i;
                    boolean esSpam = Math.random() < 0.5;
                    Mensaje correo = new Mensaje(id, esSpam);
                    buzonEntrada.depositar(correo);
                    Thread.sleep(10);
                }

                // Enviar mensaje de FIN
                Mensaje fin = new Mensaje(TipoMensaje.FIN, "Cliente-" + idCliente);
                buzonEntrada.depositar(fin);
                System.out.println("🏁 Cliente " + idCliente + " FINALIZADO");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ======================== SERVIDOR ENTREGA ========================
    // CAMBIO 2f: Detectar INICIO antes de procesar correos
    public static class ServidorEntrega extends Thread {
        private int idServidor;
        private BuzonEntrega buzonEntrega;
        private boolean iniciado = false;

        public ServidorEntrega(int id, BuzonEntrega buzon) {
            this.idServidor = id;
            this.buzonEntrega = buzon;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Mensaje m = buzonEntrega.extraer();

                    if (m == null) {
                        System.out.println("🏁 [SERVIDOR-" + idServidor + "] Finalizado (buzón vacío y fin enviado)");
                        break;
                    }

                    // CAMBIO 2f: Detectar INICIO
                    if (m.getTipo() == TipoMensaje.INICIO) {
                        iniciado = true;
                        System.out.println("🟢 [SERVIDOR-" + idServidor + "] INICIADO (recibido mensaje INICIO)");
                        continue;
                    }

                    // Detectar FIN
                    if (m.getTipo() == TipoMensaje.FIN) {
                        System.out.println("🏁 [SERVIDOR-" + idServidor + "] Recibido FIN - Finalizando");
                        break;
                    }

                    // Procesar mensaje (decisión: procesamos aunque no haya INICIO aún)
                    if (!iniciado) {
                        System.out.println("⚠️ [SERVIDOR-" + idServidor + "] Procesando mensaje antes de INICIO: " + m.getId());
                    }

                    int tiempoProceso = 50 + (int)(Math.random() * 100);
                    Thread.sleep(tiempoProceso);
                    System.out.println("📧 [SERVIDOR-" + idServidor + "] Procesado: " + m.getId() + " (tiempo: " + tiempoProceso + "ms)");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}