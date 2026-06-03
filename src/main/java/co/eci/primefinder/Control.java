package co.eci.primefinder;

import java.util.Scanner;

public class Control extends Thread {

    private static final int NTHREADS = 3;
    private static final int MAXVALUE = 30_000_000;
    private static final int TMILISECONDS = 5000;

    private final int NDATA = MAXVALUE / NTHREADS;
    private final PrimeFinderThread[] pft;
    private final Object monitor = new Object(); 

    private Control() {
        super();
        this.pft = new PrimeFinderThread[NTHREADS];
        int i;
        for (i = 0; i < NTHREADS - 1; i++) {
            pft[i] = new PrimeFinderThread(i * NDATA, (i + 1) * NDATA, monitor);
        }
        pft[i] = new PrimeFinderThread(i * NDATA, MAXVALUE + 1, monitor);
    }

    public static Control newControl() {
        return new Control();
    }

    @Override
    public void run() {

        // Start
        for (PrimeFinderThread t : pft) {
            t.start();
        }

        Scanner sc = new Scanner(System.in);

        while (true) {
            try {
                Thread.sleep(TMILISECONDS); // Wait
            } catch (InterruptedException e) {
                break;
            }

            // Paused
            for (PrimeFinderThread t : pft) {
                t.setPaused(true);
            }

            int total = 0;
            for (PrimeFinderThread t : pft) {
                total += t.getPrimes().size();
            }
            System.out.println("\n>>> Primos encontrados hasta ahora: " + total);
            System.out.println(">>> Presiona ENTER para continuar...");

            sc.nextLine(); 

            synchronized (monitor) {
                for (PrimeFinderThread t : pft) {
                    t.setPaused(false);
                }
                monitor.notifyAll(); 
            }
            System.out.println(">>> Reanudando...\n");
        }
    }
}