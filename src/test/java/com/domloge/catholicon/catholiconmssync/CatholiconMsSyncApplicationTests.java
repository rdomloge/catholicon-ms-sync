package com.domloge.catholicon.catholiconmssync;

import com.domloge.catholiconmsmatchcardlibrary.Fixture;
import com.domloge.catholiconmsmatchcardlibrary.Matchcard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatholiconMsSyncApplicationTests {

	@Autowired
	ObjectMapper mapper;

	@Test
	void contextLoads() {
	}

	@Test
	void fixtureSerializable() throws JsonProcessingException {
		Fixture f = new Fixture();
		f.setId("1");
		Matchcard m = new Matchcard();
		m.setId("2");
		// m.setFixture(f);
		f.setMatchCard(m);
		byte[] bytes = mapper.writeValueAsBytes(f);
		System.out.println("Serialised to "+new String(bytes));
	}

}
