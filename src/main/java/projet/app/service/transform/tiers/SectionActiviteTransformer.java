package projet.app.service.transform.tiers;

import java.util.TreeMap;

public class SectionActiviteTransformer {

    private static final TreeMap<Integer, Integer> SECTION_MAPPING = new TreeMap<>();

    static {
        SECTION_MAPPING.put(1110, 1);
        SECTION_MAPPING.put(1111, 1);
        SECTION_MAPPING.put(1112, 1);
        SECTION_MAPPING.put(1113, 1);
        SECTION_MAPPING.put(1120, 1);
        SECTION_MAPPING.put(1121, 1);
        SECTION_MAPPING.put(1122, 1);
        SECTION_MAPPING.put(1123, 1);
        SECTION_MAPPING.put(1130, 1);
        SECTION_MAPPING.put(1210, 2);
        SECTION_MAPPING.put(1220, 2);
        SECTION_MAPPING.put(1301, 3);
        SECTION_MAPPING.put(1302, 3);
        SECTION_MAPPING.put(2100, 5);
        SECTION_MAPPING.put(2200, 6);
        SECTION_MAPPING.put(2301, 7);
        SECTION_MAPPING.put(2302, 7);
        SECTION_MAPPING.put(2303, 7);
        SECTION_MAPPING.put(2304, 7);
        SECTION_MAPPING.put(2305, 7);
        SECTION_MAPPING.put(2306, 7);
        SECTION_MAPPING.put(2307, 7);
        SECTION_MAPPING.put(2901, 8);
        SECTION_MAPPING.put(2902, 8);
        SECTION_MAPPING.put(2903, 8);
        SECTION_MAPPING.put(2909, 8);
        SECTION_MAPPING.put(3110, 10);
        SECTION_MAPPING.put(3111, 10);
        SECTION_MAPPING.put(3112, 10);
        SECTION_MAPPING.put(3113, 10);
        SECTION_MAPPING.put(3114, 10);
        SECTION_MAPPING.put(3115, 10);
        SECTION_MAPPING.put(3116, 10);
        SECTION_MAPPING.put(3117, 10);
        SECTION_MAPPING.put(3118, 10);
        SECTION_MAPPING.put(3119, 10);
        SECTION_MAPPING.put(3121, 10);
        SECTION_MAPPING.put(3122, 10);
        SECTION_MAPPING.put(3123, 10);
        SECTION_MAPPING.put(3124, 10);
        SECTION_MAPPING.put(3130, 11);
        SECTION_MAPPING.put(3131, 11);
        SECTION_MAPPING.put(3132, 11);
        SECTION_MAPPING.put(3133, 11);
        SECTION_MAPPING.put(3134, 11);
        SECTION_MAPPING.put(3140, 12);
        SECTION_MAPPING.put(3210, 13);
        SECTION_MAPPING.put(3211, 13);
        SECTION_MAPPING.put(3212, 13);
        SECTION_MAPPING.put(3213, 13);
        SECTION_MAPPING.put(3214, 13);
        SECTION_MAPPING.put(3215, 13);
        SECTION_MAPPING.put(3219, 13);
        SECTION_MAPPING.put(3220, 14);
        SECTION_MAPPING.put(3230, 14);
        SECTION_MAPPING.put(3231, 15);
        SECTION_MAPPING.put(3232, 15);
        SECTION_MAPPING.put(3233, 15);
        SECTION_MAPPING.put(3240, 15);
        SECTION_MAPPING.put(3310, 16);
        SECTION_MAPPING.put(3311, 16);
        SECTION_MAPPING.put(3312, 16);
        SECTION_MAPPING.put(3319, 16);
        SECTION_MAPPING.put(3320, 31);
        SECTION_MAPPING.put(3410, 17);
        SECTION_MAPPING.put(3411, 17);
        SECTION_MAPPING.put(3412, 17);
        SECTION_MAPPING.put(3419, 17);
        SECTION_MAPPING.put(3420, 18);
        SECTION_MAPPING.put(3510, 20);
        SECTION_MAPPING.put(3511, 20);
        SECTION_MAPPING.put(3512, 20);
        SECTION_MAPPING.put(3513, 20);
        SECTION_MAPPING.put(3514, 20);
        SECTION_MAPPING.put(3515, 20);
        SECTION_MAPPING.put(3520, 20);
        SECTION_MAPPING.put(3521, 20);
        SECTION_MAPPING.put(3522, 21);
        SECTION_MAPPING.put(3523, 20);
        SECTION_MAPPING.put(3529, 20);
        SECTION_MAPPING.put(3530, 19);
        SECTION_MAPPING.put(3540, 19);
        SECTION_MAPPING.put(3550, 22);
        SECTION_MAPPING.put(3551, 22);
        SECTION_MAPPING.put(3559, 22);
        SECTION_MAPPING.put(3560, 22);
        SECTION_MAPPING.put(3610, 23);
        SECTION_MAPPING.put(3620, 23);
        SECTION_MAPPING.put(3690, 23);
        SECTION_MAPPING.put(3691, 23);
        SECTION_MAPPING.put(3692, 23);
        SECTION_MAPPING.put(3699, 23);
        SECTION_MAPPING.put(3710, 24);
        SECTION_MAPPING.put(3720, 24);
        SECTION_MAPPING.put(3810, 25);
        SECTION_MAPPING.put(3811, 25);
        SECTION_MAPPING.put(3812, 25);
        SECTION_MAPPING.put(3813, 25);
        SECTION_MAPPING.put(3819, 25);
        SECTION_MAPPING.put(3820, 25);
        SECTION_MAPPING.put(3821, 33);
        SECTION_MAPPING.put(3822, 33);
        SECTION_MAPPING.put(3823, 33);
        SECTION_MAPPING.put(3824, 33);
        SECTION_MAPPING.put(3825, 33);
        SECTION_MAPPING.put(3829, 33);
        SECTION_MAPPING.put(3830, 33);
        SECTION_MAPPING.put(3831, 33);
        SECTION_MAPPING.put(3832, 33);
        SECTION_MAPPING.put(3833, 33);
        SECTION_MAPPING.put(3839, 33);
        SECTION_MAPPING.put(3840, 30);
        SECTION_MAPPING.put(3841, 33);
        SECTION_MAPPING.put(3842, 33);
        SECTION_MAPPING.put(3843, 29);
        SECTION_MAPPING.put(3844, 30);
        SECTION_MAPPING.put(3845, 33);
        SECTION_MAPPING.put(3849, 30);
        SECTION_MAPPING.put(3850, 33);
        SECTION_MAPPING.put(3851, 33);
        SECTION_MAPPING.put(3852, 33);
        SECTION_MAPPING.put(3853, 98);
        SECTION_MAPPING.put(3901, 32);
        SECTION_MAPPING.put(3902, 32);
        SECTION_MAPPING.put(3903, 32);
        SECTION_MAPPING.put(3909, 32);
        SECTION_MAPPING.put(4101, 35);
        SECTION_MAPPING.put(4102, 35);
        SECTION_MAPPING.put(4103, 35);
        SECTION_MAPPING.put(4104, 35);
        SECTION_MAPPING.put(4105, 35);
        SECTION_MAPPING.put(4106, 35);
        SECTION_MAPPING.put(4107, 35);
        SECTION_MAPPING.put(4200, 36);
        SECTION_MAPPING.put(5000, 41);
        SECTION_MAPPING.put(6110, 46);
        SECTION_MAPPING.put(6111, 46);
        SECTION_MAPPING.put(6112, 46);
        SECTION_MAPPING.put(6113, 46);
        SECTION_MAPPING.put(6114, 46);
        SECTION_MAPPING.put(6115, 46);
        SECTION_MAPPING.put(6116, 46);
        SECTION_MAPPING.put(6117, 46);
        SECTION_MAPPING.put(6120, 46);
        SECTION_MAPPING.put(6130, 46);
        SECTION_MAPPING.put(6131, 46);
        SECTION_MAPPING.put(6132, 46);
        SECTION_MAPPING.put(6133, 46);
        SECTION_MAPPING.put(6134, 46);
        SECTION_MAPPING.put(6135, 46);
        SECTION_MAPPING.put(6136, 46);
        SECTION_MAPPING.put(6140, 46);
        SECTION_MAPPING.put(6210, 47);
        SECTION_MAPPING.put(6220, 47);
        SECTION_MAPPING.put(6225, 62);
        SECTION_MAPPING.put(6310, 56);
        SECTION_MAPPING.put(6320, 55);
        SECTION_MAPPING.put(7110, 49);
        SECTION_MAPPING.put(7111, 49);
        SECTION_MAPPING.put(7112, 49);
        SECTION_MAPPING.put(7113, 49);
        SECTION_MAPPING.put(7114, 49);
        SECTION_MAPPING.put(7115, 49);
        SECTION_MAPPING.put(7116, 49);
        SECTION_MAPPING.put(7120, 50);
        SECTION_MAPPING.put(7121, 50);
        SECTION_MAPPING.put(7122, 50);
        SECTION_MAPPING.put(7123, 50);
        SECTION_MAPPING.put(7130, 51);
        SECTION_MAPPING.put(7131, 51);
        SECTION_MAPPING.put(7132, 51);
        SECTION_MAPPING.put(7190, 52);
        SECTION_MAPPING.put(7191, 52);
        SECTION_MAPPING.put(7192, 52);
        SECTION_MAPPING.put(7201, 53);
        SECTION_MAPPING.put(7202, 63);
        SECTION_MAPPING.put(8101, 64);
        SECTION_MAPPING.put(8102, 64);
        SECTION_MAPPING.put(8103, 64);
        SECTION_MAPPING.put(8200, 65);
        SECTION_MAPPING.put(8310, 68);
        SECTION_MAPPING.put(8311, 68);
        SECTION_MAPPING.put(8312, 68);
        SECTION_MAPPING.put(8313, 68);
        SECTION_MAPPING.put(8320, 74);
        SECTION_MAPPING.put(8321, 69);
        SECTION_MAPPING.put(8322, 69);
        SECTION_MAPPING.put(8323, 62);
        SECTION_MAPPING.put(8324, 71);
        SECTION_MAPPING.put(8325, 73);
        SECTION_MAPPING.put(8329, 74);
        SECTION_MAPPING.put(8330, 77);
        SECTION_MAPPING.put(9101, 84);
        SECTION_MAPPING.put(9102, 84);
        SECTION_MAPPING.put(9200, 86);
        SECTION_MAPPING.put(9302, 62);
        SECTION_MAPPING.put(9310, 85);
        SECTION_MAPPING.put(9311, 85);
        SECTION_MAPPING.put(9312, 85);
        SECTION_MAPPING.put(9313, 85);
        SECTION_MAPPING.put(9320, 72);
        SECTION_MAPPING.put(9325, 87);
        SECTION_MAPPING.put(9331, 86);
        SECTION_MAPPING.put(9332, 75);
        SECTION_MAPPING.put(9340, 88);
        SECTION_MAPPING.put(9350, 94);
        SECTION_MAPPING.put(9390, 96);
        SECTION_MAPPING.put(9391, 94);
        SECTION_MAPPING.put(9395, 98);
        SECTION_MAPPING.put(9399, 96);
        SECTION_MAPPING.put(9410, 59);
        SECTION_MAPPING.put(9411, 59);
        SECTION_MAPPING.put(9412, 59);
        SECTION_MAPPING.put(9413, 60);
        SECTION_MAPPING.put(9414, 90);
        SECTION_MAPPING.put(9415, 90);
        SECTION_MAPPING.put(9420, 91);
        SECTION_MAPPING.put(9490, 90);
        SECTION_MAPPING.put(9510, 95);
        SECTION_MAPPING.put(9511, 95);
        SECTION_MAPPING.put(9512, 95);
        SECTION_MAPPING.put(9513, 45);
        SECTION_MAPPING.put(9514, 95);
        SECTION_MAPPING.put(9519, 95);
        SECTION_MAPPING.put(9520, 96);
        SECTION_MAPPING.put(9530, 96);
        SECTION_MAPPING.put(9590, 96);
        SECTION_MAPPING.put(9591, 96);
        SECTION_MAPPING.put(9592, 96);
        SECTION_MAPPING.put(9599, 96);
        SECTION_MAPPING.put(9600, 99);
        SECTION_MAPPING.put(9710, 98);
        SECTION_MAPPING.put(9720, 98);
    }

    /**
     * Transform sectionactivite source value to valeur régulateur.
     * Steps:
     * 1. Divide source by 10
     * 2. Exact match in mapping
     * 3. If not found, find closest key numerically
     */
    public static Integer transform(String sectionActivite) {
        if (sectionActivite == null || sectionActivite.isBlank()) {
            return null;
        }
        try {
            int sourceValue = Integer.parseInt(sectionActivite.trim());
            int normalized = sourceValue / 10;

            // Exact match
            if (SECTION_MAPPING.containsKey(normalized)) {
                return SECTION_MAPPING.get(normalized);
            }

            // Closest lower
            Integer lower = SECTION_MAPPING.floorKey(normalized);
            // Closest higher
            Integer higher = SECTION_MAPPING.ceilingKey(normalized);

            if (lower == null && higher == null) return null;
            if (lower == null) return SECTION_MAPPING.get(higher);
            if (higher == null) return SECTION_MAPPING.get(lower);

            int diffLower = Math.abs(normalized - lower);
            int diffHigher = Math.abs(higher - normalized);

            return diffLower <= diffHigher
                    ? SECTION_MAPPING.get(lower)
                    : SECTION_MAPPING.get(higher);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
