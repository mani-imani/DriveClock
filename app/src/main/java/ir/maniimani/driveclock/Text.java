package ir.maniimani.driveclock;

final class Text {
    private Text() {
    }

    static String fa(long n) {
        return fa(String.valueOf(n));
    }

    static String fa(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return b.toString();
    }

    private static String two(int v) {
        return fa(v < 10 ? "0" + v : String.valueOf(v));
    }

    /** "۲۲:۴۰" */
    static String time(int h, int m) {
        return two(h) + ":" + two(m);
    }

    /** "۱۸:۰۰" */
    static String hour(int h) {
        return two(h) + ":" + two(0);
    }

    /** "۴۲ دقیقه رانندگی", "۱ ساعت و ۱۲ دقیقه رانندگی"; empty under a minute. */
    static String drive(long ms) {
        long min = ms / 60000;
        if (min < 1) return "";
        long h = min / 60, m = min % 60;
        if (h == 0) return fa(m) + " دقیقه رانندگی";
        if (m == 0) return fa(h) + " ساعت رانندگی";
        return fa(h) + " ساعت و " + fa(m) + " دقیقه رانندگی";
    }
}
