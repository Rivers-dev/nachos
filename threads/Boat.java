package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 2, b);

    }

    public static void begin(int adults, int children, BoatGrader b)
    {
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		
		for (int i = 0; i < adults; i++){
			Runnable runner = new Runnable() {
				public void run() {
						AdultItinerary();
					}
				};
			KThread thread = new KThread(runner);
			thread.setName("Oahu");
			thread.fork();
		}

		for (int i = 0; i < children; i++) {
			Runnable runner = new Runnable() {
				public void run() {
						ChildItinerary();
					}
				};
			KThread thread = new KThread(runner);
			thread.setName("Oahu");
			thread.fork();
		}

		bg = b;
		
		total = adults + children; 
		hasPassenger = false;
		passengerThread = null;
		asleep = 0;
		finished = false;
		boat = new Lock();
		passenger = new Condition(boat);
		oahuTotal = adults + children; 
		oahuChildren = children;
		childOahuQueue = new Condition(boat);
		adultOahuQueue = new Condition(boat);
		molokaiTotal = 0;
		childMolokaiQueue = new Condition(boat);
		adultMolokaiQueue = new Condition(boat);
    }

    static void AdultItinerary()
    {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
		boat.acquire();

		if (asleep < total - 1) {
			asleep++;
			adultOahuQueue.sleep(); 
		}

		while (!finished) {
			if (oahuChildren < 2) {
				bg.AdultRowToMolokai();
				oahuTotal--;
				molokaiTotal++;

				if (molokaiTotal == total){
					finished = true;
					adultMolokaiQueue.sleep();
				}
				else {
					childMolokaiQueue.wake();
					adultMolokaiQueue.sleep();
				}
			}
			else {
				childOahuQueue.wake();
				adultOahuQueue.sleep();
			}
		}
    }

    static void ChildItinerary()
    {
		boat.acquire();
		if (asleep < total - 1) {
			asleep++;
			childOahuQueue.sleep();
		}

		while (!finished) {		
			if (molokaiTotal == total) { 
				finished = true;
				childMolokaiQueue.sleep();
			}
	
			if (KThread.currentThread().getName().equals("Oahu")) {
				if (oahuChildren >= 2) {
					if (hasPassenger) {
						bg.ChildRowToMolokai();
						bg.ChildRideToMolokai();

						oahuTotal -= 2;
						oahuChildren -= 2;
						molokaiTotal += 2;
	
						KThread.currentThread().setName("Molokai");
	
						hasPassenger = false;
						passengerName = "null";
						passengerThread.setName("Molokai");
						passenger.wake();
						childMolokaiQueue.sleep();
					}
					else {
						hasPassenger = true;
						passengerThread = KThread.currentThread();
						childOahuQueue.wake();
						passenger.sleep();	
					}
				}
				else if (oahuChildren == oahuTotal) {
					bg.ChildRowToMolokai();

					oahuTotal--;
					oahuChildren--;
					molokaiTotal++;
					KThread.currentThread().setName("Molokai");
					childMolokaiQueue.sleep();
				}
				else {
					adultOahuQueue.wake();
					childOahuQueue.sleep();
				}
			}
	
			else if (KThread.currentThread().getName().equals("Molokai")) { 
				bg.ChildRowToOahu();

				oahuTotal++;
				oahuChildren++;
				molokaiTotal--;

				KThread.currentThread().setName("Oahu");
	
				if (oahuChildren < 2) {
					adultOahuQueue.wake();
					childOahuQueue.sleep();
				}
			}
		}
    }

	static BoatGrader bg;
	private static Condition passenger;
	private static boolean hasPassenger;
	private static KThread passengerThread;
	private static String passengerName;
    public static boolean finished;
	private static Lock boat;
	private static int total;
	private static int asleep;
	private static int oahuTotal;
	private static int oahuChildren;
	private static Condition childOahuQueue;
	private static Condition adultOahuQueue;
	private static int molokaiTotal;
	private static Condition childMolokaiQueue;
	private static Condition adultMolokaiQueue;
}
