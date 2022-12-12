package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
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
    protected Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    public final boolean human;

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
     * chnges every iteration to notifiy when display change needs to be done 
     */
    // private int countUpdate;

    /**
     * @true - if the dealer put you in palenty and you have to move a card
     */
    private boolean needToMoveCardBecausePanelty;

    

    

    

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
        needToMoveCardBecausePanelty = false;
        // countUpdate =- 1;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        synchronized(table.lock){
            if (!human) {
                createArtificialIntelligenceSmart();
                try{table.lock.wait();}
                catch(InterruptedException ex){}
            }
            else
                table.lock.notifyAll();
        }


        while (!terminate) {

            while(frozenTimer >= 0 && !terminate){

                env.ui.setFreeze(id, frozenTimer);
                frozenTimer -= 1000;

                try{ Thread.sleep(1000); }
                catch(InterruptedException ex){}
            }
            if(terminate)
                break;
            synchronized(this){
                try{ wait(); }
                catch(InterruptedException ex){}
            }
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
                keyPressed(r.nextInt((int)(env.config.rows*env.config.columns))); 
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
            synchronized(table.lock){
                table.lock.notifyAll();
            }

            while (!terminate) {
                // implement player key press simulator

                if(needToMoveCardBecausePanelty){ //got a panelty beacuse a wrong set - so remove all my tokens first
                    int[][] tokensToMove = getSetFromQueue();
                    keyPressed(tokensToMove[0][1]);
                    keyPressed(tokensToMove[1][1]);
                    keyPressed(tokensToMove[2][1]);
                }
                else{ //choose new set

                    Random r = new Random();
                    Integer[] attempt = new Integer[3];
                    for (int i = 0; i < 5000; i++) {
                        attempt[0] = r.nextInt((int)(env.config.rows*env.config.columns)) ;
                        attempt[1] = r.nextInt((int)(env.config.rows*env.config.columns)) ;
                        attempt[2] = r.nextInt((int)(env.config.rows*env.config.columns)) ;

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

                        if (attempt[0] == attempt[1] || attempt[0] == attempt[2] || attempt[1] == attempt[2])
                            break;
                            
                        
                        keyPressed(attempt[i]); 

                        try{
                            Thread.sleep(800);
                        }catch(InterruptedException ex){}
                    }
                }
                
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public synchronized void terminate() {
        // implement
        terminate = true;
        notifyAll();
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
            if(frozenTimer >= 0)
                return;

            //first thing - remove token if already in queue
            Integer[] toRemove = null;
            for(Integer[] i : incomingActions)
                    if(i[1] != null) 
                        //check if he pressed the card location:
                        if(i[1].equals(slot)){
                            toRemove = i;
                        }
            
            if(toRemove != null){
                incomingActions.remove(toRemove);
                table.removeToken(id, toRemove[1]);
                needToMoveCardBecausePanelty = false;
            }
            else{
                if(!table.setsToCheckQueue.contains(id)){
                    //if didn't choose this card yet:
                    if (incomingActions.size() < 3 && table.slotToCard[slot] != null){
                        table.placeToken(id, slot);
                        Integer[] toAdd = {table.slotToCard[slot], slot};
                        incomingActions.add(toAdd);
                        if (incomingActions.size() == 3){
                            try{
                                table.lockDealerQueue.acquire();
                                table.setsToCheckQueue.add(id);
                                // System.out.println("player " + id + " added to the queue: " + table.setsToCheckQueue.toString());
                            } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
                            table.lockDealerQueue.release();
                        }
                    }
                }
            }
                        
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public synchronized void point() {
        // implement

        frozenTimer = env.config.pointFreezeMillis;
        env.ui.setScore(id, ++score);
        notifyAll();

    }


    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void panelty() {
        // implement
        needToMoveCardBecausePanelty = true;
        frozenTimer = env.config.penaltyFreezeMillis;
        notifyAll();

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
    public int[][] getSetFromQueue(){ // CRITICAL SECTION dealer has the key therefore it is synchronized

        int[][] setToCheck = new int[3][2];
        for(int i = 0 ; i < setToCheck.length;i++){
            setToCheck[i][0] = incomingActions.peek()[0];
            setToCheck[i][1] = incomingActions.peek()[1];
            incomingActions.add(incomingActions.peek());
            incomingActions.poll();
        }

        return setToCheck;
    }

    public void emptyQueue(){ // CRITICAL SECTION dealer has the key therefore it is synchronized
        while(!incomingActions.isEmpty())
            incomingActions.poll();
    }
    

    public synchronized void updateFreezeTime(){
        if(frozenTimer > 0){

            frozenTimer -= 10;
            
            if(frozenTimer == 0)
                frozenTimer = -1000;
            
            if(frozenTimer %1000 == 0)
                notifyAll();
        }
    }
        

    
}

