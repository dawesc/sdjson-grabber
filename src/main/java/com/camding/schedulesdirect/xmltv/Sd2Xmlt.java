package com.camding.schedulesdirect.xmltv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;

import javax.xml.bind.Marshaller;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.schedulesdirect.api.Airing;
import org.schedulesdirect.api.ContentRating;
import org.schedulesdirect.api.Lineup;
import org.schedulesdirect.api.Program.Credit;
import org.schedulesdirect.api.Station;
import org.schedulesdirect.api.ZipEpgClient;
import org.schedulesdirect.api.exception.InvalidCredentialsException;
import org.schedulesdirect.grabber.Grabber;

import com.fasterxml.jackson.core.JsonParseException;
import com.xmltv.xmltvschema.Actor;
import com.xmltv.xmltvschema.Channel;
import com.xmltv.xmltvschema.Credits;
import com.xmltv.xmltvschema.Desc;
import com.xmltv.xmltvschema.Director;
import com.xmltv.xmltvschema.DisplayName;
import com.xmltv.xmltvschema.Editor;
import com.xmltv.xmltvschema.EpisodeNum;
import com.xmltv.xmltvschema.Guest;
import com.xmltv.xmltvschema.Icon;
import com.xmltv.xmltvschema.Language;
import com.xmltv.xmltvschema.Length;
import com.xmltv.xmltvschema.Premiere;
import com.xmltv.xmltvschema.Presenter;
import com.xmltv.xmltvschema.PreviouslyShown;
import com.xmltv.xmltvschema.Producer;
import com.xmltv.xmltvschema.Programme;
import com.xmltv.xmltvschema.Rating;
import com.xmltv.xmltvschema.SubTitle;
import com.xmltv.xmltvschema.Subtitles;
import com.xmltv.xmltvschema.Title;
import com.xmltv.xmltvschema.Tv;
import com.xmltv.xmltvschema.Writer;

public class Sd2Xmlt {
	private static File ROOT = new File(new File(System.getProperty("user.dir")), "sd2xmlt");
	private static final SimpleDateFormat DATE_TIMEZONE_XMLTV = new SimpleDateFormat("yyyyMMddHHmmss Z");
	private static final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("yyyyMMdd");
	private static final File ZIP_FILE = new File(System.getProperty("user.dir"), "sd2xmlt.json.zip");
	
	public static void main(String[] args) throws Exception {
		ROOT.mkdirs();
		
		ArrayList<String> argList = new ArrayList<String>();
		Collections.addAll(argList, args);
		argList.add("grab");
		argList.add("--target");
		argList.add(ZIP_FILE.getAbsolutePath().toString());
		if (!argList.contains("--no-grab")) {
			Grabber.main(argList.toArray(new String[0]));
		}
		zipToXml();
	}

	public static void zipToXml() throws IOException, JSONException, JsonParseException {
		if (!ZIP_FILE.exists()) {
			return;
		}
		ZipEpgClient zipClnt = new ZipEpgClient(ZIP_FILE);
		Tv tv = new Tv();

		tv.setDate(DATE_ONLY.format(new Date()));
		tv.setSourceInfoUrl(zipClnt.getBaseUrl());
		tv.setSourceInfoName("Schedules Direct");
		tv.setSourceDataUrl(zipClnt.getBaseUrl());
		tv.setGeneratorInfoName("SD2XML");
		tv.setGeneratorInfoUrl("https://github.com/dawesc/sdjson2xmltv-grabber");
		ArrayList<Channel> channelscl = new ArrayList<Channel>();
		for (Lineup l : zipClnt.getLineups()) {
			for (Station s : l.getStations()) {
				Channel channel = stationToChannel(s);
				channelscl.add(channel);
				for (Airing a : s.getAirings()) {
					tv.getProgramme().add(airingToProgramme(channel, l, s, a));
				}
			}
		}
		Collections.sort(channelscl, new Comparator<Channel>() {
			@Override
			public int compare(Channel o1, Channel o2) {
				return o1.getId().compareTo(o2.getId());
			}
		});
		for (Channel c : channelscl)
			tv.getChannel().add(c);
		
		FileWriter fw = null;
		try {
			// Create JAXB Context
			JAXBContext jaxbContext = JAXBContext.newInstance(Tv.class);

			// Create Marshaller
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

			// Required formatting??
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

			// Print XML String to Console
			fw = new FileWriter(new File(ROOT, "sd2xmlt.xml"));
			
			// Write XML to StringWriter
			jaxbMarshaller.marshal(tv, fw);

		} catch (JAXBException e) {
			e.printStackTrace();
		} finally {
			if (fw != null)
				fw.close();
		}
	}

