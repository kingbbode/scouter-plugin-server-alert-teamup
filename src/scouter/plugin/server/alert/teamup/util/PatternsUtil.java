package scouter.plugin.server.alert.teamup.util;

import scouter.util.StringUtil;

public class PatternsUtil {
	public static boolean isInValid(String patterns, String url) {
		if (!StringUtil.isEmpty(patterns)) {
			String[] methodPatterns = StringUtil.split(patterns, ',');
			if (methodPatterns.length > 0) {
				String[] urlDot = StringUtil.split(url.trim(), '.');
				if (urlDot.length > 0) {
					for (String pattern : methodPatterns) {
						String[] patternDot = StringUtil.split(pattern.trim(), '.');
						boolean result = false;
						for (int i = 0; i < urlDot.length; i++) {
							if (patternDot.length > i) {
								if (patternDot[i] == "*") {
									if (result) {
										return false;
									}
									break;
								} else if (urlDot[i] == patternDot[i]) {
									if (i == urlDot.length - 1 && i == patternDot.length - 1) {
										return false;
									}
									result = true;
								}
							}
							break;
						}
					}
				}
			}
		} else {
			return true;
		}
		return false;
	}
}
