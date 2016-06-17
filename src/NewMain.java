
import filerequestserver.Utils;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Braynstorm on 16.6.2016 г..
 */
public class NewMain {

	public static void main(String[] args) {
		Set<Integer> ids = new HashSet<>();

		ids.add(0);
		ids.add(2);
		ids.add(-7);
		ids.add(4);

		//		int id = 0;
		//		while (Integer.compareUnsigned(id, -1) < 0) {
		//			id = Utils.findLowestMissingNumber(ids);
		//			ids.add(id);
		//
		//			System.out.println(id);
		//		}

		for (int i = 0; Integer.compareUnsigned(i, -1) < 0; i++) {
			if (Integer.compareUnsigned(i, -20) >= 0)
				System.out.println(Integer.toUnsignedString(i));
		}
	}
}