	private static Programme airingToProgramme(Channel channel, Lineup l, Station s, Airing a) {
		Programme p = new Programme();
		p.setStart(DATE_TIMEZONE_XMLTV.format(a.getGmtStart()));
		Date stop = new Date(a.getGmtStart().getTime() + (a.getDuration() * 1000));
		p.setStop(DATE_TIMEZONE_XMLTV.format(stop));
		p.setChannel(channel.getId());
		if (a.getPartNum() != 0) {
			p.setClumpidx(String.valueOf(a.getPartNum()) + "/" + String.valueOf(a.getTotalParts()));
		}
		if (a.getProgram().getTitle() != null) {
			p.getTitle().add(toTitle(a.getProgram().getTitle()));
		}
		if (a.getProgram().getShortTitles() != null) {
			for (String st : a.getProgram().getShortTitles()) {
				if (st != null) {
					SubTitle subTitle = new SubTitle();
					subTitle.setvalue(st);
					p.getSubTitle().add(subTitle);
				}
			}
		}
		if (a.getProgram().getDescription() != null && a.getProgram().getDescription().length() > 0) {
			p.getDesc().add(toDesc(a.getProgram().getDescriptionLanguage(), a.getProgram().getDescription()));
		}
		if (a.getProgram().getAlternateDescription() != null && a.getProgram().getAlternateDescription().length() > 0) {
			p.getDesc().add(toDesc(a.getProgram().getDescriptionLanguage(), a.getProgram().getAlternateDescription()));
		}
		if (a.getProgram().getAlternateDescriptionShort() != null && a.getProgram().getAlternateDescriptionShort().length() > 0) {
			p.getDesc().add(
					toDesc(a.getProgram().getDescriptionLanguage(), a.getProgram().getAlternateDescriptionShort()));
		}
		p.setCredits(new Credits());
		for (Credit credit : a.getProgram().getCredits()) {
			addCredit(p.getCredits(), credit);	
		}
		if (a.getProgram().getOriginalAirDate() != null) {
			p.setDate(DATE_ONLY.format(a.getProgram().getOriginalAirDate()));
		}
		if (a.getBroadcastLanguage() != null) {
			Language lang = new Language();
			lang.setLang(a.getBroadcastLanguage());
			lang.setvalue(a.getBroadcastLanguage());
			p.setLanguage(lang);
		}
		{
			EpisodeNum en = new EpisodeNum();
			en.setSystem("dd_progid");
			en.setvalue(a.getProgram().getId());
			p.getEpisodeNum().add(en);
		}
		if (a.getProgram().getEpisodeNumber() != null && !a.getProgram().getEpisodeNumber().isEmpty()) {
			EpisodeNum en = new EpisodeNum();
			en.setSystem("onscreen");
			en.setvalue(a.getProgram().getEpisodeNumber());
			p.getEpisodeNum().add(en);
		}
		for (Map<String, Object> maps : a.getProgram().getMetadata()) {
			for (String key : maps.keySet()) {
				if (key.equalsIgnoreCase("Gracenote")) {
					org.json.JSONObject info = (org.json.JSONObject) maps.get(key);
					Integer season = null; //Index from 1
					Integer episode = null; //Index from 1
					Integer totalEpisodes = null;					//FIXME use these
					Integer totalSeasons = null;
					for (String infoName : org.json.JSONObject.getNames(info)) {
						if (infoName.equals("season")) {
							season = info.getInt(infoName);
						} else if (infoName.equals("episode")) {
							episode = info.getInt(infoName);
						} else if (infoName.equals("totalEpisodes")) {
							totalEpisodes = info.getInt(infoName);
						} else if (infoName.equals("totalSeasons")) {
							totalSeasons = info.getInt(infoName);
						} else {
							throw new RuntimeException("What is Gracenote." + infoName + "\n" + info.toString() + "\n" + a.toString() + "\n" + p.toString());
						}
					}
					if (season != null && episode != null) {
						{
							EpisodeNum en = new EpisodeNum();
							en.setSystem("common");
							en.setvalue("S" + StringUtils.leftPad(season.toString(), 2, '0') + "E" + StringUtils.leftPad(episode.toString(), 2, '0'));
							p.getEpisodeNum().add(en);
						}
						if (season > 0 && episode > 0) {
							EpisodeNum en = new EpisodeNum();
							en.setSystem("xmltv_ns");
							en.setvalue(String.valueOf(season - 1) + "." + String.valueOf(episode - 1) + ".");
							if (a.getPartNum() != 0) {
								en.setvalue(String.valueOf(season - 1) + "." + String.valueOf(episode - 1) + "." + String.valueOf(a.getPartNum() - 1) + "/" + String.valueOf(a.getTotalParts()));
							}
							p.getEpisodeNum().add(en);
						}
					}
				} else if (key.equalsIgnoreCase("TheTVDB")) {
					org.json.JSONObject info = (org.json.JSONObject) maps.get(key);
					Integer season    = null;   //Index from 1
					Integer episode   = null;   //Index from 1
					Integer episodeID = null;   //Index from 1
					Integer seriesID  = null;   //Index from 1
					//FIXME use these
					for (String infoName : org.json.JSONObject.getNames(info)) {
						if (infoName.equals("season")) {
							season = info.getInt(infoName);
						} else if (infoName.equals("episode")) {
							episode = info.getInt(infoName);
						} else if (infoName.equals("episodeID")) {
							episode = info.getInt(infoName);
						} else if (infoName.equals("seriesID")) {
							episode = info.getInt(infoName);
						} else {
							throw new RuntimeException("What is TheTVDB." + infoName + "\n" + info.toString() + "\n" + a.toString() + "\n" + p.toString());
						}
					}
				} else {
					throw new RuntimeException("What is " + key);
				}
			}
		}			

		if (a.getProgram().getRunTime() != 0) {
			Length len = new Length();
			len.setUnits("seconds");
			len.setvalue(String.valueOf(a.getProgram().getRunTime()));
			p.setLength(len);
		}
		if (a.getPremiereStatus() != null) {
			Premiere prem = new Premiere();
			prem.setLang("en");
			prem.setvalue(a.getPremiereStatus().toString());
			p.setPremiere(prem);
		}
		if (a.getProgram().getRatings() != null) {
			for (ContentRating cr : a.getProgram().getRatings()) {
				p.getRating().add(crToRating(cr));
			}
		}
		if (a.getSubtitleLanguage() != null) {
			Subtitles sub = new Subtitles();
			Language lang = new Language();
			lang.setLang(a.getSubtitleLanguage());
			lang.setvalue(a.getSubtitleLanguage());
			sub.setLanguage(lang);
			sub.setType("teletext");
			p.getSubtitles().add(sub);
		}
		PreviouslyShown ps = new PreviouslyShown();
		if (a.getProgram().getOriginalAirDate() != null && a.getProgram().getOriginalAirDate().getTime() < a.getGmtStart().getTime()) {
			ps.setStart(DATE_TIMEZONE_XMLTV.format(a.getProgram().getOriginalAirDate()));
		}
		p.setPreviouslyShown(ps);
		return p;
	}

