package com.domloge.catholicon.catholiconmssync;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ParserUtil;
import com.domloge.catholicon.ms.common.ScraperException;
import com.domloge.catholiconmsclublibrary.Club;
import com.domloge.catholiconmsclublibrary.Team;
import com.domloge.catholiconmsmatchcardlibrary.Fixture;
import com.domloge.catholiconmsmatchcardlibrary.Matchcard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FixtureScraper {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FixtureScraper.class);
	
	private static final String url = "/TeamFixtureList.asp?ID=%d&Season=%d&Juniors=false&Schools=false&Website=1";
	
	private static final Pattern dataLineRegExp = Pattern.compile("var data.*divisionID:(\\d+),.*;");
	
	private static final Pattern fixtureLineRegExp = Pattern.compile("fixtureInfo\\['\\d+_\\d+'\\] = (.*);");

	@Value("${CLUBS_SVC_BASE_URL:http://catholicon-ms-club-service:85/clubs}")
	private String CLUBS_SVC_BASE_URL;	
	
	@Autowired
	private Loader loader;
	
	@Autowired
	private ResultScraper resultScraper;

	private RestTemplate clubTemplate;

	@Autowired
	private DateStringUtilities dateStringUtilities;

	
	
	public FixtureScraper(@Autowired RestTemplateBuilder builder) {
		builder.setConnectTimeout(Duration.ofSeconds(2));
		builder.setReadTimeout(Duration.ofMillis(100));
		
		this.clubTemplate = builder.build();
	}

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
			
			int homeTeamId = Integer.parseInt(fixtureMap.get("homeTeamID"));
			int awayTeamId = Integer.parseInt(fixtureMap.get("awayTeamID"));

			Club homeClub = clubTemplate.getForObject(CLUBS_SVC_BASE_URL+"/search/findClubByTeamId?teamId={homeTeamId}", Club.class, homeTeamId);
			Team homeTeam = homeClub.getTeams().stream().filter(team -> homeTeamId == team.getTeamId()).findFirst().get();

			Club awayClub = clubTemplate.getForObject(CLUBS_SVC_BASE_URL+"/search/findClubByTeamId?teamId={awayTeamId}", Club.class, awayTeamId);
			Team awayTeam = awayClub.getTeams().stream().filter(team -> awayTeamId == team.getTeamId()).findFirst().get();

			Fixture f = new Fixture(
					fixtureId, 
					divisionId,
					dateStringUtilities.convertWebDateValueToSaneValue(fixtureMap.get("matchDate")), 
					homeTeamId, 
					awayTeamId,
					homeTeam.getTeamName(),
					awayTeam.getTeamName(),
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
			
			LOGGER.debug("Found fixture {}", f.getExternalFixtureId());
			list.add(f);
		}
		
		LOGGER.info("Found {} fixtures for season {} for team {}", list.size(), season, teamId);
		return list;
	}
}
