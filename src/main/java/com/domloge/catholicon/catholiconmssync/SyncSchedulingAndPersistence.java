package com.domloge.catholicon.catholiconmssync;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.hateoas.CollectionModel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.domloge.catholicon.ms.common.Diff;
import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ScraperException;
import com.domloge.catholicon.ms.common.Sync;

@Component
public class SyncSchedulingAndPersistence {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SyncSchedulingAndPersistence.class);
	
	@Value("${SEASONS_SVC_BASE_URL:http://catholicon-ms-seasons-service:81}")
	private String SEASONS_SVC_BASE_URL;
	
	@Value("${DIVISION_TEAMS_URL:/Division.asp?LeagueTypeID=%d&Division=%d&Season=%d&Juniors=false&Schools=false&Website=1}")
	private String DIVISION_TEAMS_URL;
	
	public static final Pattern teamPatternExp = Pattern.compile("teamList\\[\"([0-9]+)\"\\].*clubName:\"([^\"]+)\"");

	@Autowired
	private FixtureScraper fixtureScraper;
	
	@Autowired
	private FixtureRepository fixtureRepository;
	
	@Autowired
	private Sync<Fixture> fixtureSync;
	
	private RestTemplate seasonsTemplate;
	
	
	
	public SyncSchedulingAndPersistence(@Autowired RestTemplateBuilder builder) {
		builder.setConnectTimeout(Duration.ofSeconds(2));
		builder.setReadTimeout(Duration.ofMillis(100));
		this.seasonsTemplate = builder.build();
	}

	@PostConstruct
	public void init() {
		ExecutorService executorService;
		BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern("catholicon-ms-matchcard-initialiser-thread-%d").build();

		executorService = Executors.newSingleThreadExecutor(factory);
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				syncFixtures();
			}
		});

		executorService.shutdown();
	}
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Scheduled(cron = "0 4 0 0 0 0")
	public void syncFixtures() {
		LOGGER.info("Synching fixtures");
		try {
			// find all divisions
			CollectionModel<LinkedHashMap> seasons = seasonsTemplate.getForObject(
					SEASONS_SVC_BASE_URL+"/seasons?sort=seasonStartYear,desc", 
					CollectionModel.class);
			
			Collection<LinkedHashMap> content = seasons.getContent();
			LOGGER.info("Synching {} seasons", content.size());
			for (LinkedHashMap season : content) {
				LOGGER.info("Synching season {}", season.get("seasonStartYear"));
				ArrayList<LinkedHashMap> leagues = (ArrayList) season.get("leagues");
				for (LinkedHashMap league : leagues) {
					LOGGER.info("Synching league {}", league.get("label"));
					ArrayList<LinkedHashMap> divisions = (ArrayList) league.get("divisions");
					for (LinkedHashMap division : divisions) {
						LOGGER.info("Found division {} of league {} of season {}", division.get("label"), league.get("label"), season.get("seasonStartYear"));
						int divisionId = (int) division.get("divisionId");
						int leagueTypeId = (int) league.get("leagueTypeId");
						int seasonApiIdentifier = (int) season.get("apiIdentifier");
						loadTeamsForDivision(seasonApiIdentifier, leagueTypeId, divisionId);
					}
				}
			}
			LOGGER.info("Sync complete");
		}
		catch(Exception e) {
			Optional<Exception> optE = Optional.ofNullable(e);
			optE.ifPresent(ex -> LOGGER.error("Failed to sync fixtures ("+ex.getClass().getName()+")", ex));
		}
	}

	@Autowired
	private Loader loader;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void loadTeamsForDivision(int seasonApiIdentifier, int leagueTypeId, int divisionId) throws ScraperException {
		// http://bdbl.org.uk/Division.asp?LeagueTypeID=26&Division=59&Season=0&Juniors=false&Schools=false&Website=1
		String url = String.format(DIVISION_TEAMS_URL, 
				leagueTypeId, 
				divisionId, 
				seasonApiIdentifier);

		String page = loader.load(url);
		// teamList["377"] = {teamID:377,clubName:"Andover",clubID:3};
		Matcher m = teamPatternExp.matcher(page);
		while(m.find()) {
			String teamId = m.group(1);
			String teamName = m.group(2);
			LOGGER.info("Synching devision for team {}({}), for season {}", teamName, teamId, seasonApiIdentifier);
			syncDivision(Integer.parseInt(teamId), seasonApiIdentifier);
		}
	}
	
	
	public void syncDivision(int teamId, int season) throws ScraperException {
		LOGGER.debug("Synching fixtures for team {} for season {}", teamId, season);
		
		List<Fixture> masterFixtures = fixtureScraper.load(teamId, season);
		LOGGER.debug("Scrape complete (found {} fixtures)", masterFixtures.size());
		for (Fixture masterFixture : masterFixtures) {
			LOGGER.debug("Master fixture ID {}", masterFixture.getFixtureId());
		}
		
		List<Fixture> dbFixtures = fixtureRepository.findByHomeTeamIdOrAwayTeamIdAndSeason(teamId, teamId, season);
		LOGGER.debug("Loaded DB {} fixtures", dbFixtures.size());
		for (Fixture dbFixture : dbFixtures) {
			LOGGER.debug("DB fixture ID {}", dbFixture.getFixtureId());
		}
		Diff<Fixture> compare = fixtureSync.compare(mapFixtures(masterFixtures), mapFixtures(dbFixtures));
		
		LOGGER.debug("Compare complete");
		
		for (Fixture f : compare.getDelete()) {
			try {
				fixtureRepository.delete(f);
			}
			catch(HibernateException hex) {
				LOGGER.error("failed to delete fixture "+f, hex);
			}
			LOGGER.debug("Delete {}", f);
		}
		
		for (Fixture f : compare.getNewValues()) {
			try {
				fixtureRepository.save(f);
			}
			catch(HibernateException hex) {
				LOGGER.error("failed to create fixture "+f, hex);
			}
			LOGGER.info("Persisted {}", f);
		}
		
		for (Fixture f : compare.getUpdate()) {
			try {
				fixtureRepository.save(f);
			}
			catch(DataIntegrityViolationException divex) {
				LOGGER.error("failed to update fixture "+f, divex);
				for (Fixture fixture : dbFixtures) {
					if(fixture.getFixtureId() == f.getFixtureId()) {
						LOGGER.info("Found matching DB fixture {}", fixture);
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
		Map<Integer, Fixture> map = new HashMap<>();
		for (Fixture fixture : fixtures) {
			map.put(fixture.getFixtureId(), fixture);
		}
		return map;
	}
}
