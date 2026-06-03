package co.eci.primefinder;

import java.util.LinkedList;
import java.util.List;

public class PrimeFinderThread extends Thread {

    int a, b;
    private List<Integer> primes;
    private final Object monitor;

    //Flag
    private volatile boolean paused = false;

    public PrimeFinderThread(int a, int b, Object monitor) {
        super();
        this.primes = new LinkedList<>();
        this.a = a;
        this.b = b;
        this.monitor = monitor;
    }

    @Override
    public void run() {
        for (int i = a; i < b; i++) {
            if (isPrime(i)) {
                synchronized (primes) {
                    primes.add(i);
                }
                System.out.println(i);
            }
            
            // Paused
            synchronized (monitor) {
                while (paused) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public List<Integer> getPrimes() {
        synchronized (primes) {
            return new LinkedList<>(primes);
        }
    }

    boolean isPrime(int n) {
        if (n > 2) {
            if (n % 2 == 0) return false;
            for (int i = 3; i * i <= n; i += 2) {
                if (n % i == 0) return false;
            }
            return true;
        }
        return n == 2;
    }
}