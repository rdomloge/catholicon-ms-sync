package com.domloge.catholicon.catholiconmssync;

import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.domloge.catholiconmsmatchcardlibrary.Fixture;
import com.domloge.catholiconmsmatchcardlibrary.Matchcard;
import com.domloge.catholiconmsmatchcardlibrary.MatchcardStatus;
import com.domloge.catholiconmsmatchcardlibrary.Rubber;
import com.domloge.catholicon.ms.common.Loader;
import com.domloge.catholicon.ms.common.ScraperException;

@Component
public class ResultScraper {
	
	private static Logger LOGGER = LoggerFactory.getLogger(ResultScraper.class);

	private static final String initialUrlTemplate = 
			"/MatchCard.asp?FixtureID=%1$s&Juniors=false&Schools=false&Season=0&Website=1";
	
	private static final String NAME_WITHHELD = "Name withheld";

	@Autowired
	private Loader loader;
	
	
	public Matchcard loadMatchcard(Fixture f) throws ScraperException {
		
		String initialUrl = String.format(String.format(initialUrlTemplate, f.getExternalFixtureId()), f.getExternalFixtureId());
		
		String newUrl = loader.loadRedirect(initialUrl);
		String page = loader.load(newUrl);
		
		Document doc = Jsoup.parse(page);
		Elements scores = doc.select("span.Boxed[id^=Score]");
		if(scores.size() == 0) {
			throw new ScraperException("Could not load matchcard for fixture "+f.getExternalFixtureId()+" - no scores data in page "+newUrl);
		}
		Map<Integer, Rubber> scoreMap = new HashMap<>();
		
		for(int i=0; i < scores.size(); i++) {
			Element scoreEl = scores.get(i);
			String id = scoreEl.attr("id").substring(5);
			String score = scoreEl.text();
			boolean homeScore = id.startsWith("H");
			int gameNum = Integer.parseInt(id.substring(id.indexOf("_")+1));
			int rubberNum = Integer.parseInt(id.substring(1, id.indexOf("_")));
			if(! scoreMap.containsKey(rubberNum)) {
				scoreMap.put(rubberNum, new Rubber());
			}
			
			Rubber r = scoreMap.get(rubberNum);
			r.setGameProgrammatically(gameNum, homeScore, score);
			
		}
		
		String[] awayPlayers = preFillNamesWithWitheld(new String[6]);
		String[] homePlayers = preFillNamesWithWitheld(new String[6]);
		Elements players = doc.select("span.Boxed[id*=Player]");
		for(int i=0; i < players.size(); i++) {
			Element player = players.get(i);
			String id = player.attr("id");
			String playerType = id.substring(0, id.indexOf("Player"));
			String playerNum = id.substring(id.indexOf("Player")+6);
			if("home".equalsIgnoreCase(playerType)) {
				homePlayers[Integer.parseInt(playerNum)] = player.text();
			}
			else {
				awayPlayers[Integer.parseInt(playerNum)] = player.text();
			}
		}
		
		String homeTeam = doc.select("#homeTeam").first().attr("value");
		String awayTeam = doc.select("#awayTeam").first().attr("value");
		
		String matchDate = doc.select("#matchDate").first().attr("value");
		String score = doc.select("#ScoreBoard").first().text();
		int hyphen = score.indexOf("-");
		/*
		 * NB Can't use trim() here because the whitespace characters are not the usual characters
		 * that trim() is removing and it leaves them in, causing a NumberFormatException.
		 * NB2 This is only a problem for the Ladies4 pages!
		 */
		int homeScore = Integer.parseInt(score.substring(0, hyphen).replaceAll("[^\\d.]", ""));  
		int awayScore = Integer.parseInt(score.substring(hyphen+1).replaceAll("[^\\d.]", ""));
		
		Elements results = doc.select("input[id^=Result]");
		Boolean[] homeTeamWins = new Boolean[results.size()]; // tempting to make this fixed to len 9, but ladies matches only have 6
		for(int i=0; i < results.size(); i++) {
			String result = results.get(i).attr("value");
			String rubberNumStr = results.get(i).attr("id");
			int rubberNum = Integer.parseInt(rubberNumStr.substring(rubberNumStr.indexOf("Result")+6)) - 1;
			homeTeamWins[rubberNum] = result.toLowerCase().contains("home") ? true : false;
		}

		Elements cardStatus = doc.select("input[id^=CardStatus]");
		int cardStatusInt = Integer.parseInt(cardStatus.attr("value"));
		MatchcardStatus status = MatchcardStatus.convert(cardStatusInt);
		
		Matchcard m =  new Matchcard(scoreMap, homePlayers, awayPlayers, homeTeam, awayTeam, matchDate, 
				homeScore, awayScore, homeTeamWins, newUrl.contains("MatchCard6.asp"), f, status);
		
		LOGGER.debug("Scraped matchcard: {} vs {} on ", m.getHomeTeamName(), m.getAwayTeamName(), m.getMatchDate());
		
		return m;
	}
	
	private String[] preFillNamesWithWitheld(String[] empty) {
		for (int i = 0; i < empty.length; i++) {
			empty[i] = NAME_WITHHELD;
		}
		return empty;
	}

	
}
