package com.domloge.catholicon.catholiconmssync;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DateStringUtilities {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateStringUtilities.class);

    private static final Pattern datePattern = Pattern.compile("new Date\\((.*)\\)");
    
	private static final SimpleDateFormat inputFormat = new SimpleDateFormat("dd MMM yyyy");

    public ZonedDateTime convertWebDateValueToSaneValue(String input) {
		Matcher matcher = datePattern.matcher(input);
		LOGGER.debug("Parsing '{}' - pattern matches: {}", input, matcher.matches());
		String group =  matcher.group(1);
		try {
			Date date = inputFormat.parse(group);
			LOGGER.debug("Parsed date: {}", date);
			return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("Europe/London"));
		} 
		catch (ParseException e) {			
			throw new RuntimeException("Could not parse >"+input);
		}
	}
    
}
