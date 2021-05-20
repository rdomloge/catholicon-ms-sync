package com.domloge.catholicon.catholiconmssync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ScraperException;
import com.domloge.catholiconmsclublibrary.Club;
import com.domloge.catholiconmsclublibrary.PhoneNumber;
import com.domloge.catholiconmsclublibrary.PhoneNumberType;
import com.domloge.catholiconmsclublibrary.Session;
import com.domloge.catholiconmsclublibrary.Team;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClubScraper {

	private static final Logger LOGGER = LoggerFactory.getLogger(ClubScraper.class);

	@Value("${CLUBS_SVC_BASE_URL:http://10.0.0.15:85/clubs}")
	private String CLUBS_SVC_BASE_URL;
	
	private static String seedUrl = "/ClubInfo.asp?Season=%1$s&website=1";
	private static String clubUrl = "/ClubInfo.asp?Club=%1$s&Season=%2$s&Juniors=false&Schools=false&Website=1";
	// private static String teamListUrl = "/Division.asp?LeagueTypeID=%1$s&Division=%2$s&Season=%3$s&Juniors=false&Schools=false&Website=1";
	private static final String teamsForLeagueUrl = "/LeagueRegistration.asp?Season=%1$s&website=1";
	private static String teamDetailsUrl = "/TeamStats.asp?TeamID=%1$s&Season=%2$s&Juniors=false&Schools=false&Website=1";

	private static final Pattern clubForLeaguePattern = Pattern.compile("var sortedClubList =\\s+\\[((?s).)*\\];\\s+var ");
	private static final Pattern clubPattern = 
		Pattern.compile("\\{clubID:(\\d+),clubInfoList.*shortName:\"(.+)\",longName:\"(.+)\",.*,sortedTeams:\\[(.*)\\].*\\}");
	private static final Pattern teamsPattern = Pattern.compile("\\{teamID:(\\d+),leagueTypeID:(\\d+),divisionID:(\\d+),name:\"([\\w  ]+)\",websiteID:\\d\\}");

	// private static final Pattern teamClubMapping = Pattern.compile("teamList\\[\"(\\d+)\"\\] = \\{teamID:\\d+,clubName:.*,clubID:(\\d+)\\};");
	private static final Pattern teamNamePattern = Pattern.compile("teamName : \"(.*)\"");
	
	@Autowired
	private Loader loader;

	private Map<Integer, List<Team>> teamCache = new HashMap<>();

	public void getTeamsForClub(Club c) throws ScraperException {
		String page = loader.load(String.format(teamsForLeagueUrl, c.getSeasonId()));
		Matcher allClubsMatcher = clubForLeaguePattern.matcher(page);
		if( ! allClubsMatcher.find()) throw new ScraperException("Could not find clubs section");
		Matcher clubMatcher = clubPattern.matcher(allClubsMatcher.group());
		while(clubMatcher.find()) {
			int clubId = Integer.parseInt(clubMatcher.group(1));
			if(clubId != c.getClubId()) continue;
			List<Team> teamList = new ArrayList<>();
			String shortName = clubMatcher.group(2);
			String longName = clubMatcher.group(3);
			String teams = clubMatcher.group(4);
			Matcher teamsMatcher = teamsPattern.matcher(teams);
			while(teamsMatcher.find()) {
				String teamIdStr = teamsMatcher.group(1);
				String leagueTypeId = teamsMatcher.group(2);
				String divisionId = teamsMatcher.group(3);
				String teamName = teamsMatcher.group(4);
				int teamId = Integer.parseInt(teamIdStr);
				String fullTeamName = shortName + " " + teamName;
				Team t = new Team(teamId, fullTeamName);
				teamList.add(t);
			}
			c.setTeams(teamList);
			return;
		}
		
	}

	// public void getTeams(int leagueId, int divisionId, int season, TeamCallback callback) throws ScraperException {

	// 	String page = loader.load(String.format(teamListUrl, leagueId, divisionId, season));
	// 	Matcher matcher = teamClubMapping.matcher(page);
	// 	while(matcher.find()) {
	// 		String teamIdStr = matcher.group(1);
	// 		String clubIdStr = matcher.group(2);
	// 		LOGGER.info("Club {} has team {}", clubIdStr, teamIdStr);
	// 		Team team = getTeamDetails(Integer.parseInt(teamIdStr), season);
	// 		callback.foundTeam(team, Integer.parseInt(clubIdStr), season);
	// 	}
	// 	// var teamList = {};
	// 	// teamList["377"] = {teamID:377,clubName:"Andover",clubID:3};
	// 	// teamList["395"] = {teamID:395,clubName:"Andover",clubID:3};
	// 	// teamList["374"] = {teamID:374,clubName:"Beechdown",clubID:6};
	// 	// teamList["380"] = {teamID:380,clubName:"Beechdown",clubID:6};
	// 	// teamList["379"] = {teamID:379,clubName:"Phoenix",clubID:11};
	// 	// teamList["375"] = {teamID:375,clubName:"Viking",clubID:13};
	// 	// teamList["376"] = {teamID:376,clubName:"Waverley",clubID:14};
	// }

	private Team getTeamDetails(int teamId, int season) throws ScraperException {
		String page = loader.load(String.format(teamDetailsUrl, teamId, season));
		Matcher matcher = teamNamePattern.matcher(page);
		if( ! matcher.find()) throw new ScraperException("Stats not found for team "+teamId+" in season "+season);
		String teamName = matcher.group(1);
		return new Team(teamId, teamName);
		// $scope.stats = 
		// {
		// 	teamName : "Viking Ladies",
		// 	lastModified : "19 Sep 2017",
		// 	divisionName : "Division 1",
		// 	penaltyPoints : 0,
		// 	totalMatchPenalties : 0,
		// 	matchesPWDL : "10 / 9 / 0 / 1",
		// 	rubbersPWDL : "42 / 0 / 18",
		// 	gamesWL : "91 / 42",
		// 	gamePointsWL : "2543 / 2025"
		// };
	}
	
	
	public List<Club> getClubIds(int seasonId) throws ScraperException {
		String seedPage = loader.load(String.format(seedUrl, seasonId));
		Document doc = Jsoup.parse(seedPage);
		List<Club> clubList = new LinkedList<>();
		
		Elements clubs = doc.select("select[id$=ClubList] option");
		if(clubs.size() < 1) throw new ScraperException("Could not find any clubs");
		
		for(int i=0; i < clubs.size(); i++) {
			Element clubEl = clubs.get(i);
			int clubId = Integer.parseInt(clubEl.attr("value"));
			String clubName = clubEl.ownText().replaceAll("Badminton Club", "");
			// Club club = new Club(clubId, clubName, seasonId);
			Club club = new Club();
			club.setClubId(clubId);
			club.setClubName(clubName);
			club.setSeasonId(seasonId);
			clubList.add(club);
		}
		return clubList;
	}
	
	public Club getClub(int seasonId, int clubId) throws ScraperException {
		Club club = new Club();
		club.setClubId(clubId);
		club.setSeasonId(seasonId);
		fillOutClub(club);
		getTeamsForClub(club);
		return club;
	}
	
	private static String getClubName(Document doc) {
		// var clubName = "Aldermaston Badminton Club";
		String script = doc.getElementsByTag("script").get(6).html();
		int firstQuote = script.indexOf('"')+1;
		return script.substring(firstQuote, script.indexOf('"', firstQuote)).replaceAll("Badminton Club", "");		
	}
	
	private void fillOutClub(Club club, Document doc) throws ScraperException {
		club.setClubName(getClubName(doc));
		String chairMan = parseRole(doc, "#ChairmanID + span");
		String secretary = parseRole(doc, "#SecretaryID + span");
		String matchSec = parseRole(doc, "#MatchSecID + span");
		String treasurer = parseRole(doc, "#TreasurerID + span");
		club.fillOutRoles(chairMan, secretary, matchSec, treasurer);
		
		fillOutPhoneNumbers(doc, club);
		fillOutEmailAddrs(doc, club);
		fillOutClubSessions(doc, club);
		fillOutMatchSessions(doc, club);
	}
	
	private static String parseRole(Document doc, String selector) throws ScraperException {
		Elements elements = doc.select(selector);
		if(elements.size() != 1) throw new ScraperException("Could not find role element");
		return elements.first().ownText();
	}
	
	private void fillOutPhoneNumbers(Document doc, Club club) throws ScraperException {
		// Elements phoneNumbers = 
		// 		doc.select("#clubForm center i table[cellpadding=3] tbody tr:nth-child(3) td");
		Elements phoneNumbers = 
				doc.select("table").get(1).select("tr").get(2).select("td");
		PhoneNumber[] chairmanPhoneNumbers = parsePhoneNumbers(phoneNumbers.get(0).ownText().trim());
		PhoneNumber[] secretaryPhoneNumbers = parsePhoneNumbers(phoneNumbers.get(1).ownText().trim());
		PhoneNumber[] matchSecPhoneNumbers = parsePhoneNumbers(phoneNumbers.get(2).ownText().trim());
		PhoneNumber[] treasurerPhoneNumbers = parsePhoneNumbers(phoneNumbers.get(3).ownText().trim());
		club.fillOutPhoneNumbers(chairmanPhoneNumbers, secretaryPhoneNumbers, matchSecPhoneNumbers, treasurerPhoneNumbers);
	}
	
	private PhoneNumber[] parsePhoneNumbers(String s) {
		List<PhoneNumber> numbers = new LinkedList<>();
		String regex = "([ 0-9]+).*\\(([MH])\\)";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(s);
		if(m.find()) {
			String number = m.group(1);
			String type = m.group(2);
			numbers.add(new PhoneNumber(PhoneNumberType.forIdentifier(type), number));
		}
		
		return numbers.toArray(new PhoneNumber[numbers.size()]);
	}
	
	private void fillOutEmailAddrs(Document doc, Club club) throws ScraperException {
		Map<String, String> parsedEmails = EmailParser.parseEmails(doc.outerHtml());
		/**
		 * This doesn't work because the email address is generated
		 */
		Elements emails = 
				doc.select("#ChairmanID").first().parent().parent().parent().select("tr").get(3).select("td");
		
		String chairmanJs = emails.get(0).select("a[onclick]").attr("onclick");
		String chairmanIdentifier = EmailParser.parseIdentifier(chairmanJs);
		String chairmanEmail = parsedEmails.get(chairmanIdentifier);
		
		String secretaryJs = emails.get(1).select("a[onclick]").attr("onclick");
		String secretaryIdentifier = EmailParser.parseIdentifier(secretaryJs);
		String secretaryEmail = parsedEmails.get(secretaryIdentifier);
		
		String matchSecJs = emails.get(2).select("a[onclick]").attr("onclick");
		String matchSecIdentifier = EmailParser.parseIdentifier(matchSecJs);
		String matchSecEmail = parsedEmails.get(matchSecIdentifier);
		
		String treasurerJs = emails.get(3).select("a[onclick]").attr("onclick");
		String treasurerIdentifier = EmailParser.parseIdentifier(treasurerJs);
		String treasurerEmail = parsedEmails.get(treasurerIdentifier);
		
		club.fillOutEmailAddresses(chairmanEmail, secretaryEmail, matchSecEmail, treasurerEmail);
	}
	
	private void fillOutMatchSessions(Document doc, Club club) {
		Elements rows = 
				doc.select("h4:contains(MATCH SESSIONS) + table tr");
		
		club.setMatchSessions(parseSessions(rows));
	}
	
	private List<Session> parseSessions(Elements rows) {
		ArrayList<Session> sessions = new ArrayList<>();
		
		String locationName = null;
		String locationAddr = null;
		String days = null;
		String numCourts = null;
		String start = null;
		String end = null;
		
		for(int i=0; i < rows.size(); i++) {
			Element row = rows.get(i);
			if(row.select("tr td.TableColHeading").size() > 0) continue; // heading row
			
			if(row.select("td[rowspan]").size() > 0) {				// venue row
				locationName = row.select("td[rowspan] b").text();
				locationAddr = row.select("td[rowspan]").text();				
			}
			
			if(row.select("td[align]").size() > 0) {				// session details row
				if(row.select("td:containsOwn(As Above)").size() > 0) {
					locationName = sessions.get(sessions.size()-1).getLocationName();
					locationAddr = sessions.get(sessions.size()-1).getLocationAddr();
				}
				Elements sessionCells = row.select("td[align] + td");
				days = sessionCells.get(0).childNode(0).toString();
				numCourts = sessionCells.get(0).childNode(2).toString();
				start = sessionCells.get(1).childNode(0).toString();
				end = sessionCells.get(1).childNode(2).toString();
				
				sessions.add(new Session(locationName, locationAddr, days, numCourts, start, end));
				locationName = null;
				locationAddr = null;
				days = null;
				numCourts = null;
				start = null;
				end = null;
			}
		}
		
		return sessions;
	}
	
	private void fillOutClubSessions(Document doc, Club club) {
		Elements rows = 
				doc.select("h4:contains(CLUB SESSIONS) + table tr");
		
		club.setClubSessions(parseSessions(rows));
	}
	
	private void fillOutClub(Club club) throws ScraperException {
		String clubPage = loader.load(String.format(clubUrl, club.getClubId(), club.getSeasonId()));
		Map<String, String> emails = EmailParser.parseEmails(clubPage);
		fillOutClub(club, Jsoup.parse(clubPage));
	}
}
