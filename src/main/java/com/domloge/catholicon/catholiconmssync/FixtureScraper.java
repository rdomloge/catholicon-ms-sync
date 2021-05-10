package com.domloge.catholicon.catholiconmssync;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.domloge.catholiconmsmatchcardlibrary.Fixture;
import com.domloge.catholiconmsmatchcardlibrary.Matchcard;
import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ParserUtil;
import com.domloge.catholicon.ms.common.ScraperException;

@Component
public class FixtureScraper {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FixtureScraper.class);
	
	private static final String url = "/TeamFixtureList.asp?ID=%d&Season=%d&Juniors=false&Schools=false&Website=1";
	
	private static final Pattern dataLineRegExp = Pattern.compile("var data.*divisionID:(\\d+),.*;");
	
	private static final Pattern fixtureLineRegExp = Pattern.compile("fixtureInfo\\['\\d+_\\d+'\\] = (.*);");
	
	
	@Autowired
	private Loader loader;
	
	@Autowired
	private ResultScraper resultScraper;
	
	
	public List<Fixture> load(int teamId, int season) throws ScraperException {
		List<Fixture> list = new LinkedList<Fixture>();
		
		String page = loader.load(String.format(url, teamId, season));
		Matcher dataLineMatcher = dataLineRegExp.matcher(page);
		
		if( ! dataLineMatcher.find()) {
			throw new ScraperException("Could not determine divisionId");
		}
		
		int divisionId = Integer.parseInt(dataLineMatcher.group(1));
		
		Matcher fixtureMatcher = fixtureLineRegExp.matcher(page);
		while(fixtureMatcher.find()) {
			String fixtureJson = fixtureMatcher.group(1);
			Map<String, String> fixtureMap = ParserUtil.convertJsonToMap(fixtureJson);
			int fixtureId = Integer.parseInt(fixtureMap.get("fixtureID"));
			if(0 == fixtureId) {
				LOGGER.warn("Ignoring fixture for season {} for team {} with zero id: {}", season, teamId, fixtureJson);
				continue;
			}
			
			Fixture f = new Fixture(
					fixtureId, 
					convertWebDateValueToSaneValue(fixtureMap.get("matchDate")), 
					Integer.parseInt(fixtureMap.get("homeTeamID")), 
					Integer.parseInt(fixtureMap.get("awayTeamID")), 
					divisionId,
					null,
					season);
			
			Matchcard matchcard = null;
			try {
				matchcard = resultScraper.loadMatchcard(f);
				LOGGER.info("Successfully loaded matchcard for fixture {}", fixtureId);
				f.setMatchCard(matchcard);
			}
			catch(ScraperException sex) {
				LOGGER.debug("Could not load matchcard for fixture {} - presumably not played yet [{}]", 
						fixtureId, sex.getMessage());
			}
			
			LOGGER.debug("Found fixture {}", f.getId());
			list.add(f);
		}
		
		LOGGER.info("Found {} fixtures for season {} for team {}", list.size(), season, teamId);
		return list;
	}

	public static void main(String[] aStrings) {
		String input = "new Date(15 Oct 2019)";
		String converted = convertWebDateValueToSaneValue(input);
		System.out.println(converted);
	}

	private static final Pattern datePattern = Pattern.compile("new Date\\((.*)\\)");
	private static final SimpleDateFormat inputFormat = new SimpleDateFormat("dd MMM yyyy");
	private static final SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy'-'MM'-'dd");
	/*
		new Date(15 Oct 2019)
	*/
	private static final String convertWebDateValueToSaneValue(String input) {
		Matcher matcher = datePattern.matcher(input);
		LOGGER.debug("Parsing '{}' - pattern matches: {}", input, matcher.matches());
		String group =  matcher.group(1);
		try {
			Date date = inputFormat.parse(group);
			LOGGER.debug("Parsed date: {}", date);
			return outputFormat.format(date);
		} 
		catch (ParseException e) {
			LOGGER.error("It seems we couldn't parse input date "+group, e);
			return input;
		}
	}
	
}
