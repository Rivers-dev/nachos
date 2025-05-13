package nachos.threads;

import java.util.Enumeration;
import java.util.Hashtable;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        if (!hash.isEmpty()) {
    		Machine.interrupt().disable();	

    		Enumeration times = hash.keys();	
    		Enumeration threads = hash.elements();	

    		while (threads.hasMoreElements()) {	
    			Long wakeTime = (Long)times.nextElement();
    			KThread thread = (KThread)threads.nextElement();	

    			if(wakeTime <= (Machine.timer().getTime())){	
    				thread.ready();							
    				hash.remove(wakeTime);
    			}
    		}

    		Machine.interrupt().enable();
    	}

    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */

    Hashtable<Long,KThread> hash = new Hashtable<Long,KThread>();
    public void waitUntil(long x) {
	    long wakeTime = Machine.timer().getTime() + x;
	    boolean intStatus = Machine.interrupt().disable();

	    hash.put(wakeTime, KThread.currentThread());
	    KThread.currentThread().sleep();

	    Machine.interrupt().restore(intStatus);	
    }
}
