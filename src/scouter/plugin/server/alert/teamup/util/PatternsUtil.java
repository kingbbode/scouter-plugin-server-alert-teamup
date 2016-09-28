package scouter.plugin.server.alert.teamup.util;

import scouter.util.StringUtil;

public class PatternsUtil {
	public static boolean isValid(String patterns, String service) {
		if (!StringUtil.isEmpty(patterns)) {
			String[] methodPatterns = StringUtil.split(patterns, ',');
			if (methodPatterns.length > 0) {
				String[] serviceDot = StringUtil.split(service.trim(), '.');
				if (serviceDot.length > 0) {
					for (String pattern : methodPatterns) {
						String[] patternDot = StringUtil.split(pattern.trim(), '.');
						for (int i = 0; i < serviceDot.length; i++) {
							if (patternDot.length > i) {
								if (patternDot[i] == "*") {
									return true;
								} else if (serviceDot[i] == patternDot[i]) {
									if (i == serviceDot.length - 1 && i == patternDot.length - 1) {
										return true;
									}
								} else {
									break;
								}
							}else{
								break;
							}
						}
					}
				}
			}
		}
		return false;
	}
}
