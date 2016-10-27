package scouter.plugin.server.alert.teamup.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternsUtil {
	public static boolean isValid(String patterns, String url) {
		Pattern pattern = Pattern.compile(patterns);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
	}
}
