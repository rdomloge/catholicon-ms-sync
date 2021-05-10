package com.domloge.catholicon.catholiconmssync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.domloge.catholicon.ms.common.ScraperException;

public class EmailParser {

//	var em = {};var ISP = {};em['AndreaJefferies'] = 'andreaj';ISP['AndreaJefferies'] = 'urbis-schreder.com';em['KarenHume'] = 'karen.hume30';ISP['KarenHume'] = 'gmail.com';em['CathrynPerkins'] = 'bdblsecretary';ISP['CathrynPerkins'] = 'gmail.com';em['PeterBevell'] = 'peter.bevell';ISP['PeterBevell'] = 'gmail.com';em['HoraceMitchell'] = 'horacemitchell';ISP['HoraceMitchell'] = 'virginmedia.com';em['PhilipOxford'] = 'coachbjbc';ISP['PhilipOxford'] = 'gmail.com';em['StephenLambe'] = 'stephenlambe';ISP['StephenLambe'] = 'aol.com';em['MartinDavey'] = 'martinjohndavey';ISP['MartinDavey'] = 'gmail.com';em['PeterAskew'] = 'theboyski';ISP['PeterAskew'] = 'talktalk.net';
//	andreaj@urbis-schreder.com
	
	private static final Pattern linePattern = Pattern.compile("(?s)var em =\\s+\\{\\}.*;");
	private static final Pattern emailPattern = Pattern.compile("em\\['([^\\;']*)'\\] = '([^\\;']*)';");
	private static final Pattern servicePattern = Pattern.compile("ISP\\['([^\\;']*)'\\] = '([^\\;']*)';");
	
	public static Map<String,String> parseEmails(String page) throws ScraperException {
		
		Map<String, String> emailMap = new HashMap<>();
		Set<String> identifiers = new HashSet<>();
		Map<String, String> accountMap = new HashMap<>();
		Map<String, String> serviceMap = new HashMap<>();
		
		Matcher lineMatcher = linePattern.matcher(page);
		
		if(lineMatcher.find()) {
			String line = lineMatcher.group(0);
			Matcher emailMatcher = emailPattern.matcher(line);
			while(emailMatcher.find()) {
				String identifier = emailMatcher.group(1);
				String account = emailMatcher.group(2);
				accountMap.put(identifier, account);
				identifiers.add(identifier);
			}
			
			Matcher serviceMatcher = servicePattern.matcher(line);
			while(serviceMatcher.find()) {
				String identifier = serviceMatcher.group(1);
				String service = serviceMatcher.group(2);
				serviceMap.put(identifier, service);
				identifiers.add(identifier);
			}
		}
		else {
			throw new ScraperException("Could not locate email line in page");
		}
		
		for (String identifier : identifiers) {
			String email = accountMap.get(identifier) + '@' + serviceMap.get(identifier);
			emailMap.put(identifier, email);
		}
		
		return emailMap;
	}
	
	//GenerateMailHref(this, 'StephenGaunt');
	
	private static final Pattern p = Pattern.compile("GenerateMailHref\\(this, '([^']*)'\\);");
	
	public static String parseIdentifier(String s) {
		Matcher m = p.matcher(s);
		if(m.find()) {
			return m.group(1);
		}
		else {
			return null;
		}
	}
}
