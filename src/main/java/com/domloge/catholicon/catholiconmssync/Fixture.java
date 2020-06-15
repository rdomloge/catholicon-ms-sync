package com.domloge.catholicon.catholiconmssync;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
public class Fixture {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	
	@Column(unique=true)
	private int fixtureId;
	
	private int divisionId;
	
	private String matchDate;
	
	private int homeTeamId;
	
	private int awayTeamId;
	
	@OneToOne(cascade = CascadeType.ALL)
	private Matchcard matchCard;
	
	private int season;
	
	
	public Fixture() {
	}

	public Fixture(int fixtureId, String matchDate, int homeTeamId, int awayTeamId, int divisionId, Matchcard card, int season) {
		this.fixtureId = fixtureId;
		this.divisionId = divisionId;
		this.matchDate = matchDate;
		this.homeTeamId = homeTeamId;
		this.awayTeamId = awayTeamId;
		this.matchCard = card;
		this.season = season;
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public int getFixtureId() {
		return fixtureId;
	}
	
	public void setFixtureId(int fixtureId) {
		this.fixtureId = fixtureId;
	}
	
	public int getDivisionId() {
		return divisionId;
	}

	public void setDivisionId(int divisionId) {
		this.divisionId = divisionId;
	}

	public String getMatchDate() {
		return matchDate;
	}
	
	public void setMatchDate(String matchDate) {
		this.matchDate = matchDate;
	}
	
	public int getHomeTeamId() {
		return homeTeamId;
	}
	
	public void setHomeTeamId(int homeTeamId) {
		this.homeTeamId = homeTeamId;
	}
	
	public int getAwayTeamId() {
		return awayTeamId;
	}
	
	public void setAwayTeamId(int awayTeamId) {
		this.awayTeamId = awayTeamId;
	}
	
	public Matchcard getMatchCard() {
		return matchCard;
	}

	public void setMatchCard(Matchcard matchCard) {
		this.matchCard = matchCard;
	}

	public int getSeason() {
		return season;
	}

	public void setSeason(int season) {
		this.season = season;
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
