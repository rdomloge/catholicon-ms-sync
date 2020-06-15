package com.domloge.catholicon.catholiconmssync;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Entity
public class Rubber {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	
	@OneToOne(fetch = FetchType.EAGER, cascade=CascadeType.ALL)
	private Game firstGame;
	
	@OneToOne(fetch = FetchType.EAGER, cascade=CascadeType.ALL)
	private Game secondGame;
	
	@OneToOne(fetch = FetchType.EAGER, cascade=CascadeType.ALL)
	private Game finalGame;
	
	
	
	public void setGameProgrammatically(int gameNum, boolean homeScore, String score) {
		Game g = null;
		switch(gameNum) {
			case 1:
				if(null == firstGame) firstGame = new Game();
				g = firstGame;
				break;
			case 2:
				if(null == secondGame) secondGame = new Game();
				g = secondGame;
				break;
			case 3:
				if(null == finalGame) finalGame = new Game();
				g = finalGame;
				break;
			default:
				throw new IllegalArgumentException("Invalid gameNum param: "+gameNum);
		}
		
		g.setGameNum(gameNum);
		
		if(homeScore) g.setHomeScore(Integer.parseInt(score));
		else g.setAwayScore(Integer.parseInt(score));
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Game getFirstGame() {
		return firstGame;
	}

	public void setFirstGame(Game firstGame) {
		this.firstGame = firstGame;
	}

	public Game getSecondGame() {
		return secondGame;
	}

	public void setSecondGame(Game secondGame) {
		this.secondGame = secondGame;
	}

	public Game getFinalGame() {
		return finalGame;
	}

	public void setFinalGame(Game finalGame) {
		this.finalGame = finalGame;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
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