	private static void addCredit(Credits credits, Credit credit) {
		switch (credit.getRole()) {
			case UNKNOWN:
				/* Not used */
				return;
			case ACTOR:
			case HOST:
			case JUDGE:
			case NARRATOR:
			case VOICE:
				Actor actor = new Actor();
				actor.setRole(credit.getCharacterName());
				actor.setvalue(credit.getName());
				credits.getActor().add(actor);
				return;
			case ANCHOR:
				credits.getPresenter().add(getPresenter(credit.getName()));
				return;
			case GUEST_STAR:
			case MUSICAL_GUEST:
			case GUEST:
			case CONTESTANT:
				credits.getGuest().add(getGuest(credit.getName()));
				return;
			case CORRESPONDENT:
				credits.getPresenter().add(getPresenter(credit.getName()));
				return;
			case ASSISTANT_DIRECTOR:
			case DIRECTOR:
			case CASTING_DIRECTOR:
				credits.getDirector().add(getDirector(credit.getName()));
				return;
			case ASSOCIATE_PRODUCER:
			case EXECUTIVE_PRODUCER:
			case PRODUCER:
				credits.getProducer().add(getProducer(credit.getName()));
				return;
			case WRITER:
				credits.getWriter().add(getWriter(credit.getName()));
				return;
			case FILM_EDITOR:
				credits.getEditor().add(getEditor(credit.getName()));
				return;
			case COSTUME_DESIGNER:
			case SET_DECORATION:
			case ART_DIRECTION:
			case PRODUCTION_DESIGNER:
			case CASTING:
			case CINEMATOGRAPHER:
			case ORIGINAL_MUSIC:
			case COMPOSER:
			case PRODUCTION_MANAGER:
			case DIRECTORY_OF_PHOTOGRAPHY:
			case VISUAL_EFFECTS:
				break; /* Not supported */
		}
	}

