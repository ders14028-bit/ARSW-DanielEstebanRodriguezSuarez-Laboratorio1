# Snake Race — ARSW Lab #1 (Java 21, Virtual Threads)

## Author: Daniel Esteban Rodriguez Suarez

# Laboratory Development

## How to run

### Requirements
- JDK 21
- Maven 3.9+

### Part I — PrimeFinder

```bash
mvn exec:java -Pprimes
```

Every 5 seconds the program pauses and displays the number of primes 
found so far. Press **ENTER** to resume.

### Part II — Snake Race

```bash
# Default (2 snakes)
mvn exec:java -Psnake

# Custom number of snakes
mvn exec:java -Psnake -Dsnakes=N
```

## Part I — (Warm-up) `wait/notify` in a multi-threaded program

### Description

Part I consists of a multithreaded Java program that searches for prime 
numbers in the range from 0 to 30,000,000. The workload is divided among 
3 worker threads, each responsible for a different segment of the range.

The program runs the search concurrently and every 5 seconds automatically 
pauses all threads, prints how many prime numbers have been found so far, 
and waits for the user to press ENTER before resuming. This cycle repeats 
until all numbers in the range have been evaluated.

The goal of this part is to practice thread suspension and resumption 
without busy-waiting, using Java's monitor model (`synchronized`, `wait()`, 
and `notifyAll()`).

### What was modified
The `PrimeFinderThread` and `Control` classes were modified to support 
pause/resume behavior every `t` milliseconds without busy-waiting.

### Synchronization design

**Lock used:**  
A shared `Object monitor` is created in `Control` and passed to every 
`PrimeFinderThread` via constructor. All threads synchronize on this 
same object, making it the single monitor for the entire system.

**Condition used:**  
A `volatile boolean paused` flag inside each `PrimeFinderThread` acts 
as the condition. Worker threads check this flag at the end of each 
number evaluation inside a `synchronized(monitor)` block.

**How busy-waiting is avoided:**  
Instead of looping on the flag without releasing the CPU, each thread 
calls `monitor.wait()` when `paused == true`. This releases the lock 
and suspends the thread until `Control` calls `monitor.notifyAll()`, 
which wakes all waiting threads at once.

**How lost wakeups are avoided:**  
The condition check uses a `while` loop instead of `if`:

```java
synchronized (monitor) {
    while (paused) {
        monitor.wait();
    }
}
```

This ensures that even if a spurious wakeup occurs, the thread 
re-checks the condition before continuing execution.

**Resume flow:**  
After the user presses ENTER, `Control` sets `paused = false` on all 
threads and calls `monitor.notifyAll()` inside a `synchronized(monitor)` 
block, guaranteeing all sleeping threads are woken up consistently.

## Evidence

- First screenshot shows the program paused after 5 seconds, displaying the count of primes found so far.

![alt text](Images/Captura%20de%20pantalla%202026-06-03%20225436.png)

- Second screenshot shows the program resumed after pressing ENTER, with worker threads continuing their search.

![alt text](Images/image.png)

- Third screenshot shows the program paused again after another 5 seconds, with an updated count of primes found.

![alt text](Images/image-1.png)

### Observations

1. **notifyAll() over notify():** `notifyAll()` is used to wake all worker 
   threads at once. Using `notify()` would risk leaving threads sleeping 
   indefinitely.

2. **volatile flag:** The `paused` flag is `volatile` to ensure changes 
   made by `Control` are immediately visible to all threads.

3. **No deadlock risk:** Since `Control` always calls `notifyAll()` after 
   setting `paused = false`, no thread can wait forever.


## Part II — Concurrent SnakeRace (core of the lab)

## 1. Analysis of concurrency

### 1) How the code uses threads

The Snake Race program gives each snake its own independent thread using 
Java 21 virtual threads. When the application starts, one `SnakeRunner` 
is created per snake and submitted to a `VirtualThreadPerTaskExecutor`, 
which automatically assigns a virtual thread to each one.

Inside its thread, each snake runs an infinite loop independently from 
the others. On every iteration it decides whether to change direction, 
moves one step on the board, reacts to what it finds (a mouse, an 
obstacle, a turbo, or a teleport), and then sleeps for 80ms (or 40ms 
if turbo is active) before repeating.

The UI runs separately on the Swing Event Dispatch Thread (EDT). A 
`GameClock` triggers a repaint every 60ms using 
`SwingUtilities.invokeLater()`, keeping the visual update decoupled 
from the snake movement threads.

### 2) Identified concurrency issues

#### Possible race conditions

If two snakes eat a mouse at the same time, both threads will try to add 
new elements to the board simultaneously, which could place items in the 
same position or leave the collections in an inconsistent state.

The `randomEmpty()` method is not protected, so if two threads call it at 
the same time they could pick the same position for a new element.

#### Unsafe collections in concurrent context

`Snake.body` is an `ArrayDeque`, which is not thread-safe. The snake thread 
writes to it through `advance()` while the UI thread reads it through 
`snapshot()` to draw it on screen, which can cause a 
`ConcurrentModificationException`.

#### Busy-waiting and unnecessary synchronization

The `GameClock` keeps firing every 60ms even when paused, wasting CPU for 
no reason. Also, pressing Action only stops the repaint but the snake 
threads keep running in the background, so snakes continue moving even 
though the screen looks frozen.

## 2. Minimal corrections and critical regions

`Snake.advance()` and `snapshot()` were synchronized to prevent a data 
race between the snake thread writing the body and the UI reading it to 
paint the snake on screen.

`Board.step()` was synchronized so multiple snake threads cannot eat the 
same mouse or place elements in the same position at the same time.

`Board.randomEmpty()` was synchronized to ensure two threads never pick 
the same empty position when adding new elements to the board.

`SnakeRunner` was extended with a `paused` flag and a `pauseLock` monitor. 
Each runner suspends with `pauseLock.wait()` when paused and resumes with 
`pauseLock.notifyAll()`, avoiding busy-waiting and ensuring a consistent 
state when the game is paused.

## 3. Safe execution control (UI)

The Action button was updated to support Pause and Resume. When pressed, 
it pauses all `SnakeRunner` threads and the `GameClock`, waits 150ms for 
all threads to reach their suspension point, and then displays a popup 
with the longest and shortest snake at that moment. When pressed again, 
it resumes all threads and the clock simultaneously, ensuring a consistent 
game state on both pause and resume.

- First screenshot shows the game started.
![alt text](Images/image-2.png)

- Second screenshot shows the game paused after pressing Action, with a popup showing the longest and shortest snake lengths at that moment.
![alt text](Images/image-3.png)

- Third screenshot shows the game resumed after pressing Action again, with snakes continuing to move on screen.
![alt text](Images/image-4.png)

## 4. Robustness under load

The game was tested with `-Dsnakes=20` and higher values. No 
`ConcurrentModificationException` or deadlocks were observed. The 
synchronization applied to `Board.step()`, `Snake.advance()`, and 
`Snake.snapshot()` ensures that even with a high number of concurrent 
threads the game remains stable. The pause/resume mechanism also works 
correctly under load, suspending and resuming all threads consistently 
without leaving the game in an inconsistent state.

![alt text](Images/image-5.png)

