package droidelhelpers;

import java.util.Random;

public class Nondet {
    private static Random r = new Random();
    
    public static int nondetInt() {
	return r.nextInt();
    }
    
    public static boolean nondetBool() {
	return r.nextBoolean();
    }
}