	private static Editor getEditor(String name) {
		Editor editor = new Editor();
		editor.setvalue(name);
		return editor;
	}

	private static Writer getWriter(String name) {
		Writer writer = new Writer();
		writer.setvalue(name);
		return writer;
	}

	private static Producer getProducer(String name) {
		Producer producer = new Producer();
		producer.setvalue(name);
		return producer;
	}

	private static Director getDirector(String name) {
		Director director = new Director();
		director.setvalue(name);
		return director;
	}

	private static Guest getGuest(String name) {
		Guest guest = new Guest();
		guest.setvalue(name);
		return guest;
	}

	private static Presenter getPresenter(String name) {
		Presenter presenter = new Presenter();
		presenter.setvalue(name);
		return presenter;
	}

	private static Rating crToRating(ContentRating cr) {
		Rating r = new Rating();
		r.setSystem(cr.getBody());
		r.setValue(cr.getRating());
		return r;
	}

	private static Desc toDesc(String descriptionLanguage, String description) {
		Desc desc = new Desc();
		desc.setLang(descriptionLanguage);
		desc.setvalue(description);
		return desc;
	}

	private static Title toTitle(String title) {
		Title t = new Title();
		t.setvalue(title);
		return t;
	}

	private static Channel stationToChannel(Station s) throws IOException {
		Channel c = new Channel();
		c.setId(s.getId());

		if (s.getName() != null && s.getCallsign() != null) {
			c.setId(s.getCallsign());
			c.getDisplayName().add(getDisplayName(s.getName()));
		} else if (s.getName() != null) {
			c.setId(s.getName());
			c.getDisplayName().add(getDisplayName(s.getName()));
		} else if (s.getCallsign() != null) {
			c.setId(s.getCallsign());
			c.getDisplayName().add(getDisplayName(s.getCallsign()));
		} else {
			c.setId(s.getId());
		}
		if (s.getLogicalChannelNumber() != null) {
			c.getDisplayName().add(getDisplayName(s.getLogicalChannelNumber()));
		}
		File logoFile = null;
		if (s.getLogo() != null) {
			logoFile = new File(ROOT, s.getId() + ".png");
			if (!logoFile.exists())
				s.getLogo().writeImageToFile(logoFile);
			Icon icon = new Icon();
			icon.setHeight(String.valueOf(s.getLogo().getHeight()));
			icon.setWidth(String.valueOf(s.getLogo().getWidth()));
			icon.setSrc(logoFile.getAbsolutePath());
			c.getIcon().add(icon);
		}
		return c;
	}

	private static DisplayName getDisplayName(String callsign) {
		DisplayName dn = new DisplayName();
		dn.setvalue(callsign);
		return dn;
	}

	private static void execute(Grabber grabber, String[] cmd, String[] args)
			throws IOException, InvalidCredentialsException {
		ArrayList<String> argsPlus = new ArrayList<String>();
		Collections.addAll(argsPlus, args);
		Collections.addAll(argsPlus, cmd);
		grabber.execute(argsPlus.toArray(new String[0]));
	}
}
