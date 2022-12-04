package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Arrays;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // implement

    /**
     * a queue of the incoming actions - key pressed translated to slots locations
     */
    public Queue<Integer[]> incomingActions;

     /**
     * Indicate the remaine freeze time after pelenty or point
     */
    protected long frozenTimer;

    protected Object actionsLocker;

    

    

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        // implement
        incomingActions = new LinkedList<Integer[]>();
        frozenTimer = -1000;
        terminate = false;
        actionsLocker = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

        if (!human) createArtificialIntelligenceSmart();

        while (!terminate) {
            // implement main player loop

        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // implement player key press simulator
                Random r = new Random();
                keyPressed(r.nextInt(12)); 
                try{
                    Thread.sleep(500);
                }catch(InterruptedException ex){}
                // try {
                //     synchronized (this) { wait(); }
                // } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    private void createArtificialIntelligenceSmart() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // implement player key press simulator
                Random r = new Random();
                Integer[] attempt = new Integer[3];
                for (int i = 0; i < 1000; i++) {
                    attempt[0] = r.nextInt(12) ;
                    attempt[1] = r.nextInt(12) ;
                    attempt[2] = r.nextInt(12) ;

                    int[] testSet = new int[3];
                    // if(attempt[0] == null || attempt[1] == null || attempt[2] == null)
                    //     continue;
                    if(table.slotToCard[attempt[0]] != null && table.slotToCard[attempt[1]] != null && table.slotToCard[attempt[2]] != null){
                        testSet[0] = table.slotToCard[attempt[0]];
                        testSet[1] = table.slotToCard[attempt[1]];
                        testSet[2] = table.slotToCard[attempt[2]];
                    }
                    // if(testSet[0] == null || testSet[1] == null || testSet[2] == null)
                    //     continue;


                    if(env.util.testSet(testSet))
                        break;
                }

                // System.out.println(Arrays.toString(attempt));

                for (int i = 0; i < attempt.length; i++) {
                    
                    keyPressed(attempt[i]); 
                    try{
                        Thread.sleep(500);
                    }catch(InterruptedException ex){}
                }
                
                // try {
                //     synchronized (this) { wait(); }
                // } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // implement

        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

        // implement

        // when arriving to full capacitiy(3) need to lock the insertion avoiding 
        // any input until the queue is complitly defleted
        synchronized(actionsLocker){
        if(frozenTimer < 0){
            boolean added = false;
            for(Integer[] i : incomingActions)
                if(i[0].equals(table.slotToCard[slot]))
                    added = true;
            if (!added && incomingActions.size() < 3 && table.slotToCard[slot] != null){
                table.placeToken(id, slot);
                Integer[] toAdd = {table.slotToCard[slot], slot};
                incomingActions.add(toAdd);
                if (incomingActions.size() == 3){
                    synchronized(table.queueLocker){
                        table.setsToCheckQueue.add(id);
                        System.out.println("player " + id + " added to the queue: " + table.setsToCheckQueue.toString());
                    }
                }
            }
        }
            // if the incoming actions arrived to full capacity (3) send my id to the ready sets for check queue
            
                // try{
                //     Thread.sleep(1000);
                // }catch(InterruptedException ex){}
                
            
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // implement

// ??
        // int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        frozenTimer = env.config.pointFreezeMillis;
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(id,frozenTimer);

    }


    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // implement

        frozenTimer = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(id,frozenTimer);


    }

    public int getScore() {
        return score;
    }

    // implement
    
     
     /**
      *  @pre incomingActions.length == 3
      *  @post incomingActions.length == 0
      * @return 
      */
    public int[][] getSetFromQueue(){ // may be CRITICAL SECTION

        int[][] setToCheck = new int[3][2];
        for(int i = 0 ; i < setToCheck.length;i++){
            setToCheck[i][0] = incomingActions.peek()[0];
            setToCheck[i][1] = incomingActions.peek()[1];
            incomingActions.poll();
        }

        return setToCheck;
    }
    

    public void updateFreezeTime(){
        
        // System.out.println("frozen timer" + frozenTimer);
        if (frozenTimer > 0){
            env.ui.setFreeze(id,frozenTimer);
            frozenTimer -= 1000;
        }
        else if (frozenTimer == 0){
            env.ui.setFreeze(id,frozenTimer);
            frozenTimer -= 1000;
        }

    }
}

