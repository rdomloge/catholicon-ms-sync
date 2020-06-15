package com.domloge.catholicon.catholiconmssync;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
public class Matchcard {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	
	@Column(unique=true)
	private int fixtureId;
	
	@ElementCollection(fetch = FetchType.EAGER, targetClass = String.class)
	@OrderColumn(name = "PLAYERORDER")
	private List<String> homePlayers;
	
	@ElementCollection(fetch = FetchType.EAGER, targetClass = String.class)
	@OrderColumn(name = "PLAYERORDER")
	private List<String> awayPlayers;
	
	private String homeTeamName;
	
	private int homeScore;
	
	private String awayTeamName;
	
	private int awayScore;
	
	/**
	 * 
	 * https://stackoverflow.com/questions/23409026/duplicate-entry-by-hibernate-on-join-table-in-springmvc
	 * 
	 * This issue is really just a result of the well documented effect of using a List without an Index column. 
	 * This causes Hibernate to treat the List like a Bag and so deleting the join table before inserting is 
	 * expected and necessary behaviour. Bags and their behaviour are well documented in countless blogs and 
	 * the Hibernate documentation so I won't go into any of that here.
	 * The solution was just to slap a @OrderColumn annotation on each collection. With this index hibernate 
	 * no longer needs to treat the List like a Bag and so has no need to perform the delete before insert. 
	 * So no need to use the tables of outer joins.
	 * 
	 * So then I added an 'order column' with the name 'order' and that broke it due to this being a reserved keyword
	 * 
	 * https://stackoverflow.com/questions/20152311/hibernate-table-not-found-error-on-runtime
	 */
	@ElementCollection(fetch = FetchType.EAGER, targetClass = Boolean.class)
	@OrderColumn(name = "RUBBER") // this is critical to prevent issues with findById returning a list with > 2k elements
	private List<Boolean> homeTeamWins;
	
	@OneToMany(fetch = FetchType.EAGER, cascade=CascadeType.ALL)
	@OrderColumn(name = "RUBBER")
	private List<Rubber> rubbers = new LinkedList<Rubber>();
	
	private boolean teamSize6;

	private String matchDate;
	
	
	
	public Matchcard(Map<Integer, Rubber> scoreMap, String[] homePlayers, String[] awayPlayers, String homeTeam,
			String awayTeam, String matchDate, int homeScore, int awayScore, Boolean[] homeTeamWins,
			boolean teamSize6, int fixtureId) {
		
		rubbers.addAll(scoreMap.values());
		this.homePlayers = Arrays.asList(homePlayers);
		this.awayPlayers = Arrays.asList(awayPlayers);
		this.homeTeamName = homeTeam;
		this.awayTeamName = awayTeam;
		this.matchDate = convertDayMonthYearToYearMonthDay(matchDate);
		
		this.homeScore = homeScore;
		this.awayScore = awayScore;
		
		this.homeTeamWins = Arrays.asList(homeTeamWins);
		this.teamSize6 = teamSize6;
		this.fixtureId = fixtureId;
	}
	
	public Matchcard() {
		
	}

	public static String convertDayMonthYearToYearMonthDay(String dayMonthYear) {
		SimpleDateFormat dmyFormat = new SimpleDateFormat("dd MMM yyyy");
		SimpleDateFormat ymdFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return ymdFormat.format(dmyFormat.parse(dayMonthYear));
		} 
		catch (ParseException e) {
			return "[Cannot parse] "+dayMonthYear;
		}
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getMatchDate() {
		return matchDate;
	}
	
	public void setMatchDate(String matchDate) {
		this.matchDate = matchDate;
	}
	
	public int getFixtureId() {
		return fixtureId;
	}

	public void setFixtureId(int fixtureId) {
		this.fixtureId = fixtureId;
	}

	public List<String> getHomePlayers() {
		return homePlayers;
	}

	public void setHomePlayers(List<String> homePlayers) {
		this.homePlayers = homePlayers;
	}

	public List<String> getAwayPlayers() {
		return awayPlayers;
	}

	public void setAwayPlayers(List<String> awayPlayers) {
		this.awayPlayers = awayPlayers;
	}

	public String getHomeTeamName() {
		return homeTeamName;
	}

	public void setHomeTeamName(String homeTeamName) {
		this.homeTeamName = homeTeamName;
	}

	public int getHomeScore() {
		return homeScore;
	}

	public void setHomeScore(int homeScore) {
		this.homeScore = homeScore;
	}

	public String getAwayTeamName() {
		return awayTeamName;
	}

	public void setAwayTeamName(String awayTeamName) {
		this.awayTeamName = awayTeamName;
	}

	public int getAwayScore() {
		return awayScore;
	}

	public void setAwayScore(int awayScore) {
		this.awayScore = awayScore;
	}

	public List<Boolean> getHomeTeamWins() {
		return homeTeamWins;
	}

	public void setHomeTeamWins(List<Boolean> homeTeamWins) {
		this.homeTeamWins = homeTeamWins;
	}

	public List<Rubber> getRubbers() {
		return rubbers;
	}

	public void setRubbers(List<Rubber> rubbers) {
		this.rubbers = rubbers;
	}

	public boolean isTeamSize6() {
		return teamSize6;
	}

	public void setTeamSize6(boolean teamSize6) {
		this.teamSize6 = teamSize6;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this, "id");
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj, "id");
	}
	
}
