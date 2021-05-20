package com.domloge.catholicon.catholiconmssync;

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
import com.domloge.catholiconmsclublibrary.Club;
import com.domloge.catholiconmsclublibrary.Team;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Value("${SEASONS_SVC_BASE_URL:http://catholicon-ms-season-service:81}")
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

    @Scheduled(cron = "0 0 */12 * * *")
	@PostConstruct
	public void syncClubs() throws ScraperException {
		if(postConstructEnabled) {
			_synchClubs();
		}
	}
	
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
			LOGGER.info("Synching clubs for season {}", seasonStartYear);
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
		for (Club club : diff.getUpdate()) {
			clubTemplate.patchForObject(CLUBS_SVC_BASE_URL, club, Club.class);
		}

		LOGGER.info("Deleting {} clubs", diff.getDelete().size());
		for (Club club : diff.getDelete()) {
			clubTemplate.delete(CLUBS_SVC_BASE_URL+"/?clubId={clubId}&season={season}", club.getClubId(), club.getSeasonId());
		}

        // syncTeamsForClubs();
		LOGGER.info("Club sync complete");
	}

	private void findTeamsForClub(Club c) {
		// http://bdbl.org.uk/LeagueRegistration.asp?Season=2018&website=1
		
	}

    // @SuppressWarnings({ "unchecked", "rawtypes" })
    // private void syncTeamsForClubs() throws ScraperException {
    //     CollectionModel<LinkedHashMap> seasons = seasonsTemplate
	// 				.getForObject(SEASONS_SVC_BASE_URL + "/seasons?sort=seasonStartYear,desc", CollectionModel.class);

    //     for (LinkedHashMap season : seasons) {
	// 		int seasonApiIdentifier = Integer.parseInt(""+season.get("apiIdentifier"));
    //         List<LinkedHashMap> leagues = (List<LinkedHashMap>) season.get("leagues");
    //         for (LinkedHashMap league : leagues) {
    //             int leagueId = Integer.parseInt(""+league.get("leagueTypeId"));
    //             List<LinkedHashMap> divisions = (List<LinkedHashMap>) league.get("divisions");
    //             for (LinkedHashMap division : divisions) {
    //                 int divisionId = Integer.parseInt(""+division.get("divisionId"));
    //                 clubScraper.getTeams(leagueId, divisionId, seasonApiIdentifier, new TeamCallback(){
	// 					@Override
	// 					public void foundTeam(Team t, int clubId, int season) {
	// 						Club club = 
	// 							clubTemplate.getForObject(CLUBS_SVC_BASE_URL+CLUB_FIND_BY_ID_SEASON_URL, Club.class, clubId, season);
	// 						LOGGER.info("Linking team {}({}) with club {}({})", t.getTeamName(), t.getTeamId(), club.getClubName(), clubId);
	// 						club.linkTeam(t);
	// 						clubTemplate.patchForObject(CLUBS_SVC_BASE_URL, club, Club.class);
	// 					}
	// 				});
    //             }
    //         }
    //     }
    // }

    private Map<String, Club> mapClubsToId(List<Club> clubs) {
		Map<String, Club> map = new HashMap<>();
		for (Club club : clubs) {
			map.put("S"+club.getSeasonId()+"C"+club.getClubId(), club);
		}
		return map;
	}
}
