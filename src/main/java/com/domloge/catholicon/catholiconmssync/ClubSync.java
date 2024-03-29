package com.domloge.catholicon.catholiconmssync;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.domloge.catholicon.ms.common.Diff;
import com.domloge.catholicon.ms.common.ScraperException;
import com.domloge.catholicon.ms.common.Sync;
import com.domloge.catholicon.ms.common.Tuple;
import com.domloge.catholiconmsclublibrary.Club;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.hateoas.CollectionModel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ClubSync {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClubSync.class);

    @Value("${CLUBS_SVC_BASE_URL:http://catholicon-ms-club-service:85/clubs}")
	private String CLUBS_SVC_BASE_URL;

    @Value("${SEASONS_SVC_BASE_URL:http://catholicon-ms-seasons-service:81}")
	private String SEASONS_SVC_BASE_URL;

	@Value("${CLUB_FIND_BY_ID_SEASON_URL:/search/findClubByClubIdAndSeasonId?clubId={clubId}&season={season}}")
	private String CLUB_FIND_BY_ID_SEASON_URL;

    @Autowired
	private ClubScraper clubScraper;

    private RestTemplate clubTemplate;

    private RestTemplate seasonsTemplate;

    @Value("${postConstructEnabled:false}")
	private boolean postConstructEnabled;

    @Autowired
	private Sync<String,Club> clubSync;


    
    
    public ClubSync(@Autowired RestTemplateBuilder builder) {
        this.clubTemplate = builder.build();
        this.seasonsTemplate = builder.build();
    }

	@PostConstruct
	public void syncClubs() throws ScraperException {
		if(postConstructEnabled) {
			_synchClubs();
		}
	}
	
	@Scheduled(cron = "0 0 */12 * * *")
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void _synchClubs() throws ScraperException {
		LOGGER.info("Synching clubs");
		Club[] dbClubs = clubTemplate.getForObject(CLUBS_SVC_BASE_URL, Club[].class);
		LOGGER.info("Loaded {} clubs from service", dbClubs.length);

		CollectionModel<LinkedHashMap> seasons = seasonsTemplate
					.getForObject(SEASONS_SVC_BASE_URL + "/seasons?sort=seasonStartYear,desc", CollectionModel.class);

		Collection<LinkedHashMap> content = seasons.getContent();

		List<Club> masterClubs = new LinkedList<>();

		for (LinkedHashMap season : content) {
			int seasonStartYear = Integer.parseInt(""+season.get("seasonStartYear"));
			int seasonApiIdentifier = Integer.parseInt(""+season.get("apiIdentifier"));
			LOGGER.debug("Synching clubs for season {}", seasonStartYear);
			List<Club> clubIds = clubScraper.getClubIds(seasonApiIdentifier);
			LOGGER.info("Found {} clubs for season {}", clubIds.size(), seasonStartYear);
			for (Club club : clubIds) {
				LOGGER.debug("Fetching master data for club {} which is '{}'", club.getClubId(), club.getClubName());
				Club masterClub = clubScraper.getClub(seasonApiIdentifier, club.getClubId());
				masterClubs.add(masterClub);
			}
		}

		Diff<Club> diff = clubSync.compare(mapClubsToId(masterClubs), mapClubsToId(Arrays.asList(dbClubs)));

		LOGGER.info("Creating {} new clubs", diff.getNewValues().size());
		for (Club club : diff.getNewValues()) {
			clubTemplate.postForObject(CLUBS_SVC_BASE_URL, club, Club.class);
		}

		LOGGER.info("Updating {} clubs", diff.getUpdate().size());
		for (Tuple<Club, Club> club : diff.getUpdate()) {
			BeanUtils.copyProperties(club.master, club.db, "id, externalId");
			clubTemplate.patchForObject(CLUBS_SVC_BASE_URL, club.db, Club.class);
		}

		LOGGER.info("Deleting {} clubs", diff.getDelete().size());
		for (Club club : diff.getDelete()) {
			clubTemplate.delete(CLUBS_SVC_BASE_URL+"/?clubId={clubId}&season={season}", club.getClubId(), club.getSeasonId());
		}

        // syncTeamsForClubs();
		LOGGER.info("Club sync complete");
	}

    private Map<String, Club> mapClubsToId(List<Club> clubs) {
		Map<String, Club> map = new HashMap<>();
		for (Club club : clubs) {
			map.put("S"+club.getSeasonId()+"C"+club.getClubId(), club);
		}
		return map;
	}
}
