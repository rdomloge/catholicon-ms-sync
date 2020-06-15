package com.domloge.catholicon.catholiconmssync;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
			Matchcard matchcard = null;
			try {
				matchcard = resultScraper.loadMatchcard(fixtureId);
			}
			catch(ScraperException sex) {
				LOGGER.debug("Could not load matchcard for fixture {} - presumably not played yet [{}]", 
						fixtureId, sex.getMessage());
			}
			
			Fixture f = new Fixture(
					fixtureId, 
					fixtureMap.get("matchDate"), 
					Integer.parseInt(fixtureMap.get("homeTeamID")), 
					Integer.parseInt(fixtureMap.get("awayTeamID")), 
					divisionId,
					matchcard,
					season);
			LOGGER.debug("Found fixture {}", f.getFixtureId());
			list.add(f);
		}
		
		LOGGER.info("Found {} fixtures for season {} for team {}", list.size(), season, teamId);
		return list;
	}
	
}
