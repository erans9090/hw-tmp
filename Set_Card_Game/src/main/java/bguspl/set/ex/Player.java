package bguspl.set.ex;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import javax.xml.transform.SourceLocator;

import java.security.Guard;
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
    protected volatile boolean terminate;

    protected volatile boolean aiTerminated;

    protected volatile boolean playerTerminated;

    

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
     * the dealer
     */
    private Dealer dealer;

    /**
     * holds the current placed tokens([card][slot])
     * @size is 3*2
     */
    protected Set<Integer[]> setToCheck;

    protected boolean needToRemoveToken;

    protected boolean waitingToCheck;


    

    


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
        this.dealer = dealer;
        // System.out.println("in player " + id + " const : " + dealerThread);
        incomingActions = new LinkedList<Integer[]>();
        frozenTimer = -1000;
        terminate = false;
        actionsLocker = new Object();
        needToMoveCardBecausePanelty = false;
        setToCheck = new HashSet<Integer[]>(3);
        needToRemoveToken = false;
        waitingToCheck = false;
        aiTerminated = false;
        playerTerminated = false;
        // countUpdate =- 1;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        
        // deals threads order  -> 
        synchronized(table.lock){
            if (!human) {
                createArtificialIntelligenceImproved();
                try{table.lock.wait();}
                catch(InterruptedException ex){}
            }
            else
                table.lock.notifyAll();
        }
        //   <-
        
        //--- main loop player ---//
        while (!terminate) {

            waitUntilWoken();
            updateFreezeTimeDisplay();
            manageInput();
            
        }

        System.out.println("# try to close ai " + id);
        if (!human){
            // Boolean closed = false;
            aiThread.interrupt();
            while(aiThread.isAlive()){
                try {
                    System.out.println("# ai " + id + " is -> " + aiThread.isAlive() + " trying to close it loop");

                    aiThread.join(); }
                catch (InterruptedException ignored) {
                System.out.println("# ai "+ id +" close interupted");
                aiThread.interrupt();
            }
            }

            System.out.println("# ai "+id +": spose to be close");
        }

        playerTerminated = true;
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    private void updateFreezeTimeDisplay(){
        
        if(terminate)
            return;
                    
        while(frozenTimer >= 0 && !terminate){

            env.ui.setFreeze(id, frozenTimer);
            frozenTimer -= 1000;
            
            
            try{Thread.sleep(1000);}
            catch(InterruptedException ex){}
                if(frozenTimer < 0)
                    if(!human)
                        aiThread.interrupt();
                    
        }
    }

    private void manageInput(){

        if(terminate)
            return;

        // System.out.println("manage input");
        boolean needToAdd = false;

        synchronized(actionsLocker){
            // System.out.println("player " + id + " got actionslocker key");

            while(!incomingActions.isEmpty() && !terminate){
                
                Integer[] card_slot = incomingActions.poll();
                // if(card_slot[0] == null )
                //     break;
                // System.out.println("player: "+ id + " got card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());

                // if(incomingActions.isEmpty())System.out.println("actions Queue is empty");
                // System.out.println("is setToCheck contains card_slot");
                // System.out.println(isSlotInSetToCheck(card_slot[1]));

                if(isSlotInSetToCheck(card_slot[1])){
                    
                    // removeToken(card_slot[0],card_slot[1]) <- main action

                    System.out.println("is the set removed: " + removeToken(card_slot[0],card_slot[1]));
                    System.out.println("player: " + id + " remove card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());
                    
                }
                else if(card_slot[0] != null && table.isRelevant(card_slot[0], card_slot[1]) && setToCheck.size() < 3){
 
                    setToCheck.add(card_slot);
                    table.placeToken(id,card_slot[1]);
                    if(setToCheck.size() == 3){
                        needToAdd = true;
                        waitingToCheck = true;       
                    }
                    System.out.println("player: "+ id + " add card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());
                }
                else{
                    System.out.println("player: "+ id + " denayed card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());

                }
            }
        }

        if(needToAdd && !terminate){
            boolean catchDealerQueue = false;
            while(!catchDealerQueue && !terminate){
                try{
                    table.lockDealerQueue.acquire();

                    table.setsToCheckQueue.add(id);
                    System.out.println("dealer add to queue player: "+ id + " with the set: " + printSetToCheck());
                                
                    dealer.getThread().interrupt();
                    table.lockDealerQueue.release();
                    catchDealerQueue = true;
                }
                catch(InterruptedException ex) {System.out.println("----player " + id + " didn't catch dealer queue-----");}
            }
        }
    }
        
    

    private synchronized void waitUntilWoken(){
        if(terminate)
            return;
            

        System.out.println("player " + id + " is waiting");
        try{ wait();}
        catch(InterruptedException ex){ System.out.println("player " + id + " stop waiting");}
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    // private void createArtificialIntelligence() {
    //     // note: this is a very very smart AI (!)
    //     aiThread = new Thread(() -> {
    //         System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
    //         while (!terminate) {
    //             // implement player key press simulator
    //             Random r = new Random();
    //             keyPressed(r.nextInt((int)(env.config.rows*env.config.columns))); 
    //             try{
    //                 Thread.sleep(500);
    //             }catch(InterruptedException ex){}
    //             // try {
    //             //     synchronized (this) { wait(); }
    //             // } catch (InterruptedException ignored) {}
    //         }
    //         System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    //     }, "computer-" + id);
    //     aiThread.start();
    // }

    private void createArtificialIntelligenceImproved() {

        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            synchronized(table.lock){
                table.lock.notifyAll();
            }

            System.out.println("ai " +id + " going to sleep");
            try{Thread.sleep(3000);}
            catch(InterruptedException ignored){System.out.println("ai sleep interupted");}
            System.out.println("ai " + id+ "woke up");


            // -- main loop --//
            while (!terminate) {

                // while(waitingToCheck)
                // {}

                while(!table.tableIsReady && !terminate){ //change hear!!!
                        try{ Thread.sleep((10));}
                        catch(InterruptedException ex){}
                    }

                boolean startAgainTheLoop = false;

                synchronized(actionsLocker){
                    for(Integer[] card_slot : setToCheck)
                        table.removeToken(id, card_slot[1]);
                    incomingActions.clear();
                    setToCheck.clear();
                    // needToRemoveToken = false;
                }

                // try{Thread.sleep(1000);}
                // catch(InterruptedException ignored){}

                List<Integer> cards = new LinkedList<>();
                for (Integer card : table.slotToCard) {
                    if(card != null)
                        cards.add(card);
                }
                List<int[]> sets = env.util.findSets(cards,1);

                Random r = new Random();

                int chance = r.nextInt(3);
                
                if (chance == 0 && sets.size() > 0){
                    for (int card : sets.get(0)) {
                        
                        System.out.println("ai " +id+ " going to sleep");
                        try{Thread.sleep(50);}
                        catch(InterruptedException ex){System.out.println("ai sleep interupted");}
                        if(terminate){
                            startAgainTheLoop = true;
                            break;
                        }
                        System.out.println("ai " +id+ " woke up");

                        Integer slot = table.cardToSlot[card];
                        if(slot != null){
                            keyPressed(slot.intValue());
                            System.out.println("ai of player: " + id + " key pressed slot " + slot);
                        }
                        else{
                            System.out.println("slot is null");
                            startAgainTheLoop = true;
                            break;
                        }

                    }
                }
                else{
                    Integer slot1 = r.nextInt(11);
                    Integer slot2 = r.nextInt(11);
                    Integer slot3 = r.nextInt(11);

                    while(slot2 == slot1)
                        slot2 = r.nextInt(11);

                    while(slot3 == slot2 | slot3 == slot1)
                        slot3 = r.nextInt(11);

                    if(table.slotToCard[slot1] == null || table.slotToCard[slot2] == null || table.slotToCard[slot3] == null)
                        continue;

                    keyPressed(slot1);
                    System.out.println("ai of player: " + id + " key pressed slot " + slot1);

                    keyPressed(slot2);
                    System.out.println("ai of player: " + id + " key pressed slot " + slot2);
                    
                    keyPressed(slot3);
                    System.out.println("ai of player: " + id + " key pressed slot " + slot3);


                }

                if(startAgainTheLoop)
                    continue;

                synchronized(aiThread){
                    try{System.out.println(id + " ai is waiting"); 
                        aiThread.wait(); }
                    catch( InterruptedException ex ){
                        System.out.println(id + " ai back to work");
                    }
                }





            }
            aiTerminated = true;
            // playerThread.interrupt();
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
        aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

        // implement

        
        if(frozenTimer > 0 || table.slotToCard[slot] == null || incomingActions.size() >= 3){
            System.out.println("key presed: but frozen or slot is empty or to much actions");
            return;}

        // when arriving to full capacitiy(3) need to lock the insertion avoiding 
        // any input until the queue is complitly defleted
        synchronized(actionsLocker){

            // System.out.println("key presed: player "+ id + " got slot " + slot);
            // System.out.println("the queue now is: " + printSetToCheck());

            // keypressed

            if(!waitingToCheck){
                Integer[] tmp = {table.slotToCard[slot],slot};
                incomingActions.add(tmp);
                synchronized(playerThread){
                playerThread.interrupt();}
            }
                        
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

        frozenTimer = env.config.pointFreezeMillis;
        env.ui.setScore(id, ++score);
        // needToRemoveToken = false;
        // if(!human)try{synchronized(aiThread){aiThread.wait();}}
        // catch(InterruptedException ignored){}
        playerThread.interrupt();

    }


    /**
     * Penalize a player and perform other related actions.
     */
    public void panelty() {
        // implement
        // needToRemoveToken = true;
        System.out.println("--------------Panelty----------------");
        
        frozenTimer = env.config.penaltyFreezeMillis;
        // if(!human)try{synchronized(aiThread){aiThread.wait();}}
        //             catch(InterruptedException ignored){}
        playerThread.interrupt();

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
    public int[][] getSetFromHahSet(){ // CRITICAL SECTION dealer has the key therefore it is synchronized

        int[][] ans = new int[3][2];
        int i = 0;
        for(Integer[] card_slot : setToCheck){
            ans[i][0] = card_slot[0];
            ans[i][1] = card_slot[1];
            i += 1;
        }
        return ans;
    }

    public void emptyHashSet(){ // CRITICAL SECTION dealer has the key therefore it is synchronized
        incomingActions.clear();
        setToCheck.clear();
        needToRemoveToken = false;
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

    private boolean isSlotInSetToCheck(int slot)
    {
        for(Integer[] i : setToCheck)
            if(i[1] == slot)
                return true;
        return false;
    }

    public String printSetToCheck()
    {
        String ans = "[";
        for (Integer[] cardslot: setToCheck) {
            ans += Arrays.toString(cardslot) + ",";
        }
        return ans.substring(0, ans.length()) + "]";
    }

    protected boolean removeToken(int card, int slot)
    {
        for (Integer[] set : setToCheck) {
            if (slot == set[1]){

                setToCheck.remove(set);
                table.removeToken(id,slot);
                needToRemoveToken = false;
                return true;

            }
        }
        return false;
    }

    protected void wakeAi(){
        if(!human){
            synchronized(aiThread){
                System.out.println("ai interupt");
                aiThread.interrupt();
            }
        }
    }
        
    
}

