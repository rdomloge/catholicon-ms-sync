package com.domloge.catholicon.catholiconmssync;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import com.domloge.catholicon.ms.common.Diff;
import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ScraperException;
import com.domloge.catholicon.ms.common.Sync;
import com.domloge.catholiconmsmatchcardlibrary.Fixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SyncSchedulingAndPersistence {

	private static final Logger LOGGER = LoggerFactory.getLogger(SyncSchedulingAndPersistence.class);

	@Value("${SEASONS_SVC_BASE_URL:http://catholicon-ms-seasons-service:81}")
	private String SEASONS_SVC_BASE_URL;

	@Value("${MATCHCARDS_SVC_BASE_URL:http://catholicon-ms-matchcard-service:84}")
	private String MATCHCARD_SVC_BASE_URL;

	@Value("${DIVISION_TEAMS_URL:/Division.asp?LeagueTypeID=%d&Division=%d&Season=%d&Juniors=false&Schools=false&Website=1}")
	private String DIVISION_TEAMS_URL;

	@Value("${FIXTURES_TEMPLATE:/fixtures/search/findByHomeTeamIdOrAwayTeamIdAndSeason?homeTeamId=%d&awayTeamId=%d&season=%d&projection=SuppressedMatchCard}")
	private String FIXTURES_TEMPLATE;

	@Value("${DELETE_FIXTURE_TEMPLATE:/fixtures/%s}")
	private String MODIFY_FIXTURE_TEMPLATE;

	@Value("${CREAT_FIXTURE_TEMPLATE:/fixtures}")
	private URI CREATE_FIXTURE_TEMPLATE;

	public static final Pattern teamPatternExp = Pattern.compile("teamList\\[\"([0-9]+)\"\\].*clubName:\"([^\"]+)\"");

	@Value("${postConstructEnabled:false}")
	private boolean postConstructEnabled;

	@Autowired
	private FixtureScraper fixtureScraper;

	private RestTemplate fixturesTemplate;

	private RestTemplate seasonsTemplate;

	@Autowired
	private Sync<Integer,Fixture> fixtureSync;

	

	public SyncSchedulingAndPersistence(@Autowired RestTemplateBuilder builder) {
		builder.setConnectTimeout(Duration.ofSeconds(2));
		builder.setReadTimeout(Duration.ofMillis(100));
		
		this.seasonsTemplate = builder.build();
		this.fixturesTemplate = builder.build();
		
	}

	// @Scheduled(cron = "0 */10 * * * *")
	@PostConstruct
	public void syncFixtures() {
		if(postConstructEnabled) {
			_synchFixtures();
		}
	}
	
	@Scheduled(fixedDelay = 1000 * 60 * 30) // every 30 mins
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void _synchFixtures() {
		LOGGER.info("Synching fixtures");
		try {
			// find all divisions
			CollectionModel<LinkedHashMap> seasons = seasonsTemplate
					.getForObject(SEASONS_SVC_BASE_URL + "/seasons?sort=seasonStartYear,desc", CollectionModel.class);

			Collection<LinkedHashMap> content = seasons.getContent();
			LOGGER.info("Synching {} seasons", content.size());
			for (LinkedHashMap season : content) {
				LOGGER.info("Synching season {}", season.get("seasonStartYear"));
				ArrayList<LinkedHashMap> leagues = (ArrayList) season.get("leagues");
				for (LinkedHashMap league : leagues) {
					LOGGER.info("Synching league {}", league.get("label"));
					ArrayList<LinkedHashMap> divisions = (ArrayList) league.get("divisions");
					for (LinkedHashMap division : divisions) {
						LOGGER.info("Found division {} of league {} of season {}", division.get("label"),
								league.get("label"), season.get("seasonStartYear"));
						int divisionId = (int) division.get("divisionId");
						int leagueTypeId = (int) league.get("leagueTypeId");
						int seasonApiIdentifier = (int) season.get("apiIdentifier");
						loadTeamsForDivision(seasonApiIdentifier, leagueTypeId, divisionId);
					}
				}
			}
			LOGGER.info("Sync complete");
		} catch (Exception e) {
			Optional<Exception> optE = Optional.ofNullable(e);
			optE.ifPresent(ex -> LOGGER.error("Failed to sync fixtures (" + ex.getClass().getName() + ")", ex));
		}
	}

	@Autowired
	private Loader loader;

	private void loadTeamsForDivision(int seasonApiIdentifier, int leagueTypeId, int divisionId)
			throws ScraperException {
		// http://bdbl.org.uk/Division.asp?LeagueTypeID=26&Division=59&Season=0&Juniors=false&Schools=false&Website=1
		String url = String.format(DIVISION_TEAMS_URL, leagueTypeId, divisionId, seasonApiIdentifier);

		String page = loader.load(url);
		// teamList["377"] = {teamID:377,clubName:"Andover",clubID:3};
		Matcher m = teamPatternExp.matcher(page);
		while (m.find()) {
			String teamId = m.group(1);
			String teamName = m.group(2);
			LOGGER.info("Synching devision for team {}({}), for season {}", teamName, teamId, seasonApiIdentifier);
			syncDivision(Integer.parseInt(teamId), seasonApiIdentifier);
		}
	}

	public void syncDivision(int teamId, int season) throws ScraperException {
		LOGGER.debug("Synching fixtures for team {} for season {}", teamId, season);
		
		List<Fixture> masterFixtures = fixtureScraper.load(teamId, season);
		LOGGER.debug("Scrape complete (found {} *master* fixtures)", masterFixtures.size());
		
		ParameterizedTypeReference<CollectionModel<EntityModel<Fixture>>> param = 
			new ParameterizedTypeReference<CollectionModel<EntityModel<Fixture>>>(){ };
		
		String url = String.format(MATCHCARD_SVC_BASE_URL + FIXTURES_TEMPLATE, teamId, teamId, season);
		LOGGER.debug("Calling {}", url);

		Collection<EntityModel<Fixture>> dbFixtures = fixturesTemplate.exchange(
				url, HttpMethod.GET, null, param)
			.getBody().getContent();

		LOGGER.debug("Loaded {} *database* fixtures", dbFixtures.size());
		
		Diff<Fixture> compare = fixtureSync.compare(mapFixtures(masterFixtures), mapFixturesEm(dbFixtures));
		
		LOGGER.debug("Compare complete");
		
		for (Fixture f : compare.getDelete()) {
			String uri = String.format(MATCHCARD_SVC_BASE_URL + MODIFY_FIXTURE_TEMPLATE, f.getExternalFixtureId());
			fixturesTemplate.delete(uri);
			LOGGER.debug("Delete {}", f);
		}
		
		for (Fixture f : compare.getNewValues()) {
			ResponseEntity<Fixture> response = fixturesTemplate.postForEntity(MATCHCARD_SVC_BASE_URL + CREATE_FIXTURE_TEMPLATE, f, Fixture.class);
			LOGGER.info("Persisted new fixture {}", response.getBody());
		}
		
		for (Fixture f : compare.getUpdate()) {
			try {
				String fixtureUri = String.format(MATCHCARD_SVC_BASE_URL + MODIFY_FIXTURE_TEMPLATE, f.getExternalFixtureId());
				LOGGER.debug("Patching fixture at {} with {}",  fixtureUri, f);
				fixturesTemplate.patchForObject(fixtureUri, f, Fixture.class);				
			}
			catch(DataIntegrityViolationException divex) {
				LOGGER.error("failed to update fixture "+f, divex);
				for (EntityModel<Fixture> entityModel : dbFixtures) {
					if(entityModel.getContent().getExternalFixtureId() == f.getExternalFixtureId()) {
						LOGGER.info("Found matching DB fixture {}", entityModel.getContent());
						LOGGER.info("And here's the master fixture again {}", f);
					}
				}
				throw new ScraperException("GOTO");
			}
			LOGGER.debug("Update {}", f);
		}
		
		LOGGER.info("Synch complete");
		
	}
	
	private Map<Integer, Fixture> mapFixtures(List<Fixture> fixtures) {
		return mapFixtures(fixtures.toArray(new Fixture[0]));
	}

	private Map<Integer, Fixture> mapFixturesEm(Collection<EntityModel<Fixture>> fixtures) {
		
		Fixture[] arr = fixtures.stream().map(x -> x.getContent()).toArray(size -> new Fixture[size]);
		return mapFixtures(arr);
	}
	
	private Map<Integer, Fixture> mapFixtures(Fixture[] fixtures) {
		Map<Integer, Fixture> map = new HashMap<>();
		for (Fixture fixture : fixtures) {
				map.put(fixture.getExternalFixtureId(), fixture);
		}
		return map;
	}
}
